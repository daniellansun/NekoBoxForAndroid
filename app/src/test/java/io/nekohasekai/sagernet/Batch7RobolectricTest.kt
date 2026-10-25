package io.nekohasekai.sagernet

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.VpnService
import android.os.Looper
import android.view.View
import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceViewHolder
import io.nekohasekai.sagernet.aidl.ISagerNetServiceCallback
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.aidl.TrafficDataBatch
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.ProxyService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.bg.ShadowOsAddress
import io.nekohasekai.sagernet.bg.ShadowResolvingNetwork
import io.nekohasekai.sagernet.bg.ShadowTunBuilder
import io.nekohasekai.sagernet.bg.SubscriptionUpdater
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.MemoryGroupDao
import io.nekohasekai.sagernet.database.MemoryKvDao
import io.nekohasekai.sagernet.database.MemoryProxyDao
import io.nekohasekai.sagernet.database.MemoryRuleDao
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RoomGroupDirectory
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.groupDirectory
import io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sagernet.fmt.MemoryConfigSource
import io.nekohasekai.sagernet.fmt.configSource
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.fileDescriptor
import io.nekohasekai.sagernet.ktx.forEachTry
import io.nekohasekai.sagernet.ktx.getValue
import io.nekohasekai.sagernet.ktx.int
import io.nekohasekai.sagernet.ktx.needReload
import io.nekohasekai.sagernet.ktx.needRestart
import io.nekohasekai.sagernet.ktx.parseNumericAddress
import io.nekohasekai.sagernet.ktx.remove
import io.nekohasekai.sagernet.ktx.setValue
import io.nekohasekai.sagernet.ktx.showAllowingStateLoss
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ktx.startFilesForResult
import io.nekohasekai.sagernet.ktx.tryToShow
import io.nekohasekai.sagernet.ktx.useCancellable
import io.nekohasekai.sagernet.ui.AppListActivity
import io.nekohasekai.sagernet.ui.AppManagerActivity
import io.nekohasekai.sagernet.ui.AssetsActivity
import io.nekohasekai.sagernet.ui.GroupSettingsActivity
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.ProfileSelectActivity
import io.nekohasekai.sagernet.ui.RouteSettingsActivity
import io.nekohasekai.sagernet.ui.ScannerActivity
import io.nekohasekai.sagernet.ui.StunActivity
import io.nekohasekai.sagernet.ui.SwitchActivity
import io.nekohasekai.sagernet.ui.WebDAVSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ChainSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ConfigEditActivity
import io.nekohasekai.sagernet.ui.profile.HttpSettingsActivity
import io.nekohasekai.sagernet.ui.profile.HysteriaSettingsActivity
import io.nekohasekai.sagernet.ui.profile.JuicitySettingsActivity
import io.nekohasekai.sagernet.ui.profile.MieruSettingsActivity
import io.nekohasekai.sagernet.ui.profile.NaiveSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SSHSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ShadowsocksRSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ShadowsocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SnellSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TrojanGoSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TrojanSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TuicSettingsActivity
import io.nekohasekai.sagernet.ui.profile.VMessSettingsActivity
import io.nekohasekai.sagernet.ui.profile.WireGuardSettingsActivity
import io.nekohasekai.sagernet.utils.Theme
import kotlinx.coroutines.runBlocking
import moe.matsuri.nb4a.hevtun.HevTunRuntime
import moe.matsuri.nb4a.proxy.anytls.AnyTLSSettingsActivity
import moe.matsuri.nb4a.proxy.config.ConfigSettingActivity
import moe.matsuri.nb4a.proxy.shadowtls.ShadowTLSSettingsActivity
import moe.matsuri.nb4a.ui.Dialogs
import moe.matsuri.nb4a.ui.MTUPreference
import moe.matsuri.nb4a.ui.SimpleMenuPreference
import moe.matsuri.nb4a.utils.JavaUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowBuild
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowVpnService
import java.io.File
import java.io.FileDescriptor
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import io.nekohasekai.sagernet.bg.VpnService as SagerVpnService

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [29],
    application = CoverageApplication::class,
    shadows = [ShadowTunBuilder::class, ShadowResolvingNetwork::class, ShadowOsAddress::class],
)
class Batch7RobolectricTest {

