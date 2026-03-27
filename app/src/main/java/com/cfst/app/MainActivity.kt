package com.cfst.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.time.Duration.Companion.seconds
import com.cfst.app.model.SpeedTestResult
import com.cfst.app.speedtest.SpeedTestEngine
import com.cfst.app.viewmodel.SpeedTestViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SpeedTestApp(application)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestApp(application: android.app.Application) {
    val viewModel: SpeedTestViewModel = viewModel(factory = SpeedTestViewModel.createFactory(application))
    val isTesting by viewModel.isTesting.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val results by viewModel.results.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showHistoryDetailDialog by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Cloudflare Speed Test",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1976D2),
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // 状态卡片
            StatusCard(
                isTesting = isTesting,
                progress = progress,
                statusMessage = statusMessage
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 控制按钮
            ControlButtons(
                isTesting = isTesting,
                onStartClick = { viewModel.startTest() },
                onStopClick = { viewModel.stopTest() },
                onExportClick = { showExportDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))


            Spacer(modifier = Modifier.height(8.dp))

            // 结果列表
            ResultsList(
                results = results,
                onCopyClick = { ip -> viewModel.copyIp(ip) }
            )
        }
    }

    // 设置对话框
    if (showSettings) {
        EnhancedSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettings = false }
        )
    }

    // 导出对话框
    if (showExportDialog) {
        ExportDialog(
            results = results,
            onDismiss = { showExportDialog = false }
        )
    }

    // 历史记录详情对话框
    if (showHistoryDetailDialog != null) {
        HistoryDetailDialog(
            historyRecord = viewModel.historyRecords.value.getOrElse(showHistoryDetailDialog!!) { emptyList() },
            onDismiss = { showHistoryDetailDialog = null }
        )
    }
}

@Composable
fun SortToggleSwitch(viewModel: SpeedTestViewModel) {
    val sortByLatency by viewModel.sortByLatency.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (sortByLatency) "按延迟排序 (低 → 高)" else "按速度排序 (高 → 低)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Switch(
                checked = sortByLatency,
                onCheckedChange = { viewModel.updateSortByLatency(it) }
            )
        }
    }
}

@Composable
fun StatusCard(
    isTesting: Boolean,
    progress: SpeedTestEngine.TestProgress,
    statusMessage: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTesting) Color(0xFFE3F2FD) else Color(0xFFF5F5F5)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 进度指示器
            if (isTesting) {
                val progressValue = progress.current.toFloat() / progress.total.toFloat()
                CircularProgressIndicator(
                    progress = progressValue,
                    modifier = Modifier.size(64.dp),
                    color = Color(0xFF1976D2),
                    strokeWidth = 4.dp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${progress.current} / ${progress.total}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFF1976D2)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (statusMessage.isNotEmpty()) statusMessage else "点击开始测速",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            if (isTesting && progress.currentIp.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = progress.currentIp,
                    fontSize = 12.sp,
                    color = Color(0xFF1976D2),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ControlButtons(
    isTesting: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onExportClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = if (isTesting) onStopClick else onStartClick,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTesting) Color(0xFFF44336) else Color(0xFF1976D2)
            )
        ) {
            Icon(
                imageVector = if (isTesting) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = if (isTesting) "停止" else "开始测速")
        }

        OutlinedButton(
            onClick = onExportClick,
            enabled = !isTesting
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "导出")
        }
    }
}

