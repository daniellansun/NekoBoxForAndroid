package io.nekohasekai.sagernet

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.aidl.ISagerNetServiceCallback
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.aidl.TrafficDataBatch
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.ProxyService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.bg.SubscriptionUpdater
import io.nekohasekai.sagernet.bg.proto.TrafficLooper
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.MemoryGroupDao
import io.nekohasekai.sagernet.database.MemoryKvDao
import io.nekohasekai.sagernet.database.MemoryProxyDao
import io.nekohasekai.sagernet.database.MemoryRuleDao
import io.nekohasekai.sagernet.database.RoomGroupDirectory
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.groupDirectory
import io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sagernet.fmt.ProductionConfigSource
import io.nekohasekai.sagernet.group.ProductionSubscriptionClient
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.broadcastReceiver
import io.nekohasekai.sagernet.ktx.crossFadeFrom
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.getColour
import io.nekohasekai.sagernet.ktx.listenForPackageChanges
import io.nekohasekai.sagernet.ktx.parseNumericAddress
import io.nekohasekai.sagernet.ktx.resolveResourceId
import io.nekohasekai.sagernet.ktx.scrollTo
import io.nekohasekai.sagernet.ui.AboutFragment
import io.nekohasekai.sagernet.ui.BlankActivity
import io.nekohasekai.sagernet.ui.ConfigurationFragment
import io.nekohasekai.sagernet.ui.GroupFragment
import io.nekohasekai.sagernet.ui.LogcatFragment
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.MessageStore
import io.nekohasekai.sagernet.ui.RouteFragment
import io.nekohasekai.sagernet.ui.SettingsFragment
import io.nekohasekai.sagernet.ui.ToolsFragment
import io.nekohasekai.sagernet.ui.VpnRequestActivity
import io.nekohasekai.sagernet.ui.WebviewFragment
import io.nekohasekai.sagernet.ui.isValidServerDns
import io.nekohasekai.sagernet.utils.AppLocale
import io.nekohasekai.sagernet.utils.CrashHandler
import io.nekohasekai.sagernet.utils.DefaultNetworkListener
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.utils.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import moe.matsuri.nb4a.ui.Dialogs
import moe.matsuri.nb4a.ui.MTUPreference
import moe.matsuri.nb4a.utils.JavaUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import io.nekohasekai.sagernet.bg.VpnService as SagerVpnService

/**
 * Sandbox application. Memory stores are installed before [SagerNet.onCreate]
 * reads [DataStore], so the real process init runs without opening Room.
 * Production still leaves the overrides null.
 */
class CoverageApplication : SagerNet() {
    override fun onCreate() {
        DataStore.configurationStoreOverride = RoomPreferenceDataStore(MemoryKvDao())
        DataStore.profileCacheStoreOverride = RoomPreferenceDataStore(MemoryKvDao())
        DataStore.userIndexOverride = 0
        DataStore.serviceState = BaseService.State.Stopped
        DataStore.mixedInboundAuthed = false
        DataStore.serviceMode = Key.MODE_PROXY
        DataStore.selectedProxy = 0L
        SagerDatabase.groupDaoOverride = MemoryGroupDao()
        SagerDatabase.proxyDaoOverride = MemoryProxyDao()
        SagerDatabase.rulesDaoOverride = MemoryRuleDao()
        SubscriptionUpdater.reconfigureOverride = {}
        super.onCreate()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = CoverageApplication::class)
class UiBgRobolectricTest {

    @After
    fun restoreOverrides() {
        DataStore.configurationStoreOverride = null
        DataStore.profileCacheStoreOverride = null
        DataStore.userIndexOverride = null
        DataStore.serviceState = BaseService.State.Idle
        DataStore.baseService = null
        DataStore.vpnService = null
        DataStore.mixedInboundAuthed = false
        groupDirectory = RoomGroupDirectory
        SagerDatabase.groupDaoOverride = null
        SagerDatabase.proxyDaoOverride = null
        SagerDatabase.rulesDaoOverride = null
        SubscriptionUpdater.reconfigureOverride = null
        Theme.currentNightMode = -1
    }

