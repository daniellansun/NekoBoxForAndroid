package io.nekohasekai.sagernet

import com.github.shadowsocks.plugin.Empty
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.ktx.SubscriptionFoundException
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.decodeBase64UrlSafe
import io.nekohasekai.sagernet.ktx.filterIsInstance
import io.nekohasekai.sagernet.ktx.forEach
import io.nekohasekai.sagernet.ktx.getBool
import io.nekohasekai.sagernet.ktx.getIntNya
import io.nekohasekai.sagernet.ktx.getStr
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.isJsonObjectValid
import io.nekohasekai.sagernet.ktx.linesNoComments
import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.parseProxies
import io.nekohasekai.sagernet.ktx.toLink
import io.nekohasekai.sagernet.ktx.toStringPretty
import io.nekohasekai.sagernet.ktx.unwrapIPV6Host
import io.nekohasekai.sagernet.ktx.urlSafe
import io.nekohasekai.sagernet.ktx.wrapIPV6Host
import io.nekohasekai.sagernet.utils.Commandline
import io.nekohasekai.sagernet.utils.Subnet
import kotlinx.coroutines.runBlocking
import moe.matsuri.nb4a.utils.JavaUtil
import moe.matsuri.nb4a.utils.NGUtil
import moe.matsuri.nb4a.utils.Util
import moe.matsuri.nb4a.utils.listByLineOrComma
import moe.matsuri.nb4a.utils.toBytesString
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class PureHelperTest {
    @Test
    fun formatsAndSubscriptionParser() {
        val obj = JSONObject("""{"s":"value","blank":"  ","n":1,"b":true,"bad":"x"}""")
        assertEquals("value", obj.getStr("s"))
        assertNull(obj.getStr("blank"))
        assertNull(obj.getStr("n"))
        assertNull(obj.getStr("missing"))
        assertEquals(true, obj.getBool("b"))
        assertNull(obj.getBool("bad"))
        assertEquals(1, obj.getIntNya("n"))
        assertNull(obj.getIntNya("s"))
        assertTrue(obj.toStringPretty().contains("value"))

        assertTrue(isJsonObjectValid(JSONObject()))
        assertTrue(isJsonObjectValid(JSONArray()))
        assertTrue(isJsonObjectValid("""{"a":1}"""))
        // JSONArray(Object) rejects a String, so a JSON array literal is not accepted.
        assertFalse(isJsonObjectValid("[1]"))
        assertTrue(isJsonObjectValid(JSONArray().put(1)))
        assertFalse(isJsonObjectValid("nope"))

        val array = JSONArray().put("a").put(1).put("b")
        assertEquals(listOf("a", "b"), array.filterIsInstance<String>())
        val seen = mutableListOf<Any>()
        array.forEach { _, value -> seen.add(value) }
        assertEquals(3, seen.size)
        val keys = mutableListOf<String>()
        obj.forEach { key, _ -> keys.add(key) }
        assertTrue(keys.contains("s"))

        assertEquals(listOf("a", "", "b"), " # c\na\n\n#d\n b ".linesNoComments())
        assertEquals("hi", "aGk".decodeBase64UrlSafe())
        assertEquals("a b", "a b".urlSafe().let { java.net.URLDecoder.decode(it, "UTF-8") })
        assertEquals("a%20b", "a b".urlSafe())
        assertNull(" ".blankAsNull())
        assertEquals("x", "x".blankAsNull())

        val uuid = "b831381d-6324-4d53-ad4f-8cda48b30811"
        val text = """
            # comment
            vless://$uuid@h.example:443?type=tcp&security=none#node socks5://user:pass@1.2.3.4:1080#socks
            http://user:pass@proxy.example:8080/#web
            hysteria://hy.example:443?auth=a#hy
            hy2://tok@hy.example#h2
            tuic://id:tok@tuic.example:443#tuic
            juicity://id:pw@j.example:443#ju
            snell://psk@sn.example:443?version=4#sn
            anytls://pw@a.example:443#any
            naive+https://user:pass@n.example:443#naive
            ss://aes-256-gcm:pass@1.2.3.4:8388#ss
            trojan://pw@tr.example:443?security=tls#tr
            trojan-go://pw@tg.example:443#tg
            not-a-link
        """.trimIndent()
        val proxies = runBlocking { parseProxies(text) }
        assertTrue(proxies.any { it is VMessBean && it.name == "node" })
        assertTrue(proxies.any { it is HysteriaBean && it.name == "hy" })
        assertTrue(proxies.any { it is NaiveBean && it.name == "naive" })
        assertTrue(proxies.any { it is ShadowsocksBean && it.name == "ss" })
        assertTrue(proxies.size >= 13)

        val thrown = assertThrows(SubscriptionFoundException::class.java) {
            runBlocking { parseProxies("https://example.com/sub/path") }
        }
        assertTrue(thrown.link.startsWith("clash://"))
        assertThrows(SubscriptionFoundException::class.java) {
            runBlocking { parseProxies("sn://subscription?abc") }
        }
        assertTrue(runBlocking { parseProxies("# only\n\nnot-a-link") }.isEmpty())

        val port443 = linkBuilder().host("example.com").port(443).toLink("vmess")
        assertTrue(port443.startsWith("vmess://example.com:443"))
        val omitted = linkBuilder().host("example.com").port(443).toLink("vmess", appendDefaultPort = false)
        assertFalse(omitted.contains(":443"))
        assertEquals("2001:db8::1", "[[2001:db8::1]]".unwrapIPV6Host())
        assertEquals("[2001:db8::1]", "2001:db8::1".wrapIPV6Host())
        assertEquals("example.com", "example.com".wrapIPV6Host())
        assertTrue("1.2.3.4".isIpAddress())
        assertFalse("example.com".isIpAddress())
    }

    @Test
    fun commandLineSubnetAndSmallUtils() {
        assertEquals("", Commandline.toString(null as Iterable<String>?))
        assertEquals("a b\\ c\\ \\\"", Commandline.toString(arrayOf("a", "b c \"")))
        assertArrayEquals(arrayOf("a", "b c"), Commandline.translateCommandline("a \"b c\""))
        assertArrayEquals(arrayOf("a", "b c"), Commandline.translateCommandline("a 'b c'"))
        assertArrayEquals(arrayOf("a", "b c"), Commandline.translateCommandline("a b\\ c"))
        assertEquals(0, Commandline.translateCommandline("   ").size)
        assertEquals(0, Commandline.translateCommandline(null).size)
        assertThrows(IllegalArgumentException::class.java) {
            Commandline.translateCommandline("\"unterminated")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Commandline.translateCommandline("trailing\\")
        }

        assertNull(Subnet.fromString("10.0.0.0/8"))
        assertNull(Subnet.fromString("not-an-ip"))
        val v4 = Subnet(InetAddress.getByAddress(byteArrayOf(10, 1, 2, 0)), 24)
        assertEquals("10.1.2.0/24", v4.toString())
        assertEquals(
            "10.1.2.0",
            Subnet(InetAddress.getByAddress(byteArrayOf(10, 1, 2, 0)), 32).toString()
        )
        val immutable = v4.toImmutable()
        assertTrue(immutable.matches(byteArrayOf(10, 1, 2, 9)))
        assertFalse(immutable.matches(byteArrayOf(10, 1, 3, 1)))
        assertFalse(immutable.matches(byteArrayOf(10, 1)))
        val other = Subnet(InetAddress.getByAddress(byteArrayOf(10, 1, 2, 9)), 32)
        assertTrue(v4 < other || v4.compareTo(other) != 0)
        assertEquals(v4, Subnet(InetAddress.getByAddress(byteArrayOf(10, 1, 2, 0)), 24))
        assertEquals(v4.hashCode(), Subnet(InetAddress.getByAddress(byteArrayOf(10, 1, 2, 0)), 24).hashCode())
        val v6 = Subnet(InetAddress.getByName("2001:db8::"), 128)
        assertTrue(v4 < v6)
        assertThrows(IllegalArgumentException::class.java) {
            Subnet(InetAddress.getByAddress(byteArrayOf(10, 0, 0, 1)), 99)
        }

        assertEquals("between", Util.getSubString("xxLEFTbetweenRIGHTyy", "LEFT", "RIGHT"))
        assertEquals("all", Util.getSubString("all", null, null))
        assertEquals("tail", Util.getSubString("tail", "missing", null))
        val raw = "hello shadowsocks".toByteArray()
        assertArrayEquals(raw, Util.zlibDecompress(Util.zlibCompress(raw, 9)))
        assertEquals("hi", String(Util.b64Decode(Util.b64EncodeUrlSafe("hi"))))
        assertFalse(Util.b64EncodeOneLine("hello".toByteArray()).contains("\n"))
        val merged = Util.mergeMap(
            mutableMapOf("list" to listOf("a"), "child" to mapOf("k" to "v")),
            mapOf("+list" to listOf("z"), "list+" to listOf("b"), "child" to mapOf("n" to "w"), "plain" to listOf("p"))
        )
        assertEquals(listOf("z", "a", "b"), merged["list"])
        @Suppress("UNCHECKED_CAST")
        assertEquals("w", (merged["child"] as Map<String, Any?>)["n"])
        val jsonMerged = mutableMapOf<String, Any?>("keep" to "yes")
        Util.mergeJSON(jsonMerged, """{"added":1}""")
        Util.mergeJSON(jsonMerged, " ")
        assertEquals("yes", jsonMerged["keep"])
        assertEquals(1L, jsonMerged["added"])
        assertEquals("file name", Util.decodeFilename("attachment; filename*=UTF-8''file%20name"))
        assertTrue(Util.timeStamp2Text(0).matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
        assertEquals("v", Util.map2StringMap(mapOf("k" to "v", 1 to "skip"))["k"])

        assertTrue(NGUtil.isIpv4Address("1.2.3.4"))
        assertFalse(NGUtil.isIpv4Address("1.2.3"))
        assertTrue(NGUtil.isIpv6Address("[2001:db8::1]"))
        assertTrue(NGUtil.isIpAddress("10.0.0.0/8"))
        assertTrue(NGUtil.isIpAddress("::ffff:192.168.0.1"))
        assertTrue(NGUtil.isIpAddress("[::ffff:192.168.0.1]:80"))
        assertFalse(NGUtil.isIpAddress(" "))
        assertFalse(NGUtil.isPureIpAddress("example.com"))
        assertEquals(12, NGUtil.parseInt("12"))
        assertEquals(0, NGUtil.parseInt("nope"))
        assertEquals(1, NGUtil.arrayFind(arrayOf("a", "b"), "b"))
        assertEquals(-1, NGUtil.arrayFind(arrayOf("a"), "z"))
        assertEquals("a b", NGUtil.urlDecode(NGUtil.urlEncode("a b")))
        assertEquals(32, NGUtil.getUuid().length)
        assertEquals("A", JavaUtil.unescapeString("\\u0041"))
        assertEquals("A", JavaUtil.unescapeString("\\101"))
        assertEquals("hi\n", JavaUtil.unescapeString("hi\\n"))
        assertEquals("0aff", JavaUtil.bytesToHex(byteArrayOf(0x0a, 0xff.toByte())))
        assertTrue(JavaUtil.isNullOrBlank("  "))
        assertFalse(JavaUtil.isNotBlank(null))
        assertTrue(JavaUtil.isEmpty(null))
        assertFalse(JavaUtil.isEmpty(byteArrayOf(1)))

        assertEquals(listOf("a", "b"), "a, b\n".listByLineOrComma())
        assertEquals("0 Bytes", 0L.toBytesString())
        assertTrue(1024L.toBytesString().contains("KiB"))
        assertTrue((1024L * 1024L).toBytesString().contains("MiB"))
        assertTrue((1024L * 1024L * 1024L).toBytesString().contains("GiB"))
        assertNotNull(Empty())
    }
}
