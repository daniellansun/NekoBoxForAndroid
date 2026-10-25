package io.nekohasekai.sagernet

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Looper
import android.service.quicksettings.Tile
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.bg.AbstractInstance
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.bg.ShadowOsAddress
import io.nekohasekai.sagernet.bg.ShadowResolvingNetwork
import io.nekohasekai.sagernet.bg.ShadowTunBuilder
import io.nekohasekai.sagernet.bg.TileService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RoomGroupDirectory
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.database.groupDirectory
import io.nekohasekai.sagernet.database.preference.KeyValuePair
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.mieru.MieruBean
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.trojan_go.TrojanGoBean
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.ui.AboutFragment
import io.nekohasekai.sagernet.ui.BackupFragment
import io.nekohasekai.sagernet.ui.ConfigurationFragment
import io.nekohasekai.sagernet.ui.GroupFragment
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.RouteFragment
import io.nekohasekai.sagernet.ui.SettingsPreferenceFragment
import io.nekohasekai.sagernet.utils.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import moe.matsuri.nb4a.hevtun.HevTunRuntime
import moe.matsuri.nb4a.ui.LongClickListPreference
import moe.matsuri.nb4a.utils.JavaUtil
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import io.nekohasekai.sagernet.bg.VpnService as SagerVpnService
import io.nekohasekai.sagernet.bg.proto.ProxyInstance
import io.nekohasekai.sagernet.database.MemoryKvDao as TestKvDao

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [29],
    application = CoverageApplication::class,
    shadows = [ShadowTunBuilder::class, ShadowResolvingNetwork::class, ShadowOsAddress::class],
)
class Batch8RobolectricTest {

    @After
    fun restoreOverrides() {
        DataStore.configurationStoreOverride = null
        DataStore.profileCacheStoreOverride = null
        DataStore.userIndexOverride = null
        DataStore.serviceState = BaseService.State.Idle
        DataStore.baseService = null
        DataStore.vpnService = null
        DataStore.runningTest = false
        DataStore.speedInterval = 0
        DataStore.enableHevTun = false
        DataStore.proxyApps = false
        DataStore.bypass = false
        DataStore.ipv6Mode = IPv6Mode.DISABLE
        groupDirectory = RoomGroupDirectory
        SagerDatabase.groupDaoOverride = null
        SagerDatabase.proxyDaoOverride = null
        SagerDatabase.rulesDaoOverride = null
        PublicDatabase.kvPairDaoOverride = null
        PluginManager.initOverride = null
        JavaUtil.lockProbe = null
        HevTunRuntime.resetNativeHooks()
        Theme.currentNightMode = -1
        ShadowTunBuilder.establishReturnsNull = false
        ShadowApplication.setProcessName(BuildConfig.APPLICATION_ID)
        // FileProvider caches path roots for the first sandbox that shares a file.
        runCatching {
            val cache = Class.forName("androidx.core.content.FileProvider")
                .getDeclaredField("sCache")
            cache.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (cache.get(null) as MutableMap<Any, Any>).clear()
        }
    }

    @Test(timeout = 180_000)
    fun pagesPluginsAndTun() {
        idleMain()
        PublicDatabase.kvPairDaoOverride = TestKvDao()
        driveJavaUtil()
        val activity = openMain()
        driveConfiguration(activity)
        driveGroup(activity)
        driveRoute(activity)
        driveSettings(activity)
        driveAbout(activity)
        driveBackup(activity)
        drivePluginsAndTun()
        driveTile()
        idleMain()
        runCatching {
            activity.displayFragmentWithId(R.id.nav_settings)
            activity.supportFragmentManager.executePendingTransactions()
            idleMain()
            val prefs = activity.supportFragmentManager.findFragmentById(R.id.settings) as SettingsPreferenceFragment
            prefs.findPreference<Preference>(Key.NIGHT_THEME)!!.callChangeListener("1")
            idleMain()
            prefs.findPreference<Preference>(Key.APP_LANGUAGE)!!.callChangeListener("en")
            idleMain()
        }
    }

