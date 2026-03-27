package com.cfst.app.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cfst.app.model.SpeedTestResult
import com.cfst.app.speedtest.SpeedTestEngine
import com.cfst.app.utils.IpRangeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * 测速 ViewModel
 */
class SpeedTestViewModel(private val application: Application) : ViewModel() {
    
    // 测速引擎
    private val engine = SpeedTestEngine()
    
    // 内置 IPv4 IP 范围
    private val builtInIpv4Ranges = """173.245.48.0/20
103.21.244.0/22
103.22.200.0/22
103.31.4.0/22
141.101.64.0/18
108.162.192.0/18
190.93.240.0/20
188.114.96.0/20
197.234.240.0/22
198.41.128.0/17
162.158.0.0/15
104.16.0.0/12
172.64.0.0/17
172.64.128.0/18
172.64.192.0/19
172.64.224.0/22
172.64.229.0/24
172.64.230.0/23
172.64.232.0/21
172.64.240.0/21
172.64.248.0/21
172.65.0.0/16
172.66.0.0/16
172.67.0.0/16
131.0.72.0/22""".trimIndent()
    
    // 内置 IPv6 IP 范围
    private val builtInIpv6Ranges = """2400:cb00:2049::/48
2400:cb00:f00e::/48
2606:4700::/32
2606:4700:10::/48
2606:4700:130::/48
2606:4700:3000::/48
2606:4700:3001::/48
2606:4700:3002::/48
2606:4700:3003::/48
2606:4700:3004::/48
2606:4700:3005::/48
2606:4700:3006::/48
2606:4700:3007::/48
2606:4700:3008::/48
2606:4700:3009::/48
2606:4700:3010::/48
2606:4700:3011::/48
2606:4700:3012::/48
2606:4700:3013::/48
2606:4700:3014::/48
2606:4700:3015::/48
2606:4700:3016::/48
2606:4700:3017::/48
2606:4700:3018::/48
2606:4700:3019::/48
2606:4700:3020::/48
2606:4700:3021::/48
2606:4700:3022::/48
2606:4700:3023::/48
2606:4700:3024::/48
2606:4700:3025::/48
2606:4700:3026::/48
2606:4700:3027::/48
2606:4700:3028::/48
2606:4700:3029::/48
2606:4700:3030::/48
2606:4700:3031::/48
2606:4700:3032::/48
2606:4700:3033::/48
2606:4700:3034::/48
2606:4700:3035::/48
2606:4700:3036::/48
2606:4700:3037::/48
2606:4700:3038::/48
2606:4700:3039::/48
2606:4700:a0::/48
2606:4700:a1::/48
2606:4700:a8::/48
2606:4700:a9::/48
2606:4700:a::/48
2606:4700:b::/48
2606:4700:c::/48
2606:4700:d0::/48
2606:4700:d1::/48
2606:4700:d::/48
2606:4700:e0::/48
2606:4700:e1::/48
2606:4700:e2::/48
2606:4700:e3::/48
2606:4700:e4::/48
2606:4700:e5::/48
2606:4700:e6::/48
2606:4700:e7::/48
2606:4700:e::/48
2606:4700:f1::/48
2606:4700:f2::/48
2606:4700:f3::/48
2606:4700:f4::/48
2606:4700:f5::/48
2606:4700:f::/48
2803:f800:50::/48
2803:f800:51::/48
2a06:98c1:3100::/48
2a06:98c1:3101::/48
2a06:98c1:3102::/48
2a06:98c1:3103::/48
2a06:98c1:3104::/48
2a06:98c1:3105::/48
2a06:98c1:3106::/48
2a06:98c1:3107::/48
2a06:98c1:3108::/48
2a06:98c1:3109::/48
2a06:98c1:310a::/48
2a06:98c1:310b::/48
2a06:98c1:310c::/48
2a06:98c1:310d::/48
2a06:98c1:310e::/48
2a06:98c1:310f::/48
2a06:98c1:3120::/48
2a06:98c1:3121::/48
2a06:98c1:3122::/48
2a06:98c1:3123::/48
2a06:98c1:3200::/48
2a06:98c1:50::/48
2a06:98c1:51::/48
2a06:98c1:54::/48
2a06:98c1:58::/48""".trimIndent()
    
