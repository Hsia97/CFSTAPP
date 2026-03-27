package com.cfst.app.speedtest

import com.cfst.app.model.SpeedTestResult
import com.cfst.app.utils.IpRangeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 测速引擎
 * 参考 Windows 版 cfst.exe 的测速逻辑优化
 */
class SpeedTestEngine {

    /**
     * 测速配置
     */
    data class TestConfig(
        val testCount: Int = 100,
        val pingCount: Int = 4,
        val pingTimeout: Int = 1000,
        val downloadTest: Boolean = true,
        val downloadDuration: Long = 10000,
        val speedLimit: Double = 0.0,
        val latencyLimit: Double = 0.0,
        val port: Int = 443,
        val pingOnly: Boolean = false,
        val downloadTestCount: Int = 20,
        val pingFirst: Boolean = true,
        val maxConcurrentDownloads: Int = 5
    )

    /**
     * 测速状态
     */
    data class TestProgress(
        val current: Int,
        val total: Int,
        val currentIp: String,
        val status: String
    )

    private val isRunning = AtomicBoolean(false)
    private var shouldStop = AtomicBoolean(false)

    suspend fun startTest(
        ipRanges: List<IpRangeParser.IpRange>,
        config: TestConfig,
        onProgress: (TestProgress) -> Unit,
        onResult: (SpeedTestResult) -> Unit,
        onComplete: (List<SpeedTestResult>) -> Unit
    ) {
        if (isRunning.getAndSet(true)) {
            return
        }

        shouldStop.set(false)
        val results = mutableListOf<SpeedTestResult>()
        val random = Random()
        val testedIps = mutableSetOf<String>()

        var consecutiveFailures = 0
        val maxConsecutiveFailures = 50

        try {
            withContext(Dispatchers.IO) {
                if (config.pingFirst) {
                    val pingResults = mutableListOf<Pair<String, TcpPingTest.PingResult>>()

                    for (i in 0 until config.testCount) {
                        if (shouldStop.get()) break
                        if (consecutiveFailures >= maxConsecutiveFailures) break

                        var ip: String? = null
                        var attempts = 0
                        while (ip == null || testedIps.contains(ip)) {
                            ip = IpRangeParser.getRandomIpFromRanges(ipRanges, random)
                            attempts++
                            if (attempts > 100) break
                        }

                        if (ip == null || testedIps.contains(ip)) continue
                        testedIps.add(ip)

                        onProgress(
                            TestProgress(
                                current = i + 1,
                                total = config.testCount,
                                currentIp = ip,
                                status = "正在 Ping 测试..."
                            )
                        )

                        val pingResult = TcpPingTest.ping(
                            ip = ip,
                            port = config.port,
                            count = config.pingCount,
                            timeoutMs = config.pingTimeout
                        )

                        if (pingResult.received == 0) {
                            consecutiveFailures++
                            continue
                        }

                        consecutiveFailures = 0

                        if (config.latencyLimit > 0 && pingResult.avgLatency > config.latencyLimit) {
                            continue
                        }

                        pingResults.add(ip to pingResult)
                    }

                    if (config.pingOnly) {
                        pingResults.forEach { (ip, pingResult) ->
                            val result = SpeedTestResult(
                                ipAddress = ip,
                                packetsSent = pingResult.sent,
                                packetsReceived = pingResult.received,
                                packetLoss = pingResult.packetLoss,
                                avgLatency = pingResult.avgLatency,
                                downloadSpeed = 0.0,
                                regionCode = "N/A"
                            )
                            results.add(result)
                            onResult(result)
                        }
                        return@withContext
                    }

                    val sortedPingResults = pingResults
                        .sortedBy { it.second.avgLatency }
                        .take(config.downloadTestCount)

                    val totalDownloads = sortedPingResults.size
                    val downloadSemaphore = Semaphore(config.maxConcurrentDownloads)
                    val completedDownloads = AtomicInteger(0)

                    if (totalDownloads > 0) {
                        onProgress(
                            TestProgress(
                                current = totalDownloads,
                                total = totalDownloads,
                                currentIp = sortedPingResults.first().first,
                                status = "正在下载测速..."
                            )
                        )
                    }

                    val downloadTasks = sortedPingResults.map { (ip, pingResult) ->
                        async {
                            if (shouldStop.get()) return@async null

                            downloadSemaphore.withPermit {
                                if (shouldStop.get()) return@withPermit null

                                try {
                                    val downloadSpeed = if (config.downloadTest) {
                                        DownloadTest.testDownloadSpeed(
                                            ip = ip,
                                            testDurationMs = config.downloadDuration
                                        )
                                    } else {
                                        0.0
                                    }

                                    if (config.speedLimit > 0 && downloadSpeed < config.speedLimit) {
                                        return@withPermit null
                                    }

                                    SpeedTestResult(
                                        ipAddress = ip,
                                        packetsSent = pingResult.sent,
                                        packetsReceived = pingResult.received,
                                        packetLoss = pingResult.packetLoss,
                                        avgLatency = pingResult.avgLatency,
                                        downloadSpeed = downloadSpeed,
                                        regionCode = "N/A"
                                    )
                                } finally {
                                    val finished = completedDownloads.incrementAndGet()
                                    val remaining = (totalDownloads - finished).coerceAtLeast(1)
                                    onProgress(
                                        TestProgress(
                                            current = remaining,
                                            total = totalDownloads,
                                            currentIp = ip,
                                            status = "正在下载测速..."
                                        )
                                    )
                                }
                            }
                        }
                    }

                    val downloadResults = downloadTasks.awaitAll()
                    downloadResults.filterNotNull().forEach { result ->
                        results.add(result)
                        onResult(result)
                    }
                } else {
                    for (i in 0 until config.testCount) {
                        if (shouldStop.get()) break
                        if (consecutiveFailures >= maxConsecutiveFailures) break

                        var ip: String? = null
                        var attempts = 0
                        while (ip == null || testedIps.contains(ip)) {
                            ip = IpRangeParser.getRandomIpFromRanges(ipRanges, random)
                            attempts++
                            if (attempts > 100) break
                        }

                        if (ip == null || testedIps.contains(ip)) continue
                        testedIps.add(ip)

                        onProgress(
                            TestProgress(
                                current = i + 1,
                                total = config.testCount,
                                currentIp = ip,
                                status = "正在测试..."
                            )
                        )

                        val pingResult = TcpPingTest.ping(
                            ip = ip,
                            port = config.port,
                            count = config.pingCount,
                            timeoutMs = config.pingTimeout
                        )

                        if (pingResult.received == 0) {
                            consecutiveFailures++
                            continue
                        }

                        consecutiveFailures = 0

                        if (config.latencyLimit > 0 && pingResult.avgLatency > config.latencyLimit) {
                            continue
                        }

                        var downloadSpeed = 0.0
                        if (config.downloadTest && !shouldStop.get()) {
                            downloadSpeed = DownloadTest.testDownloadSpeed(
                                ip = ip,
                                testDurationMs = config.downloadDuration
                            )
                        }

                        if (config.speedLimit > 0 && downloadSpeed < config.speedLimit) {
                            continue
                        }

                        val result = SpeedTestResult(
                            ipAddress = ip,
                            packetsSent = pingResult.sent,
                            packetsReceived = pingResult.received,
                            packetLoss = pingResult.packetLoss,
                            avgLatency = pingResult.avgLatency,
                            downloadSpeed = downloadSpeed,
                            regionCode = "N/A"
                        )

                        results.add(result)
                        onResult(result)
                    }
                }

                if (consecutiveFailures >= maxConsecutiveFailures) {
                    throw java.io.IOException(
                        "网络不可达：连续 $consecutiveFailures 次 Ping 测试失败，可能是 IPv6 网络未启用或不支持"
                    )
                }
            }
        } finally {
            isRunning.set(false)
            val sortedResults = results.sorted()
            onComplete(sortedResults)
        }
    }

    fun stopTest() {
        shouldStop.set(true)
    }

    fun isRunning(): Boolean = isRunning.get()
}
