package io.nekohasekai.sagernet

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import android.os.PowerManager
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import androidx.recyclerview.widget.RecyclerView
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem
import com.danielstone.materialaboutlibrary.items.MaterialAboutItemOnClickAction
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.VpnService as SagerVpnService
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.bg.ShadowDnsResolver
import io.nekohasekai.sagernet.bg.ShadowOsAddress
import io.nekohasekai.sagernet.bg.ShadowProcessPhoenix
import io.nekohasekai.sagernet.bg.ShadowResolvingNetwork
import io.nekohasekai.sagernet.bg.ShadowTunBuilder
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.v2ray.buildSingBoxOutboundStandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.buildSingBoxOutboundStreamSettings
import io.nekohasekai.sagernet.fmt.v2ray.parseV2Ray
import io.nekohasekai.sagernet.fmt.v2ray.toUriVMessVLESSTrojan
import io.nekohasekai.sagernet.fmt.v2ray.toV2rayN
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.fileDescriptor
import io.nekohasekai.sagernet.ktx.listenForPackageChanges
import io.nekohasekai.sagernet.ktx.needReload
import io.nekohasekai.sagernet.ktx.needRestart
import io.nekohasekai.sagernet.ktx.parseNumericAddress
import io.nekohasekai.sagernet.ktx.scrollTo
import io.nekohasekai.sagernet.ktx.useCancellable
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.ui.AboutFragment
import io.nekohasekai.sagernet.ui.ConfigurationFragment
import io.nekohasekai.sagernet.ui.GroupSettingsActivity
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.RouteSettingsActivity
import io.nekohasekai.sagernet.widget.StatsBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import libcore.Libcore
import moe.matsuri.nb4a.plugin.Plugins
import moe.matsuri.nb4a.proxy.shadowtls.ShadowTLSBean
import moe.matsuri.nb4a.utils.NGUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowPopupMenu
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import android.content.ClipData
import android.content.ClipboardManager
import androidx.appcompat.view.ContextThemeWrapper

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [29],
    application = CoverageApplication::class,
    instrumentedPackages = ["com.jakewharton.processphoenix"],
    shadows = [
        ShadowTunBuilder::class,
        ShadowResolvingNetwork::class,
        ShadowOsAddress::class,
        ShadowProcessPhoenix::class,
        ShadowDnsResolver::class,
    ],
)
class Batch17RobolectricTest {

    private var mainController: org.robolectric.android.controller.ActivityController<MainActivity>? = null
    private var selectedGroup = 0L

    @Before
    fun drainStaleMain() {
        clearListenerList(ProfileManager, "listeners")
        clearListenerList(ProfileManager, "ruleListeners")
        clearListenerList(GroupManager, "listeners")
        selectedGroup = DataStore.currentGroupId()
        idleMain()
    }

    @After
    fun restoreOverrides() {
        closeMain()
        DataStore.serviceState = BaseService.State.Idle
        DataStore.selectedProxy = 0L
        DataStore.selectedGroup = selectedGroup
        DataStore.runningTest = false
        DataStore.dirty = false
        DataStore.editingId = 0L
        DataStore.connectionTestConcurrent = 5
        DataStore.alwaysShowAddress = false
        DataStore.groupLayoutMode = 0
        DataStore.serverConfig = ""
        SagerNet.underlyingNetwork = null
        BaseService.Binder.restartProcessOverride = null
        Libcore.httpBody = ""
        runCatching { ShadowDialog.getLatestDialog()?.dismiss() }
        runCatching { Runtime.getRuntime().exec(arrayOf("pkill", "-f", "echo b17")).waitFor() }
        idleMain()
    }

    @Test(timeout = 120_000)
    fun processPoolFastThenStderr() {
        fastGuardExit()
        stderrGuardStaysUp()
    }

    @Test(timeout = 120_000)
    fun v2rayBeansLinksAndBuilders() {
        cloneTransports()
        oldBuffers()
        parseLinks()
        exportLinks()
        singBoxBranches()
    }