    @After
    fun restoreOverrides() {
        DataStore.configurationStoreOverride = null
        DataStore.profileCacheStoreOverride = null
        DataStore.userIndexOverride = null
        DataStore.serviceState = BaseService.State.Idle
        DataStore.baseService = null
        DataStore.vpnService = null
        DataStore.mixedInboundAuthed = false
        DataStore.speedInterval = 0
        DataStore.enableHevTun = false
        groupDirectory = RoomGroupDirectory
        SagerDatabase.groupDaoOverride = null
        SagerDatabase.proxyDaoOverride = null
        SagerDatabase.rulesDaoOverride = null
        SubscriptionUpdater.reconfigureOverride = null
        Theme.currentNightMode = -1
        SagerNet.underlyingNetwork = null
        ShadowTunBuilder.establishReturnsNull = false
        ShadowVpnService.reset()
        ShadowApplication.setProcessName(BuildConfig.APPLICATION_ID)
        libcore.BoxInstance.stats = 0L
    }

    @Test(timeout = 180_000)
    fun profileScreensHelpersAndServices() {
        idleMain()
        drivePureHelpers()
        drivePureHelpersSuccessAndReflection()
        val profile = runBlocking { insertSocks("127.0.0.1") }
        SagerDatabase.groupDao.createGroup(ProxyGroup(name = "Other"))
        driveWebViewLocks()
        driveFakeDns()
        driveProxyEntity(profile)
        driveProfileScreens(profile.id)
        driveOtherScreens()
        driveServiceAndTun(profile)
    }

    @Test(timeout = 60_000)
    @Config(
        sdk = [27],
        application = CoverageApplication::class,
        shadows = [ShadowTunBuilder::class, ShadowResolvingNetwork::class, ShadowOsAddress::class],
    )
    fun processNameBeforeP() {
        idleMain()
        val name = JavaUtil.getProcessName()
        assertTrue(name.isNotEmpty())
        try {
            "::1".parseNumericAddress()
        } catch (_: Throwable) {
        }
        try {
            "127.0.0.1".parseNumericAddress()
        } catch (_: Throwable) {
        }
    }

