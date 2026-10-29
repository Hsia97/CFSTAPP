# 阶段 1 优化实施方案 (定稿版 - 已吸收多模型交叉审查意见)

## 一、 背景与范围
根据用户指令，本次优化仅执行阶段 1 的第 1 项与第 4 项（第 2、3 项不做）：
1. **任务 1：清理冗余代码**：安全移除 `MainActivity.kt` 中未引用的遗留版 `SettingsDialog`；
2. **任务 4：接入 Cloudflare Colo 识别**：对测速 IP 获取数据中心代码（Colo，如 HKG、SJC、NRT 等），填补数据模型 `regionCode`，并在 UI 与 CSV 导出中正常呈现。

---

## 二、 交叉审查结论与架构修正点 (Reviewer Feedback Addressed)

经过多模型交叉审查，方案吸纳了以下关键修正，确保零破坏性回归：
1. **P0 边界校准**：`MainActivity.kt` 中第 350~408 行是活跃在用的 `ResultItem`，绝对不可误删！真正待删除的废弃 `SettingsDialog` 符号边界为 **第 409 行 `@OptIn` 至 第 709 行闭合大括号 `}`**。
2. **P1 避免信号量阻塞**：Colo 请求必须在 `downloadSemaphore.withPermit` 外发起异步 `async`，使其与 10 秒下载测速并行重叠，下载完成后直接 `await()`，不占用下载信号量配额与时间。
3. **P1 连接池与资源复用**：`CloudflareTrace` 复用单例 OkHttpClient 及其 ConnectionPool，仅通过 `newBuilder().dns(...)` 派生轻量配置，短超时（connectTimeout: 2s, readTimeout: 2s），避免频繁创建实例造成连接或线程泄漏。
4. **P2 窄屏 UI 保护**：`ResultItem` 中将 Colo 标签放置在 IP 列内部（IP 地址下方或右侧加小 Badge），设置 `maxLines = 1`，避免在超长 IPv6 下挤压右侧的延迟与速度数值。

---

## 三、 具体实现细节

### 1. 任务 1：清理废弃 `SettingsDialog`
- **目标文件**：`app/src/main/java/com/cfst/app/MainActivity.kt`
- **改动范围**：以符号为界，完整移除 `fun SettingsDialog` 函数及其上方注解（第 409~709 行）。
- **保留项**：严格保留 `ResultItem`（350~408 行）和 `EnhancedSettingsDialog`（711 行起）以及 `SpecifiedSegmentsDialog`。

### 2. 任务 4：接入 Cloudflare Colo 识别
#### (1) 新建 `app/src/main/java/com/cfst/app/speedtest/CloudflareTrace.kt`
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

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext "N/A"

            val body = response.body?.string().orEmpty()
            // 匹配 colo=XXX
            val coloRegex = Regex("""(?m)^colo=([A-Z0-9]+)""")
            val match = coloRegex.find(body)
            match?.groupValues?.get(1) ?: "N/A"
        } catch (e: Exception) {
            "N/A"
        }
    }
}
```

#### (2) 修改 `SpeedTestEngine.kt`
- 在 `pingOnly` 分支：对 ping 成功的候选 IP，通过 `async` 并行拉取 `regionCode`，并注入 `SpeedTestResult`。
- 在 `downloadTasks` 分支：在进入 `downloadSemaphore.withPermit` 前，先 `val regionCodeDeferred = async(Dispatchers.IO) { CloudflareTrace.getRegionCode(ip) }`；下载完成后通过 `regionCodeDeferred.await()` 填充。
- 在逐个测速分支（`pingFirst == false`）：测速时同步/异步获取 `regionCode`。

#### (3) 修改 `MainActivity.kt` 呈现 Colo
- 在 `ResultItem` 中，在 IP 地址同一行增加圆角小标签展示 Colo（若为 `"N/A"` 则不显示或弱化展示）：
  ```kotlin
  Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          text = result.ipAddress,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp
      )
      if (result.regionCode.isNotEmpty() && result.regionCode != "N/A") {
          Spacer(modifier = Modifier.width(6.dp))
          Surface(
              shape = RoundedCornerShape(4.dp),
              color = Color(0xFFE3F2FD)
          ) {
              Text(
                  text = result.regionCode,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF1976D2),
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                  maxLines = 1
              )
          }
      }
  }
  ```
- 在 `HistoryDetailDialog` 中展示 `数据中心：${result.regionCode}`。

---

## 四、 验收契约 (Acceptance Criteria)

1. **编译与打包**：`./gradlew.bat assembleDebug` 成功通过，0 编译错误。
2. **代码卫生**：`MainActivity.kt` 中废弃的 `SettingsDialog` 彻底消除，代码行数减少 290+ 行，`ResultItem` 完好无损。
3. **Colo 探测性能**：Colo 请求单次硬超时 2 秒，完全不阻塞 `downloadSemaphore`。
4. **数据与导出一致性**：CSV 导出的“地区码”列从全 `"N/A"` 变为真实数据中心三字码，格式零破坏性变更。
