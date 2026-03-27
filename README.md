# Cloudflare Speed Test Android App

这是一个用于测试 Cloudflare CDN IP 速度的 Android 应用程序，移植自 [XIU2/CloudflareSpeedTest](https://github.com/XIU2/CloudflareSpeedTest) 项目。

本项目完全由 AI 协助完成，开发阶段使用的模型包括 GLM5、Qwen3-Code-Plus、Kimi-K2.5、DeepSeek-V3.2 等，修改阶段使用的模型包括 Gemini3.1Pro、Gemini3Pro、GPT4o、GPT5.4 等，图标由豆包生成。

项目继承原版使用 GNU 开源协议

## 功能特性

- **TCP 延迟测试**: 通过 TCP 连接测试 IP 的延迟和丢包率
- **下载速度测试**: 可选的下载速度测试功能
- **自动筛选**: 支持按延迟上限和速度下限筛选 IP
- **结果排序**: 结果自动按丢包率、延迟和速度排序
- **一键复制**: 点击结果即可复制 IP 到剪贴板
- **单击复制**: 用户可以通过单击测速结果快速复制 IP 地址到剪贴板。
- **CSV 导出**: 支持将结果导出为 CSV 格式
- **Cloudflare 官方 IP 更新**: 支持直接从 Cloudflare 官方 `ips-v4` / `ips-v6` 地址获取最新 IP 段
- **指定 IP 段测试**: 支持只对勾选的一个或多个 IPv4 / IPv6 IP 段进行测速

## 关键优化与问题修复

### 1. 为什么安卓端测速比电脑端慢？

- **Root 权限问题**: 安卓设备通常没有 Root 权限，无法直接访问底层网络栈，影响测速性能。
- **系统 I/O 限制**: 安卓系统的 I/O 缓冲区较小，默认配置可能限制了网络吞吐量。
- **硬件性能差异**: 移动设备的 CPU 和网络模块性能通常低于 PC。

### 2. 自定义 IP 数量测试

- 原版工具默认测试所有 IP，安卓移植版改为支持自定义 IP 数量。
- **原因**: 提升测速效率，适配移动设备的性能。
- **效果**: 用户可以根据需求灵活调整测试范围，避免不必要的资源消耗。

### 3. IPv6 测试优化

- **问题现象**: 之前的 IPv6 测试逻辑可能陷入“黑洞”现象（测试逻辑卡住或资源耗尽），且未能跳过无效 IP 段。
- **解决方案**: 优化测试逻辑，跳过无效 IP 段，避免测试中断。
- **解释**: IPv6 地址池过大，可能导致测试逻辑陷入死循环或资源耗尽。

### 4. 测速瞬间结束且全部显示 0.00 MB/s 的问题

- **问题现象**：进行下载测速时，进度条瞬间闪过，所有 IP 的下载速度均显示为 0.00 MB/s。
- **根本原因**：之前所使用的测速地址（`https://cf.xiu2.xyz/url`）因访问限制或国内 SNI 阻断，返回了 `HTTP 403 Forbidden`。原版代码中 `if (response.isSuccessful)` 的判断导致程序直接跳过了流读取，耗时为 0，从而得出 0 MB/s。
- **解决方案**：
  - 将下载测速地址改回了官方的 `speed.cloudflare.com`，但**降级使用 HTTP 协议（`http://speed.cloudflare.com/__down?bytes=50000000`）**。这成功绕过了针对 HTTPS 握手期的 SNI 阻断和 403 拦截，保证了真实的下载数据流能顺利传输。
  - **引入“智能续航”循环测速架构**：针对 5G/千兆宽带极速网络可能在 1 秒内就把单次 50MB 数据包跑完的情况，重写了 `DownloadTest.kt` 的逻辑。现在系统会持续、无缝地重复发起缓冲包抓取，直到完全消耗完用户设定的“10秒测试周期”，确保算出的兆/秒（MB/s）与 Windows 电脑端一样高度精确。

### 5. Android 端下载测速上限锁死在 1MB/s 左右的问题

- **问题现象**：部分高级网络环境下，Windows 版能测出 10MB/s 甚至更高的速度，但本 App 无论怎么测最高都卡在 1MB/s 左右。
- **根本原因**：由于 Android 系统的 I/O 内存帧分配策略，原逻辑中采用默认的或较小的字节缓冲区（如 8KB）导致 OkHttp 在从网络层不断拉取缓冲时发生了高频 I/O 阻塞。
- **解决方案**：在字节流读取部分，强制显式分配了一块以 128KB（`ByteArray(131072)`）为单位的大吞吐缓冲区，极大减轻了读盘负担，从而解锁了 Android 的真实满血下载性能。

### 6. 下载测速进度卡在 20/20 不变化的问题

- **问题现象**：开启下载测速后，界面会先显示 `20/20`，但不会继续变成 `19/20`、`18/20` 直到 `1/20`，而是停留一段时间后直接弹出最终结果，给人的感觉像是进度卡死了。
- **根本原因**：下载阶段原先采用的是并行 `async` 启动多个测速任务，并且在“任务启动时”就立即上报一次进度。这样 20 个任务几乎会在同一时刻把进度连续刷新到最后一项，UI 最终只会停留在 `20/20`。真正耗时的下载过程发生在后台，但中间没有新的进度回调，因此倒数进度看起来不会动。
- **解决方案**：
  - 重写了 `SpeedTestEngine.kt` 中下载阶段的进度更新逻辑，不再按“启动了第几个任务”显示，而是改为按“还剩多少个 IP 未完成”显示。
  - 现在每当一个下载测速任务真正完成时，才会触发一次新的进度刷新，因此界面会按照 `20/20 -> 19/20 -> 18/20 -> ... -> 1/20` 的节奏持续更新，用户可以明确看到测速仍在进行中。
  - 同时补上了并发控制，真正启用了 `maxConcurrentDownloads` 限制，避免所有下载任务一股脑同时抢占资源，让进度显示与后台执行节奏保持一致。

### 7. 从 GitHub 获取最新 IP 无反馈与格式报错

- **问题现象**：点击“从 GitHub 获取最新 IP”后，既没有原生弹窗提示，且容易报“非CIDR格式”错误；不仅如此，历史记录中复制 IP 时也没有反馈。
- **解决方案**：
  - 接入了 Android 原生的 `Toast.makeText` 以及 `ClipboardManager` 系统服务。
  - 重修了获取 IP 时的 CIDR 字符串过滤与检测逻辑。现在无论拉取成功与否，或是在主页/历史记录里点击复制 IP，都会有统一的贴心浮出弹窗（Toast）提示了。

### 8. 从 Cloudflare 官方地址获取最新 IP

- **新增能力**：现在设置页新增了“从 Cloudflare 获取最新 IP”按钮，可直接从 Cloudflare 官方地址拉取最新 IP 段：
  - IPv4：`https://www.cloudflare.com/ips-v4`
  - IPv6：`https://www.cloudflare.com/ips-v6`
- **实现细节**：程序会根据当前的 IPv4 / IPv6 开关自动选择对应地址，并在拉取后直接解析、加载并缓存到本地。
- **兼容处理**：考虑到官方返回内容在不同环境下可能表现为“换行分隔”或“空白分隔”，代码中额外加入了内容标准化步骤，确保 CIDR 列表能稳定解析，不会出现“内容有返回但解析为空”的问题。

### 9. 指定一个或多个 IP 段进行测速

- **新增能力**：设置页新增“仅测试指定 IP 段”开关。开启后会弹出一个可滚动、多选的 IP 段选择面板，支持勾选一个或多个 IP 段进行定向测速。
- **交互逻辑**：
  - 默认在 IPv4 模式下展示 IPv4 段列表；
  - 当切换为“仅测 IPv6”后，选择面板会自动切换为 IPv6 段列表；
  - 面板支持上下滑动、逐项勾选、全选和清空，适合在 IP 段较多时快速筛选。
- **实际效果**：开启该模式后，测速引擎不再从全部 Cloudflare IP 段中随机抽样，而是只会从用户勾选的 IP 段集合中生成测试目标，更适合定向排查某些网段的质量表现。

## 截图展示

以下是应用的界面截图：
![主界面示意图](\screenshots\主界面.jpg)
![设置示意图](\screenshots\设置界面.jpg)
![设置示意图2](\screenshots\设置界面2.jpg)

---

## 项目结构

```
CFSTAPP/
├── app/                              # 应用模块
│   ├── build.gradle                  # 应用级构建配置，定义依赖和构建逻辑
│   ├── proguard-rules.pro            # ProGuard 混淆规则文件
│   └── src/main/                     # 应用主代码和资源
│       ├── AndroidManifest.xml      # 应用清单文件，定义权限、组件等
│       ├── java/                    # Java/Kotlin 源代码
│       │   ├── com/cfst/app/        # 主包名目录
│       │   │   ├── MainActivity.kt  # 应用主界面逻辑
│       │   │   ├── model/           # 数据模型类
│       │   │   ├── speedtest/       # 测速核心逻辑
│       │   │   ├── utils/           # 工具类（如 IP 解析）
│       │   │   └── viewmodel/       # MVVM 架构中的 ViewModel
│       ├── res/                     # 应用资源文件
│       │   ├── layout/              # 布局文件（XML 格式）
│       │   ├── values/              # 字符串、主题等资源
│       │   └── drawable/            # 图片资源
├── build.gradle                      # 项目级构建配置，定义插件和全局配置
├── settings.gradle                   # 项目设置文件，定义模块结构
```

## 技术栈

- **Kotlin**: 项目主要开发语言，简洁高效，支持协程和现代编程特性。
- **Jetpack Compose**: 用于构建现代化的声明式 UI，简化了界面开发。
- **Material Design 3**: 提供一致的 UI 设计规范，确保应用外观现代且易用。
- **Coroutines**: 用于实现异步编程，优化测速逻辑，避免阻塞主线程。
- **OkHttp**: 用于网络请求和数据传输，支持高效的 HTTP/HTTPS 通信。
- **MVVM**: 架构模式，分离视图（View）和逻辑（ViewModel），提高代码可维护性。
- **Android ViewModel**: Jetpack 组件，用于管理 UI 相关数据的生命周期，避免内存泄漏。
- **Toast 和 ClipboardManager**: 提供用户反馈（如复制 IP 地址时的提示）。

## 使用方法

### 方法一：使用 Android Studio（推荐）

1. **安装 Android Studio**
   - 下载地址: https://developer.android.com/studio
   - 安装时选择包含 Android SDK

2. **打开项目**
   - 启动 Android Studio
   - 选择 "Open" 或 "File → Open"
   - 选择 `CFSTAPP` 目录

3. **等待 Gradle 同步**
   - 首次打开会自动下载依赖，需要等待几分钟

4. **构建 APK**
   - 点击菜单 `Build → Build Bundle(s) / APK(s) → Build APK(s)`
   - 或者点击工具栏的绿色播放按钮直接运行到连接的手机

5. **获取 APK**
   - 构建完成后，APK 位于: `app/build/outputs/apk/debug/app-debug.apk`

### 方法二：使用命令行构建

**前置条件:**
- 安装 JDK 17 或更高版本 (下载: https://adoptium.net/)
- 安装 Android SDK 并设置 `ANDROID_HOME` 环境变量
  - Windows: `set ANDROID_HOME=C:\Users\你的用户名\AppData\Local\Android\Sdk`
  - 或安装 Android Studio 后会自动包含 SDK

**构建步骤:**

Windows:
```batch
cd CFSTAPP
gradlew.bat assembleDebug
```

构建成功后，APK 文件位于:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 方法三：一键构建（Windows）

双击运行 `build-apk.bat` 脚本，会自动检查环境并构建 APK。

### 使用应用

1. 打开应用，点击「开始测速」按钮
2. 等待测速完成
3. 查看测速结果列表
4. 点击任意 IP 即可复制到剪贴板
5. 点击右上角设置图标可调整参数

### 设置选项

- **测试IP数量**: 要测试的 IP 总数
- **Ping次数**: 每个 IP 的探测次数
- **下载测速**: 是否启用下载速度测试
- **速度下限**: 筛选条件，低于此速度的 IP 不显示
- **延迟上限**: 筛选条件，高于此延迟的 IP 不显示
- **从 Cloudflare 获取最新 IP**: 直接从 Cloudflare 官方 `ips-v4` / `ips-v6` 地址更新当前 IP 段列表
- **仅测试指定 IP 段**: 开启后可在弹出的多选列表中勾选一个或多个 IP 段，仅对这些网段进行测速

## 注意事项

1. 测速时请关闭 VPN/代理，否则结果不准确
2. 下载测速会消耗一定流量
3. 首次测速可能延迟偏高，建议多次测速

## 致谢

本项目移植自 [XIU2/CloudflareSpeedTest](https://github.com/XIU2/CloudflareSpeedTest)，感谢原作者的优秀工作。