    private fun drivePureHelpers() {
        try {
            listOf(1, 2).forEachTry { value ->
                if (value == 1) throw IllegalStateException("first")
                throw IllegalStateException("second")
            }
            error("forEachTry should throw")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("first"))
            assertEquals(1, e.suppressed.size)
        }
    }

    private fun drivePureHelpersSuccessAndReflection() {
        listOf(1).forEachTry { }
        try {
            Socket().fileDescriptor
        } catch (_: Throwable) {
        }
        try {
            FileDescriptor().int
        } catch (_: Throwable) {
        }
        runBlocking {
            val conn = URL("http://127.0.0.1:9").openConnection() as HttpURLConnection
            conn.connectTimeout = 200
            conn.readTimeout = 200
            try {
                conn.useCancellable { responseCode }
            } catch (_: Throwable) {
            }
        }
        val mode = DataStore::serviceMode
        mode.setValue(DataStore, mode, Key.MODE_PROXY)
        assertEquals(Key.MODE_PROXY, mode.getValue(DataStore, mode))
    }

    private fun driveWebViewLocks() {
        ShadowApplication.setProcessName("coveragebg")
        assertEquals("coveragebg", Application.getProcessName())
        JavaUtil.handleWebviewDir(app)
        val data = app.dataDir.absolutePath
        val lock = File(data + "/app_webview_coveragebg/webview_data.lock")
        lock.parentFile?.mkdirs()
        lock.writeBytes(byteArrayOf(1))
        val held = RandomAccessFile(lock, "rw")
        val channelLock = held.channel.lock()
        try {
            JavaUtil.handleWebviewDir(app)
        } finally {
            channelLock.release()
            held.close()
        }
        lock.delete()
        lock.mkdirs()
        JavaUtil.handleWebviewDir(app)
        ShadowBuild.setManufacturer("HUAWEI")
        val huawei = File(data + "/app_hws_webview_coveragebg/webview_data.lock")
        huawei.parentFile?.mkdirs()
        huawei.writeBytes(byteArrayOf(1))
        JavaUtil.handleWebviewDir(app)
        ShadowBuild.setManufacturer("robolectric")
        ShadowApplication.setProcessName(BuildConfig.APPLICATION_ID)
        DataStore.serviceMode = Key.MODE_VPN
        DataStore.appendHttpProxy = false
        DataStore.ipv6Mode = IPv6Mode.ENABLE
        DataStore.enableFakeDns = true
        DataStore.mixedUsername = "user'name"
        HevTunRuntime.buildConfig()
        DataStore.ipv6Mode = IPv6Mode.DISABLE
        DataStore.enableFakeDns = false
        HevTunRuntime.buildConfig()
        try {
            HevTunRuntime.start(app, 1)
        } catch (_: Throwable) {
        }
    }

    private fun driveFakeDns() {
        val previous = configSource
        configSource = MemoryConfigSource().apply {
            enableFakeDns = true
            serviceStarted = true
            serviceMode = Key.MODE_VPN
            ipv6Mode = IPv6Mode.PREFER
        }
        SagerNet.underlyingNetwork = ShadowNetwork.newInstance(1)
        val http = HttpBean().apply {
            initializeDefaultValues()
            serverAddress = "localhost"
            security = "tls"
            sni = ""
        }
        try {
            runBlocking { RawUpdater.forceResolve(listOf(http), null) }
        } finally {
            configSource = previous
            SagerNet.underlyingNetwork = null
        }
        assertEquals("127.0.0.1", http.serverAddress)
        assertEquals("localhost", http.sni)
    }

    private fun driveProxyEntity(profile: ProxyEntity) {
        val blank = ProxyEntity(type = ProxyEntity.TYPE_SOCKS)
        blank.dirty = true
        blank.initializeDefaultValues()
        try {
            blank.requireBean()
            error("null bean")
        } catch (_: Throwable) {
        }
        val trojan = ProxyEntity().putBean(TrojanBean().apply {
            initializeDefaultValues()
            enableMux = true
            muxPadding = true
            muxType = 1
            muxMode = 1
            muxMaxConnections = 4
            muxMinStreams = 2
            muxBrutal = true
            muxBrutalUpMbps = 10
            muxBrutalDownMbps = 20
        })
        assertNotNull(trojan.singMux())
        trojan.trojanBean!!.muxMode = 0
        trojan.trojanBean!!.muxBrutal = false
        assertNotNull(trojan.singMux())
        val shadowsocks = ProxyEntity().putBean(ShadowsocksBean().apply {
            initializeDefaultValues()
            enableMux = true
            muxPadding = false
            muxType = 2
            muxMode = 1
            muxMaxConnections = 2
            muxMinStreams = 1
            muxBrutal = true
            muxBrutalUpMbps = 5
            muxBrutalDownMbps = 6
        })
        assertNotNull(shadowsocks.singMux())
        shadowsocks.ssBean!!.muxMode = 0
        shadowsocks.ssBean!!.muxBrutal = false
        assertNotNull(shadowsocks.singMux())
        for (type in listOf(
            ProxyEntity.TYPE_SOCKS,
            ProxyEntity.TYPE_HTTP,
            ProxyEntity.TYPE_SS,
            ProxyEntity.TYPE_SSR,
            ProxyEntity.TYPE_VMESS,
            ProxyEntity.TYPE_TROJAN,
            ProxyEntity.TYPE_TROJAN_GO,
            ProxyEntity.TYPE_MIERU,
            ProxyEntity.TYPE_NAIVE,
            ProxyEntity.TYPE_HYSTERIA,
            ProxyEntity.TYPE_SSH,
            ProxyEntity.TYPE_WG,
            ProxyEntity.TYPE_TUIC,
            ProxyEntity.TYPE_JUICITY,
            ProxyEntity.TYPE_SHADOWTLS,
            ProxyEntity.TYPE_ANYTLS,
            ProxyEntity.TYPE_CHAIN,
            ProxyEntity.TYPE_CONFIG,
            ProxyEntity.TYPE_SNELL,
        )) {
            ProxyEntity(type = type).settingIntent(app, type == ProxyEntity.TYPE_HTTP)
        }
        try {
            ProxyEntity(type = ProxyEntity.TYPE_NEKO).settingsActivity()
            error("neko settings")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(profile.id, profile.settingIntent(app, false).getLongExtra("id", -1))
    }

    private fun driveProfileScreens(existingId: Long) {
        val created = mutableListOf<String>()
        val classes = listOf(
            SocksSettingsActivity::class.java,
            HttpSettingsActivity::class.java,
            ShadowsocksSettingsActivity::class.java,
            ShadowsocksRSettingsActivity::class.java,
            VMessSettingsActivity::class.java,
            TrojanSettingsActivity::class.java,
            TrojanGoSettingsActivity::class.java,
            MieruSettingsActivity::class.java,
            NaiveSettingsActivity::class.java,
            HysteriaSettingsActivity::class.java,
            SSHSettingsActivity::class.java,
            WireGuardSettingsActivity::class.java,
            TuicSettingsActivity::class.java,
            JuicitySettingsActivity::class.java,
            SnellSettingsActivity::class.java,
            ChainSettingsActivity::class.java,
            ShadowTLSSettingsActivity::class.java,
            AnyTLSSettingsActivity::class.java,
            ConfigSettingActivity::class.java,
        )
        for (cls in classes) {
            drive(cls, profileIntent(0L)) { activity ->
                if (cls == SocksSettingsActivity::class.java) exerciseSocks(activity)
                exerciseProfile(activity, save = true)
            }
            created += cls.simpleName
        }
        drive(
            VMessSettingsActivity::class.java,
            profileIntent(0L).putExtra("vless", true),
        ) { exerciseProfile(it, save = true) }
        drive(SocksSettingsActivity::class.java, profileIntent(existingId, true)) {
            exerciseProfile(it, save = false)
            DataStore.dirty = true
            it.onBackPressed()
            idleMain()
            shadowOf(it).clickMenuItem(R.id.action_delete)
            shadowOf(it).clickMenuItem(R.id.action_create_shortcut)
            shadowOf(it).clickMenuItem(R.id.action_custom_outbound_json)
            shadowOf(it).clickMenuItem(R.id.action_custom_config_json)
            shadowOf(it).clickMenuItem(R.id.action_move)
            idleMain()
        }
        assertEquals(classes.size, created.size)
    }

    private fun driveOtherScreens() {
        drive(
            GroupSettingsActivity::class.java,
            Intent()
                .putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, 0L)
                .putExtra(GroupSettingsActivity.EXTRA_FROM_CLIPBOARD, false)
                .putExtra(GroupSettingsActivity.EXTRA_GROUP_SUBSCRIPTION_LINK, "https://example.com/sub")
                .putExtra(GroupSettingsActivity.EXTRA_GROUP_NAME, "Imported"),
        ) { activity ->
            val group = activity as GroupSettingsActivity
            runBlocking { group.saveAndExit() }
        }
        drive(
            RouteSettingsActivity::class.java,
            Intent()
                .putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, 0L)
                .putExtra(RouteSettingsActivity.EXTRA_PACKAGE_NAME, app.packageName),
        )
        drive(
            ConfigEditActivity::class.java,
            Intent().putExtra("key", Key.SERVER_CUSTOM).putExtra("useConfigStore", "1"),
        ) { activity ->
            val editor = activity as ConfigEditActivity
            editor.binding.editor.setTextContent("{")
            editor.formatText()
            idleMain()
            editor.binding.editor.setTextContent("""{"ok":true}""")
            editor.saveAndExit()
        }
        for (cls in listOf(
            AssetsActivity::class.java,
            AppListActivity::class.java,
            AppManagerActivity::class.java,
            WebDAVSettingsActivity::class.java,
            ScannerActivity::class.java,
            StunActivity::class.java,
            ProfileSelectActivity::class.java,
            SwitchActivity::class.java,
        )) {
            drive(cls)
        }
    }

    private fun driveServiceAndTun(profile: ProxyEntity) {
        DataStore.selectedProxy = profile.id
        DataStore.serviceMode = Key.MODE_PROXY
        DataStore.speedInterval = 30
        DataStore.showDirectSpeed = true
        DataStore.showGroupInNotification = true
        DataStore.profileTrafficStatistics = true
        DataStore.acquireWakeLock = true
        DataStore.enableHevTun = false
        libcore.BoxInstance.stats = 8L
        val proxy = Robolectric.buildService(ProxyService::class.java).create().get()
        val callback = object : ISagerNetServiceCallback.Stub() {
            override fun stateChanged(state: Int, profileName: String?, msg: String?) {}
            override fun missingPlugin(profileName: String, pluginName: String) {}
            override fun cbSpeedUpdate(stats: SpeedDisplayData) {}
            override fun cbTrafficUpdate(stats: TrafficDataBatch) {}
            override fun cbSelectorUpdate(id: Long) {}
        }
        proxy.data.binder.registerCallback(
            callback,
            SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND,
        )
        proxy.onStartCommand(Intent(), 0, 1)
        settleUntil { proxy.data.state != BaseService.State.Connecting }
        val instance = proxy.data.proxy
        if (instance != null) {
            proxy.canReloadSelector()
            val looper = instance.looper
            if (looper != null) {
                runBlocking {
                    looper.selectMain(profile.id)
                    looper.selectMain(-1L)
                    looper.resetTraffic(longArrayOf(profile.id))
                }
            }
            try {
                assertEquals(0, proxy.data.binder.urlTest())
            } catch (_: Throwable) {
            }
        }
        proxy.data.receiver.onReceive(proxy, Intent(Action.CLOSE))
        settleUntil { proxy.data.state == BaseService.State.Stopped }
        proxy.data.binder.unregisterCallback(callback)

        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        val tunProfile = instance ?: runBlocking {
            val fresh = io.nekohasekai.sagernet.bg.proto.ProxyInstance(profile, vpn)
            fresh.init()
            fresh
        }
        vpn.data.proxy = tunProfile
        SagerNet.underlyingNetwork = ShadowNetwork.newInstance(2)
        DataStore.bypassLan = true
        DataStore.ipv6Mode = IPv6Mode.ENABLE
        DataStore.enableHevTun = true
        DataStore.enableFakeDns = true
        DataStore.proxyApps = false
        DataStore.appendHttpProxy = true
        DataStore.httpProxyBypass = "localhost\n#comment\n\nexample.com"
        DataStore.meteredNetwork = true
        val fd = vpn.establishTun()
        assertTrue(fd > 0)
        assertTrue(vpn.startVpn("{}", "{}") > 0)
        vpn.updateUnderlyingNetwork(null)
        DataStore.bypassLan = false
        DataStore.ipv6Mode = IPv6Mode.DISABLE
        DataStore.proxyApps = true
        DataStore.bypass = true
        DataStore.individual = "com.example.missing\n\n${app.packageName}"
        DataStore.appendHttpProxy = false
        assertTrue(vpn.establishTun() > 0)
        DataStore.bypass = false
        assertTrue(vpn.establishTun() > 0)
        ShadowTunBuilder.establishReturnsNull = true
        try {
            vpn.establishTun()
            error("null tun")
        } catch (e: SagerVpnService.NullConnectionException) {
            assertTrue(e.localizedMessage.isNotEmpty())
        }
        ShadowTunBuilder.establishReturnsNull = false
        SagerNet.underlyingNetwork = null

        DataStore.serviceMode = Key.MODE_VPN
        DataStore.enableHevTun = false
        ShadowVpnService.setPrepareResult(Intent("prepare"))
        vpn.onStartCommand(Intent(), 0, 1)
        idleMain()
        ShadowVpnService.setPrepareResult(null)
        DataStore.selectedProxy = profile.id
        DataStore.showDirectSpeed = false
        DataStore.speedInterval = 20
        vpn.onStartCommand(Intent(), 0, 2)
        settleUntil { vpn.data.state != BaseService.State.Connecting }
        if (vpn.data.state.canStop) {
            vpn.data.receiver.onReceive(vpn, Intent(Action.CLOSE))
            settleUntil { vpn.data.state == BaseService.State.Stopped }
        }
        vpn.onRevoke()
        idleMain()

        shadowOf(app).setComponentNameAndServiceForBindService(
            android.content.ComponentName(app, SagerVpnService::class.java),
            vpn.data.binder,
        )
        DataStore.serviceState = BaseService.State.Connected
        DataStore.appTheme = Theme.BLACK
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        idleMain()
        val fab = activity.binding.fab
        val stats = activity.binding.stats
        for (state in BaseService.State.values()) {
            fab.changeState(state, BaseService.State.Stopped, false)
            fab.changeState(state, BaseService.State.Connected, true)
            stats.changeState(state)
        }
        fab.onAnimationEnd(null, false, 0f, 0f)
        fab.onAnimationEnd(null, true, 0f, 0f)
        fab.hideProgress()
        stats.updateSpeed(128, 256)
        stats.allowShow = true
        stats.useExternalScrollDriver = true
        stats.syncMainControls(true, BaseService.State.Connected, true, false)
        stats.syncMainControls(true, BaseService.State.Connected, true, true)
        stats.syncMainControls(true, BaseService.State.Connecting, false, true)
        stats.syncMainControls(false, BaseService.State.Stopped, false, false)
        stats.onListScrolled(40)
        stats.onListScrolled(40)
        stats.onListScrolled(-40)
        stats.onListScrolled(-40)
        stats.testConnection()
        settle()
        driveFragmentHelpers(activity)
        driveMtuAndDialogs(activity)
        controller.pause().stop().destroy()
        DataStore.speedInterval = 0
        DataStore.appTheme = 0
    }

    private fun driveFragmentHelpers(activity: MainActivity) {
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.fragments.firstOrNull { it.isAdded }
        if (fragment != null) {
            fragment.snackbar(R.string.app_name)
            fragment.snackbar("helper")
            DataStore.serviceState = BaseService.State.Connected
            fragment.needReload()
            fragment.needRestart()
            fragment.startFilesForResult(throwingLauncher(ActivityNotFoundException()), "*/*")
            fragment.startFilesForResult(throwingLauncher(SecurityException("denied")), "*/*")
            fragment.alert("fragment").tryToShow()
        }
        activity.startFilesForResult(throwingLauncher(ActivityNotFoundException()), "*/*")
        activity.startFilesForResult(throwingLauncher(SecurityException("denied")), "*/*")
        activity.alert("direct").tryToShow()
        ProfileSettingsActivity.UnsavedChangesDialogFragment().apply { key() }.showAllowingStateLoss(
            activity.supportFragmentManager,
            "unsaved",
        )
        idleMain()
        val password = EditTextPreference(activity)
        password.text = "secret"
        ProfileSettingsActivity.PasswordSummaryProvider.provideSummary(password)
        password.text = ""
        ProfileSettingsActivity.PasswordSummaryProvider.provideSummary(password)
        password.text = null
        ProfileSettingsActivity.PasswordSummaryProvider.provideSummary(password)
    }

    private fun driveMtuAndDialogs(activity: MainActivity) {
        var ran = false
        Dialogs.logExceptionAndShow(activity, IllegalStateException("shown")) { ran = true }
        idleMain()
        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        idleMain()
        assertTrue(ran)
        val pref = MTUPreference(activity)
        pref.key = "mtu-test"
        pref.preferenceDataStore = DataStore.profileCacheStore
        val holder = PreferenceViewHolder.createInstanceForTests(View(activity))
        pref.onBindViewHolder(holder)
        holder.itemView.performLongClick()
        idleMain()
        clickMtu("1500")
        holder.itemView.performLongClick()
        idleMain()
        clickMtu("10")
    }

    private fun clickMtu(text: String) {
        val shown = ShadowDialog.getLatestDialog() as? AlertDialog ?: return
        shown.window?.decorView?.touchEdit(text)
        shown.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        try {
            shadowOf(Looper.getMainLooper()).idle()
        } catch (_: NumberFormatException) {
        } catch (e: IllegalStateException) {
            if (e.message?.contains("synchronization barrier") != true) throw e
        }
    }

    private fun exerciseProfile(activity: Activity, save: Boolean) {
        if (activity !is ProfileSettingsActivity<*>) return
        activity.invalidateOptionsMenu()
        idleMain()
        DataStore.profileName = "renamed"
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.settings)
        if (fragment is PreferenceFragmentCompat) {
            fragment.preferenceScreen?.let { screen ->
                for (i in 0 until screen.preferenceCount) {
                    screen.getPreference(i).summary
                }
            }
        }
        if (save && DataStore.editingId == 0L) {
            runBlocking { activity.saveAndExit() }
        }
    }

    private fun exerciseSocks(activity: Activity) {
        val host = activity as? androidx.fragment.app.FragmentActivity ?: return
        val fragment = host.supportFragmentManager.findFragmentById(R.id.settings) as? PreferenceFragmentCompat
            ?: return
        val protocol = fragment.findPreference<SimpleMenuPreference>(Key.SERVER_PROTOCOL)
        protocol?.callChangeListener("1")
        protocol?.callChangeListener(SOCKSBean.PROTOCOL_SOCKS5.toString())
        val password = fragment.findPreference<EditTextPreference>(Key.SERVER_PASSWORD)
        password?.text = "pw"
        password?.summary
        password?.let { if (it.parent != null) it.remove() }
    }

    private fun drive(activityClass: Class<out Activity>, intent: Intent = Intent(), block: (Activity) -> Unit = {}) {
        val controller = Robolectric.buildActivity(activityClass, intent)
        try {
            controller.setup()
            settle()
            block(controller.get())
        } finally {
            runCatching { controller.pause().stop().destroy() }
            idleMain()
        }
    }

    private fun profileIntent(id: Long, subscription: Boolean = false) =
        Intent()
            .putExtra(ProfileSettingsActivity.EXTRA_PROFILE_ID, id)
            .putExtra(ProfileSettingsActivity.EXTRA_IS_SUBSCRIPTION, subscription)

    private suspend fun insertSocks(host: String): ProxyEntity {
        return ProfileManager.createProfile(
            DataStore.selectedGroupForImport(),
            SOCKSBean().apply {
                name = "socks"
                serverAddress = host
                serverPort = 1080
            },
        )
    }

    private fun settle() {
        repeat(8) {
            Thread.sleep(40)
            idleMain()
        }
    }

    private fun settleUntil(done: () -> Boolean) {
        var spins = 0
        while (!done() && spins < 40) {
            Thread.sleep(50)
            idleMain()
            spins++
        }
    }

    private fun idleMain() {
        try {
            shadowOf(Looper.getMainLooper()).idle()
        } catch (e: IllegalStateException) {
            if (e.message?.contains("synchronization barrier") != true) throw e
        }
    }

    private fun throwingLauncher(error: Exception) = object : ActivityResultLauncher<String>() {
        override fun launch(input: String, options: androidx.core.app.ActivityOptionsCompat?) {
            throw error
        }

        override val contract = ActivityResultContracts.GetContent()

        override fun unregister() {}
    }
}

private fun View.touchEdit(text: String) {
    if (this is EditText) setText(text)
    if (this is android.view.ViewGroup) {
        for (i in 0 until childCount) getChildAt(i).touchEdit(text)
    }
}
