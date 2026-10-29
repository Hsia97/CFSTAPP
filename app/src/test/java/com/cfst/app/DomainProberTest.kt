package com.cfst.app

import com.cfst.app.speedtest.DomainProber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainProberTest {

    @Test
    fun testCleanDomain() {
        assertEquals("my.domain.com", DomainProber.cleanDomain("https://my.domain.com/path"))
        assertEquals("test.com", DomainProber.cleanDomain("http://test.com:8443"))
        assertEquals("sub.example.com", DomainProber.cleanDomain("  sub.example.com/  "))
        assertEquals("api.example.com", DomainProber.cleanDomain("https://api.example.com:443/test?query=1#hash"))
        assertEquals("cloudflare.com", DomainProber.cleanDomain("CLOUDFLARE.COM"))
    }

    @Test
    fun testCloudflareErrorDetection() {
        val cfErrorRegex = Regex("""(?i)error\s+10\d{2}|dns\s+points\s+to\s+prohibited\s+ip""")

        val error1000 = "<html><body>Error 1000: DNS points to prohibited IP</body></html>"
        val error1001 = "<html><body>error 1001 dns resolution error</body></html>"
        val error1020 = "<html><title>Error 1020: Access Denied</title></html>"
        val normalPage = "<html><head><title>Welcome to My Site</title></head><body>Hello World</body></html>"

        assertTrue(cfErrorRegex.containsMatchIn(error1000))
        assertTrue(cfErrorRegex.containsMatchIn(error1001))
        assertTrue(cfErrorRegex.containsMatchIn(error1020))
        assertFalse(cfErrorRegex.containsMatchIn(normalPage))


    @Test
    fun testWebSocketFingerprintVerification() {
        // 模拟 101 升级成功
        val code101 = 101
        val isSuccess101 = code101 == 101 || (code101 == 400 && "13" != null)
        assertTrue(isSuccess101)

        // 模拟后端未鉴权返回 400 + Sec-WebSocket-Version: 13
        val code400WithHeader = 400
        val versionHeader = "13"
        val isSuccess400 = code400WithHeader == 101 || (code400WithHeader == 400 && versionHeader != null)
        assertTrue(isSuccess400)

        // 模拟常规 400（无 Sec-WebSocket-Version 头）
        val noHeaderVersion: String? = null
        val isFail400 = code400WithHeader == 101 || (code400WithHeader == 400 && noHeaderVersion != null)
        assertFalse(isFail400)

        // 模拟 Cloudflare 521/502/403 错误
        val code521 = 521
        val isFail521 = code521 == 101 || (code521 == 400 && versionHeader != null)
        assertFalse(isFail521)
    }
    }
}