    @Test(timeout = 120_000)
    fun themesWebviewServicesAndPureUi() {
        idleMain()
        val context = app

        for (theme in 0..Theme.VERDANT_MINT) {
            assertNotEquals(0, Theme.getTheme(theme))
            assertNotEquals(0, Theme.getDialogTheme(theme))
        }
        Theme.apply(context)
        Theme.applyDialog(context)
        for (mode in 0..4) {
            assertNotEquals(0, Theme.getNightMode(mode))
        }
        Theme.currentNightMode = -1
        Theme.getNightMode()
        assertFalse(Theme.usingNightMode())
        DataStore.nightTheme = 1
        assertTrue(Theme.usingNightMode())
        DataStore.nightTheme = 2
        assertFalse(Theme.usingNightMode())
        AppLocale.localeList(null)
        assertEquals(0, AppLocale.localeList("").size())
        assertTrue(AppLocale.localeList("zh-CN").size() > 0)

        val dataDir = context.dataDir
        val process = Application.getProcessName()
        java.io.File(dataDir, "app_webview").mkdirs()
        java.io.File(dataDir, "app_webview/webview_data.lock").createNewFile()
        JavaUtil.handleWebviewDir(context)
        org.robolectric.shadows.ShadowBuild.setManufacturer("HUAWEI")
        JavaUtil.handleWebviewDir(context)
        org.robolectric.shadows.ShadowBuild.setManufacturer("robolectric")
        assertEquals(process, JavaUtil.getProcessName())

        SagerNet.updateNotificationChannels()
        assertEquals("", SagerNet.getClipboardText())
        assertTrue(SagerNet.trySetPrimaryClip("copied"))
        assertEquals("copied", SagerNet.getClipboardText())
        assertNotNull(context.getWorkManagerConfiguration())
        context.onTrimMemory(Application.TRIM_MEMORY_UI_HIDDEN)
        context.onConfigurationChanged(context.resources.configuration)
        SagerNet.reloadService()
        SagerNet.stopService()
        CrashHandler.formatThrowable(IllegalStateException("boom", IOException("io")))

        val body = Uri.parse("content://coverage/body")
        shadowOf(context.contentResolver).registerInputStream(
            body,
            ByteArrayInputStream("hello".toByteArray()),
        )
        assertEquals("hello", ProductionSubscriptionClient.readContent(body.toString()))
        assertTrue(ProductionSubscriptionClient.noProxiesFound().isNotEmpty())
        assertTrue(ProductionSubscriptionClient.noProxiesInSubscription().isNotEmpty())
        assertTrue(ProductionSubscriptionClient.noProxiesFoundInFile().isNotEmpty())

        runBlocking { PackageCache.register() }
        assertNotNull(PackageCache.installedPackages)
        ProductionConfigSource.ensurePackagesLoaded()
        ProductionConfigSource.packageUid("android")
        assertTrue(ProductionConfigSource.usesMatsuriExe("obfs-local"))
        assertFalse(ProductionConfigSource.hasExternalPlugin("obfs-local"))
        ProductionConfigSource.warnPackageRuleNeedsVpn("rule")
        ProductionConfigSource.warnMissingOutbound("rule")

        assertTrue(isValidServerDns(""))
        assertTrue(isValidServerDns("1.1.1.1"))
        assertTrue(isValidServerDns("https://dns.google/dns-query"))
        assertTrue(isValidServerDns("tls://dns.google"))
        assertTrue(isValidServerDns("quic://dns.google"))
        assertTrue(isValidServerDns("[2001:db8::1]:53"))
        assertFalse(isValidServerDns("bad host"))
        assertFalse(isValidServerDns("http://dns.google"))
        assertFalse(isValidServerDns("https://"))

        assertNotEquals(0, context.getColour(android.R.color.black))
        context.theme.resolveResourceId(android.R.attr.colorPrimary)
        context.getColorAttr(android.R.attr.colorPrimary)
        var received = 0
        val receiver = broadcastReceiver { _, _ -> received++ }
        receiver.onReceive(context, Intent("coverage.broadcast"))
        assertEquals(1, received)
        var packages = 0
        val packageReceiver = context.listenForPackageChanges(true) { packages++ }
        packageReceiver.onReceive(
            context,
            Intent(Intent.ACTION_PACKAGE_REMOVED).setData(Uri.parse("package:com.example.coverage")),
        )
        assertEquals(1, packages)
        "127.0.0.1".parseNumericAddress()
        "::1".parseNumericAddress()

        val listener = object : ProfileManager.Listener {
            override suspend fun onAdd(profile: ProxyEntity) {}
            override suspend fun onUpdated(data: List<TrafficData>) {}
            override suspend fun onUpdated(profile: ProxyEntity, noTraffic: Boolean) {}
            override suspend fun onRemoved(groupId: Long, profileId: Long) {}
        }
        ProfileManager.addListener(listener)
        try {
            runBlocking {
                ProfileManager.postUpdate(0L)
                ProfileManager.postUpdate(ProxyEntity())
                ProfileManager.postUpdate(listOf(TrafficData(id = 1L)))
                assertTrue(ProfileManager.getRules().isNotEmpty())
            }
        } finally {
            ProfileManager.removeListener(listener)
        }

        val boot = BootReceiver()
        boot.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        DataStore.configurationStore.putBoolean(Key.PERSIST_ACROSS_REBOOT, true)
        DataStore.selectedProxy = 0L
        boot.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        boot.onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))

