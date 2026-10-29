# R02_PLAN: 状态卡片跳行与布局抖动根治方案 (定稿版 - 已吸收双审意见)

## 一、 审查裁决与结论 (Reviewer Verdict)

经过 GLM 与 DeepSeek 双模型独立审查，对方案进行了全仓引用与视觉保护的深度论证：
1. **全仓检索确认零下游回归**：经 grep 排查，全仓仅 `SpeedTestViewModel.kt:890` 一处存在拼接 IP 行为，且 `statusMessage` 仅供 `StatusCard` 显示，无任何字符串反解契约，解耦完全安全；
2. **两行定高容器约束**：为防止测试前/后没有 IP 时卡片在 1 行与 2 行之间塌陷，在 `StatusCard` 中为文本区域设定最小高度约束（`Modifier.heightIn(min = 42.dp)`），彻底从物理尺寸上消除高度抖动；
3. **单行截断与等宽字体防护**：
   - 状态文本：`maxLines = 1`, `overflow = TextOverflow.Ellipsis`；
   - IP 文本：`maxLines = 1`, `overflow = TextOverflow.Ellipsis`, `fontFamily = FontFamily.Monospace`，保证超长 IPv6 与 IPv4 视觉宽度更均衡整洁。

---

## 二、 详细代码实现规格

### 1. `SpeedTestViewModel.kt`
在 `engine.startTest` 的 `onProgress` 回调中（第 890 行）：
```kotlin
onProgress = { progress ->
    _progress.value = progress
    _statusMessage.value = progress.status // 纯净状态，不再带 IP
}
```

### 2. `MainActivity.kt:StatusCard`
```kotlin
            Spacer(modifier = Modifier.height(6.dp))

            // 文本区域定高容器，彻底锁定两行结构，防止高度塌缩抖动
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (statusMessage.isNotEmpty()) statusMessage else "准备就绪，点击开始测速",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isTesting && progress.currentIp.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = progress.currentIp,
                        fontSize = 12.sp,
                        color = Color(0xFF1976D2),
                        fontWeight = FontWeight.Medium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
```

---

## 三、 验收契约 (Acceptance Criteria)

1. **绝对恒定高度**：测速全流程中状态卡片高度恒定，彻底终结两行与三行之间的忽大忽小抽搐；
2. **纯粹单点展示**：状态描述与目标 IP 各司其职，无信息重复渲染；
3. **全流程稳定**：待机态、Ping 阶段、域名探测阶段、下载阶段、完成态高度平滑过渡。
