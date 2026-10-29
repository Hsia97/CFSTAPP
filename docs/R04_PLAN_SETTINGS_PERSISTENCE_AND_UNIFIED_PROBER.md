# R04_PLAN: 统一域名/节点探针与全量配置持久化及恢复默认方案 (定稿版)

## 一、 审查裁决与关键架构修正 (Reviewer Verdict)

经过双模型独立审查，对方案进行了两项关键的工业级体验与架构加固：
1. **模式动态回显（消除隐式路由黑盒）**：
   在统一探针输入卡片内，根据 `targetWsPath` 是否填写，动态向用户呈现当前生效模式：
   - 留空时：绿底提示 `当前模式：Web 网页 TLS 连通性校验`；
   - 填写路径时：紫底提示 `当前模式：专属通道 (WS) 全链路穿透校验 (过滤假通死节点)`。
2. **全量配置持久化与 UI 双向同步重置**：
   - 全量持久化 14 项配置（参数修改即时 `apply()` 异步落盘，应用冷启动全量恢复）；
   - 在设置面板提供「恢复默认设置」动作，不仅重置 ViewModel 与 `app_prefs`，同时重置 UI 中所有局部输入框（`remember` 文本状态），确保界面即刻刷回默认值。

---

## 二、 详细代码实现规格

### 1. `SpeedTestEngine.kt` 探针统一分流
在 `TestConfig` 中收敛字段：
```kotlin
val enableTargetProbe: Boolean = false,
val targetDomain: String = "",
val targetWsPath: String = ""
```
在 Ping 完成后统一调度：
```kotlin
val qualifiedPingResults = if (config.enableTargetProbe && config.targetDomain.isNotBlank()) {
    val cleanedDomain = DomainProber.cleanDomain(config.targetDomain)
    val totalProbes = pingResults.size
    val probeCompleted = AtomicInteger(0)
    val isWs = config.targetWsPath.isNotBlank()
    val probeLabel = if (isWs) "专属通道穿透校验" else "自定义域名连通性校验"

    if (pingResults.isNotEmpty()) {
        onProgress(TestProgress(0, totalProbes, cleanedDomain, "正在执行 $probeLabel (0/$totalProbes)..."))
    }
    val probeSemaphore = Semaphore(config.maxConcurrentPings.coerceAtLeast(1))
    coroutineScope {
        pingResults.map { pair ->
            async {
                if (shouldStop.get()) return@async null
                probeSemaphore.withPermit {
                    if (shouldStop.get()) return@withPermit null
                    val isOk = if (isWs) {
                        DomainProber.probeWebSocket(pair.first, cleanedDomain, config.targetWsPath)
                    } else {
                        DomainProber.probeDomain(pair.first, cleanedDomain)
                    }
                    val done = probeCompleted.incrementAndGet()
                    onProgress(TestProgress(done, totalProbes, pair.first, "正在执行 $probeLabel ($done/$totalProbes)..."))
                    if (isOk) pair else null
                }
            }
        }.awaitAll().filterNotNull()
    }
} else {
    pingResults
}
```

### 2. `SpeedTestViewModel.kt` 全量持久化与重置
- 统一探针状态：`enableTargetProbe`、`targetDomain`、`targetWsPath`；
- 所有 `updateXxx()` 函数异步写入 `app_prefs`；
- `init` 块统一读取已保存值；
- `resetToDefaultSettings()`：恢复全部 StateFlow 为官方初始推荐值，写入 `app_prefs` 并提示 Toast。

### 3. `MainActivity.kt` 界面呈现与联动
- 卡片 3：收敛为单一开关与单张展开卡片，底部带动态模式徽标；
- 设置对话框右上角新增「恢复默认」TextButton，点击调用 `viewModel.resetToDefaultSettings()` 并重置所有输入框文本。

---

## 三、 验收契约 (Acceptance Criteria)

1. **界面体验**：卡片 3 单开关收敛，动态回显当前测速模式；
2. **记忆保持**：改动任一参数退出 App，冷启动后 100% 保持用户设定；
3. **一键恢复**：点击恢复默认，所有参数秒级刷回官方推荐值；
4. **编译与功能零回归**。