        runBlocking { DefaultNetworkListener.stop(context) }

        val proxy = Robolectric.buildService(ProxyService::class.java).create().get()
        assertEquals("SagerNetProxyService", proxy.tag)
        proxy.onBind(Intent(Action.SERVICE))
        proxy.data.receiver.onReceive(proxy, Intent(Intent.ACTION_SHUTDOWN))
        shadowOf(SagerNet.power).setIsDeviceIdleMode(true)
        proxy.data.receiver.onReceive(proxy, Intent(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED))
        DataStore.wakeResetConnections = true
        shadowOf(SagerNet.power).setIsDeviceIdleMode(false)
        proxy.data.receiver.onReceive(proxy, Intent(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED))
        proxy.data.receiver.onReceive(proxy, Intent(Action.RESET_UPSTREAM_CONNECTIONS))
        idleMain()

        val binder = proxy.data.binder
        assertEquals("Idle", binder.profileName)
        try {
            binder.urlTest()
            throw AssertionError("urlTest should fail before the core starts")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("core not started"))
        }
        val callback = object : ISagerNetServiceCallback.Stub() {
            override fun stateChanged(state: Int, profileName: String?, msg: String?) {}
            override fun missingPlugin(profileName: String, pluginName: String) {}
            override fun cbSpeedUpdate(stats: SpeedDisplayData) {}
            override fun cbTrafficUpdate(stats: TrafficDataBatch) {}
            override fun cbSelectorUpdate(id: Long) {}
        }
        binder.registerCallback(callback, SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND)
        runBlocking { binder.broadcast { } }
        binder.stateChanged(BaseService.State.Stopped, null)
        binder.missingPlugin("plugin")
        idleMain()
        binder.unregisterCallback(callback)

        DataStore.acquireWakeLock = true
        runBlocking { proxy.lateInit() }
        DataStore.acquireWakeLock = false
        runBlocking { proxy.lateInit() }
        proxy.acquireWakeLock()
        runBlocking { proxy.preInit() }
        proxy.killProcesses()

        val scope = CoroutineScope(Dispatchers.Main.immediate + Job())
        val looper = TrafficLooper(proxy.data, scope)
        runBlocking {
            looper.resetTraffic(longArrayOf())
            looper.resetTraffic(longArrayOf(1L, 0L, -1L))
            looper.selectMain(1L)
        }
        DataStore.speedInterval = 20
        looper.start()
        runBlocking {
            delay(30)
            looper.stop()
        }
        DataStore.speedInterval = 0
        looper.start()
        runBlocking { looper.stop() }

        DataStore.selectedProxy = 0L
        proxy.onStartCommand(Intent(), 0, 1)
        idleMain()
        proxy.data.receiver.onReceive(proxy, Intent(Action.RELOAD))
        idleMain()
        proxy.data.receiver.onReceive(proxy, Intent(Action.CLOSE))
        idleMain()

        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        assertNotNull(vpn.onBind(Intent(Action.SERVICE)))
        vpn.onBind(Intent(VpnService.SERVICE_INTERFACE))
        DataStore.proxyApps = true
        DataStore.bypass = true
        DataStore.bypassLan = true
        DataStore.ipv6Mode = IPv6Mode.ENABLE
        DataStore.enableHevTun = true
        DataStore.enableFakeDns = true
        DataStore.individual = "com.example.one\n\n#skip"
        DataStore.appendHttpProxy = true
        DataStore.httpProxyBypass = "localhost\n#comment\n\nexample.com"
        DataStore.meteredNetwork = true
        // Builder.addAddress calls the hidden InetAddress.parseNumericAddress, which
        // the JVM does not have. The lines before that call still execute.
        try {
            vpn.establishTun()
        } catch (_: Throwable) {
        }
        DataStore.bypassLan = false
        DataStore.ipv6Mode = IPv6Mode.DISABLE
        DataStore.bypass = false
        try {
            vpn.establishTun()
        } catch (_: Throwable) {
        }
        DataStore.serviceMode = Key.MODE_PROXY
        vpn.onStartCommand(Intent(), 0, 1)
        idleMain()
        vpn.onRevoke()
        idleMain()
        vpn.onDestroy()
        idleMain()
    }

    @Test(timeout = 180_000)
    fun mainActivityFragmentsAndWidgets() {
        idleMain()
        // Robolectric does not call Service.onBind. Hand it the real VPN binder so
        // MainActivity's SagerConnection receives a non-null IBinder.
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, SagerVpnService::class.java),
            vpn.data.binder,
        )
        DataStore.serviceMode = Key.MODE_VPN
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        idleMain()
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()

        activity.snackbar(R.string.app_name).show()
        activity.snackbar("coverage").show()
        MessageStore.showMessage("stored")
        MessageStore.showMessage(activity, R.string.app_name)
        MessageStore.showMessage(activity, "direct")
        MessageStore.showMessage(activity, R.string.app_name, "x")
        idleMain()

        Dialogs.message(activity, "title", "body")
        Dialogs.logExceptionAndShow(activity, IllegalStateException("shown")) {}
        idleMain()
        MTUPreference(activity)

        val recycler = RecyclerView(activity)
        recycler.layoutManager = LinearLayoutManager(activity)
        activity.findViewById<View>(android.R.id.content).let { content ->
            (content as? android.view.ViewGroup)?.addView(
                recycler,
                LinearLayout.LayoutParams(100, 100),
            )
        }
        recycler.scrollTo(0, true)
        val first = View(activity)
        val second = View(activity)
        first.crossFadeFrom(second)
        idleMain()

        activity.driveBottomBar(20)
        activity.driveBottomBar(-20)
        for (id in listOf(
            R.id.nav_configuration,
            R.id.nav_group,
            R.id.nav_route,
            R.id.nav_settings,
            R.id.nav_tools,
            R.id.nav_logcat,
            R.id.nav_about,
            R.id.nav_traffic,
            R.id.nav_faq,
        )) {
            activity.displayFragmentWithId(id)
            activity.supportFragmentManager.executePendingTransactions()
            idleMain()
        }
        assertTrue(activity.supportFragmentManager.fragments.any {
            it is ConfigurationFragment || it is GroupFragment || it is RouteFragment ||
                it is SettingsFragment || it is ToolsFragment || it is LogcatFragment ||
                it is AboutFragment || it is WebviewFragment
        })

        Robolectric.buildActivity(BlankActivity::class.java, Intent().putExtra("sendLog", "line"))
            .setup()
        DataStore.serviceMode = Key.MODE_PROXY
        Robolectric.buildActivity(VpnRequestActivity::class.java).setup()
        idleMain()
        controller.pause().stop().destroy()
    }

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