    private fun openMain(): MainActivity {
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, SagerVpnService::class.java),
            vpn.data.binder,
        )
        DataStore.serviceMode = Key.MODE_VPN
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idleMain()
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        return activity
    }

    private fun driveJavaUtil() {
        JavaUtil()
        JavaUtil.handleWebviewDir(object : ContextWrapper(app) {
            override fun getDataDir(): File {
                throw IllegalStateException("coverage")
            }
        })
        ShadowApplication.setProcessName("coveragebg")
        val lock = File(app.dataDir, "app_webview_coveragebg/webview_data.lock")
        lock.parentFile?.mkdirs()
        lock.writeBytes(byteArrayOf(1))
        JavaUtil.lockProbe = JavaUtil.FileLockProbe { null }
        JavaUtil.handleWebviewDir(app)
        JavaUtil.lockProbe = null
        ShadowApplication.setProcessName(BuildConfig.APPLICATION_ID)
    }

    private fun driveConfiguration(activity: MainActivity) {
        activity.displayFragmentWithId(R.id.nav_configuration)
        activity.supportFragmentManager.executePendingTransactions()
        layout(activity)
        idleMain()
        val config = activity.supportFragmentManager.fragments
            .filterIsInstance<ConfigurationFragment>()
            .first()
        runBlocking {
            ProfileManager.createProfile(DataStore.selectedGroupForImport(), SOCKSBean().apply {
                name = "local"
                serverAddress = "127.0.0.1"
                serverPort = 1
            })
            ProfileManager.createProfile(DataStore.selectedGroupForImport(), SOCKSBean().apply {
                name = "example.com"
                serverAddress = "example.com"
                serverPort = 1
            })
        }
        SagerNet.underlyingNetwork = ShadowNetwork.newInstance(1)
        config.onQueryTextChange("loc")
        config.onQueryTextChange("")
        config.onQueryTextSubmit("loc")
        config.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        config.refreshProfileState()
        config.switchAllGroupFragmentsLayout()
        config.refreshAllGroupFragmentsCardStyle()
        runBlocking {
            config.import(listOf(SOCKSBean().apply {
                name = "imported"
                serverAddress = "127.0.0.1"
                serverPort = 1
            }))
        }
        idleMain()

        SagerNet.trySetPrimaryClip("")
        config.onMenuItemClick(item(activity, R.id.action_import_clipboard))
        settle()
        SagerNet.trySetPrimaryClip("socks5://u:p@127.0.0.1:1#clip")
        config.onMenuItemClick(item(activity, R.id.action_import_clipboard))
        settle()
        SagerNet.trySetPrimaryClip("not a proxy")
        config.onMenuItemClick(item(activity, R.id.action_import_clipboard))
        settle()
        SagerNet.trySetPrimaryClip("sn://subscription?url=https://example.com/sub")
        config.onMenuItemClick(item(activity, R.id.action_import_clipboard))
        settle()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        SagerNet.trySetPrimaryClip("https://example.com/not-a-proxy")
        config.onMenuItemClick(item(activity, R.id.action_import_clipboard))
        settle()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        activity.displayFragmentWithId(R.id.nav_configuration)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val attached = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as ConfigurationFragment

        val menuIds = intArrayOf(
            R.id.action_scan_qr_code,
            R.id.action_import_file,
            R.id.action_new_socks,
            R.id.action_new_http,
            R.id.action_new_ss,
            R.id.action_new_ssr,
            R.id.action_new_vmess,
            R.id.action_new_vless,
            R.id.action_new_trojan,
            R.id.action_new_trojan_go,
            R.id.action_new_mieru,
            R.id.action_new_naive,
            R.id.action_new_hysteria,
            R.id.action_new_tuic,
            R.id.action_new_juicity,
            R.id.action_new_ssh,
            R.id.action_new_snell,
            R.id.action_new_wg,
            R.id.action_new_shadowtls,
            R.id.action_new_anytls,
            R.id.action_new_config,
            R.id.action_new_chain,
            R.id.action_update_subscription,
        )
        for (id in menuIds) {
            runCatching { attached.onMenuItemClick(item(activity, id)) }
            idleMain()
        }

        val group = SagerDatabase.groupDao.allGroups().first { it.type != GroupType.SUBSCRIPTION }
        val profiles = SagerDatabase.proxyDao.getByGroup(group.id)
        if (profiles.size >= 2) {
            profiles[0].tx = 10
            profiles[0].rx = 4
            profiles[0].status = 2
            profiles[1].status = 2
            runBlocking { ProfileManager.updateProfile(profiles) }
        }
        attached.onMenuItemClick(item(activity, R.id.action_clear_traffic_statistics))
        settle()
        attached.onMenuItemClick(item(activity, R.id.action_connection_test_clear_results))
        settle()
        attached.onMenuItemClick(item(activity, R.id.action_connection_test_delete_unavailable))
        settle()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        attached.onMenuItemClick(item(activity, R.id.action_remove_duplicate))
        settle()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)

        DataStore.serviceState = BaseService.State.Connected
        val global = item(activity, R.id.action_global_mode)
        attached.onMenuItemClick(global)
        settle()
        DataStore.serviceState = BaseService.State.Stopped

        attached.pingTest(false)
        attached.pingTest(false)
        settle()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        settleUntil { !DataStore.runningTest }
        DataStore.runningTest = false
        attached.pingTest(true)
        settle()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        settleUntil { !DataStore.runningTest }
        DataStore.runningTest = false
        attached.urlTest()
        settle()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        settleUntil { !DataStore.runningTest }
        DataStore.runningTest = false
        SagerNet.underlyingNetwork = null
    }

    private fun driveGroup(activity: MainActivity) {
        val sub = ProxyGroup(name = "Sub", type = GroupType.SUBSCRIPTION).apply {
            subscription = SubscriptionBean().apply {
                initializeDefaultValues()
                link = "http://example.com/sub"
                bytesUsed = 10
                bytesRemaining = 5
            }
        }
        SagerDatabase.groupDao.createGroup(sub)
        val info = ProxyGroup(name = "Info", type = GroupType.SUBSCRIPTION).apply {
            subscription = SubscriptionBean().apply {
                initializeDefaultValues()
                link = "https://example.com/sub"
                bytesUsed = 0
                bytesRemaining = 0
                subscriptionUserinfo = "upload=1; download=2; total=10"
            }
        }
        SagerDatabase.groupDao.createGroup(info)
        for (existing in SagerDatabase.groupDao.allGroups()) {
            existing.subscription?.apply {
                if (bytesUsed == null) bytesUsed = 0
                if (bytesRemaining == null) bytesRemaining = 0
                initializeDefaultValues()
            }
        }
        activity.displayFragmentWithId(R.id.nav_group)
        activity.supportFragmentManager.executePendingTransactions()
        layout(activity)
        idleMain()
        val group = activity.supportFragmentManager.fragments.filterIsInstance<GroupFragment>().first()
        runBlocking { group.groupAdapter.reload() }
        idleMain()
        group.onMenuItemClick(item(activity, R.id.action_new_group))
        group.onMenuItemClick(item(activity, R.id.action_update_all))
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        group.onMenuItemClick(item(activity, R.id.action_update_all))
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()

        val subIndex = group.groupAdapter.groupList.indexOfFirst { it.type == GroupType.SUBSCRIPTION }
        if (subIndex >= 0) {
            val parent = group.groupListView
            val holder = group.groupAdapter.onCreateViewHolder(parent, 0)
            group.groupAdapter.onBindViewHolder(holder, subIndex)
            holder.itemView.performClick()
            val typed = holder as GroupFragment.GroupHolder
            typed.editButton.performClick()
            typed.updateButton.performClick()
            settle()
            runCatching { typed.optionsButton.performClick() }
            idleMain()
            typed.onMenuItemClick(item(activity, R.id.action_universal_clipboard))
            typed.onMenuItemClick(item(activity, R.id.action_export_clipboard))
            settle()
            runCatching { typed.onMenuItemClick(item(activity, R.id.action_export_file)) }
            runCatching { typed.onMenuItemClick(item(activity, R.id.action_universal_qr)) }
            idleMain()
            typed.onMenuItemClick(item(activity, R.id.action_clear))
            idleMain()
            clickDialog(AlertDialog.BUTTON_NEGATIVE)
        }
    }

    private fun driveRoute(activity: MainActivity) {
        runBlocking {
            for (outbound in longArrayOf(-2, -1, 0, 4)) {
                ProfileManager.createRule(RuleEntity(name = "r$outbound", outbound = outbound, enabled = true), false)
            }
        }
        activity.displayFragmentWithId(R.id.nav_route)
        activity.supportFragmentManager.executePendingTransactions()
        layout(activity)
        idleMain()
        val route = activity.supportFragmentManager.fragments.filterIsInstance<RouteFragment>().first()
        runBlocking { route.ruleAdapter.reload() }
        idleMain()
        route.onMenuItemClick(item(activity, R.id.action_new_route))
        route.onMenuItemClick(item(activity, R.id.action_manage_assets))
        route.onMenuItemClick(item(activity, R.id.action_reset_route))
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        route.onMenuItemClick(item(activity, R.id.action_reset_route))
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()
        runBlocking { route.ruleAdapter.reload() }
        idleMain()
        val parent = route.ruleListView
        val doc = route.ruleAdapter.onCreateViewHolder(parent, 0)
        route.ruleAdapter.onBindViewHolder(doc, 0)
        runCatching { doc.itemView.performClick() }
        if (route.ruleAdapter.itemCount > 1) {
            val holder = route.ruleAdapter.onCreateViewHolder(parent, 1) as RouteFragment.RuleAdapter.RuleHolder
            route.ruleAdapter.onBindViewHolder(holder, 1)
            holder.enableSwitch.isChecked = false
            holder.editButton.performClick()
            idleMain()
        }
    }

    private fun driveSettings(activity: MainActivity) {
        activity.displayFragmentWithId(R.id.nav_settings)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val prefs = activity.supportFragmentManager.findFragmentById(R.id.settings) as SettingsPreferenceFragment
        prefs.findPreference<Preference>(Key.SPEED_INTERVAL)!!.callChangeListener("1")
        prefs.findPreference<Preference>(Key.SPEED_INTERVAL)!!.callChangeListener("0")
        prefs.findPreference<Preference>(Key.BYPASS_LAN)!!.callChangeListener(false)
        prefs.findPreference<Preference>(Key.APPEND_HTTP_PROXY)!!.callChangeListener(true)
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        prefs.findPreference<Preference>(Key.PROXY_APPS)!!.callChangeListener(true)
        prefs.findPreference<Preference>("localProxySettings")!!.performClick()
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        val logLevel = prefs.findPreference<LongClickListPreference>(Key.LOG_LEVEL)!!
        val holder = PreferenceViewHolder.createInstanceForTests(View(activity))
        logLevel.onBindViewHolder(holder)
        holder.itemView.performLongClick()
        idleMain()
        findEdit(ShadowDialog.getLatestDialog()?.window?.decorView)?.setText("40")
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        idleMain()
        prefs.findPreference<Preference>(Key.CLEAR_CACHE)!!.performClick()
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()
        prefs.findPreference<Preference>("resetSettings")!!.performClick()
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
    }

    private fun driveAbout(activity: MainActivity) {
        activity.displayFragmentWithId(R.id.nav_about)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val about = activity.supportFragmentManager.fragments
            .filterIsInstance<AboutFragment.AboutContent>()
            .firstOrNull()
            ?: activity.supportFragmentManager.fragments
                .flatMap { it.childFragmentManager.fragments }
                .filterIsInstance<AboutFragment.AboutContent>()
                .first()
        about.checkUpdate(false)
        about.checkUpdate(true)
        settle()
    }

    private fun driveBackup(activity: MainActivity) {
        val backup = BackupFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(R.id.fragment_holder, backup, "backup")
            .commitNow()
        idleMain()
        val root = backup.requireView()
        root.findViewById<View>(R.id.backup_settings).performClick()
        root.findViewById<View>(R.id.action_export).performClick()
        settle()
        root.findViewById<View>(R.id.action_share).performClick()
        settle()
        root.findViewById<View>(R.id.action_import_file).performClick()
        idleMain()
        root.findViewById<View>(R.id.webdav_settings).performClick()
        idleMain()
        root.findViewById<View>(R.id.backup_to_webdav).performClick()
        root.findViewById<View>(R.id.restore_from_webdav).performClick()
        idleMain()
        DataStore.webdavServer = "http://["
        root.findViewById<View>(R.id.backup_to_webdav).performClick()
        root.findViewById<View>(R.id.restore_from_webdav).performClick()
        settle()

        val profile = SagerDatabase.proxyDao.getAll().first()
        val rule = runBlocking {
            ProfileManager.createRule(RuleEntity(name = "backup-rule", domains = "example.com"), false)
        }
        val setting = KeyValuePair("coverage").put("value")
        PublicDatabase.kvPairDao.put(setting)
        val json = JSONObject()
            .put("version", 1)
            .put("profiles", org.json.JSONArray().put(backup.run { profile.toBase64Str() }))
            .put("groups", org.json.JSONArray().put(backup.run { profileGroup(profile).toBase64Str() }))
            .put("rules", org.json.JSONArray().put(backup.run { rule.toBase64Str() }))
            .put("settings", org.json.JSONArray().put(backup.run { setting.toBase64Str() }))
        val text = json.toString()
        val jsonFile = File(app.cacheDir, "coverage-backup.json")
        jsonFile.writeText(text)
        runBlocking { backup.startImport(Uri.fromFile(jsonFile)) }
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        val bad = File(app.cacheDir, "coverage-backup.txt")
        bad.writeText("nope")
        runBlocking { backup.startImport(Uri.fromFile(bad)) }
        idleMain()
        val zip = File(app.cacheDir, "coverage-backup.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("nekobox_backup.json"))
            out.write(text.toByteArray())
            out.closeEntry()
        }
        runBlocking { backup.startImport(Uri.fromFile(zip)) }
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        backup.finishImport(json, true, true, true)
        backup.finishImport(JSONObject().put("version", 1), true, true, true)
    }

    private fun profileGroup(profile: ProxyEntity): ProxyGroup {
        return SagerDatabase.groupDao.allGroups().first { it.id == profile.groupId }
    }

    private fun drivePluginsAndTun() {
        PluginManager.initOverride = {
            PluginManager.InitResult("/bin/true", ProviderInfo())
        }
        val groupId = DataStore.selectedGroupForImport()
        launchPlugin(groupId, TrojanGoBean().apply {
            password = "secret"
            serverAddress = "127.0.0.1"
            serverPort = 443
        }, logTrace = false)
        launchPlugin(groupId, MieruBean().apply {
            username = "u"
            password = "p"
            serverAddress = "127.0.0.1"
            serverPort = 443
        }, logTrace = false)
        launchPlugin(groupId, NaiveBean().apply {
            username = "u"
            password = "p"
            certificates = "CERT"
            serverAddress = "example.com"
            serverPort = 443
        }, logTrace = false)
        DataStore.logLevel = 1
        launchPlugin(groupId, HysteriaBean().apply {
            protocolVersion = 1
            protocol = HysteriaBean.PROTOCOL_WECHAT_VIDEO
            serverAddress = "127.0.0.1"
            serverPorts = "443"
            caText = "CA"
        }, logTrace = true)
        DataStore.logLevel = 0
        launchPlugin(groupId, HysteriaBean().apply {
            protocolVersion = 1
            protocol = HysteriaBean.PROTOCOL_FAKETCP
            serverAddress = "127.0.0.1"
            serverPorts = "443"
        }, logTrace = false)
        driveProcessPool()

        val hysteria = runBlocking {
            ProfileManager.createProfile(groupId, HysteriaBean().apply {
                protocolVersion = 1
                protocol = HysteriaBean.PROTOCOL_FAKETCP
                serverAddress = "127.0.0.1"
                serverPorts = "443"
                name = "fake"
            })
        }
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        val instance = ProxyInstance(hysteria, vpn)
        runBlocking { instance.init() }
        vpn.data.proxy = instance
        DataStore.proxyApps = false
        DataStore.bypass = true
        DataStore.bypassLan = false
        DataStore.ipv6Mode = IPv6Mode.DISABLE
        DataStore.enableHevTun = false
        DataStore.appendHttpProxy = false
        assertTrue(vpn.establishTun() > 0)
        DataStore.proxyApps = true
        DataStore.bypass = true
        DataStore.individual = app.packageName
        assertTrue(vpn.establishTun() > 0)
        DataStore.ipv6Mode = IPv6Mode.ENABLE
        assertTrue(vpn.establishTun() > 0)
        DataStore.ipv6Mode = IPv6Mode.DISABLE

        val stopped = AtomicBoolean(false)
        HevTunRuntime.startNative = { _, _ -> true }
        HevTunRuntime.stopNative = { stopped.set(true) }
        HevTunRuntime.runningNative = { false }
        HevTunRuntime.start(app, 4)
        HevTunRuntime.stop()
        assertTrue(stopped.get())
        HevTunRuntime.startNative = { _, _ -> false }
        var rejected = false
        try {
            HevTunRuntime.start(app, 4)
        } catch (_: IllegalStateException) {
            rejected = true
        }
        assertTrue(rejected)
        HevTunRuntime.runningNative = { throw UnsatisfiedLinkError("coverage") }
        assertFalse(HevTunRuntime.isRunning())
        HevTunRuntime.runningNative = { throw ExceptionInInitializerError("coverage") }
        assertFalse(HevTunRuntime.isRunning())
        HevTunRuntime.resetNativeHooks()
        PluginManager.initOverride = null
    }

    private fun launchPlugin(groupId: Long, bean: io.nekohasekai.sagernet.fmt.AbstractBean, logTrace: Boolean) {
        bean.name = bean.javaClass.simpleName
        val profile = runBlocking { ProfileManager.createProfile(groupId, bean) }
        val instance = ProxyInstance(profile)
        instance.processes = GuardedProcessPool { }
        try {
            runBlocking { instance.init() }
            instance.launch()
            idleMain()
            val port = instance.config.externalIndex.firstOrNull()?.chain?.keys?.firstOrNull()
            if (port != null) {
                instance.externalInstances[port] = object : AbstractInstance {
                    override fun launch() {}
                    override fun close() {}
                }
                runCatching { instance.launch() }
                idleMain()
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { instance.close() }
            settle()
        }
        if (logTrace) runBlocking { delay(20) }
    }

    private fun driveProcessPool() {
        val fast = GuardedProcessPool { }
        fast.start(listOf("/bin/true"), mutableMapOf("COVERAGE" to "1"))
        settle()
        fast.close(GlobalScope + Dispatchers.IO)
        settle()
        val slow = GuardedProcessPool { }
        slow.start(listOf("/bin/sh", "-c", "sleep 0.3"))
        // elapsedRealtime does not follow Thread.sleep under the paused looper.
        ShadowSystemClock.advanceBy(1500, TimeUnit.MILLISECONDS)
        Thread.sleep(400)
        idleMain()
        settle()
        slow.close(GlobalScope + Dispatchers.IO)
        settle()
    }

    private fun driveTile() {
        runBlocking {
            ProfileManager.createProfile(DataStore.selectedGroupForImport(), SOCKSBean().apply {
                name = "tile"
                serverAddress = "127.0.0.1"
                serverPort = 1
            })
        }
        val controller = Robolectric.buildService(TileService::class.java).create()
        val tileService = controller.get()
        val tile = ReflectionHelpers.callConstructor(Tile::class.java)
        val field = android.service.quicksettings.TileService::class.java.getDeclaredField("mTile")
        field.isAccessible = true
        field.set(tileService, tile)
        val binder = object : ISagerNetService.Stub() {
            override fun getState(): Int = BaseService.State.Stopped.ordinal
            override fun getProfileName(): String = "tile"
            override fun registerCallback(cb: io.nekohasekai.sagernet.aidl.ISagerNetServiceCallback?, id: Int) {}
            override fun unregisterCallback(cb: io.nekohasekai.sagernet.aidl.ISagerNetServiceCallback?) {}
            override fun resetTraffic(profileIds: LongArray?) {}
            override fun urlTest(): Int = 0
        }
        for (state in BaseService.State.values()) {
            runCatching { tileService.stateChanged(state, "profile", null) }
        }
        tileService.onClick()
        tileService.onServiceConnected(binder)
        idleMain()
        val id = SagerDatabase.proxyDao.getAll().first().id
        tileService.cbSelectorUpdate(id)
        tileService.cbSelectorUpdate(-1)
        idleMain()
        tileService.onStartListening()
        idleMain()
        tileService.onClick()
        idleMain()
        shadowOf(tileService).setLocked(true)
        tileService.onClick()
        idleMain()
        tileService.onStopListening()
        idleMain()
        runCatching { controller.destroy() }
    }

    private fun item(activity: MainActivity, id: Int): MenuItem {
        return MenuBuilder(activity).add(0, id, 0, "item")
    }

    private fun layout(activity: MainActivity) {
        val content = activity.window.decorView
        content.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        content.layout(0, 0, 1080, 1920)
    }

    private fun clickDialog(which: Int) {
        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog ?: return
        dialog.getButton(which)?.performClick()
        idleMain()
    }

    private fun findEdit(view: View?): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findEdit(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun idleMain() {
        try {
            shadowOf(Looper.getMainLooper()).idle()
        } catch (e: IllegalStateException) {
            if (e.message?.contains("synchronization barrier") != true) throw e
        }
    }

    private fun settle() {
        repeat(8) {
            Thread.sleep(40)
            idleMain()
        }
    }

    private fun settleUntil(ready: () -> Boolean) {
        repeat(40) {
            if (ready()) return
            Thread.sleep(50)
            idleMain()
        }
    }
}
