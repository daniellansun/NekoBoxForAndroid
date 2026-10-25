package io.nekohasekai.sagernet.fmt.v2ray

import io.nekohasekai.sagernet.fmt.b64Std
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.withDefaults
import moe.matsuri.nb4a.SingBoxOptions
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class V2RayFmtTest {
    private val uuid = "b831381d-6324-4d53-ad4f-8cda48b30811"

    @Test
    fun tlsFlagFollowsSecurity() {
        val bean = VMessBean()
        assertFalse(bean.isTLS())
        bean.setTLS(true)
        assertTrue(bean.isTLS())
        assertEquals("tls", bean.security)
        bean.setTLS(false)
        assertEquals("", bean.security)
    }

    @Test
    fun parsesVlessDucksoftAndRoundTripsUri() {
        val link = "vless://$uuid@example.com:443" +
            "?type=ws&security=tls&sni=example.com&host=cdn.example" +
            "&path=%2Fws&ed=2048&eh=X-Early&fp=chrome&alpn=h2,http%2F1.1" +
            "&allowInsecure=1&packetEncoding=xudp&flow=xtls-rprx-vision-udp443" +
            "&encryption=mlkem768x25519plus&cert=CERT#edge"
        val bean = parseV2Ray(link) as VMessBean
        assertEquals(uuid, bean.uuid)
        assertEquals("example.com", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("edge", bean.name)
        assertEquals(-1, bean.alterId)
        assertTrue(bean.isVLESS)
        assertEquals("ws", bean.type)
        assertEquals("tls", bean.security)
        assertEquals("example.com", bean.sni)
        assertEquals("cdn.example", bean.host)
        assertEquals("/ws", bean.path)
        assertEquals(2048, bean.wsMaxEarlyData)
        assertEquals("X-Early", bean.earlyDataHeaderName)
        assertEquals("chrome", bean.utlsFingerprint)
        assertEquals("h2,http/1.1", bean.alpn)
        assertTrue(bean.allowInsecure)
        assertEquals(2, bean.packetEncoding)
        assertEquals("xtls-rprx-vision", bean.encryption)
        assertEquals("mlkem768x25519plus", bean.vlessEncryption)
        assertEquals("CERT", bean.certificates)
        assertEquals(bean, bean.withDefaults().clone())

        val again = parseV2Ray(bean.withDefaults().toUriVMessVLESSTrojan(false)) as VMessBean
        assertEquals(uuid, again.uuid)
        assertEquals("ws", again.type)
        assertEquals("tls", again.security)
        assertEquals("/ws", again.path)
        assertEquals(2048, again.wsMaxEarlyData)
        assertEquals("xtls-rprx-vision", again.encryption)
        assertEquals("mlkem768x25519plus", again.vlessEncryption)
        assertEquals(2, again.packetEncoding)
        assertEquals("edge", again.name)
    }

    @Test
    fun parsesTransportAndSecurityVariants() {
        val grpc = parseV2Ray(
            "vless://$uuid@h.example:8443?type=grpc&serviceName=Gun&security=reality&pbk=PUB&sid=ab&fp=firefox&sni=www.example"
        ) as VMessBean
        assertEquals("grpc", grpc.type)
        assertEquals("Gun", grpc.path)
        assertEquals("tls", grpc.security)
        assertEquals("PUB", grpc.realityPubKey)
        assertEquals("ab", grpc.realityShortId)
        assertEquals("firefox", grpc.utlsFingerprint)

        val kcp = parseV2Ray(
            "vless://$uuid@h.example:443?type=kcp&headerType=srtp&seed=abc&mtu=1350&tti=40&cwnd=2&security=none&packetEncoding=packet"
        ) as VMessBean
        assertEquals("kcp", kcp.type)
        assertEquals("srtp", kcp.headerType)
        assertEquals("abc", kcp.mKcpSeed)
        assertEquals(1350, kcp.kcpMtu)
        assertEquals(40, kcp.kcpTti)
        assertEquals(2, kcp.kcpCwndMultiplier)
        assertEquals(1, kcp.packetEncoding)

        val http = parseV2Ray(
            "vless://$uuid@h.example:8080?type=tcp&headerType=http&host=a.example&path=%2F&security=none&alpn=none"
        ) as VMessBean
        assertEquals("http", http.type)
        assertEquals("a.example", http.host)
        assertEquals("none", http.security)
        assertTrue(http.alpn.isNullOrBlank())

        val h2 = parseV2Ray("vless://$uuid@h.example:443?type=h2&host=a.example&path=%2Fh2") as VMessBean
        assertEquals("http", h2.type)
        assertEquals("/h2", h2.path)

        val upgraded = parseV2Ray(
            "vless://$uuid@h.example:443?type=httpupgrade&host=up.example&path=%2Fup"
        ) as VMessBean
        assertEquals("httpupgrade", upgraded.type)
        assertEquals("up.example", upgraded.host)

        val sniFromHost = parseV2Ray(
            "vless://$uuid@h.example:443?security=tls&host=sni.example&type=tcp&allowInsecure=true"
        ) as VMessBean
        assertEquals("sni.example", sniFromHost.sni)
        assertTrue(sniFromHost.allowInsecure)

        val rooted = parseV2Ray("vless://$uuid@h.example:443/nested/path?type=ws") as VMessBean
        assertEquals("nested/path", rooted.path)

        assertThrows(IllegalStateException::class.java) {
            parseV2Ray("vless://$uuid@h.example:1?type=kcp&headerType=nope")
        }
    }

    @Test
    fun parsesXhttpExtraThroughConverter() {
        val extra = """{"xPaddingBytes":"100-200","xmux":{"maxConcurrency":"8"}}"""
        val link = "vless://$uuid@h.example:443?type=xhttp&security=tls&mode=stream-one&path=%2Fx&host=cdn" +
            "&extra=${URLEncoder.encode(extra, "UTF-8")}"
        val bean = parseV2Ray(link) as VMessBean
        assertEquals("xhttp", bean.type)
        assertEquals("stream-one", bean.xhttpMode)
        assertEquals("/x", bean.path)
        val stored = JSONObject(bean.xhttpExtra)
        assertEquals("100-200", stored.getString("x_padding_bytes"))
        assertEquals("8", stored.getJSONObject("xmux").getString("max_concurrency"))

        val again = parseV2Ray(bean.withDefaults().toUriVMessVLESSTrojan(false)) as VMessBean
        assertEquals("xhttp", again.type)
        assertEquals("stream-one", again.xhttpMode)
        assertTrue(again.xhttpExtra.contains("xPaddingBytes") || again.xhttpExtra.contains("x_padding_bytes"))
    }

    @Test
    fun parsesRarePasswordFormats() {
        val password = "$uuid-0"
        val ws = parseV2Ray(
            "vmess://ws+tls:$password@rare.example:443?path=%2Fw&host=cdn&tlsServerName=sni.example#rare"
        ) as VMessBean
        // type is stored before "+tls" is stripped from the local protocol name.
        assertEquals("ws+tls", ws.type)
        assertEquals("tls", ws.security)
        assertEquals("sni.example", ws.sni)
        assertEquals("/w", ws.path)
        assertEquals("cdn", ws.host)
        assertEquals(uuid, ws.uuid)
        assertEquals(0, ws.alterId)
        assertEquals("rare", ws.name)

        val http = parseV2Ray(
            "vmess://http:$password@rare.example:80?path=%2Fa&host=h1%7Ch2"
        ) as VMessBean
        assertEquals("http", http.type)
        assertEquals("h1,h2", http.host)

        val grpc = parseV2Ray("vmess://grpc:$password@rare.example:443?serviceName=svc") as VMessBean
        assertEquals("grpc", grpc.type)
        assertEquals("svc", grpc.path)

        val upgrade = parseV2Ray(
            "vmess://httpupgrade:$password@rare.example:443?path=%2Fu&host=up"
        ) as VMessBean
        assertEquals("httpupgrade", upgrade.type)
        assertEquals("up", upgrade.host)
    }

    @Test
    fun parsesV2rayNJsonQrAndCsv() {
        val qr = """
            {"v":"2","ps":"qr node","add":"1.2.3.4","port":"443","id":"$uuid","aid":"2",
            "scy":"aes-128-gcm","net":"tcp","type":"http","host":"ex.com","path":"/p",
            "tls":"reality","sni":"","alpn":"h2","fp":"chrome"}
        """.trimIndent().replace("\n", "")
        val bean = parseV2Ray("vmess://${b64Std(qr)}") as VMessBean
        assertEquals("qr node", bean.name)
        assertEquals("1.2.3.4", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals(uuid, bean.uuid)
        assertEquals(2, bean.alterId)
        assertEquals("aes-128-gcm", bean.encryption)
        assertEquals("http", bean.type)
        assertEquals("tls", bean.security)
        assertEquals("ex.com", bean.sni)
        assertEquals("h2", bean.alpn)
        assertEquals("chrome", bean.utlsFingerprint)

        val exported = bean.withDefaults().toV2rayN()
        assertTrue(exported.startsWith("vmess://"))
        val parsed = parseV2RayN(exported)
        assertEquals("1.2.3.4", parsed.serverAddress)
        assertEquals(uuid, parsed.uuid)
        assertEquals("http", parsed.type)
        assertEquals("tls", parsed.security)

        val plainHttp = VMessBean().apply {
            serverAddress = "1.2.3.4"
            serverPort = 80
            uuid = this@V2RayFmtTest.uuid
            alterId = 0
            type = "http"
            host = "ex.com"
            path = "/"
            security = ""
            encryption = "auto"
        }.withDefaults()
        val httpQr = parseV2RayN(plainHttp.toV2rayN())
        assertEquals("http", httpQr.type)
        assertEquals("ex.com", httpQr.host)
        assertEquals("/", httpQr.path)

        val csv = """name = vmess,10.0.0.8,10086,auto,"$uuid",over-tls=true,tls-host=csv.example,obfs=ws,obfs-path="/ray"obfs Host:cdn.example["""
        val csvBean = parseV2Ray("vmess://${b64Std(csv)}")
        assertEquals("10.0.0.8", csvBean.serverAddress)
        assertEquals(10086, csvBean.serverPort)
        assertEquals("auto", csvBean.encryption)
        assertEquals(uuid, csvBean.uuid)
        assertEquals("tls", csvBean.security)
        assertEquals("cdn.example", csvBean.host)
        assertEquals("ws", csvBean.type)
        assertEquals("/ray", csvBean.path)
    }

    @Test
    fun parsesKitsunebiLink() {
        val user = b64Std("auto:$uuid@183.232.56.161:1202")
        val link = "vmess://$user?remarks=JP&obfs=websocket&obfsParam=%7B%22Host%22%3A%22cdn.example%22%7D&path=/v2ray&alterId=0&tls=1&allowInsecure=true"
        val bean = parseV2Ray(link) as VMessBean
        assertEquals("183.232.56.161", bean.serverAddress)
        assertEquals(1202, bean.serverPort)
        assertEquals(uuid, bean.uuid)
        assertEquals("auto", bean.encryption)
        assertEquals("JP", bean.name)
        assertEquals(0, bean.alterId)
        assertEquals("/v2ray", bean.path)
        assertEquals("ws", bean.type)
        assertEquals("cdn.example", bean.host)
        assertEquals("tls", bean.security)
        assertTrue(bean.allowInsecure)

        val plain = parseV2Ray(
            "vmess://${b64Std("none:$uuid@10.0.0.1:80")}?obfs=none&alterId=1"
        ) as VMessBean
        assertEquals("tcp", plain.type)
        assertEquals(1, plain.alterId)

        val sni = parseV2Ray(
            "vmess://${b64Std("auto:$uuid@10.0.0.2:443")}?obfs=websocket&tls=1&obfsParam=sni.example"
        )
        assertEquals("sni.example", sni.sni)
    }

    @Test
    fun rejectsGarbageVmessLink() {
        assertThrows(Exception::class.java) {
            parseV2Ray("vmess://%%%not-a-link")
        }
    }

    @Test
    fun buildsStreamSettingsForEachTransport() {
        val ws = VMessBean().apply {
            type = "ws"
            host = "cdn"
            path = "/ws?ed=bogus"
            wsMaxEarlyData = 64
            earlyDataHeaderName = "X-Early"
            security = "none"
        }.withDefaults()
        val wsOpt = buildSingBoxOutboundStreamSettings(ws) as SingBoxOptions.V2RayTransportOptions_WebsocketOptions
        assertEquals("ws", wsOpt.type)
        assertEquals("/ws", wsOpt.path)
        assertEquals(64, wsOpt.max_early_data)
        assertEquals("X-Early", wsOpt.early_data_header_name)
        assertEquals("cdn", wsOpt.headers["Host"])

        val earlyOnly = VMessBean().apply {
            type = "ws"
            path = "/ed?ed=nope"
            security = "none"
        }.withDefaults()
        val early = buildSingBoxOutboundStreamSettings(earlyOnly) as SingBoxOptions.V2RayTransportOptions_WebsocketOptions
        assertEquals("/ed", early.path)
        assertEquals(2048, early.max_early_data)
        assertEquals("Sec-WebSocket-Protocol", early.early_data_header_name)

        val bareWs = VMessBean().apply { type = "ws"; security = "none" }.withDefaults()
        val bare = buildSingBoxOutboundStreamSettings(bareWs) as SingBoxOptions.V2RayTransportOptions_WebsocketOptions
        assertEquals("/", bare.path)

        val kcp = VMessBean().apply {
            type = "kcp"
            headerType = "utp"
            mKcpSeed = "seed"
            kcpMtu = 1200
            kcpTti = 20
            kcpCwndMultiplier = 3
            security = "none"
        }.withDefaults()
        val kcpOpt = buildSingBoxOutboundStreamSettings(kcp) as SingBoxOptions.V2RayTransportOptions_KCPOptions
        assertEquals(1200, kcpOpt.mtu)
        assertEquals(20, kcpOpt.tti)
        assertEquals(3, kcpOpt.cwnd_multiplier)
        assertEquals("utp", kcpOpt.header_type)
        assertEquals("seed", kcpOpt.seed)

        val kcpDefault = VMessBean().apply { type = "kcp"; security = "none" }.withDefaults()
        val kcpDefOpt = buildSingBoxOutboundStreamSettings(kcpDefault) as SingBoxOptions.V2RayTransportOptions_KCPOptions
        assertEquals(1350, kcpDefOpt.mtu)
        assertEquals(50, kcpDefOpt.tti)
        assertEquals("none", kcpDefOpt.header_type)

        val http = VMessBean().apply {
            type = "http"
            host = "a.example,b.example"
            path = ""
            security = "none"
        }.withDefaults()
        val httpOpt = buildSingBoxOutboundStreamSettings(http) as SingBoxOptions.V2RayTransportOptions_HTTPOptions
        assertEquals("GET", httpOpt.method)
        assertEquals(listOf("a.example", "b.example"), httpOpt.host)
        assertEquals("/", httpOpt.path)

        val quic = buildSingBoxOutboundStreamSettings(VMessBean().apply { type = "quic"; security = "none" }.withDefaults())
        assertEquals("quic", quic!!.type)

        val grpc = buildSingBoxOutboundStreamSettings(
            VMessBean().apply { type = "grpc"; path = "svc"; security = "none" }.withDefaults()
        ) as SingBoxOptions.V2RayTransportOptions_GRPCOptions
        assertEquals("svc", grpc.service_name)

        val upgrade = buildSingBoxOutboundStreamSettings(
            VMessBean().apply { type = "httpupgrade"; host = "up"; path = "/u"; security = "none" }.withDefaults()
        ) as SingBoxOptions.V2RayTransportOptions_HTTPUpgradeOptions
        assertEquals("up", upgrade.host)
        assertEquals("/u", upgrade.path)

        assertNull(buildSingBoxOutboundStreamSettings(VMessBean().apply { type = "tcp"; security = "none" }.withDefaults()))
        assertNull(buildSingBoxOutboundStreamSettings(VMessBean().apply { type = "unknown"; security = "none" }.withDefaults()))
        assertNull(buildSingBoxOutboundTLS(VMessBean().apply { security = "none" }.withDefaults()))
    }

    @Test
    fun buildsXhttpTransportAndMergesExtra() {
        val bean = VMessBean().apply {
            type = "xhttp"
            host = "cdn"
            path = "/x"
            xhttpMode = "stream-one"
            xhttpExtra = """{"x_padding_bytes":"100-200","xmux":{"max_concurrency":"4"}}"""
            security = "none"
        }.withDefaults()
        val opt = buildSingBoxOutboundStreamSettings(bean) as SingBoxOptions.V2RayTransportOptions_XHTTPOptions
        assertEquals("xhttp", opt.type)
        assertEquals("stream-one", opt.mode)
        assertEquals("cdn", opt.host)
        assertEquals("/x", opt.path)
        assertNotNull(opt.x_padding_bytes)
        assertNotNull(opt.xmux)

        val broken = VMessBean().apply {
            type = "xhttp"
            xhttpExtra = "not-json"
            security = "none"
        }.withDefaults()
        val fallback = buildSingBoxOutboundStreamSettings(broken) as SingBoxOptions.V2RayTransportOptions_XHTTPOptions
        assertEquals("auto", fallback.mode)
        assertEquals("/", fallback.path)
    }

    @Test
    fun buildsOutboundsWithoutGlobalTlsStore() {
        val id = uuid
        val vmess = VMessBean().apply {
            serverAddress = "vm.example"
            serverPort = 443
            uuid = id
            alterId = 4
            encryption = "aes-128-gcm"
            type = "tcp"
            security = "none"
            packetEncoding = 1
        }.withDefaults()
        val vmessOut = buildSingBoxOutboundStandardV2RayBean(vmess) as SingBoxOptions.Outbound_VMessOptions
        assertEquals("vmess", vmessOut.type)
        assertEquals(uuid, vmessOut.uuid)
        assertEquals(4, vmessOut.alter_id)
        assertEquals("aes-128-gcm", vmessOut.security)
        assertEquals("packetaddr", vmessOut.packet_encoding)
        assertNull(vmessOut.transport)

        val vless = VMessBean().apply {
            alterId = -1
            serverAddress = "vl.example"
            serverPort = 443
            uuid = id
            encryption = "xtls-rprx-vision"
            vlessEncryption = "mlkem"
            type = "grpc"
            path = "svc"
            security = "none"
            packetEncoding = 0
        }.withDefaults()
        val vlessOut = buildSingBoxOutboundStandardV2RayBean(vless) as SingBoxOptions.Outbound_VLESSOptions
        assertEquals("vless", vlessOut.type)
        assertEquals("xtls-rprx-vision", vlessOut.flow)
        assertEquals("mlkem", vlessOut.encryption)
        assertEquals("", vlessOut.packet_encoding)
        assertEquals("grpc", vlessOut.transport!!.type)

        val trojan = TrojanBean().apply {
            serverAddress = "tr.example"
            serverPort = 443
            password = "secret"
            type = "quic"
            security = "none"
        }.withDefaults()
        trojan.security = "none"
        val trojanOut = buildSingBoxOutboundStandardV2RayBean(trojan) as SingBoxOptions.Outbound_TrojanOptions
        assertEquals("trojan", trojanOut.type)
        assertEquals("secret", trojanOut.password)
        assertEquals("quic", trojanOut.transport!!.type)
        assertNull(trojanOut.tls)

        val http = HttpBean().apply {
            serverAddress = "pr.example"
            serverPort = 8080
            username = "user"
            password = "pass"
            security = "none"
        }.withDefaults()
        val httpOut = buildSingBoxOutboundStandardV2RayBean(http) as SingBoxOptions.Outbound_HTTPOptions
        assertEquals("http", httpOut.type)
        assertEquals("user", httpOut.username)
        assertEquals("pass", httpOut.password)
    }

    @Test
    fun xhttpExtraConverterMapsBothDirections() {
        assertEquals("", XhttpExtraConverter.xrayToSingBox(" "))
        assertEquals("", XhttpExtraConverter.singBoxToXray(""))
        assertEquals("not-json", XhttpExtraConverter.xrayToSingBox("not-json"))
        assertEquals("{}", XhttpExtraConverter.singBoxToXray("{}"))

        val already = """{"x_padding_bytes":"1"}"""
        assertEquals(already, XhttpExtraConverter.xrayToSingBox(already))
        val alreadyXray = """{"xPaddingBytes":"1"}"""
        assertEquals(alreadyXray, XhttpExtraConverter.singBoxToXray(alreadyXray))

        val sing = JSONObject(
            XhttpExtraConverter.xrayToSingBox(
                """
                {"headers":{"a":"b"},"xPaddingBytes":"100-200","noGRPCHeader":true,
                "xmux":{"maxConcurrency":"8","hKeepAlivePeriod":"15"},
                "downloadSettings":{"address":"dl.example","port":8443,"security":"tls",
                "tlsSettings":{"serverName":"dl.example","alpn":["h2"],"allowInsecure":true,"fingerprint":"chrome"},
                "xhttpSettings":{"mode":"stream-one","host":"cdn","path":"/d","extra":{"xPaddingKey":"key"}}}}
                """.trimIndent().replace("\n", "")
            )
        )
        assertEquals("100-200", sing.getString("x_padding_bytes"))
        assertTrue(sing.getBoolean("no_grpc_header"))
        assertEquals("8", sing.getJSONObject("xmux").getString("max_concurrency"))
        val download = sing.getJSONObject("download")
        assertEquals("dl.example", download.getString("server"))
        assertEquals(8443, download.getInt("server_port"))
        assertEquals("stream-one", download.getString("mode"))
        assertEquals("key", download.getString("x_padding_key"))
        assertEquals("dl.example", download.getJSONObject("tls").getString("server_name"))
        assertEquals("chrome", download.getJSONObject("tls").getJSONObject("utls").getString("fingerprint"))

        val xray = JSONObject(XhttpExtraConverter.singBoxToXray(sing.toString()))
        assertEquals("100-200", xray.getString("xPaddingBytes"))
        val xrayDown = xray.getJSONObject("downloadSettings")
        assertEquals("dl.example", xrayDown.getString("address"))
        assertEquals("xhttp", xrayDown.getString("network"))
        assertEquals("tls", xrayDown.getString("security"))
        assertEquals("stream-one", xrayDown.getJSONObject("xhttpSettings").getString("mode"))

        val reality = JSONObject(
            XhttpExtraConverter.xrayToSingBox(
                """
                {"downloadSettings":{"address":"r.example","port":443,"security":"reality",
                "realitySettings":{"serverName":"www.example","publicKey":"pub","shortId":"ab","fingerprint":"edge"}}}
                """.trimIndent().replace("\n", "")
            )
        )
        val realityTls = reality.getJSONObject("download").getJSONObject("tls")
        assertTrue(realityTls.getJSONObject("reality").getBoolean("enabled"))
        assertEquals("pub", realityTls.getJSONObject("reality").getString("public_key"))
        val back = JSONObject(XhttpExtraConverter.singBoxToXray(reality.toString()))
        assertEquals("reality", back.getJSONObject("downloadSettings").getString("security"))
        assertEquals("pub", back.getJSONObject("downloadSettings").getJSONObject("realitySettings").getString("publicKey"))
    }
}
