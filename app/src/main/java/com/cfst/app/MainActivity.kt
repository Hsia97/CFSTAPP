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
import androidx.compose.ui.text.style.TextOverflow
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
    val useIpv6 by viewModel.useIpv6.collectAsState()
    val sortByLatency by viewModel.sortByLatency.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Cloudflare Speed Test",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.22f)
                        ) {
                            Text(
                                text = if (useIpv6) "IPv6" else "IPv4",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1976D2),
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History",
                            tint = Color.White
                        )
                    }
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
                .padding(12.dp)
        ) {
            // 状态卡片
            StatusCard(
                isTesting = isTesting,
                progress = progress,
                statusMessage = statusMessage
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 控制按钮栏
            ControlBar(
                isTesting = isTesting,
                hasResults = results.isNotEmpty(),
                onStartClick = { viewModel.startTest() },
                onStopClick = { viewModel.stopTest() },
                onCopyBestClick = {
                    viewModel.getBestIp()?.let { viewModel.copyIp(it.ipAddress) }
                },
                onClearClick = { viewModel.clearResults() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 结果工作台 (撑满剩余高度)
            ResultsWorkspace(
                results = results,
                sortByLatency = sortByLatency,
                onToggleSort = { viewModel.updateSortByLatency(!sortByLatency) },
                onExportClick = { showExportDialog = true },
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

    // 历史记录对话框
    if (showHistory) {
        HistoryDialog(
            viewModel = viewModel,
            onDismiss = { showHistory = false }
        )
    }

    // 导出对话框
    if (showExportDialog) {
        ExportDialog(
            results = results,
            onDismiss = { showExportDialog = false }
        )
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
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 进度指示器
            if (isTesting) {
                val progressValue = if (progress.total > 0) progress.current.toFloat() / progress.total.toFloat() else 0f
                CircularProgressIndicator(
                    progress = progressValue,
                    modifier = Modifier.size(56.dp),
                    color = Color(0xFF1976D2),
                    strokeWidth = 4.dp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${progress.current} / ${progress.total}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = Color(0xFF1976D2)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
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
        }
    }
}

@Composable
fun ControlBar(
    isTesting: Boolean,
    hasResults: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onCopyBestClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = if (isTesting) onStopClick else onStartClick,
            modifier = Modifier.weight(1.4f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTesting) Color(0xFFF44336) else Color(0xFF1976D2)
            )
        ) {
            Icon(
                imageVector = if (isTesting) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = if (isTesting) "停止" else "开始测速", maxLines = 1)
        }

        OutlinedButton(
            onClick = onCopyBestClick,
            enabled = hasResults,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Text(text = "复制最佳", fontSize = 12.sp, maxLines = 1)
        }

        OutlinedButton(
            onClick = onClearClick,
            enabled = hasResults && !isTesting,
            modifier = Modifier.weight(0.8f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Text(text = "清空", fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
fun ColumnScope.ResultsWorkspace(
    results: List<SpeedTestResult>,
    sortByLatency: Boolean,
    onToggleSort: () -> Unit,
    onExportClick: () -> Unit,
    onCopyClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "测速结果 (${results.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onToggleSort,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = if (sortByLatency) "延迟优先" else "速度优先",
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = onExportClick,
                        enabled = results.isNotEmpty(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export",
                            tint = if (results.isNotEmpty()) Color(0xFF1976D2) else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无测速结果",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
fun EnhancedSettingsDialog(
    viewModel: SpeedTestViewModel,
    onDismiss: () -> Unit
) {
    val testCount by viewModel.testCount.collectAsState()
    val pingCount by viewModel.pingCount.collectAsState()
    val maxConcurrentPings by viewModel.maxConcurrentPings.collectAsState()
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
    val enableTargetProbe by viewModel.enableTargetProbe.collectAsState()
    val targetDomain by viewModel.targetDomain.collectAsState()
    val targetWsPath by viewModel.targetWsPath.collectAsState()

    var showSpecifiedSegmentsDialog by remember { mutableStateOf(false) }
    var showDropdownByClick by remember { mutableStateOf(false) }

    var testCountText by remember { mutableStateOf(testCount.toString()) }
    var pingCountText by remember { mutableStateOf(pingCount.toString()) }
    var maxConcurrentPingsText by remember { mutableStateOf(maxConcurrentPings.toString()) }
    var speedLimitText by remember { mutableStateOf(speedLimit.toString()) }
    var latencyLimitText by remember { mutableStateOf(latencyLimit.toString()) }
    var downloadTestCountText by remember { mutableStateOf(downloadTestCount.toString()) }
    var downloadDurationText by remember { mutableStateOf(downloadDuration.toString()) }
    var proxyUrlText by remember { mutableStateOf(proxyUrl) }
    var targetDomainText by remember { mutableStateOf(targetDomain) }
    var targetWsPathText by remember { mutableStateOf(targetWsPath) }

    val mirrorSites = viewModel.getMirrorSites()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "设置",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                TextButton(
                    onClick = {
                        viewModel.resetToDefaultSettings()
                        testCountText = "100"
                        pingCountText = "3"
                        maxConcurrentPingsText = "8"
                        downloadTestCountText = "20"
                        downloadDurationText = "10"
                        speedLimitText = "0.0"
                        latencyLimitText = "0.0"
                        proxyUrlText = ""
                        targetDomainText = ""
                        targetWsPathText = ""
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(text = "恢复默认", fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 卡片 1：TCP Ping 探测配置
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "TCP Ping 探测参数",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1976D2),
                            fontSize = 14.sp
                        )

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
                            label = { Text("单 IP Ping 次数") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = maxConcurrentPingsText,
                            onValueChange = {
                                maxConcurrentPingsText = it
                                it.toIntOrNull()?.let(viewModel::updateMaxConcurrentPings)
                            },
                            label = { Text("Ping 并发数") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 卡片 2：下载测速策略与阈值
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "下载测速策略与阈值",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1976D2),
                            fontSize = 14.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "启用下载测速", fontSize = 14.sp)
                            Switch(
                                checked = enableDownloadTest,
                                onCheckedChange = { viewModel.updateEnableDownloadTest(it) }
                            )
                        }

                        if (enableDownloadTest) {
                            OutlinedTextField(
                                value = downloadTestCountText,
                                onValueChange = {
                                    downloadTestCountText = it
                                    it.toIntOrNull()?.let(viewModel::updateDownloadTestCount)
                                },
                                label = { Text("精测 IP 数量 (延迟最低的前 N 个)") },
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
                                label = { Text("速度下限 (MB/s，0 为不限)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = latencyLimitText,
                                onValueChange = {
                                    latencyLimitText = it
                                    it.toDoubleOrNull()?.let(viewModel::updateLatencyLimit)
                                },
                                label = { Text("延迟上限 (ms，0 为不限)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 卡片 3：IP 地址池与网络管理
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "IP 地址池与网络管理",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1976D2),
                            fontSize = 14.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "仅测 IPv6 (关闭则仅测 IPv4)", fontSize = 14.sp)
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
                            Text(text = "仅测试指定 IP 段", fontSize = 14.sp)
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
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEBF3FC))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "已选 ${selectedSpecifiedIpSegments.size} / ${availableIpSegments.size} 个 IP 段",
                                        fontSize = 12.sp,
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
                        // 自定义域名连通性校验开关
                        // 目标域名/节点连通性校验 (合二为一)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "目标域名/节点连通性校验", fontSize = 14.sp)
                            Switch(
                                checked = enableTargetProbe,
                                onCheckedChange = { viewModel.updateEnableTargetProbe(it) }
                            )
                        }

                        if (enableTargetProbe) {
                            val isWsMode = targetWsPathText.trim().isNotBlank()
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isWsMode) Color(0xFFF3E5F5) else Color(0xFFE8F5E9)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = targetDomainText,
                                        onValueChange = {
                                            targetDomainText = it
                                            viewModel.updateTargetDomain(it)
                                        },
                                        label = { Text("目标域名 / SNI") },
                                        placeholder = { Text("例如：my.domain.com") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = targetWsPathText,
                                        onValueChange = {
                                            targetWsPathText = it
                                            viewModel.updateTargetWsPath(it)
                                        },
                                        label = { Text("专属通道路径 (可选)") },
                                        placeholder = { Text("例如：/api/v1/stream (留空仅测网页)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isWsMode) Color(0xFF7B1FA2).copy(alpha = 0.12f) else Color(0xFF2E7D32).copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = if (isWsMode) {
                                                "⚡ 当前模式：专属通道 (WS) 全链路穿透校验 (过滤假通死节点)"
                                            } else {
                                                "🌐 当前模式：Web 网页 TLS 连通性校验"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isWsMode) Color(0xFF7B1FA2) else Color(0xFF2E7D32),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 数据源更新按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.fetchCloudflareIpRanges() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text(text = "从 Cloudflare 更新", fontSize = 12.sp, maxLines = 1)
                            }
                            Button(
                                onClick = { viewModel.fetchGithubIpRanges() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text(text = "从 GitHub 更新", fontSize = 12.sp, maxLines = 1)
                            }
                        }

                        // 自定义镜像网址设置
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = proxyUrlText,
                                onValueChange = { text ->
                                    proxyUrlText = text
                                    viewModel.updateProxyUrl(text)
                                },
                                label = { Text("自定义镜像网址") },
                                placeholder = { Text("例如：https://ghproxy.com/...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused && mirrorSites.isNotEmpty()) {
                                            showDropdownByClick = true
                                        }
                                    },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                singleLine = false,
                                maxLines = 2
                            )

                            DropdownMenu(
                                expanded = showDropdownByClick && mirrorSites.isNotEmpty(),
                                onDismissRequest = { showDropdownByClick = false },
                                modifier = Modifier.fillMaxWidth()
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
                            Text(text = "使用自定义镜像", fontSize = 14.sp)
                            Switch(
                                checked = useProxy,
                                onCheckedChange = { viewModel.updateUseProxy(it) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
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
    var expandedRecordTime by remember { mutableStateOf<Long?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "历史记录",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "共 ${historyRecords.size} 次测试记录",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (historyRecords.isNotEmpty()) {
                        Button(
                            onClick = {
                                expandedRecordTime = null
                                viewModel.clearAllHistoryRecords()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("清空全部", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

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
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(historyRecords) { index: Int, record: List<SpeedTestResult> ->
                            if (record.isNotEmpty()) {
                                val firstResult = record.first()
                                val recordTime = firstResult.testTime
                                val isExpanded = expandedRecordTime == recordTime

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        // 记录标题、时间和删除按钮
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "记录 ${index + 1} (${record.size} 个结果)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = firstResult.getTestTimeFormatted(),
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    if (isExpanded) expandedRecordTime = null
                                                    viewModel.deleteHistoryRecord(index)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Record",
                                                    tint = Color(0xFFF44336),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // 若未展开，展示 Top 3 预览
                                        if (!isExpanded) {
                                            val previewList = record.take(3)
                                            previewList.forEach { result ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = result.ipAddress,
                                                            fontSize = 12.sp
                                                        )
                                                        if (result.regionCode.isNotEmpty() && result.regionCode != "N/A") {
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "[${result.regionCode}]",
                                                                fontSize = 10.sp,
                                                                color = Color(0xFF1976D2),
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    Row {
                                                        Text(
                                                            text = "${String.format("%.1f", result.avgLatency)}ms ",
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
                                        } else {
                                            // 若已展开，展示手风琴内嵌的完整列表
                                            Divider(modifier = Modifier.padding(vertical = 6.dp))
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                record.forEach { result ->
                                                    Card(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                                        onClick = {
                                                            val clip = ClipData.newPlainText("IP Address", result.ipAddress)
                                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            cm.setPrimaryClip(clip)
                                                            Toast.makeText(context, "已复制：${result.ipAddress}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(8.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Text(
                                                                        text = result.ipAddress,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 13.sp
                                                                    )
                                                                    if (result.regionCode.isNotEmpty() && result.regionCode != "N/A") {
                                                                        Spacer(modifier = Modifier.width(4.dp))
                                                                        Surface(
                                                                            shape = RoundedCornerShape(3.dp),
                                                                            color = Color(0xFFE3F2FD)
                                                                        ) {
                                                                            Text(
                                                                                text = result.regionCode,
                                                                                fontSize = 9.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = Color(0xFF1976D2),
                                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                                Text(
                                                                    text = "${result.packetLoss}% 丢包",
                                                                    fontSize = 11.sp,
                                                                    color = if (result.packetLoss == 0.0) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                                                )
                                                            }
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text(
                                                                    text = "延迟: ${String.format("%.2f", result.avgLatency)} ms",
                                                                    fontSize = 11.sp,
                                                                    color = Color.Gray
                                                                )
                                                                Text(
                                                                    text = "下载: ${String.format("%.2f", result.downloadSpeed)} MB/s",
                                                                    fontSize = 11.sp,
                                                                    color = Color(0xFF1976D2)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // 展开 / 折叠切换按钮
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    expandedRecordTime = if (isExpanded) null else recordTime
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (isExpanded) "收起 ▴" else "展开全部 (${record.size}项) ▾",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF1976D2)
                                                )
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
}