    // UI 状态
    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()
    
    private val _progress = MutableStateFlow(SpeedTestEngine.TestProgress(0, 100, "", ""))
    val progress: StateFlow<SpeedTestEngine.TestProgress> = _progress.asStateFlow()
    
    private val _results = MutableStateFlow<List<SpeedTestResult>>(emptyList())
    val results: StateFlow<List<SpeedTestResult>> = _results.asStateFlow()
    
    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()
    
    // 配置状态
    private val _testCount = MutableStateFlow(100)
    val testCount: StateFlow<Int> = _testCount.asStateFlow()
    
    private val _pingCount = MutableStateFlow(1)
    val pingCount: StateFlow<Int> = _pingCount.asStateFlow()
    
    private val _enableDownloadTest = MutableStateFlow(true)
    val enableDownloadTest: StateFlow<Boolean> = _enableDownloadTest.asStateFlow()
    
    private val _speedLimit = MutableStateFlow(0.0)
    val speedLimit: StateFlow<Double> = _speedLimit.asStateFlow()
    
    private val _latencyLimit = MutableStateFlow(0.0)
    val latencyLimit: StateFlow<Double> = _latencyLimit.asStateFlow()
    
    // 新增配置状态
    private val _downloadTestCount = MutableStateFlow(20)
    val downloadTestCount: StateFlow<Int> = _downloadTestCount.asStateFlow()

    private val _downloadDuration = MutableStateFlow(10)
    val downloadDuration: StateFlow<Int> = _downloadDuration.asStateFlow()

    private val _useProxy = MutableStateFlow(false)
    val useProxy: StateFlow<Boolean> = _useProxy.asStateFlow()
    
    private val _proxyUrl = MutableStateFlow("")
    val proxyUrl: StateFlow<String> = _proxyUrl.asStateFlow()
    
    private val _useIpv6 = MutableStateFlow(false)
    val useIpv6: StateFlow<Boolean> = _useIpv6.asStateFlow()

    private val _useSpecifiedIpSegments = MutableStateFlow(false)
    val useSpecifiedIpSegments: StateFlow<Boolean> = _useSpecifiedIpSegments.asStateFlow()

    private val _availableIpSegments = MutableStateFlow<List<String>>(emptyList())
    val availableIpSegments: StateFlow<List<String>> = _availableIpSegments.asStateFlow()

    private val _selectedSpecifiedIpSegments = MutableStateFlow<Set<String>>(emptySet())
    val selectedSpecifiedIpSegments: StateFlow<Set<String>> = _selectedSpecifiedIpSegments.asStateFlow()
    
    // 新增配置状态：结果排序方式
    private val _sortByLatency = MutableStateFlow(false)
    val sortByLatency: StateFlow<Boolean> = _sortByLatency.asStateFlow()
    
    // IP 范围 - 默认使用内置 IPv4
    private var ipRanges: List<IpRangeParser.IpRange> = IpRangeParser.parseIpFile(builtInIpv4Ranges)
    
    // 历史记录相关
    private val _historyRecords = MutableStateFlow<List<List<SpeedTestResult>>>(emptyList())
    val historyRecords: StateFlow<List<List<SpeedTestResult>>> = _historyRecords.asStateFlow()
    
    // GitHub 原始 URL
    private val githubRawUrl = "https://raw.githubusercontent.com/XIU2/CloudflareSpeedTest/master/"

    // 镜像网站前缀列表（用于拼接在 GitHub URL 前面）
    // 注意：这些镜像可能会失效，如果失效请手动更新或使用自定义镜像
    private val mirrorPrefixes = listOf(
        // 原来的 4 个
        "https://ghps.cc/",
        "https://gh-proxy.com/",
        "https://ghfast.top/",
        "https://ghproxy.vip/",
        // 新增的 4 个
        "https://ghproxy.cc/",
        "https://ghproxy.net/",
        "https://ghproxy.click/"
    )
    
