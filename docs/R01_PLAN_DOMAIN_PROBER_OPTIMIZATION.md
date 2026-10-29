# R01_PLAN: 自定义域名连通性探测精度与实时进度倒计时优化方案 (定稿版 - 已吸收双审查意见)

## 一、 审查裁决与关键架构修正 (Reviewer Verdict)

经过 GLM 与 DeepSeek 双模型独立审查，对方案进行了关键的逻辑升级，彻底兼顾了“防误杀（消除假阴性）”与“防劫持（消除假阳性）”：

1. **倒计时与进度平滑机制（全票通过）**：
   引入 `AtomicInteger(0)` 在 `probeSemaphore.withPermit` 内部每完成一个 IP 即刻上报，消除“瞬间跳过”，让用户清晰看到平滑递增。
2. **拒绝盲目 `hostnameVerifier=true`，引入 `cf-ray` 边缘身份鉴权（核心安全加固）**：
   - 若粗暴关闭主机名校验，中间人劫持（如国内运营商劫持页、校园网/酒店 Portal 认证）也会被判通，造成严重的“假阳性”；
   - 必须通过**双重身份验证**：
     - ① **来源合法性**：响应头必须包含 `cf-ray` 或 `server: cloudflare`（铁证证明该 IP 到达了真实的 Cloudflare 边缘反代节点）；
     - ② **业务可用性**：响应体（利用 `peekBody(4096)` 快速读取前 4KB 防泄漏与超时）**不得包含** `cfErrorRegex`（Error 1000/1001/1006/1020 拒绝代理错误）。
3. **禁用重定向 (`followRedirects = false`)**：
   禁止自动跳转外部站点，防止脱离当前被测 IP 的自定义 DNS 绑定。
4. **放宽探测超时**：由 2500ms 调整为 3000ms，为跨洋节点握手留足缓冲。

---

## 二、 详细代码实现规格

### 1. `SpeedTestEngine.kt` 进度平滑改造
```kotlin
val probeCompleted = java.util.concurrent.atomic.AtomicInteger(0)
val totalProbes = pingResults.size

coroutineScope {
    pingResults.map { pair ->
        async {
            if (shouldStop.get()) return@async null
            probeSemaphore.withPermit {
                if (shouldStop.get()) return@withPermit null
                val isOk = DomainProber.probeDomain(pair.first, cleanedDomain)
                val done = probeCompleted.incrementAndGet()
                onProgress(
                    TestProgress(
                        current = done,
                        total = totalProbes,
                        currentIp = pair.first,
                        status = "正在校验自定义域名 (${done}/${totalProbes})..."
                    )
                )
                if (isOk) pair else null
            }
        }
    }.awaitAll().filterNotNull()
}
```

### 2. `DomainProber.kt` 网络探测逻辑改造
```kotlin
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

                // 2. 读取前 4KB 防流泄漏与大文件超时，检查是否包含 Cloudflare 错误代码
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
}
```

---

## 三、 验收契约 (Acceptance Criteria)

1. **实时进度条**：探测 42 个 IP 时，状态行与环形进度条平滑递增展示 `(1/42) -> (2/42) -> ... -> (42/42)`；
2. **防误杀与防劫持兼得**：
   - 正常支持该域名的有效 Cloudflare 节点均能凭借 `cf-ray` 或 `server: cloudflare` 顺利通过探测并进入测速；
   - 遭遇劫持或 Cloudflare 1000 错误的节点被精准过滤；
3. **性能保护**：`peekBody(4096)` 避免大体积网页加载造成超时。
