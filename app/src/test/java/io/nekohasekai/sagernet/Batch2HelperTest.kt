package io.nekohasekai.sagernet

import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.fileDescriptor
import io.nekohasekai.sagernet.ktx.forEachTry
import io.nekohasekai.sagernet.ktx.isExpert
import io.nekohasekai.sagernet.ktx.parseNumericAddress
import io.nekohasekai.sagernet.ktx.parsePort
import io.nekohasekai.sagernet.ktx.pathSafe
import io.nekohasekai.sagernet.ktx.tryResume
import io.nekohasekai.sagernet.ktx.tryResumeWithException
import io.nekohasekai.sagernet.ktx.unUrlSafe
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.checkEmpty
import moe.matsuri.nb4a.generateRemoteRuleSet
import moe.matsuri.nb4a.generateRuleSet
import moe.matsuri.nb4a.makeSingBoxRule
import moe.matsuri.nb4a.processRulesetUrl
import moe.matsuri.nb4a.utils.JavaUtil
import moe.matsuri.nb4a.utils.Util
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket

class Batch2HelperTest {
    @Test
    fun javaUtilStringHelpers() {
        assertEquals("A\n\t\u0007", JavaUtil.unescapeString("\\u0041\\n\\t\\007"))
        assertEquals("quote\"", JavaUtil.unescapeString("quote\\\""))
        assertEquals("000fff", JavaUtil.bytesToHex(byteArrayOf(0, 15, -1)))
        assertTrue(JavaUtil.isEmpty(null))
        assertTrue(JavaUtil.isEmpty(byteArrayOf()))
        assertFalse(JavaUtil.isEmpty(byteArrayOf(1)))
        assertTrue(JavaUtil.isNullOrBlank(" "))
        assertTrue(JavaUtil.isNotBlank("x"))
        assertEquals(BuildConfig.APPLICATION_ID, JavaUtil.getProcessName())
        assertNotNull(JavaUtil.gson)
        assertTrue(Util.timeStamp2Text(0).contains("-"))
    }

    @Test
    fun kryoRoundTripAndCorruptBytes() {
        assertEquals(0, KryoConverters.serialize(null).size)
        val bean = SOCKSBean().apply {
            initializeDefaultValues()
            serverAddress = "1.2.3.4"
            serverPort = 1080
            username = "u"
            name = "socks"
        }
        val bytes = KryoConverters.serialize(bean)
        val restored = KryoConverters.socksDeserialize(bytes)
        assertEquals("1.2.3.4", restored.serverAddress)
        assertEquals("u", restored.username)
        assertNull(KryoConverters.socksDeserialize(null))
        assertNull(KryoConverters.socksDeserialize(byteArrayOf()))
        val corrupt = KryoConverters.deserialize(SOCKSBean(), byteArrayOf(1, 2, 3, 4))
        assertEquals("127.0.0.1", corrupt.serverAddress)
    }

    @Test
    fun pureUtilsAndSingBoxOptionMaps() {
        assertEquals(8080, parsePort("8080", 1))
        assertEquals(1, parsePort(null, 1))
        assertEquals(1, parsePort("80", 1))
        assertEquals(1, parsePort("70000", 1))
        assertEquals("a+b", "a b".pathSafe())
        assertEquals("a b", "a%20b".unUrlSafe())
        assertNull("1.2.3.4".parseNumericAddress())
        assertNull("::1".parseNumericAddress())

        val errors = mutableListOf<String>()
        val thrown = assertThrows(IllegalStateException::class.java) {
            listOf("ok", "bad", "worse").forEachTry {
                if (it != "ok") error(it)
                errors.add(it)
            }
        }
        assertEquals(listOf("ok"), errors)
        assertEquals("bad", thrown.message)
        assertEquals(1, thrown.suppressed.size)

        var resumed: Result<Int>? = null
        val cont = object : Continuation<Int> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Int>) {
                if (resumed != null) throw IllegalStateException("again")
                resumed = result
            }
        }
        cont.tryResume(7)
        cont.tryResume(8)
        cont.tryResumeWithException(IllegalStateException("late"))
        assertEquals(7, resumed!!.getOrThrow())

        if (BuildConfig.DEBUG) assertTrue(isExpert)

        assertThrows(Exception::class.java) { Socket().fileDescriptor }

        val custom = SingBoxOptions.CustomSingBoxOption("null")
        assertTrue(custom.basicMap.isEmpty())
        val merged = SingBoxOptions.CustomSingBoxOption("""{"type":"direct","tag":"a"}""")
        merged._hack_config_map["extra"] = 1L
        merged._hack_custom_config = """{"tag":"b"}"""
        val map = merged.asMap()
        assertEquals("b", map["tag"])
        assertEquals(1L, map["extra"])

        val log = SingBoxOptions.LogOptions()
        log.level = "info"
        assertEquals("info", log.asMap()["level"])

        val options = SingBoxOptions.MyOptions()
        options.log = log
        options.inbounds = listOf(SingBoxOptions.Inbound_MixedOptions().apply {
            type = "mixed"
            tag = "in"
            listen_port = 2080
        })
        options.outbounds = listOf(SingBoxOptions.Outbound().apply {
            type = "direct"
            tag = "direct"
        })
        assertEquals("info", options.asMap()["log"].let { it as Map<*, *> }["level"])

        val dnsRule = SingBoxOptions.DNSRule_DefaultOptions()
        dnsRule.makeSingBoxRule(listOf("geosite:google", "full:A.com", "domain:b.com", "regexp:^c", "keyword:d", "plain.com", ""))
        assertEquals(listOf("geosite:google"), dnsRule.rule_set)
        assertEquals(listOf("a.com"), dnsRule.domain)
        assertFalse(dnsRule.checkEmpty())
        assertTrue(SingBoxOptions.DNSRule_DefaultOptions().checkEmpty())

        val rule = SingBoxOptions.Rule_DefaultOptions()
        rule.makeSingBoxRule(listOf("geoip:private", "geoip:cn", "1.1.1.1", ""), true)
        assertEquals(true, rule.ip_is_private)
        assertTrue(rule.rule_set.contains("geoip:cn"))
        rule.makeSingBoxRule(listOf("geosite:google", "full:a.com"), false)
        assertFalse(rule.checkEmpty())

        val sets = mutableListOf<SingBoxOptions.RuleSet>()
        generateRuleSet(listOf("geoip:cn", "geosite:google", "other"), sets)
        assertEquals(2, sets.size)
        assertEquals("local", sets[0].type)

        val remote = mutableListOf<SingBoxOptions.RuleSet>()
        val tag = generateRemoteRuleSet("https://example.com/r.json?x=1", remote, "24h")
        assertTrue(tag.startsWith("ruleset-"))
        assertEquals("source", remote.single().format)
        val binary = mutableListOf<SingBoxOptions.RuleSet>()
        generateRemoteRuleSet("https://example.com/r.srs", binary, "0")
        assertEquals("binary", binary.single().format)
        assertEquals("https://example.com/r.json" to false, processRulesetUrl("rssite:https://example.com/r.json"))
        assertEquals("https://example.com/ip.srs" to true, processRulesetUrl("rsip:https://example.com/ip.srs"))
    }
}