    companion object {
        // 本地存储 IP 和版本信息的键
        private const val IP_FILE_KEY = "ip_file_content"
        private const val IP_VERSION_KEY = "ip_version_info"
        private const val IP_UPDATE_TIME_KEY = "ip_update_time"
        private const val IPV4_FILE_KEY = "ipv4_file_content"
        private const val IPV6_FILE_KEY = "ipv6_file_content"
        private const val IPV4_UPDATE_TIME_KEY = "ipv4_update_time"
        private const val IPV6_UPDATE_TIME_KEY = "ipv6_update_time"
        private const val CLOUDFLARE_IPV4_URL = "https://www.cloudflare.com/ips-v4"
        private const val CLOUDFLARE_IPV6_URL = "https://www.cloudflare.com/ips-v6"
        // 历史记录相关键
        private const val HISTORY_RECORDS_KEY = "history_records"
        
        /**
         * ViewModel Factory
         */
        fun createFactory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SpeedTestViewModel::class.java)) {
                        return SpeedTestViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }
    
    init {
        // 延迟初始化，避免在构造函数中访问 SharedPreferences
        viewModelScope.launch {
            // 应用启动时加载本地 IP 列表
            loadLocalIpRanges()
            // 应用启动时加载历史记录
            loadHistoryRecords()
            
            // 监听排序方式变化，重新排序当前结果
            _sortByLatency.collect { sortByLatency ->
                val currentResults = _results.value
                if (currentResults.isNotEmpty()) {
                    val sortedResults = if (sortByLatency) {
                        currentResults.sortedBy { it.avgLatency }
                    } else {
                        currentResults.sortedByDescending { it.downloadSpeed }
                    }
                    _results.value = sortedResults
                }
            }
        }
    }
    
    /**
     * 更新测试数量
     */
    fun updateTestCount(count: Int) {
        _testCount.value = count
    }
    
    /**
     * 更新 Ping 次数
     */
    fun updatePingCount(count: Int) {
        _pingCount.value = count
    }
    
    /**
     * 更新是否启用下载测速
     */
    fun updateEnableDownloadTest(enabled: Boolean) {
        _enableDownloadTest.value = enabled
    }
    
    /**
     * 更新速度下限
     */
    fun updateSpeedLimit(limit: Double) {
        _speedLimit.value = limit
    }
    
    /**
     * 更新延迟上限
     */
    fun updateLatencyLimit(limit: Double) {
        _latencyLimit.value = limit
    }
    
    /**
     * 更新下载测速 IP 数量
     */
    fun updateDownloadTestCount(count: Int) {
        _downloadTestCount.value = count
    }

    /**
     * 更新下载测试时长 (秒)
     */
    fun updateDownloadDuration(seconds: Int) {
        _downloadDuration.value = seconds
    }

    /**
     * 更新是否使用代理
     */
    fun updateUseProxy(use: Boolean) {
        _useProxy.value = use
    }
    
    /**
     * 更新代理 URL
     */
    fun updateProxyUrl(url: String) {
        _proxyUrl.value = url
    }
    
    /**
     * 加载自定义 IP 文件内容
     */
    fun loadIpRanges(content: String) {
        val parsedRanges = IpRangeParser.parseIpFile(content)
        ipRanges = parsedRanges
        _availableIpSegments.value = extractIpSegments(content)
        trimSelectedSpecifiedIpSegments()
        _statusMessage.value = "已加载 ${ipRanges.size} 个 IP 段"
    }

    fun updateUseSpecifiedIpSegments(enabled: Boolean) {
        _useSpecifiedIpSegments.value = enabled
    }

    fun toggleSpecifiedIpSegment(segment: String) {
        val updated = _selectedSpecifiedIpSegments.value.toMutableSet()
        if (!updated.add(segment)) {
            updated.remove(segment)
        }
        _selectedSpecifiedIpSegments.value = updated
    }

    fun clearSpecifiedIpSegments() {
        _selectedSpecifiedIpSegments.value = emptySet()
    }

    fun selectAllSpecifiedIpSegments() {
        _selectedSpecifiedIpSegments.value = _availableIpSegments.value.toSet()
    }
    
    /**
     * 加载历史记录
     */
    private fun loadHistoryRecords() {
        viewModelScope.launch {
            try {
                val sharedPreferences = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val historyJson = sharedPreferences.getString(HISTORY_RECORDS_KEY, "[]") ?: "[]"
                
                val gson = com.google.gson.Gson()
                val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, String::class.java).type
                val jsonStringList = gson.fromJson<List<String>>(historyJson, type)
                
                val historyRecords = jsonStringList.mapNotNull { jsonStr ->
                    try {
                        val resultType = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, SpeedTestResult::class.java).type
                        gson.fromJson<List<SpeedTestResult>>(jsonStr, resultType)
                    } catch (e: Exception) {
                        null
                    }
                }
                _historyRecords.value = historyRecords
            } catch (e: Exception) {
                _historyRecords.value = emptyList()
            }
        }
    }

    /**
     * 保存历史记录到本地存储
     */
    private fun saveHistoryRecordsToStorage() {
        viewModelScope.launch {
            try {
                val gson = com.google.gson.Gson()
                val jsonStringList = _historyRecords.value.map { recordList ->
                    gson.toJson(recordList)
                }
                val historyJson = gson.toJson(jsonStringList)
                val sharedPreferences = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                with(sharedPreferences.edit()) {
                    putString(HISTORY_RECORDS_KEY, historyJson)
                    apply()
                }
            } catch (e: Exception) {
                _statusMessage.value = "保存历史记录失败：${e.message}"
            }
        }
    }

    /**
     * 保存当前测速结果到历史记录
     */
    fun saveCurrentResultsToHistory() {
        val currentResults = _results.value
        if (currentResults.isNotEmpty()) {
            val newHistory = _historyRecords.value.toMutableList()
            newHistory.add(0, currentResults)
            if (newHistory.size > 100) {
                newHistory.removeLast()
            }
            _historyRecords.value = newHistory
            _statusMessage.value = "已保存本次测速结果到历史记录 (${_historyRecords.value.size} 条)"
            saveHistoryRecordsToStorage()
        }
    }

    /**
     * 删除指定索引的历史记录
     */
    fun deleteHistoryRecord(index: Int) {
        if (index >= 0 && index < _historyRecords.value.size) {
            val newHistory = _historyRecords.value.toMutableList()
            newHistory.removeAt(index)
            _historyRecords.value = newHistory
            _statusMessage.value = "已删除历史记录，剩余 ${_historyRecords.value.size} 条记录"
            saveHistoryRecordsToStorage()
        }
    }

    /**
     * 清空所有历史记录
     */
    fun clearAllHistoryRecords() {
        _historyRecords.value = emptyList()
        _statusMessage.value = "已清空所有历史记录"
        saveHistoryRecordsToStorage()
    }

    /**
     * 获取最新一次的历史记录
     */
    fun getLatestHistoryRecord(): List<SpeedTestResult>? {
        return _historyRecords.value.firstOrNull()
    }
    
    /**
     * 更新是否使用 IPv6
     */
    fun updateUseIpv6(use: Boolean) {
        _useIpv6.value = use
        _useSpecifiedIpSegments.value = false
        _selectedSpecifiedIpSegments.value = emptySet()
    }

    /**
     * 获取镜像网站列表（返回完整 URL）
     */
    fun getMirrorSites(): List<String> {
        return mirrorPrefixes.map { ensureEndsWithSlash(it) + githubRawUrl }
    }

    /**
     * 更新结果排序方式
     */
    fun updateSortByLatency(sortByLatency: Boolean) {
        _sortByLatency.value = sortByLatency
    }

    private fun extractIpSegments(content: String): List<String> {
        return content.replace("\uFEFF", "")
            .lines()
            .map { it.trim().removePrefix("\uFEFF") }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    private fun trimSelectedSpecifiedIpSegments() {
        val available = _availableIpSegments.value.toSet()
        _selectedSpecifiedIpSegments.value = _selectedSpecifiedIpSegments.value.filterTo(linkedSetOf()) {
            it in available
        }
    }

    private fun getDefaultIpContent(): String {
        return if (_useIpv6.value) builtInIpv6Ranges else builtInIpv4Ranges
    }

    private fun getCurrentIpCacheKey(): String {
        return if (_useIpv6.value) IPV6_FILE_KEY else IPV4_FILE_KEY
    }

    private fun getCurrentUpdateTimeKey(): String {
        return if (_useIpv6.value) IPV6_UPDATE_TIME_KEY else IPV4_UPDATE_TIME_KEY
    }

    private fun applyIpContent(content: String) {
        ipRanges = IpRangeParser.parseIpFile(content)
        _availableIpSegments.value = extractIpSegments(content)
        trimSelectedSpecifiedIpSegments()
    }
    
    /**
     * 从本地存储获取 IP 范围
     */
    fun loadLocalIpRanges() {
        try {
            val sharedPreferences = application.getSharedPreferences("ip_cache", Context.MODE_PRIVATE)
            val updateTime = sharedPreferences.getString(getCurrentUpdateTimeKey(), "") ?:
                sharedPreferences.getString(IP_UPDATE_TIME_KEY, "") ?: ""
            val defaultContent = getDefaultIpContent()
            val localContent = sharedPreferences.getString(getCurrentIpCacheKey(), null)
                ?: sharedPreferences.getString(IP_FILE_KEY, defaultContent)
                ?: defaultContent

            applyIpContent(localContent)

            _statusMessage.value = if (updateTime.isNotEmpty()) {
                "已加载本地 ${if (_useIpv6.value) "IPv6" else "IPv4"} 列表 (更新于：$updateTime)"
            } else {
                "已加载默认 ${if (_useIpv6.value) "IPv6" else "IPv4"} 列表 ${ipRanges.size} 个 IP 段"
            }
        } catch (e: Exception) {
            applyIpContent(getDefaultIpContent())
            _statusMessage.value = "加载 ${if (_useIpv6.value) "IPv6" else "IPv4"} 列表失败，使用默认列表 ${ipRanges.size} 个 IP 段"
        }
    }

    fun fetchCloudflareIpRanges() {
        viewModelScope.launch {
            try {
                val ipVersion = if (_useIpv6.value) "IPv6" else "IPv4"
                val requestUrl = if (_useIpv6.value) CLOUDFLARE_IPV6_URL else CLOUDFLARE_IPV4_URL
                _statusMessage.value = "正在从 Cloudflare 获取最新 $ipVersion 地址..."

                val client = OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val content = fetchContent(client, requestUrl)
                val normalizedContent = content
                    ?.trim()
                    ?.split(Regex("\\s+"))
                    ?.filter { it.isNotBlank() }
                    ?.joinToString("\n")
                    .orEmpty()
                val parsedRanges = IpRangeParser.parseIpFile(normalizedContent)

                if (normalizedContent.isBlank() || parsedRanges.isEmpty()) {
                    val msg = "从 Cloudflare 获取 $ipVersion 地址失败：返回内容无效"
                    _statusMessage.value = msg
                    Toast.makeText(application, msg, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                applyIpContent(normalizedContent)
                val updateTime = java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    java.util.Locale.getDefault()
                ).format(java.util.Date())

                val sharedPreferences = application.getSharedPreferences("ip_cache", Context.MODE_PRIVATE)
                with(sharedPreferences.edit()) {
                    putString(getCurrentIpCacheKey(), normalizedContent)
                    putString(getCurrentUpdateTimeKey(), updateTime)
                    apply()
                }

                val msg = "已从 Cloudflare 获取 ${parsedRanges.size} 个 $ipVersion IP 段"
                _statusMessage.value = msg
                Toast.makeText(application, msg, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                val msg = "从 Cloudflare 获取最新 IP 失败：${e.message}"
                _statusMessage.value = msg
                Toast.makeText(application, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 从 GitHub 获取最新的 IP 地址并存储到本地
     */
    fun fetchGithubIpRanges() {
        viewModelScope.launch {
            try {
                _statusMessage.value = "正在从 GitHub 获取最新 IP 地址..."

                val clientBuilder = OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                
                // 注意：这里的代理设置是为 HTTP 代理准备的
                // 但镜像网址本身是 HTTPS，所以代理设置可能不适用
                if (_proxyUrl.value.isNotEmpty() && _useProxy.value) {
                    try {
                        val proxyParts = _proxyUrl.value.split(":")
                        if (proxyParts.size >= 2) {
                            val proxyHost = proxyParts[0].removePrefix("http://").removePrefix("https://")
                            val proxyPort = proxyParts[1].toIntOrNull() ?: 8080
                            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort))
                            clientBuilder.proxy(proxy)
                        }
                    } catch (e: Exception) {
                        // 忽略代理设置错误，继续使用直连
                    }
                }
                val client = clientBuilder.build()

                val fileName = if (_useIpv6.value) "ipv6.txt" else "ip.txt"
                var githubContent: String? = null
                var lastError: String? = null

                val urlsToTry = mutableListOf<String>()

                if (_useProxy.value && _proxyUrl.value.isNotBlank()) {
                    buildMirrorRequestUrl(_proxyUrl.value, fileName)?.let { customUrl ->
                        urlsToTry.add(customUrl)
                        _statusMessage.value = "尝试从自定义镜像获取：$customUrl"
                    }
                }

                mirrorPrefixes.mapNotNull { buildMirrorRequestUrl(it, fileName) }
                    .forEach { urlsToTry.add(it) }

                urlsToTry.add("${githubRawUrl}${fileName}")

                val uniqueUrls = urlsToTry.filter { it.isNotBlank() }.distinct()

                // 依次尝试每个 URL
                for (url in uniqueUrls) {
                    try {
                        val content = fetchContent(client, url)
                        if (!content.isNullOrEmpty()) {
                            val trimmedContent = content.trim()
                            if (trimmedContent.startsWith("<!DOCTYPE", ignoreCase = true) ||
                                trimmedContent.startsWith("<html", ignoreCase = true) ||
                                trimmedContent.startsWith("<!--")) {
                                lastError = "镜像返回 HTML 页面，可能访问受限"
                                continue
                            }

                            // 直接解析检查是否包含真正的 IP
                            val parsedRanges = IpRangeParser.parseIpFile(content)
                            if (parsedRanges.isNotEmpty()) {
                                githubContent = content
                                break
                            } else {
                                lastError = "返回内容格式不正确，并未解析到有效 IP 列表"
                            }
                        }
                    } catch (e: Exception) {
                        lastError = "请求失败：${e.message}"
                    }
                }

                if (!githubContent.isNullOrEmpty()) {
                    val resolvedContent = githubContent ?: ""
                    val sharedPreferences = application.getSharedPreferences("ip_cache", Context.MODE_PRIVATE)
                    // 根据 IPv4/IPv6 选择对应的本地缓存内容进行比较
                    val localContent = sharedPreferences.getString(getCurrentIpCacheKey(), null)
                        ?: sharedPreferences.getString(IP_FILE_KEY, getDefaultIpContent())
                        ?: getDefaultIpContent()

                    // 解析 GitHub 获取的内容
                    val parsedRanges = IpRangeParser.parseIpFile(resolvedContent)

                    if (parsedRanges.isNotEmpty()) {
                        // 解析成功，检查是否需要更新
                        if (resolvedContent.trim() != localContent.trim()) {
                            applyIpContent(resolvedContent)

                            val updateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                            val msg = "已从 GitHub 获取 ${parsedRanges.size} 个 IP 段，已保存到本地"
                            _statusMessage.value = msg
                            Toast.makeText(application, msg, Toast.LENGTH_SHORT).show()

                            with(sharedPreferences.edit()) {
                                putString(getCurrentIpCacheKey(), resolvedContent)
                                putString(getCurrentUpdateTimeKey(), updateTime)
                                apply()
                            }
                        } else {
                            val msg = "IP 列表已是最新版本，无需更新"
                            _statusMessage.value = msg
                            Toast.makeText(application, msg, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // 解析失败，说明获取的内容格式不正确
                        val msg = "从 GitHub 获取 IP 地址失败：内容格式不正确（非 CIDR 格式）"
                        _statusMessage.value = msg
                        Toast.makeText(application, msg, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val msg = "从 GitHub 获取 IP 地址失败：${lastError ?: "无法访问任何源"}"
                    _statusMessage.value = "$msg\n建议：1.检查网络 2.切换镜像 3.使用自定义镜像"
                    Toast.makeText(application, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val msg = "从 GitHub 获取 IP 地址失败：${e.message}"
                _statusMessage.value = msg
                Toast.makeText(application, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 获取内容的辅助函数
     */
    private suspend fun fetchContent(client: OkHttpClient, url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun ensureEndsWithSlash(value: String): String {
        var normalized = value.trim()
        if (normalized.isEmpty()) return ""
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized.removePrefix("//")
        }
        normalized = normalized.replace("\\", "/")
        return if (normalized.endsWith("/")) normalized else "$normalized/"
    }

    private fun buildMirrorRequestUrl(prefix: String, fileName: String): String? {
        if (prefix.isBlank()) return null
        val normalized = ensureEndsWithSlash(prefix)
        return if (normalized.contains("raw.githubusercontent.com")) {
            normalized + fileName
        } else {
            normalized + githubRawUrl + fileName
        }
    }
    
    /**
     * 开始测速
     */
    fun startTest() {
        if (_isTesting.value) return
        if (ipRanges.isEmpty()) {
            _statusMessage.value = "请先加载 IP 段文件"
            return
        }

        val activeIpRanges = if (_useSpecifiedIpSegments.value) {
            val selectedSegments = _availableIpSegments.value.filter { it in _selectedSpecifiedIpSegments.value }
            if (selectedSegments.isEmpty()) {
                _statusMessage.value = "请先选择至少一个指定 IP 段"
                return
            }
            IpRangeParser.parseIpFile(selectedSegments.joinToString("\n"))
        } else {
            ipRanges
        }

        if (activeIpRanges.isEmpty()) {
            _statusMessage.value = "当前选择的 IP 段无效，请重新选择"
            return
        }

        viewModelScope.launch {
            _isTesting.value = true
            _results.value = emptyList()
            _statusMessage.value = "开始测速..."

            val config = SpeedTestEngine.TestConfig(
                testCount = _testCount.value,
                pingCount = _pingCount.value,
                downloadTest = _enableDownloadTest.value,
                downloadDuration = _downloadDuration.value * 1000L,  // 秒转毫秒
                speedLimit = _speedLimit.value,
                latencyLimit = _latencyLimit.value,
                downloadTestCount = _downloadTestCount.value,
                pingFirst = true,
                maxConcurrentDownloads = 5
            )

            val currentResults = mutableListOf<SpeedTestResult>()

            try {
                engine.startTest(
                    ipRanges = activeIpRanges,
                    config = config,
                    onProgress = { progress ->
                        _progress.value = progress
                        _statusMessage.value = "${progress.status} ${progress.currentIp}"
                    },
                    onResult = { result ->
                        currentResults.add(result)
                        _results.value = if (_sortByLatency.value) {
                            currentResults.sortedBy { it.avgLatency }
                        } else {
                            currentResults.sortedByDescending { it.downloadSpeed }
                        }
                    },
                    onComplete = { finalResults ->
                        val sortedResults = if (_sortByLatency.value) {
                            finalResults.sortedBy { it.avgLatency }
                        } else {
                            finalResults.sortedByDescending { it.downloadSpeed }
                        }
                        _results.value = sortedResults
                        _isTesting.value = false
                        _statusMessage.value = if (finalResults.isNotEmpty()) {
                            "测速完成，找到 ${finalResults.size} 个可用 IP"
                        } else {
                            "测速完成，未找到可用 IP"
                        }
                        saveCurrentResultsToHistory()
                    }
                )
            } catch (e: java.io.IOException) {
                _isTesting.value = false
                _statusMessage.value = "测速失败：${e.message}"
            } catch (e: Exception) {
                _isTesting.value = false
                _statusMessage.value = "测速失败：${e.message}"
            }
        }
    }
    
    /**
     * 停止测速
     */
    fun stopTest() {
        engine.stopTest()
        _statusMessage.value = "正在停止测速..."
    }
    
    /**
     * 复制 IP 到剪贴板
     */
    fun copyIp(ip: String) {
        val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("IP Address", ip)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(application, "已复制：$ip", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 导出结果为 CSV
     */
    fun exportResults(): String {
        val builder = StringBuilder()
        builder.appendLine(SpeedTestResult.getCsvHeader())
        _results.value.forEach { result ->
            builder.appendLine(result.toCsvString())
        }
        return builder.toString()
    }
    
    /**
     * 获取最佳 IP
     */
    fun getBestIp(): SpeedTestResult? {
        return _results.value.firstOrNull()
    }
}