    @Test(timeout = 180_000)
    fun configurationSharePingAndAbout() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        DataStore.alwaysShowAddress = true
        val groupId = DataStore.currentGroupId()
        val longHost = "very-long-hostname-for-coverage-batch-17.example"
        val shown = runBlocking {
            ProfileManager.createProfile(groupId, socks("b17-long", longHost, 443)).also {
                it.tx = 1200
                it.rx = 3400
            }
        }
        val second = runBlocking {
            ProfileManager.createProfile(groupId, socks("b17-second", "127.0.0.1", 9)).also {
                it.status = 2
                it.error = "down"
            }
        }
        val ssh = runBlocking {
            ProfileManager.createProfile(groupId, SSHBean().apply {
                initializeDefaultValues()
                name = "b17-ssh"
                serverAddress = "10.17.0.8"
                serverPort = 22
            })
        }
        val chain = runBlocking {
            ProfileManager.createProfile(groupId, ChainBean().apply {
                initializeDefaultValues()
                name = "b17-chain"
                proxies = arrayListOf(shown.id)
            })
        }
        val activity = openMain()
        val config = configuration(activity)
        if (config != null) {
            shareAndList(activity, config, shown, second, ssh, chain)
            airportName(config)
            pingIsolated(config)
            urlTestIsolated(config)
            DataStore.serviceState = BaseService.State.Connected
            config.needReload()
            clickSnackAction(activity)
            DataStore.serviceState = BaseService.State.Idle
        }
        aboutClicks(activity)
        if (config != null) {
            runCatching { config.needRestart() }
            clickSnackAction(activity)
            idleFor(600)
        }
        closeMain()
    }

    @Test(timeout = 120_000)
    fun settingsStatsAndUtils() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        groupMissingEntity()
        preferenceCatches()
        routeDialog()
        parcelArgs()
        statsTransitions()
        utilsAndNg()
    }

    @Test(timeout = 60_000)
    @Config(
        sdk = [24],
        application = CoverageApplication::class,
        instrumentedPackages = ["com.jakewharton.processphoenix"],
        shadows = [
            ShadowTunBuilder::class,
            ShadowResolvingNetwork::class,
            ShadowOsAddress::class,
            ShadowProcessPhoenix::class,
            ShadowDnsResolver::class,
        ],
    )
    fun numericAddressBeforeApi29() {
        runCatching { "2001:db8::17".parseNumericAddress() }
        runCatching { "127.0.0.1".parseNumericAddress() }
    }

    private fun fastGuardExit() {
        val fatal = AtomicInteger()
        val pool = GuardedProcessPool { fatal.incrementAndGet() }
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        runCatching { pool.start(listOf("/bin/true")) }
        Thread.sleep(80)
        val shadow = shadowOf(Looper.getMainLooper())
        var steps = 0
        while (fatal.get() == 0 && steps < 40 && !shadow.isIdle) {
            shadow.runOneTask()
            steps++
        }
        runCatching { pool.close(scope) }
        idleMain()
        scope.cancel()
    }

    private fun stderrGuardStaysUp() {
        val pool = GuardedProcessPool { }
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        runCatching {
            pool.start(listOf("/bin/sh", "-c", "echo b17 >&2; exec sleep 30"))
        }
        Thread.sleep(200)
        idleMain()
        runCatching { pool.close(scope) }
        idleMain()
        scope.cancel()
    }

    private fun cloneTransports() {
        for (type in arrayOf("http", "httpupgrade", "xhttp", "kcp", "grpc", "ws")) {
            val bean = VMessBean().apply {
                initializeDefaultValues()
                serverAddress = "10.17.0.2"
                serverPort = 443
                uuid = "11111111-1111-1111-1111-111111111111"
                this.type = type
                host = "h.example"
                path = "/p"
                security = "tls"
                sni = "sni.example"
                xhttpMode = "stream-one"
                xhttpExtra = """{"path":"/z"}"""
                mKcpSeed = "seed"
                headerType = "srtp"
                kcpMtu = 1200
                kcpTti = 20
                kcpCwndMultiplier = 2
                enableECH = true
                echConfig = "ech"
            }
            runCatching { bean.clone() }
        }
    }

    private fun oldBuffers() {
        readVmess(3, "grpc", {
            writeString("svc")
            writeString("legacy-a")
            writeString("legacy-b")
        }, "none") {
            writeBoolean(false)
            writeString("")
            writeInt(0)
            writeBoolean(false)
            writeBoolean(false)
            writeInt(0)
            writeInt(1)
        }
        readVmess(1, "tcp", {}, "none") {
            writeBoolean(true)
            writeBoolean(false)
            writeBoolean(true)
            writeString("ech-old")
            writeInt(0)
        }
        readVmess(0, "grpc", {
            writeString("svc")
            writeString("legacy-a")
            writeString("legacy-b")
        }, "none") {
            writeBoolean(true)
            writeBoolean(false)
            writeString("ech0")
            writeInt(3)
        }
        readVmess(6, "kcp", {
            writeString("seed")
            writeString("srtp")
        }, "none") {
            writeBoolean(false)
            writeString("")
            writeInt(0)
            writeBoolean(false)
            writeBoolean(false)
            writeInt(0)
            writeInt(1)
        }
    }

    private fun readVmess(
        version: Int,
        type: String,
        typeBody: ByteBufferOutput.() -> Unit,
        security: String,
        tail: ByteBufferOutput.() -> Unit,
    ) {
        val output = ByteBufferOutput(512)
        output.writeInt(version)
        output.writeString("10.17.0.1")
        output.writeInt(443)
        output.writeString("uuid-b17")
        output.writeString("auto")
        if (version >= 5) output.writeString("")
        output.writeInt(0)
        output.writeString(type)
        output.typeBody()
        output.writeString(security)
        output.tail()
        runCatching { VMessBean().deserialize(ByteBufferInput(output.toBytes())) }
    }

    private fun parseLinks() {
        val xhttp = "vmess://xhttp:11111111-1111-1111-1111-111111111111-0@10.17.1.1:8443" +
            "?host=cdn.example&path=%2Fx&mode=stream-one&extra=%7B%22path%22%3A%22%2Fz%22%7D#b17"
        runCatching { parseV2Ray(xhttp) }
        runCatching {
            parseV2Ray("vmess://11111111-1111-1111-1111-111111111111@10.17.1.2:443?encryption=aes-128-gcm")
        }
        val kitsunebi = android.util.Base64.encodeToString(
            "onlyone@10.17.1.3:443".toByteArray(),
            android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE,
        )
        runCatching { parseV2Ray("vmess://$kitsunebi") }
        val empty = android.util.Base64.encodeToString(
            "{}".toByteArray(),
            android.util.Base64.NO_WRAP,
        )
        runCatching { parseV2Ray("vmess://$empty") }
    }

    private fun exportLinks() {
        val bean = VMessBean().apply {
            initializeDefaultValues()
            serverAddress = "10.17.2.1"
            serverPort = 443
            uuid = "11111111-1111-1111-1111-111111111111"
            name = "b17"
        }
        bean.type = "kcp"
        bean.headerType = "srtp"
        bean.mKcpSeed = "seed"
        bean.kcpMtu = 1350
        bean.kcpTti = 20
        bean.kcpCwndMultiplier = 2
        runCatching { bean.toUriVMessVLESSTrojan(false) }
        bean.type = "http"
        bean.host = "h.example"
        bean.path = "/p"
        bean.security = "none"
        runCatching { bean.toUriVMessVLESSTrojan(false) }
        bean.type = "grpc"
        bean.path = "svc.name"
        runCatching { bean.toUriVMessVLESSTrojan(false) }
        bean.type = "xhttp"
        bean.host = "xh.example"
        bean.path = "/xh"
        bean.xhttpMode = "stream-one"
        bean.xhttpExtra = """{"path":"/z"}"""
        runCatching { bean.toUriVMessVLESSTrojan(false) }
        bean.type = "ws"
        bean.security = "tls"
        bean.sni = "sni.example"
        bean.realityPubKey = "pbk"
        bean.realityShortId = "abcd"
        bean.packetEncoding = 1
        bean.wsMaxEarlyData = 2048
        bean.earlyDataHeaderName = "Sec-WebSocket-Protocol"
        runCatching { bean.toUriVMessVLESSTrojan(false) }
        runCatching { bean.toV2rayN() }
    }

    private fun singBoxBranches() {
        val blank = VMessBean().apply {
            initializeDefaultValues()
            serverAddress = "10.17.3.1"
            serverPort = 443
            type = "xhttp"
            xhttpExtra = ""
        }
        runCatching { buildSingBoxOutboundStreamSettings(blank) }
        val broken = VMessBean().apply {
            initializeDefaultValues()
            serverAddress = "10.17.3.1"
            serverPort = 443
            type = "xhttp"
            xhttpExtra = "{"
        }
        runCatching { buildSingBoxOutboundStreamSettings(broken) }
        val vmess = VMessBean().apply {
            initializeDefaultValues()
            serverAddress = "10.17.3.2"
            serverPort = 443
            uuid = "11111111-1111-1111-1111-111111111111"
            packetEncoding = 2
            type = "tcp"
        }
        runCatching { buildSingBoxOutboundStandardV2RayBean(vmess) }
        val shadow = ShadowTLSBean().apply {
            initializeDefaultValues()
            serverAddress = "10.17.3.3"
            serverPort = 443
        }
        runCatching { buildSingBoxOutboundStandardV2RayBean(shadow) }
    }

    private fun shareAndList(
        activity: MainActivity,
        config: ConfigurationFragment,
        shown: ProxyEntity,
        second: ProxyEntity,
        ssh: ProxyEntity,
        chain: ProxyEntity,
    ) {
        val fragment = config.getCurrentGroupFragment() ?: return
        runCatching { fragment.adapter?.reloadProfiles() }
        idleMain()
        val list = fragment.configurationListView
        list.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        list.layout(0, 0, 1080, 1920)
        idleMain()
        val child = if (list.childCount > 0) list.getChildAt(0) else null
        val holder = if (child != null) list.getChildViewHolder(child) else null
        if (holder != null) {
            invokeMenu(holder, "showShareMenu", holder.itemView, ssh)
            clickPopup(R.id.action_standard_clipboard)
            clickPopup(R.id.action_standard_qr)
            clickPopup(R.id.action_universal_clipboard)
            clickPopup(R.id.action_universal_qr)
            clickPopup(R.id.action_config_export_clipboard)
            invokeMenu(holder, "showShareMenu", holder.itemView, chain)
            invokeMenu(holder, "showDoubleColumnMenu", holder.itemView, shown)
            clickPopup(R.id.action_edit)
            finishStarted()
            invokeMenu(holder, "showDoubleColumnMenu", holder.itemView, shown)
            clickPopup(R.id.action_share)
            clickPopup(R.id.action_config_export_file)
            DataStore.serverConfig = """{"b17":true}"""
            val uri = Uri.parse("content://coverage/b17-out.json")
            shadowOf(appContentResolver()).registerOutputStream(uri, ByteArrayOutputStream())
            runCatching { deliver(config, "exportConfig", uri) }
            idleMain()
            runCatching {
                holder.javaClass.getDeclaredMethod("bindProfileState", ProxyEntity::class.java)
                    .apply { isAccessible = true }
                    .invoke(holder, second)
            }
            runCatching {
                holder.javaClass.getDeclaredMethod("bindTraffic", ProxyEntity::class.java)
                    .apply { isAccessible = true }
                    .invoke(holder, shown)
            }
        }
        val adapter = fragment.adapter
        if (adapter != null) {
            shown.tx = 50
            shown.rx = 60
            second.status = 3
            second.error = "failed: ECONNREFUSED"
            runCatching { adapter.clearTrafficStatistics() }
            runCatching { adapter.clearTestResults() }
            if (adapter.itemCount >= 2) {
                runCatching { adapter.move(0, 1) }
                idleMain()
                runCatching { adapter.commitMove() }
            }
            runCatching { adapter.undo(listOf(0 to second)) }
            idleMain()
        }
        runCatching { list.scrollTo(0, true) }
        idleFor(350)
        dismissDialogs()
    }

    private fun airportName(config: ConfigurationFragment) {
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "b17",
                "clash://install-config?url=http://127.0.0.1:9/b17-sub",
            ),
        )
        config.onMenuItemClick(item(config.requireContext(), R.id.action_import_clipboard))
        Thread.sleep(200)
        idleMain()
        finishStarted()
        dismissDialogs()
    }

    private fun pingIsolated(config: ConfigurationFragment) {
        val server = ServerSocket(0)
        try {
            val openGroup = runBlocking {
                GroupManager.createGroup(ProxyGroup(name = "b17-open"))
            }
            DataStore.selectedGroup = openGroup.id
            SagerNet.underlyingNetwork = null
            val open = runBlocking {
                ProfileManager.createProfile(
                    openGroup.id,
                    socks("b17-open", "127.0.0.1", server.localPort),
                )
            }
            DataStore.connectionTestConcurrent = 1
            DataStore.runningTest = false
            config.pingTest(false)
            poll(30, 50) { open.status == 1 }
            clickDialog(AlertDialog.BUTTON_NEGATIVE)
            settleUntil { !DataStore.runningTest }

            val domainGroup = runBlocking {
                GroupManager.createGroup(ProxyGroup(name = "b17-domain"))
            }
            DataStore.selectedGroup = domainGroup.id
            SagerNet.underlyingNetwork = ShadowNetwork.newInstance(17)
            val domain = runBlocking {
                ProfileManager.createProfile(domainGroup.id, socks("b17-blank", " ", 80))
            }
            DataStore.runningTest = false
            config.pingTest(false)
            poll(20, 40) { domain.status == 2 }
            clickDialog(AlertDialog.BUTTON_NEGATIVE)
            settleUntil { !DataStore.runningTest }
        } finally {
            runCatching { server.close() }
            SagerNet.underlyingNetwork = null
            DataStore.runningTest = false
            DataStore.selectedGroup = selectedGroup
        }
    }

    private fun urlTestIsolated(config: ConfigurationFragment) {
        val group = runBlocking { GroupManager.createGroup(ProxyGroup(name = "b17-url")) }
        DataStore.selectedGroup = group.id
        runBlocking {
            ProfileManager.createProfile(group.id, socks("b17-url", "127.0.0.1", 1))
        }
        DataStore.connectionTestConcurrent = 1
        DataStore.runningTest = false
        runCatching { config.urlTest() }
        poll(15, 40) { !DataStore.runningTest }
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        dismissDialogs()
        settleUntil { !DataStore.runningTest }
        DataStore.runningTest = false
        DataStore.selectedGroup = selectedGroup
    }

    private fun aboutClicks(activity: MainActivity) {
        dismissDialogs()
        activity.displayFragmentWithId(R.id.nav_about)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val about = activity.supportFragmentManager.fragments
            .filterIsInstance<AboutFragment>()
            .lastOrNull() ?: return
        about.childFragmentManager.executePendingTransactions()
        var content = about.childFragmentManager.fragments
            .filterIsInstance<AboutFragment.AboutContent>()
            .lastOrNull()
        if (content == null) {
            content = AboutFragment.AboutContent()
            runCatching {
                about.childFragmentManager.beginTransaction()
                    .replace(R.id.about_fragment_holder, content!!)
                    .commitNowAllowingStateLoss()
            }
            content = about.childFragmentManager.fragments
                .filterIsInstance<AboutFragment.AboutContent>()
                .lastOrNull()
        }
        if (content == null) return
        shadowOf(app.getSystemService(PowerManager::class.java))
            .setIgnoringBatteryOptimizations(app.packageName, false)
        val plugin = PackageInfo().apply {
            packageName = "io.nekohasekai.sagernet.plugin.b17"
            versionName = "1"
            providers = arrayOf(ProviderInfo().apply {
                packageName = packageName
                metaData = Bundle().apply { putString(Plugins.METADATA_KEY_ID, "trojan-go-b17") }
            })
        }
        val previous = runCatching { PackageCache.installedPluginPackages }.getOrNull()
        PackageCache.installedPluginPackages = mapOf(plugin.packageName to plugin)
        val list = runCatching {
            AboutFragment.AboutContent::class.java
                .getDeclaredMethod("getMaterialAboutList", Context::class.java)
                .apply { isAccessible = true }
                .invoke(content, activity) as com.danielstone.materialaboutlibrary.model.MaterialAboutList
        }.getOrNull()
        if (list != null) {
            val field = MaterialAboutActionItem::class.java.getDeclaredField("onClickAction")
            field.isAccessible = true
            for (card in list.cards) {
                for (entry in card.items) {
                    val action = entry as? MaterialAboutActionItem ?: continue
                    val click = field.get(action) as? MaterialAboutItemOnClickAction
                    runCatching { click?.onClick() }
                    finishStarted()
                    idleMain()
                }
            }
        }
        Libcore.httpBody = """{"name":"coverage-9.9.9","html_url":"https://example.com/b17"}"""
        runCatching { content.checkUpdate(false) }
        poll(25, 80) {
            val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
            val button = dialog?.getButton(AlertDialog.BUTTON_POSITIVE)
            if (button != null) {
                button.performClick()
                true
            } else {
                false
            }
        }
        idleMain()
        runCatching {
            deliver(content, "requestIgnoreBatteryOptimizations", ActivityResult(Activity.RESULT_OK, null))
        }
        idleMain()
        if (previous != null) PackageCache.installedPluginPackages = previous
        dismissDialogs()
    }

    private fun groupMissingEntity() {
        val intent = Intent().putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, 999_999L)
        val controller = Robolectric.buildActivity(GroupSettingsActivity::class.java, intent).create()
        DataStore.editingId = 999_999L
        DataStore.dirty = true
        runBlocking { runCatching { controller.get().saveAndExit() } }
        idleMain()
        runCatching { controller.pause().stop().destroy() }
    }

    private fun preferenceCatches() {
        val host = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val route = RouteSettingsActivity.MyPreferenceFragmentCompat()
        runCatching {
            host.get().supportFragmentManager.beginTransaction()
                .add(android.R.id.content, route)
                .commitNow()
        }
        runCatching { route.onDisplayPreferenceDialog(Preference(host.get())) }
        val group = GroupSettingsActivity.MyPreferenceFragmentCompat()
        runCatching {
            host.get().supportFragmentManager.beginTransaction()
                .add(android.R.id.content, group)
                .commitNow()
        }
        runCatching { host.get().supportFragmentManager.beginTransaction().remove(route).remove(group).commitNow() }
        runCatching { host.pause().stop().destroy() }
    }

    private fun routeDialog() {
        val controller = Robolectric.buildActivity(RouteSettingsActivity::class.java).setup()
        idleMain()
        val frag = controller.get().supportFragmentManager.fragments
            .filterIsInstance<RouteSettingsActivity.MyPreferenceFragmentCompat>()
            .lastOrNull()
        if (frag != null) {
            runCatching { frag.onDisplayPreferenceDialog(Preference(controller.get())) }
        }
        runCatching { controller.pause().stop().destroy() }
        idleMain()
    }

    private fun parcelArgs() {
        roundTrip(RouteSettingsActivity.ProfileIdArg(7L))
        roundTrip(GroupSettingsActivity.GroupIdArg(8L))
    }

    private fun roundTrip(value: android.os.Parcelable) {
        val parcel = Parcel.obtain()
        value.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val creator = value.javaClass.getField("CREATOR").get(null) as android.os.Parcelable.Creator<*>
        creator.createFromParcel(parcel)
        parcel.recycle()
    }

    private fun statsTransitions() {
        val themed = ContextThemeWrapper(app, R.style.Theme_SagerNet)
        val detached = StatsBar(themed)
        detached.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(48, View.MeasureSpec.EXACTLY),
        )
        detached.layout(0, 0, 1080, 48)
        detached.syncMainControls(true, BaseService.State.Stopped, false, true)
        detached.layout(0, 0, 1080, 0)
        idleMain()

        val show = StatsBar(themed)
        show.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(48, View.MeasureSpec.EXACTLY),
        )
        show.layout(0, 0, 1080, 48)
        show.syncMainControls(true, BaseService.State.Stopped, false, true)
        setField(show, "currentState", BaseService.State.Connected)
        setField(show, "allowShow", true)
        idleMain()

        val faded = StatsBar(themed)
        faded.alpha = 0f
        faded.measure(
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY),
        )
        faded.layout(0, 0, 200, 40)

        val activity = openMain()
        val bar = activity.findViewById<StatsBar>(R.id.stats)
        if (bar != null) {
            bar.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY),
            )
            bar.layout(0, 0, 1080, 80)
            bar.syncMainControls(true, BaseService.State.Stopped, false, true)
            bar.layout(0, 0, 1080, 0)
            idleFor(200)
        }
        closeMain()
    }

    private fun utilsAndNg() {
        runCatching { Socket().fileDescriptor }
        val connection = URL("http://127.0.0.1:9").openConnection() as HttpURLConnection
        runBlocking {
            runCatching { connection.useCancellable { throw IOException("b17") } }
        }
        val job = CoroutineScope(Dispatchers.Main).launch {
            runCatching { connection.useCancellable { delay(60_000) } }
        }
        idleMain()
        job.cancel()
        idleMain()
        app.listenForPackageChanges { }
        app.listenForPackageChanges(false) { }
        app.sendBroadcast(Intent(Intent.ACTION_PACKAGE_REMOVED).setData(Uri.parse("package:io.b17")))
        idleMain()
        val boom = object : ContextWrapper(app) {
            override fun getSystemService(name: String): Any? {
                if (name == Context.CLIPBOARD_SERVICE) throw IllegalStateException("b17")
                return super.getSystemService(name)
            }
        }
        NGUtil.getClipboard(boom)
        NGUtil.setClipboard(boom, "b17")
        NGUtil.isIpAddress("1.2.3.4/nope")
        runCatching { NGUtil.openUri(app, "https://example.com/b17") }
        NGUtil.urlDecode("%")
        val recycler = RecyclerView(app)
        recycler.scrollTo(3, true)
        idleFor(350)
    }

    private fun invokeMenu(holder: RecyclerView.ViewHolder, name: String, anchor: View, entity: ProxyEntity) {
        runCatching {
            val method = holder.javaClass.getDeclaredMethod(name, View::class.java, ProxyEntity::class.java)
            method.isAccessible = true
            method.invoke(holder, anchor, entity)
        }
        idleMain()
    }

    private fun clickPopup(id: Int) {
        val popup = runCatching { ShadowPopupMenu.getLatestPopupMenu() }.getOrNull() ?: return
        runCatching { popup.menu.performIdentifierAction(id, 0) }
        idleMain()
        dismissDialogs()
    }

    private fun configuration(activity: MainActivity): ConfigurationFragment? {
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        return activity.supportFragmentManager.fragments.filterIsInstance<ConfigurationFragment>().lastOrNull()
    }

    private fun socks(name: String, address: String, port: Int) = SOCKSBean().apply {
        initializeDefaultValues()
        this.name = name
        serverAddress = address
        serverPort = port
    }

    private fun openMain(): MainActivity {
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        shadowOf(app).setComponentNameAndServiceForBindService(
            android.content.ComponentName(app, SagerVpnService::class.java),
            vpn.onBind(Intent(Action.SERVICE)),
        )
        DataStore.serviceMode = Key.MODE_VPN
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        mainController = controller
        idleMain()
        return controller.get()
    }

    private fun closeMain() {
        val controller = mainController ?: return
        mainController = null
        runCatching { controller.pause().stop().destroy() }
        idleMain()
    }

    private fun item(context: Context, id: Int) = MenuBuilder(context).add(0, id, 0, "item")

    private fun clickDialog(which: Int) {
        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog ?: return
        dialog.getButton(which)?.performClick()
        idleMain()
    }

    private fun clickSnackAction(activity: Activity) {
        val actionId = com.google.android.material.R.id.snackbar_action
        poll(20, 40) {
            val action = activity.findViewById<View>(actionId) ?: return@poll false
            action.performClick()
            true
        }
        idleMain()
    }

    private fun finishStarted() {
        repeat(4) {
            val intent = shadowOf(app).nextStartedActivity ?: return
            val name = intent.component?.className ?: return
            if (name.contains("ProcessPhoenix")) return
            runCatching {
                val clazz = Class.forName(name)
                @Suppress("UNCHECKED_CAST")
                val controller = Robolectric.buildActivity(clazz as Class<out Activity>, intent).setup()
                idleMain()
                controller.pause().stop().destroy()
            }
        }
    }

    private fun dismissDialogs() {
        repeat(3) {
            val dialog = ShadowDialog.getLatestDialog() ?: return
            runCatching { dialog.dismiss() }
            idleMain()
        }
    }

    private fun idleMain() {
        repeat(8) {
            try {
                shadowOf(Looper.getMainLooper()).idle()
                return
            } catch (e: IllegalStateException) {
                val message = e.message.orEmpty()
                val known = message.contains("synchronization barrier") ||
                    message.contains("Already resumed") ||
                    message.contains("stable ID") ||
                    message.contains("computing a layout") ||
                    message.contains("not attached")
                if (!known) throw e
            }
        }
    }

    private fun idleFor(millis: Long) {
        runCatching { ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS) }
        idleMain()
    }

    private fun settleUntil(ready: () -> Boolean) {
        repeat(40) {
            if (ready()) return
            Thread.sleep(30)
            idleMain()
        }
    }

    private fun poll(times: Int, pauseMs: Long, ready: () -> Boolean): Boolean {
        repeat(times) {
            if (ready()) return true
            Thread.sleep(pauseMs)
            idleMain()
        }
        return false
    }

    private fun clearListenerList(owner: Any, name: String) {
        val field = owner.javaClass.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(owner) as MutableList<Any>).clear()
    }

    private fun setField(target: Any, name: String, value: Any?) {
        val field = generateSequence<Class<*>>(target.javaClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField(name) }.getOrNull() }
            .first()
        field.isAccessible = true
        field.set(target, value)
    }

    private fun appContentResolver() = app.contentResolver

    private fun findDeclared(type: Class<*>, name: String): java.lang.reflect.Field {
        var current: Class<*>? = type
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException(name)
    }

    private fun deliver(owner: Any, fieldName: String, value: Any?) {
        val field = generateSequence<Class<*>>(owner.javaClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField(fieldName) }.getOrNull() }
            .first()
        field.isAccessible = true
        var launcher = field.get(owner)
        val ref = launcher.javaClass.declaredFields.firstOrNull { it.name == "val\$ref" }
        if (ref != null) {
            ref.isAccessible = true
            launcher = ref.get(launcher)
        }
        if (launcher is java.util.concurrent.atomic.AtomicReference<*>) {
            launcher = launcher.get()
        }
        val keyField = launcher.javaClass.declaredFields.first { it.name == "\$key" || it.name == "key" }
        keyField.isAccessible = true
        val key = keyField.get(launcher) as String
        val registryField = launcher.javaClass.getDeclaredField("this\$0")
        registryField.isAccessible = true
        val registry = registryField.get(launcher)
        val mapField = findDeclared(registry.javaClass, "keyToCallback")
        mapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val holder = (mapField.get(registry) as Map<String, Any>)[key] ?: return
        val callbackMethod = holder.javaClass.getDeclaredMethod("getCallback")
        callbackMethod.isAccessible = true
        val callback = callbackMethod.invoke(holder) as ActivityResultCallback<*>
        @Suppress("UNCHECKED_CAST")
        (callback as ActivityResultCallback<Any?>).onActivityResult(value)
    }
}
