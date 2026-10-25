package io.nekohasekai.sagernet.fmt.hysteria

import io.nekohasekai.sagernet.fmt.withDefaults
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HysteriaFmtTest {
    @Test
    fun parsesHysteria1AndRoundTrips() {
        val link = "hysteria://[2001:db8::1]:444?mport=10000-10010,20000&auth=secret&peer=sni.example" +
            "&insecure=1&upmbps=100&downmbps=200&alpn=h3&obfsParam=obfs&protocol=faketcp#hy1"
        val bean = parseHysteria1(link)
        assertEquals(1, bean.protocolVersion)
        assertEquals("2001:db8::1", bean.serverAddress)
        assertEquals("10000-10010,20000", bean.serverPorts)
        assertEquals("hy1", bean.name)
        assertEquals(HysteriaBean.TYPE_STRING, bean.authPayloadType)
        assertEquals("secret", bean.authPayload)
        assertEquals("sni.example", bean.sni)
        assertTrue(bean.allowInsecure)
        assertEquals(100, bean.uploadMbps)
        assertEquals(200, bean.downloadMbps)
        assertEquals("h3", bean.alpn)
        assertEquals("obfs", bean.obfuscation)
        assertEquals(HysteriaBean.PROTOCOL_FAKETCP, bean.protocol)
        assertFalse(bean.canUseSingBox())
        assertEquals(bean.withDefaults(), bean.withDefaults().clone())

        val again = parseHysteria1(bean.withDefaults().toUri())
        assertEquals("2001:db8::1", again.serverAddress)
        assertEquals("10000-10010,20000", again.serverPorts)
        assertEquals("secret", again.authPayload)
        assertEquals("sni.example", again.sni)
        assertEquals("obfs", again.obfuscation)
        assertEquals(HysteriaBean.PROTOCOL_FAKETCP, again.protocol)
        assertTrue(again.allowInsecure)
        assertEquals("hy1", again.name)
    }

    @Test
    fun parsesHysteria1WechatAndIgnoresBlankAuth() {
        val bean = parseHysteria1(
            "hysteria://hy.example:443?auth=&insecure=true&upmbps=nope&downmbps=10&alpn=none&protocol=wechat-video"
        ).withDefaults()
        assertEquals(HysteriaBean.PROTOCOL_WECHAT_VIDEO, bean.protocol)
        assertEquals("443", bean.serverPorts)
        assertTrue(bean.allowInsecure)
        assertEquals(10, bean.uploadMbps)
        assertEquals(10, bean.downloadMbps)
        assertEquals("", bean.alpn)
        assertEquals("", bean.authPayload)
        assertFalse(bean.canUseSingBox())
        assertTrue(bean.toUri().startsWith("hysteria://"))

        val udp = parseHysteria1("hysteria://hy.example:443?auth=secret").withDefaults()
        assertEquals(HysteriaBean.PROTOCOL_UDP, udp.protocol)
        assertTrue(udp.canUseSingBox())
    }

    @Test
    fun parsesHysteria2UserPassAndToken() {
        val userPass = parseHysteria2(
            "hysteria2://user:pass@hy2.example:4443?mport=20000-20010&sni=hy2.example&insecure=1&obfs-password=salamander#hy2"
        )
        assertEquals(2, userPass.protocolVersion)
        assertEquals("user:pass", userPass.authPayload)
        assertEquals("20000-20010", userPass.serverPorts)
        assertEquals("hy2.example", userPass.sni)
        assertEquals("salamander", userPass.obfuscation)
        assertTrue(userPass.allowInsecure)
        assertEquals("hy2", userPass.name)

        val again = parseHysteria2(userPass.withDefaults().toUri())
        assertEquals("user:pass", again.authPayload)
        assertEquals("salamander", again.obfuscation)
        assertEquals("hy2.example", again.sni)
        assertEquals("hy2", again.name)

        val token = parseHysteria2("hy2://token@hy2.example#token")
        assertEquals("token", token.authPayload)
        assertEquals(443, token.serverPorts.toInt())
        assertEquals("token", token.name)
        assertTrue(token.withDefaults().toUri().startsWith("hy2://"))
    }

    @Test
    fun rejectsBadHysteriaLinksAndVersions() {
        assertThrows(IllegalStateException::class.java) {
            parseHysteria1("hysteria://")
        }
        assertThrows(IllegalStateException::class.java) {
            parseHysteria2("hy2://")
        }
        assertThrows(Exception::class.java) {
            HysteriaBean().apply { protocolVersion = 2 }.buildHysteria1Config(1080, null)
        }
        assertThrows(IllegalStateException::class.java) {
            buildSingBoxOutboundHysteriaBean(HysteriaBean().apply { protocolVersion = 9 }.withDefaults())
        }
    }

    @Test
    fun parsesJsonAndBuildsHysteria1Config() {
        val json = JSONObject(
            """
            {"server":"[2001:db8::2]:444","up_mbps":11,"down_mbps":22,"obfs":"xplus","auth":"YQ",
            "protocol":"wechat-video","server_name":"sni.example","alpn":"h3","insecure":true,
            "recv_window_conn":1,"recv_window":2,"disable_mtu_discovery":true}
            """.trimIndent()
        )
        val bean = json.parseHysteria1Json().withDefaults()
        assertEquals("2001:db8::2", bean.serverAddress)
        assertEquals("444", bean.serverPorts)
        assertEquals(HysteriaBean.TYPE_BASE64, bean.authPayloadType)
        assertEquals("YQ", bean.authPayload)
        assertEquals(HysteriaBean.PROTOCOL_WECHAT_VIDEO, bean.protocol)
        assertEquals(11, bean.uploadMbps)
        assertTrue(bean.allowInsecure)
        assertTrue(bean.disableMtuDiscovery)

        val authStr = JSONObject("""{"server":"hy.example:1","auth_str":"pw","protocol":"faketcp","alpn":"none"}""")
            .parseHysteria1Json()
        assertEquals(HysteriaBean.TYPE_STRING, authStr.authPayloadType)
        assertEquals("pw", authStr.authPayload)
        assertEquals(HysteriaBean.PROTOCOL_FAKETCP, authStr.protocol)
        assertTrue(authStr.alpn.isNullOrEmpty())

        val ca = File.createTempFile("hy-ca", ".pem")
        bean.caText = "CA"
        bean.finalAddress = "127.0.0.1"
        bean.serverAddress = "name.example"
        bean.sni = ""
        val built = JSONObject(bean.buildHysteria1Config(1080) { ca })
        assertEquals("name.example", built.getString("server_name"))
        assertEquals("wechat-video", built.getString("protocol"))
        assertEquals("YQ", built.getString("auth"))
        assertEquals(ca.absolutePath, built.getString("ca"))
        assertTrue(built.getBoolean("insecure"))
        assertEquals(1, built.getInt("recv_window_conn"))
        assertEquals(2, built.getInt("recv_window"))
        assertTrue(ca.readText().contains("CA"))

        val stringAuth = authStr.withDefaults()
        stringAuth.sni = "peer"
        val stringJson = JSONObject(stringAuth.buildHysteria1Config(1, null))
        assertEquals("faketcp", stringJson.getString("protocol"))
        assertEquals("pw", stringJson.getString("auth_str"))
        assertEquals("peer", stringJson.getString("server_name"))
    }

    @Test
    fun portHelpers() {
        assertFalse(isMultiPort("example.com"))
        assertFalse(isMultiPort("example.com:443"))
        assertTrue(isMultiPort("example.com:1000-2000"))
        assertTrue(isMultiPort("example.com:1,2"))
        assertEquals(8443, getFirstPort("8443,1"))
        assertEquals(443, getFirstPort("1000-2000"))
        assertEquals(443, getFirstPort("not-a-port"))
        assertEquals(listOf("1000:2000", "3000:3001"), hopPortsToSingboxList("1000-2000,3000:3001,5"))
        assertTrue(hopPortsToSingboxList("").isEmpty())
    }
}