@Composable
fun ResultsList(
    results: List<SpeedTestResult>,
    onCopyClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "测速结果",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "共 ${results.size} 个",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无测速结果",
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(results) { result ->
                        ResultItem(
                            result = result,
                            onCopyClick = { onCopyClick(result.ipAddress) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultItem(
    result: SpeedTestResult,
    onCopyClick: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFAFAFA)
        ),
        onClick = {
            onCopyClick()
            clipboardManager.setText(AnnotatedString(result.ipAddress))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // IP 地址
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.ipAddress,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "${result.packetLoss}% 丢包",
                    fontSize = 12.sp,
                    color = if (result.packetLoss == 0.0) Color(0xFF4CAF50) else Color(0xFFFF9800)
                )
            }

            // 延迟
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${String.format("%.2f", result.avgLatency)} ms",
                    fontSize = 14.sp,
                    color = when {
                        result.avgLatency < 100 -> Color(0xFF4CAF50)
                        result.avgLatency < 200 -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    }
                )
                Text(
                    text = "${String.format("%.2f", result.downloadSpeed)} MB/s",
                    fontSize = 12.sp,
                    color = if (result.downloadSpeed > 0) Color(0xFF1976D2) else Color.Gray
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: SpeedTestViewModel,
    onDismiss: () -> Unit
) {
    val testCount by viewModel.testCount.collectAsState()
    val pingCount by viewModel.pingCount.collectAsState()
    val enableDownloadTest by viewModel.enableDownloadTest.collectAsState()
    val speedLimit by viewModel.speedLimit.collectAsState()
    val latencyLimit by viewModel.latencyLimit.collectAsState()
    val downloadTestCount by viewModel.downloadTestCount.collectAsState()
    val downloadDuration by viewModel.downloadDuration.collectAsState()
    val useProxy by viewModel.useProxy.collectAsState()
    val proxyUrl by viewModel.proxyUrl.collectAsState()
    val useIpv6 by viewModel.useIpv6.collectAsState()
    val useSpecifiedIpSegments by viewModel.useSpecifiedIpSegments.collectAsState()
    val availableIpSegments by viewModel.availableIpSegments.collectAsState()
    val selectedSpecifiedIpSegments by viewModel.selectedSpecifiedIpSegments.collectAsState()
    val sortByLatency by viewModel.sortByLatency.collectAsState()
    val historyRecords by viewModel.historyRecords.collectAsState()

    var showHistoryDialog by remember { mutableStateOf(false) }
    var showSpecifiedSegmentsDialog by remember { mutableStateOf(false) }

    var testCountText by remember { mutableStateOf(testCount.toString()) }
    var pingCountText by remember { mutableStateOf(pingCount.toString()) }
    var speedLimitText by remember { mutableStateOf(speedLimit.toString()) }
    var latencyLimitText by remember { mutableStateOf(latencyLimit.toString()) }
    var downloadTestCountText by remember { mutableStateOf(downloadTestCount.toString()) }
    var downloadDurationText by remember { mutableStateOf(downloadDuration.toString()) }
    var proxyUrlText by remember { mutableStateOf(proxyUrl) }

    // 创建镜像网址下拉列表的展开状态
    var expanded by remember { mutableStateOf(false) }

    // 镜像网址列表（使用 ViewModel 中的方法获取完整 URL）
    val mirrorSites = viewModel.getMirrorSites()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 测试数量
                OutlinedTextField(
                    value = testCountText,
                    onValueChange = {
                        testCountText = it
                        it.toIntOrNull()?.let { count -> viewModel.updateTestCount(count) }
                    },
                    label = { Text("测试 IP 数量") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Ping 次数
                OutlinedTextField(
                    value = pingCountText,
                    onValueChange = {
                        pingCountText = it
                        it.toIntOrNull()?.let { count -> viewModel.updatePingCount(count) }
                    },
                    label = { Text("Ping 次数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // 下载测速开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "下载测速")
                    Switch(
                        checked = enableDownloadTest,
                        onCheckedChange = { viewModel.updateEnableDownloadTest(it) }
                    )
                }

                // 下载测速数量
                OutlinedTextField(
                    value = downloadTestCountText,
                    onValueChange = {
                        downloadTestCountText = it
                        it.toIntOrNull()?.let { count -> viewModel.updateDownloadTestCount(count) }
                    },
                    label = { Text("测速 IP 数量 (测速时对延迟最低的 N 个进行测速)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // 下载测试时长
                OutlinedTextField(
                    value = downloadDurationText,
                    onValueChange = {
                        downloadDurationText = it
                        it.toIntOrNull()?.let { seconds -> viewModel.updateDownloadDuration(seconds) }
                    },
                    label = { Text("下载测试时长 (秒)，Windows 原版默认 10 秒)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // 速度下限
                OutlinedTextField(
                    value = speedLimitText,
                    onValueChange = {
                        speedLimitText = it
                        it.toDoubleOrNull()?.let { limit -> viewModel.updateSpeedLimit(limit) }
                    },
                    label = { Text("速度下限 (MB/s), 0 表示不限制") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // 延迟上限
                OutlinedTextField(
                    value = latencyLimitText,
                    onValueChange = {
                        latencyLimitText = it
                        it.toDoubleOrNull()?.let { limit -> viewModel.updateLatencyLimit(limit) }
                    },
                    label = { Text("延迟上限 (ms), 0 表示不限制") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // 从 GitHub 获取最新 IP 按钮
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.fetchCloudflareIpRanges() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "从 Cloudflare 获取最新 IP")
                    }

                    Button(
                        onClick = { viewModel.fetchGithubIpRanges() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "从 GitHub 获取最新 IP")
                    }
                }

                // 镜像网址设置（带下拉选择）
                var showDropdownByClick by remember { mutableStateOf(false) }
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = proxyUrlText,
                        onValueChange = { text ->
                            proxyUrlText = text
                            viewModel.updateProxyUrl(text)
                        },
                        label = { Text("自定义镜像网址") },
                        placeholder = { Text("例如：https://ghproxy.com/https://raw.githubusercontent.com/") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && mirrorSites.isNotEmpty()) {
                                    showDropdownByClick = true
                                }
                            },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = false,
                        maxLines = 3
                    )

                    // 下拉列表 - 显示所有内置选项
                    DropdownMenu(
                        expanded = showDropdownByClick && mirrorSites.isNotEmpty(),
                        onDismissRequest = { showDropdownByClick = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 40.dp)
                    ) {
                        // 添加一个"使用自定义输入"选项
                        DropdownMenuItem(
                            text = { Text(text = "✏️ 手动输入自定义网址", fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) },
                            onClick = {
                                showDropdownByClick = false
                            }
                        )
                        // 分隔线
                        Divider()
                        // 内置选项
                        mirrorSites.forEach { site ->
                            DropdownMenuItem(
                                text = { Text(text = site, fontSize = 12.sp) },
                                onClick = {
                                    proxyUrlText = site
                                    viewModel.updateProxyUrl(site)
                                    showDropdownByClick = false
                                }
                            )
                        }
                    }
                }

                // 使用自定义镜像开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "使用自定义镜像")
                    Switch(
                        checked = useProxy,
                        onCheckedChange = { viewModel.updateUseProxy(it) }
                    )
                }

                // IPv6 开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "仅测 IPv6 (关闭则仅测 IPv4)")
                    Switch(
                        checked = useIpv6,
                        onCheckedChange = {
                            viewModel.updateUseIpv6(it)
                            // 当切换 IPv4/IPv6 时，重新加载 IP 列表
                            viewModel.loadLocalIpRanges()
                        }
                    )
                }

                // 按延迟排序开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "按延迟排序 (关闭则按速度排序)")
                    Switch(
                        checked = sortByLatency,
                        onCheckedChange = { viewModel.updateSortByLatency(it) }
                    )
                }

                // 历史记录按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { showHistoryDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "历史记录 (${historyRecords.size})")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    // 历史记录对话框
    if (showHistoryDialog) {
        HistoryDialog(
            viewModel = viewModel,
            onDismiss = { showHistoryDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedSettingsDialog(
    viewModel: SpeedTestViewModel,
    onDismiss: () -> Unit
) {
    val testCount by viewModel.testCount.collectAsState()
    val pingCount by viewModel.pingCount.collectAsState()
    val enableDownloadTest by viewModel.enableDownloadTest.collectAsState()
    val speedLimit by viewModel.speedLimit.collectAsState()
    val latencyLimit by viewModel.latencyLimit.collectAsState()
    val downloadTestCount by viewModel.downloadTestCount.collectAsState()
    val downloadDuration by viewModel.downloadDuration.collectAsState()
    val useProxy by viewModel.useProxy.collectAsState()
    val proxyUrl by viewModel.proxyUrl.collectAsState()
    val useIpv6 by viewModel.useIpv6.collectAsState()
    val useSpecifiedIpSegments by viewModel.useSpecifiedIpSegments.collectAsState()
    val availableIpSegments by viewModel.availableIpSegments.collectAsState()
    val selectedSpecifiedIpSegments by viewModel.selectedSpecifiedIpSegments.collectAsState()
    val sortByLatency by viewModel.sortByLatency.collectAsState()
    val historyRecords by viewModel.historyRecords.collectAsState()

    var showHistoryDialog by remember { mutableStateOf(false) }
    var showSpecifiedSegmentsDialog by remember { mutableStateOf(false) }
    var showDropdownByClick by remember { mutableStateOf(false) }

    var testCountText by remember { mutableStateOf(testCount.toString()) }
    var pingCountText by remember { mutableStateOf(pingCount.toString()) }
    var speedLimitText by remember { mutableStateOf(speedLimit.toString()) }
    var latencyLimitText by remember { mutableStateOf(latencyLimit.toString()) }
    var downloadTestCountText by remember { mutableStateOf(downloadTestCount.toString()) }
    var downloadDurationText by remember { mutableStateOf(downloadDuration.toString()) }
    var proxyUrlText by remember { mutableStateOf(proxyUrl) }

    val mirrorSites = viewModel.getMirrorSites()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = testCountText,
                    onValueChange = {
                        testCountText = it
                        it.toIntOrNull()?.let(viewModel::updateTestCount)
                    },
                    label = { Text("测试 IP 数量") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pingCountText,
                    onValueChange = {
                        pingCountText = it
                        it.toIntOrNull()?.let(viewModel::updatePingCount)
                    },
                    label = { Text("Ping 次数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "下载测速")
                    Switch(
                        checked = enableDownloadTest,
                        onCheckedChange = { viewModel.updateEnableDownloadTest(it) }
                    )
                }

                OutlinedTextField(
                    value = downloadTestCountText,
                    onValueChange = {
                        downloadTestCountText = it
                        it.toIntOrNull()?.let(viewModel::updateDownloadTestCount)
                    },
                    label = { Text("测速 IP 数量 (对延迟最低的 N 个进行测速)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = downloadDurationText,
                    onValueChange = {
                        downloadDurationText = it
                        it.toIntOrNull()?.let(viewModel::updateDownloadDuration)
                    },
                    label = { Text("下载测速时长 (秒)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = speedLimitText,
                    onValueChange = {
                        speedLimitText = it
                        it.toDoubleOrNull()?.let(viewModel::updateSpeedLimit)
                    },
                    label = { Text("速度下限 (MB/s)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = latencyLimitText,
                    onValueChange = {
                        latencyLimitText = it
                        it.toDoubleOrNull()?.let(viewModel::updateLatencyLimit)
                    },
                    label = { Text("延迟上限 (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.fetchCloudflareIpRanges() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "从 Cloudflare 获取最新 IP")
                    }

                    Button(
                        onClick = { viewModel.fetchGithubIpRanges() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "从 GitHub 获取最新 IP")
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = proxyUrlText,
                        onValueChange = { text ->
                            proxyUrlText = text
                            viewModel.updateProxyUrl(text)
                        },
                        label = { Text("自定义镜像网址") },
                        placeholder = { Text("例如：https://ghproxy.com/https://raw.githubusercontent.com/") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && mirrorSites.isNotEmpty()) {
                                    showDropdownByClick = true
                                }
                            },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = false,
                        maxLines = 3
                    )

                    DropdownMenu(
                        expanded = showDropdownByClick && mirrorSites.isNotEmpty(),
                        onDismissRequest = { showDropdownByClick = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 40.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "手动输入自定义网址",
                                    fontSize = 12.sp,
                                    fontStyle = FontStyle.Italic
                                )
                            },
                            onClick = { showDropdownByClick = false }
                        )
                        Divider()
                        mirrorSites.forEach { site ->
                            DropdownMenuItem(
                                text = { Text(text = site, fontSize = 12.sp) },
                                onClick = {
                                    proxyUrlText = site
                                    viewModel.updateProxyUrl(site)
                                    showDropdownByClick = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "使用自定义镜像")
                    Switch(
                        checked = useProxy,
                        onCheckedChange = { viewModel.updateUseProxy(it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "仅测 IPv6 (关闭则仅测 IPv4)")
                    Switch(
                        checked = useIpv6,
                        onCheckedChange = {
                            viewModel.updateUseIpv6(it)
                            viewModel.loadLocalIpRanges()
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "仅测试指定 IP 段")
                    Switch(
                        checked = useSpecifiedIpSegments,
                        onCheckedChange = {
                            viewModel.updateUseSpecifiedIpSegments(it)
                            if (it) {
                                showSpecifiedSegmentsDialog = true
                            }
                        }
                    )
                }

                if (useSpecifiedIpSegments) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F9FF))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "已选择 ${selectedSpecifiedIpSegments.size} / ${availableIpSegments.size} 个 IP 段",
                                fontSize = 13.sp,
                                color = Color(0xFF1976D2)
                            )
                            Button(
                                onClick = { showSpecifiedSegmentsDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = if (useIpv6) "选择 IPv6 IP 段" else "选择 IPv4 IP 段")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "按延迟排序 (关闭则按速度排序)")
                    Switch(
                        checked = sortByLatency,
                        onCheckedChange = { viewModel.updateSortByLatency(it) }
                    )
                }

                Button(
                    onClick = { showHistoryDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "历史记录 (${historyRecords.size})")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    if (showSpecifiedSegmentsDialog) {
        SpecifiedSegmentsDialog(
            title = if (useIpv6) "选择 IPv6 IP 段" else "选择 IPv4 IP 段",
            segments = availableIpSegments,
            selectedSegments = selectedSpecifiedIpSegments,
            onToggleSegment = viewModel::toggleSpecifiedIpSegment,
            onSelectAll = viewModel::selectAllSpecifiedIpSegments,
            onClearAll = viewModel::clearSpecifiedIpSegments,
            onDismiss = { showSpecifiedSegmentsDialog = false }
        )
    }

    if (showHistoryDialog) {
        HistoryDialog(
            viewModel = viewModel,
            onDismiss = { showHistoryDialog = false }
        )
    }
}

@Composable
fun SpecifiedSegmentsDialog(
    title: String,
    segments: List<String>,
    selectedSegments: Set<String>,
    onToggleSegment: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "共 ${segments.size} 个 IP 段，已选 ${selectedSegments.size} 个",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Row {
                        TextButton(onClick = onSelectAll) {
                            Text("全选")
                        }
                        TextButton(onClick = onClearAll) {
                            Text("清空")
                        }
                    }
                }

                if (segments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "当前没有可供选择的 IP 段", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(segments) { segment ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleSegment(segment) },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = segment in selectedSegments,
                                        onCheckedChange = { onToggleSegment(segment) }
                                    )
                                    Text(
                                        text = segment,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun ExportDialog(
    results: List<SpeedTestResult>,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    val csvContent = remember {
        buildString {
            appendLine(SpeedTestResult.getCsvHeader())
            results.forEach { appendLine(it.toCsvString()) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "导出结果") },
        text = {
            Column {
                Text(
                    text = "共 ${results.size} 条结果",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "点击复制按钮将 CSV 格式结果复制到剪贴板",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboardManager.setText(AnnotatedString(csvContent))
                onDismiss()
            }) {
                Text("复制到剪贴板")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDialog(
    viewModel: SpeedTestViewModel,
    onDismiss: () -> Unit
) {
    val historyRecords by viewModel.historyRecords.collectAsState()
    var showHistoryDetailDialog by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "历史记录") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "共 ${historyRecords.size} 条记录",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // 清空所有按钮
                    Button(
                        onClick = { viewModel.clearAllHistoryRecords() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) {
                        Text("清空全部", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (historyRecords.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无历史记录",
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(historyRecords) { index: Int, record: List<SpeedTestResult> ->
                            if (record.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        // 记录标题和时间
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "记录 ${index + 1} (${record.size} 个结果)",
                                                fontWeight = FontWeight.Bold
                                            )

                                            val firstResult = record.firstOrNull()
                                            if (firstResult != null) {
                                                Text(
                                                    text = firstResult.getTestTimeFormatted().substring(0, 16), // 显示日期时间
                                                    fontSize = 12.sp,
                                                    color = Color.Gray
                                                )
                                            }

                                            // 删除单个记录按钮
                                            IconButton(
                                                onClick = { viewModel.deleteHistoryRecord(index) },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Record",
                                                    tint = Color(0xFFF44336)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // 显示前 3 个最佳结果
                                        val top3 = record.take(3)
                                        top3.forEach { result ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = result.ipAddress,
                                                    fontSize = 12.sp
                                                )
                                                Row {
                                                    Text(
                                                        text = "${String.format("%.2f", result.avgLatency)}ms ",
                                                        fontSize = 12.sp,
                                                        color = Color.Gray
                                                    )
                                                    Text(
                                                        text = "${String.format("%.2f", result.downloadSpeed)}MB/s",
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF1976D2)
                                                    )
                                                }
                                            }
                                        }

                                        if (record.size > 3) {
                                            Text(
                                                text = "... 还有 ${record.size - 3} 项",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        }

                                        // 查看详情按钮
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    showHistoryDetailDialog = index
                                                }
                                            ) {
                                                Text("查看详情", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )

    // 历史记录详情对话框
    if (showHistoryDetailDialog != null) {
        HistoryDetailDialog(
            historyRecord = historyRecords.getOrElse(showHistoryDetailDialog!!) { emptyList() },
            onDismiss = { showHistoryDetailDialog = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailDialog(
    historyRecord: List<SpeedTestResult>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Box {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "历史记录详情") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(historyRecord.size) { index ->
                        val result = historyRecord[index]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
                            onClick = {
                                // 单击复制 IP 地址功能
                                val clip = ClipData.newPlainText("IP Address", result.ipAddress)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, "已复制：${result.ipAddress}", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = result.ipAddress,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "延迟：${String.format("%.2f", result.avgLatency)} ms",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = "下载：${String.format("%.2f", result.downloadSpeed)} MB/s",
                                        fontSize = 12.sp,
                                        color = Color(0xFF1976D2)
                                    )
                                }
                                Text(
                                    text = "丢包：${result.packetLoss}%",
                                    fontSize = 12.sp,
                                    color = if (result.packetLoss == 0.0) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        )
    }
}
