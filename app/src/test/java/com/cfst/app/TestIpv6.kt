fun main() {
    val range = com.cfst.app.utils.IpRangeParser.parseCidr("2606:4700::/32")
    if (range != null) {
        val ip = com.cfst.app.utils.IpRangeParser.getRandomIpFromRange(range)
        println("Generated IP: $ip")
    } else {
        println("Parse failed")
    }
}
