# 阶段 3 自定义域名连通性校验实施方案 (定稿版 - 已吸收多模型审查意见)

## 一、 背景与业务目标
针对用户在实际使用中“测速良好但对自己域名/VPS 不生效”的核心痛点（源于不同 SNI 阻断策略差异与 Cloudflare 边缘节点 IP 池权限隔离），新增 **“自定义域名连通性校验（Domain Prober）”** 功能：
1. **用户输入**：允许用户配置自己的域名（如 `my.example.com`）；
2. **真实 TLS + SNI 握手探测**：在第一阶段 Ping 筛选出优选 IP 后，模拟真实客户端对该域名发起带有 SNI 的 HTTPS 探测；
3. **精准淘汰**：过滤掉发生 TLS 握手失败、超时或返回 Cloudflare `Error 1000/1001/1006/1020` 拒绝代理错误的 IP；
4. **开关控制**：提供独立配置开关，开启后生效，关闭时保持原样。

---

## 二、 交叉审查结论与落地修正点 (Reviewer Feedback Addressed)

1. **P1 兼顾 `pingOnly` 模式与下载模式**：
   域名校验统一在第一阶段 `pingCandidates` 筛选出延迟合格的候选 IP 后立即触发；通过域名验证的 IP 集合既用于 `pingOnly` 结果输出，也用于后续下载精测，杜绝仅 Ping 模式下功能静默失效。
2. **P1 域名输入超强容错清洗**：
   剥离协议头（`http://`、`https://`）、去除多余路径（`/path`）、查询参数（`?query`）及端口（`:8443`），统一转为标准小写 Host，避免 OkHttp DNS 因非法 Host 抛出 `UnknownHostException`。
3. **P1 Cloudflare 拦截特征全面识别**：
   不再仅仅检测 1000/1001，采用正则匹配 Cloudflare 常见的边缘拦截特征（如 `Error 10\d{2}`：1000、1001、1003、1006、1015、1020 规则封禁等）。
4. **P2 并发限流与 `shouldStop` 响应**：
   探测过程复用单例 OkHttpClient，并使用 `Semaphore(maxConcurrentPings)` 进行并发限流，且在任务内响应 `shouldStop.get()`，确保停止测速时立刻释放。

---

## 三、 详细技术架构与实现

### 1. 新增探测工具模块：`app/src/main/java/com/cfst/app/speedtest/DomainProber.kt`
```kotlin
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
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val cfErrorRegex = Regex("""(?i)error\s+10\d{2}|dns\s+points\s+to\s+prohibited\s+ip""", RegexOption.IGNORE_CASE)

    fun cleanDomain(raw: String): String {
        var domain = raw.trim()
        if (domain.startsWith("http://", ignoreCase = true)) domain = domain.substring(7)
        if (domain.startsWith("https://", ignoreCase = true)) domain = domain.substring(8)
        domain = domain.substringBefore('/').substringBefore('?').substringBefore('#')
        domain = domain.substringBefore(':')
        return domain.trim().lowercase()
    }

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
                val body = response.body?.string().orEmpty()
                if (cfErrorRegex.containsMatchIn(body)) {
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

### 2. 测速引擎全链路整合：`SpeedTestEngine.kt`
- `TestConfig` 补充：
  ```kotlin
  val enableDomainProbe: Boolean = false,
  val customProbeDomain: String = ""
  ```
- 探测位置：在 Ping 粗筛得到 `pingResults` 后：
  ```kotlin
  val candidatePool = if (config.enableDomainProbe && config.customProbeDomain.isNotBlank()) {
      val cleaned = DomainProber.cleanDomain(config.customProbeDomain)
      onProgress(TestProgress(current = 0, total = pingResults.size, currentIp = cleaned, status = "正在校验自定义域名连通性..."))
      val probeSemaphore = Semaphore(config.maxConcurrentPings.coerceAtLeast(1))
      coroutineScope {
          pingResults.map { pair ->
              async {
                  if (shouldStop.get()) return@async null
                  probeSemaphore.withPermit {
                      if (shouldStop.get()) return@withPermit null
                      val isOk = DomainProber.probeDomain(pair.first, cleaned)
                      if (isOk) pair else null
                  }
              }
          }.awaitAll().filterNotNull()
      }
  } else {
      pingResults
  }
  ```
  后续 `pingOnly` 分支和下载测速分支均直接消费 `candidatePool`，确保两模式全覆盖。

### 3. ViewModel 状态与持久化：`SpeedTestViewModel.kt`
- 暴露 `enableDomainProbe: StateFlow<Boolean>` 与 `customProbeDomain: StateFlow<String>`；
- 在 `app_prefs` 中持久化 `custom_probe_domain` 与 `enable_domain_probe`；
- 在 `startTest()` 构建 `TestConfig` 时注入这两个字段。

### 4. UI 界面呈现：`MainActivity.kt`
- 在 `EnhancedSettingsDialog` 的【卡片 3：IP 地址池与网络管理】中增设：
  - Switch 开关行：`仅保留对自定义域名生效的 IP`；
  - 展开卡片：
    - `OutlinedTextField`：输入自定义域名（如 `my.example.com`）；
    - 辅助说明文字：`仅保留能成功完成此域名 TLS 握手且无 1000/1001 错误的 IP`。

---

## 四、 验收契约 (Acceptance Criteria)

1. **功能逻辑验证**：
   - 开启开关且输入有效域名时，测速过程触发域名探测，遭遇 TLS 阻断或 Cloudflare 1000/1001 错误的 IP 被成功剔除；
   - 在 `pingOnly` 和下载测速模式下均统一生效；关闭开关时零额外探测；
2. **容错与超时防护**：
   - 输入 `https://my.example.com:8443/` 等不规范输入时自动清洗为 `my.example.com`，不引发异常崩溃；
   - 探测单 IP 超时限制 2.5 秒，受 `Semaphore` 限流保护，停止测速时秒级中断；
3. **编译与持久化**：
   - 重新启动应用能恢复保存的自定义域名；
   - 编译检查通过，零破坏性变更。
