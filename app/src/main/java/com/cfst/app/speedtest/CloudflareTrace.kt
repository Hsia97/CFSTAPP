package com.cfst.app.speedtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object CloudflareTrace {
    private const val TRACE_URL = "http://speed.cloudflare.com/cdn-cgi/trace"
    private const val HOST_NAME = "speed.cloudflare.com"

    private val sharedConnectionPool = ConnectionPool(10, 2, TimeUnit.MINUTES)
    private val baseClient = OkHttpClient.Builder()
        .connectionPool(sharedConnectionPool)
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    suspend fun getRegionCode(ip: String): String = withContext(Dispatchers.IO) {
        try {
            val client = baseClient.newBuilder()
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        if (hostname == HOST_NAME) {
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
                .url(TRACE_URL)
                .header("Host", HOST_NAME)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext "N/A"

                val body = response.body?.string().orEmpty()
                val coloRegex = Regex("""(?m)^colo=([A-Z0-9]+)""")
                val match = coloRegex.find(body)
                match?.groupValues?.get(1) ?: "N/A"
            }
        } catch (e: Exception) {
            "N/A"
        }
    }
}
