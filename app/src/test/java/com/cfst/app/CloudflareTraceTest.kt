package com.cfst.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudflareTraceTest {

    @Test
    fun testColoRegexParsing() {
        val sampleTrace = """
            fl=123f456
            h=speed.cloudflare.com
            ip=1.1.1.1
            ts=1714567890.123
            visit_scheme=http
            uag=Mozilla/5.0
            colo=HKG
            sliver=none
            http=http/1.1
            loc=CN
        """.trimIndent()

        val coloRegex = Regex("""(?m)^colo=([A-Z0-9]+)""")
        val match = coloRegex.find(sampleTrace)
        val colo = match?.groupValues?.get(1) ?: "N/A"

        assertEquals("HKG", colo)
    }

    @Test
    fun testColoFallbackOnMissing() {
        val invalidTrace = "error 403 forbidden"
        val coloRegex = Regex("""(?m)^colo=([A-Z0-9]+)""")
        val match = coloRegex.find(invalidTrace)
        val colo = match?.groupValues?.get(1) ?: "N/A"

        assertEquals("N/A", colo)
    }
}
