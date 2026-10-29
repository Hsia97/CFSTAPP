package com.cfst.app.speedtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object DomainProber {
    private val sharedConnectionPool = ConnectionPool(10, 2, TimeUnit.MINUTES)
    private val baseClient = OkHttpClient.Builder()
        .connectionPool(sharedConnectionPool)
        .connectTimeout(3000, TimeUnit.MILLISECONDS)
        .readTimeout(3000, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val cfErrorRegex = Regex("""(?i)error\s+10\d{2}|dns\s+points\s+to\s+prohibited\s+ip""")

    /**
     * 清洗用户输入的域名，剥离协议头、端口和多余路径
     */
    fun cleanDomain(raw: String): String {
        var domain = raw.trim()
        if (domain.startsWith("http://", ignoreCase = true)) domain = domain.substring(7)
        if (domain.startsWith("https://", ignoreCase = true)) domain = domain.substring(8)
        domain = domain.substringBefore('/').substringBefore('?').substringBefore('#')
        domain = domain.substringBefore(':')
        return domain.trim().lowercase()
    }

    /**
     * 对特定 IP 针对自定义域名进行真实 HTTPS + SNI 握手探测
     * @return true 表示成功到达 Cloudflare 边缘反向代理且无 1000/1001 拒绝代理错误
     */
    suspend fun probeDomain(ip: String, rawDomain: String): Boolean = withContext(Dispatchers.IO) {
        val targetHost = cleanDomain(rawDomain)
        if (targetHost.isBlank()) return@withContext true

        try {
            val client = baseClient.newBuilder()
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        if (hostname.equals(targetHost, ignoreCase = true)) {
                            return try {
                                listOf(InetAddress.getByName(ip))
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }
                        return Dns.SYSTEM.lookup(hostname)
                    }
                })
                .build()

            val request = Request.Builder()
                .url("https://$targetHost/")
                .header("Host", targetHost)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                // 1. 验证是否真实到达 Cloudflare 边缘反代节点（防劫持、防伪造 Portal）
                val hasCfIdentity = response.header("cf-ray") != null ||
                        response.header("server")?.contains("cloudflare", ignoreCase = true) == true
                if (!hasCfIdentity) {
                    return@withContext false
                }

                // 2. 读取前 4KB 防流泄漏与大响应超时，检查是否包含 Cloudflare 致命错误
                val peekedBody = try {
                    response.peekBody(4096).string()
                } catch (e: Exception) {
                    ""
                }

                if (cfErrorRegex.containsMatchIn(peekedBody)) {
                    return@withContext false
                }

                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * WebSocket 专属通道全链路穿透校验
     * 判定标准：101 Switching Protocols 或 400 + Sec-WebSocket-Version
     */
    suspend fun probeWebSocket(ip: String, rawDomain: String, rawPath: String): Boolean = withContext(Dispatchers.IO) {
        val targetHost = cleanDomain(rawDomain)
        if (targetHost.isBlank()) return@withContext true

        var path = rawPath.trim()
        if (path.isBlank()) path = "/"
        if (!path.startsWith("/")) path = "/$path"

        try {
            val client = baseClient.newBuilder()
                .connectTimeout(2000, TimeUnit.MILLISECONDS)
                .readTimeout(2000, TimeUnit.MILLISECONDS)
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        if (hostname.equals(targetHost, ignoreCase = true)) {
                            return try {
                                listOf(InetAddress.getByName(ip))
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }
                        return Dns.SYSTEM.lookup(hostname)
                    }
                })
                .build()

            val request = Request.Builder()
                .url("https://$targetHost$path")
                .header("Host", targetHost)
                .header("Upgrade", "websocket")
                .header("Connection", "Upgrade")
                .header("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")
                .header("Sec-WebSocket-Version", "13")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                code == 101 || (code == 400 && response.header("Sec-WebSocket-Version") != null)
            }
        } catch (e: Exception) {
            false
        }
    }
}
