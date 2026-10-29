# 阶段 2 UI 逻辑与信息架构重构实施方案 (定稿版 - 已吸收多模型审查意见)

## 一、 背景与目标
在阶段 1 完成测速精度与冗余代码清理的基础上，阶段 2 聚焦于 **Android 端界面逻辑与信息架构重构**：
1. **消除结果消费割裂**：将即时排序切换、导出与结果统计整合为结果工具条，结果列表采用 `weight(1f)` 自适应铺满剩余屏幕；
2. **消除入口层级倒置**：将历史记录提升至顶部栏一级入口，并在历史卡片内采用手风琴式折叠/展开，彻底消灭弹窗套娃；
3. **设置面板结构化卡片重构**：将设置项划分为【Ping探测】、【下载策略与阈值】、【IP源与网络】3 个高内聚卡片；
4. **增强主控交互与状态防错**：主控栏新增“复制最佳 IP”与“清空结果”（并在测速中安全禁用防竞态）。

---

## 二、 交叉审查结论与落地修正点 (Reviewer Feedback Addressed)

1. **ViewModel 补齐 `clearResults()`**：
   在 `SpeedTestViewModel.kt` 中显式新增：
   ```kotlin
   fun clearResults() {
       if (!_isTesting.value) {
           _results.value = emptyList()
           _statusMessage.value = "已清空当前结果"
       }
   }
   ```
   并在 `isTesting == true` 时，主界面“清空”按钮强制 `enabled = false`，防止与后台回调发生数据写冲突。
2. **布局约束与小屏抗压防御**：
   - 严格保持主界面根 `Column` **不添加 `verticalScroll`**，确保列表的 `Modifier.weight(1f)` 能够正确获得剩余高度并由内部 `LazyColumn` 单独承载滚动；
   - 适当压缩主界面各组件间的间距（由 16dp 收紧为 10~12dp），避免小屏设备下固定内容过多挤压结果列表。
3. **历史记录手风琴展开键防错位**：
   - 展开状态以每条历史记录的时间戳（`firstOrNull()?.testTime`）或在删除时重置展开态，彻底杜绝索引漂移导致的展示错位。

---

## 三、 详细实现步骤与代码改动

### 1. `SpeedTestViewModel.kt` 扩展
- 新增 `fun clearResults()` 方法；
- 检查 `getBestIp()`、`useIpv6`、`sortByLatency` 等接口的公有可见性（已确认均为 public）。

### 2. `MainActivity.kt` 重构
#### (1) `TopAppBar` 升级
- 标题旁增加当前测试模式指示徽标（如 `Surface` 圆角标签：`IPv4` / `IPv6`，跟随 `useIpv6` 动态变化）；
- Actions 动作区包含：
  - `IconButton(onClick = { showHistory = true }) { Icon(Icons.Default.History, "历史记录") }`
  - `IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "设置") }`

#### (2) 主控制栏 `ControlButtons` 增强为 `ControlBar`
- 主按钮：`开始测速` / `停止`（权重 2f）；
- 辅按钮（水平排列）：
  - `复制最佳`：当 `results.isNotEmpty()` 时可用，点击调用 `viewModel.getBestIp()?.let { viewModel.copyIp(it.ipAddress) }`；
  - `清空`：当 `results.isNotEmpty() && !isTesting` 时可用，点击调用 `viewModel.clearResults()`。

#### (3) 结果工作台 `ResultsWorkspace` (替代原 `ResultsList`)
- 包含结果标题 `测速结果 (${results.size})`；
- 紧凑工具按钮：
  - 排序按钮：显示 `按延迟(低→高)` / `按速度(高→低)`，点击调用 `viewModel.updateSortByLatency(!sortByLatency)`，利用 ViewModel 的 collect 即时重排；
  - 导出按钮：点击呼出 `ExportDialog`；
- 结果列表：`Modifier.weight(1f).fillMaxWidth()`，内部 `LazyColumn` 撑满并自适应滚动；条目保留 IP + Colo 徽标 + 丢包率 + 延迟 + 速度。

#### (4) 设置面板 `EnhancedSettingsDialog` 分组重构
将 15 个选项清晰封装进 3 个带浅灰色背景的卡片：
- **卡片 1：TCP Ping 探测配置**（测试 IP 数量、单 IP Ping 次数、Ping 并发数）；
- **卡片 2：下载测速策略与阈值**（下载测速开关、精测 IP 数量、测试时长、速度下限、延迟上限）；
- **卡片 3：IP 地址池与网络管理**（IPv4/IPv6 开关、指定 IP 段测速开关与选择面板、从 Cloudflare 更新、从 GitHub 镜像更新、自定义镜像网址配置与开关）；
*(彻底移除设置弹窗底部的历史记录按钮与排序开关)*。

#### (5) 历史记录 `HistoryDialog` 手风琴重构（移除 `HistoryDetailDialog`）
- 点击某批次记录直接在下方折叠/展开显示该批次的优选 IP 列表；
- 展开的条目同样展示 IP、Colo 徽标、延迟与速度，点击直接复制；
- 彻底移除废弃的 `HistoryDetailDialog`。

---

## 四、 验收契约 (Acceptance Criteria)

1. **结构与入口**：
   - 顶部栏直观展示 IPv4/IPv6 模式，历史记录与设置均为顶部栏一级入口；
   - 结果区上方包含即时排序切换与导出动作，结果列表自适应撑满剩余高度；
2. **状态与交互防错**：
   - 测速运行中（`isTesting == true`），清空按钮处于不可用状态；
   - 切换排序时，当前结果列表即时完成重排；
3. **代码卫生与零破坏性**：
   - 彻底移除 `HistoryDetailDialog`，代码行更加精炼无死代码；
   - 所有现有参数调节、官方/GitHub IP 更新、指定网段勾选等功能 100% 正常运行。
