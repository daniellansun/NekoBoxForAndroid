package io.nekohasekai.sagernet

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.net.toUri
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.bg.ServiceNotification
import io.nekohasekai.sagernet.bg.ShadowOsAddress
import io.nekohasekai.sagernet.bg.ShadowProcessPhoenix
import io.nekohasekai.sagernet.bg.ShadowResolvingNetwork
import io.nekohasekai.sagernet.bg.ShadowTunBuilder
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RoomGroupDirectory
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.groupDirectory
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.plugin.PluginManager.loadString
import io.nekohasekai.sagernet.ui.AssetsActivity
import io.nekohasekai.sagernet.ui.BackupFragment
import io.nekohasekai.sagernet.ui.ConfigurationFragment
import io.nekohasekai.sagernet.ui.GroupSettingsActivity
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.MessageStore
import io.nekohasekai.sagernet.ui.RouteSettingsActivity
import io.nekohasekai.sagernet.ui.ScannerActivity
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import moe.matsuri.nb4a.plugin.Plugins
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowDialog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import io.nekohasekai.sagernet.bg.VpnService as SagerVpnService
import io.nekohasekai.sagernet.database.MemoryKvDao as TestKvDao

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
    ],
)
class Batch10RobolectricTest {

    @After
    fun restoreOverrides() {
        DataStore.serviceState = BaseService.State.Idle
        DataStore.runningTest = false
        DataStore.connectionTestConcurrent = 5
        DataStore.showDirectSpeed = true
        DataStore.speedInterval = 0
        DataStore.enableHevTun = false
        DataStore.proxyApps = false
        DataStore.bypass = false
        DataStore.dirty = false
        DataStore.webdavServer = ""
        DataStore.webdavUsername = ""
        DataStore.webdavPassword = ""
        DataStore.webdavPath = "NekoBox"
        DataStore.rulesProvider = 0
        DataStore.globalMode = false
        groupDirectory = RoomGroupDirectory
        SagerDatabase.groupDaoOverride = null
        SagerDatabase.proxyDaoOverride = null
        SagerDatabase.rulesDaoOverride = null
        PublicDatabase.kvPairDaoOverride = null
        PluginManager.initOverride = null
        GuardedProcessPool.pidReader = null
        libcore.Libcore.httpBody = ""
        libcore.Libcore.httpHeader = ""
        ShadowProcessPhoenix.reset()
        BaseService.Binder.restartProcessOverride = null
        ShadowApplication.setProcessName(BuildConfig.APPLICATION_ID)
        runCatching {
            val cache = Class.forName("androidx.core.content.FileProvider").getDeclaredField("sCache")
            cache.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (cache.get(null) as MutableMap<Any, Any>).clear()
        }
    }

    @Test(timeout = 180_000)
    fun configurationMenusAndTests() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        idleMain()
        PublicDatabase.kvPairDaoOverride = TestKvDao()
        val activity = openMain()
        MessageStore.setCurrentActivity(activity)
        activity.displayFragmentWithId(R.id.nav_configuration)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val config = activity.supportFragmentManager.fragments
            .filterIsInstance<ConfigurationFragment>()
            .first()
        val groupId = DataStore.selectedGroupForImport()
        DataStore.selectedGroup = groupId
        runBlocking {
            ProfileManager.createProfile(groupId, socks("Batch Ten"))
            ProfileManager.createProfile(groupId, socks("Batch Ten"))
            ProfileManager.createProfile(groupId, HysteriaBean().apply {
                name = "hy"
                serverAddress = "127.0.0.1"
                serverPort = 443
            })
        }
        DataStore.connectionTestConcurrent = 1
        DataStore.runningTest = false
        config.onMenuItemClick(item(activity, R.id.action_connection_tcp_ping))
        settle()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        settleUntil { !DataStore.runningTest }

        DataStore.runningTest = false
        config.onMenuItemClick(item(activity, R.id.action_connection_url_test))
        settle()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settleUntil { !DataStore.runningTest }

        config.onMenuItemClick(item(activity, R.id.action_remove_duplicate))
        settleMore()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()

        config.onMenuItemClick(item(activity, R.id.action_update_subscription))
        idleMain()

        DataStore.serviceState = BaseService.State.Connected
        val global = item(activity, R.id.action_global_mode)
        global.isCheckable = true
        global.isChecked = false
        config.onMenuItemClick(global)
        Thread.sleep(700)
        idleMain()
        DataStore.serviceState = BaseService.State.Idle

        config.onMenuItemClick(item(activity, R.id.action_import_file))
        idleMain()
        deliver(config, "importFile", null)
        deliver(config, "importFile", textUri("content://coverage/socks.txt", "socks5://127.0.0.1:1080#file"))
        settleMore()
        deliver(config, "importFile", textUri("content://coverage/empty.txt", "   "))
        settle()
        deliver(config, "importFile", textUri("content://coverage/bad.txt", "::::"))
        settle()
        deliver(config, "importFile", zipUri("content://coverage/wg.zip", "socks5://127.0.0.1:1080#zip"))
        settleMore()
        deliver(config, "importFile", textUri("content://coverage/sub.txt", "sn://subscription?url=https://example.com/file"))
        settleMore()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        activity.displayFragmentWithId(R.id.nav_configuration)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val listed = activity.supportFragmentManager.fragments
            .filterIsInstance<ConfigurationFragment>()
            .last()

        val clip = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("p", ""))
        listed.onMenuItemClick(item(activity, R.id.action_import_clipboard))
        idleMain()
        clip.setPrimaryClip(ClipData.newPlainText("p", "not-a-proxy"))
        listed.onMenuItemClick(item(activity, R.id.action_import_clipboard))
        settle()
        clip.setPrimaryClip(ClipData.newPlainText("p", "socks5://127.0.0.1:1080#clip"))
        listed.onMenuItemClick(item(activity, R.id.action_import_clipboard))
        settleMore()
        clip.setPrimaryClip(ClipData.newPlainText("p", "clash://install-config?url=https://example.com/c&name=Clash"))
        listed.onMenuItemClick(item(activity, R.id.action_import_clipboard))
        settleMore()
        finishStarted()
        clip.setPrimaryClip(ClipData.newPlainText("p", "sn://subscription?url=https://example.com/s&name=Air"))
        listed.onMenuItemClick(item(activity, R.id.action_import_clipboard))
        settleMore()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        idleMain()

        activity.displayFragmentWithId(R.id.nav_configuration)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val fresh = activity.supportFragmentManager.fragments
            .filterIsInstance<ConfigurationFragment>()
            .last()
        for (id in intArrayOf(
            R.id.action_scan_qr_code,
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
        )) {
            runCatching { fresh.onMenuItemClick(item(activity, id)) }
            idleMain()
            finishStarted()
        }

        deliverIntent(activity, Intent(Intent.ACTION_VIEW, "socks5://127.0.0.1:1080#intent".toUri()))
        settleMore()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()
        deliverIntent(activity, Intent(Intent.ACTION_VIEW, "sn://subscription?url=https://example.com/nav&name=Nav".toUri()))
        settleMore()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()
        activity.missingPlugin("profile", "missing-plugin")
        idleMain()
        activity.missingPlugin("profile", "hysteria-plugin")
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        idleMain()
        (ShadowDialog.getLatestDialog() as? AlertDialog)?.listView?.performItemClick(
            null, 0, 0
        )
        idleMain()
        DataStore.hideFromRecentApps = true
        activity.applyHideFromRecentApps(true)
        activity.refreshNavMenu(true)
        activity.binding.fab.performClick()
        idleMain()
        for (id in intArrayOf(
            R.id.nav_group,
            R.id.nav_route,
            R.id.nav_settings,
            R.id.nav_tools,
            R.id.nav_logcat,
            R.id.nav_about,
            R.id.nav_faq,
            R.id.nav_configuration,
        )) {
            runCatching { activity.onNavigationItemSelected(item(activity, id)) }
            idleMain()
        }
        activity.stateChanged(BaseService.State.Stopped, "p", "down")
        idleMain()
        runCatching { activity.urlTest() }
        idleMain()
    }

    @Test(timeout = 180_000)
    fun settingsBackupAssetsAndScanner() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        idleMain()
        PublicDatabase.kvPairDaoOverride = TestKvDao()
        val activity = openMain()
        MessageStore.setCurrentActivity(activity)
        driveGroupSettings()
        driveRouteSettings()
        driveBackup(activity)
        driveAssets()
        driveScanner()
        idleMain()
    }

    @Test(timeout = 120_000)
    fun pluginsProcessAndNotification() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        idleMain()
        drivePlugins()
        driveStableProcessCleanup()
        driveSpeedNotification()
    }

    @Test(timeout = 60_000)
    @Config(
        sdk = [21],
        application = CoverageApplication::class,
        instrumentedPackages = ["com.jakewharton.processphoenix"],
        shadows = [
            ShadowTunBuilder::class,
            ShadowResolvingNetwork::class,
            ShadowOsAddress::class,
            ShadowProcessPhoenix::class,
        ],
    )
    fun pidReflectionTouchesLazy() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        idleMain()
        GuardedProcessPool.pidReader = null
        val pool = GuardedProcessPool { }
        pool.start(listOf("/bin/sh", "-c", "sleep 2"))
        idleMain()
        val job = pool.coroutineContext[Job]!!
        pool.close(GlobalScope + Dispatchers.IO)
        awaitJob(job)
    }

    private fun driveGroupSettings() {
        val created = Robolectric.buildActivity(
            GroupSettingsActivity::class.java,
            Intent()
                .putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, 0L)
                .putExtra(GroupSettingsActivity.EXTRA_FROM_CLIPBOARD, true)
                .putExtra(GroupSettingsActivity.EXTRA_GROUP_SUBSCRIPTION_LINK, "https://example.com/sub")
                .putExtra(GroupSettingsActivity.EXTRA_GROUP_NAME, "Imported"),
        ).setup()
        val group = created.get()
        settleUntil {
            group.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        val prefs = group.supportFragmentManager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
        prefs.findPreference<EditTextPreference>(Key.SUBSCRIPTION_SERVER_DNS)?.callChangeListener("bad dns")
        prefs.findPreference<EditTextPreference>(Key.SUBSCRIPTION_SERVER_DNS)?.callChangeListener(" 1.1.1.1 ")
        prefs.findPreference<EditTextPreference>(Key.SUBSCRIPTION_SERVER_DNS)?.callChangeListener("1.1.1.1")
        DataStore.groupName = "Batch10"
        DataStore.dirty = true
        prefs.onOptionsItemSelected(item(group, R.id.action_apply))
        settleMore()
        created.pause().stop().destroy()

        val existing = SagerDatabase.groupDao.allGroups().lastOrNull()
        if (existing != null) {
            val editor = Robolectric.buildActivity(
                GroupSettingsActivity::class.java,
                Intent().putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, existing.id),
            ).setup()
            val again = editor.get()
            settleUntil {
                again.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
            }
            val child = again.supportFragmentManager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
            child.onOptionsItemSelected(item(again, R.id.action_delete))
            idleMain()
            clickDialog(AlertDialog.BUTTON_POSITIVE)
            settle()
            editor.pause().stop().destroy()
        }
    }

    private fun driveRouteSettings() {
        val created = Robolectric.buildActivity(
            RouteSettingsActivity::class.java,
            Intent()
                .putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, 0L)
                .putExtra(RouteSettingsActivity.EXTRA_PACKAGE_NAME, app.packageName),
        ).setup()
        val route = created.get()
        settleUntil {
            route.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        DataStore.routeDomain = "example.com"
        DataStore.routeName = "batch10"
        DataStore.dirty = true
        val prefs = route.supportFragmentManager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
        prefs.onOptionsItemSelected(item(route, R.id.action_apply))
        settleMore()
        created.pause().stop().destroy()

        val empty = Robolectric.buildActivity(
            RouteSettingsActivity::class.java,
            Intent().putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, 0L),
        ).setup().get()
        settleUntil {
            empty.supportFragmentManager.findFragmentById(R.id.settings) != null
        }
        DataStore.dirty = false
        runBlocking { empty.saveAndExit() }
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        empty.finish()
    }

    private fun driveBackup(activity: MainActivity) {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        val backup = BackupFragment()
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_holder, backup, "backup10")
            .commitNow()
        idleMain()
        deliver(backup, "importFile", textUri("content://coverage/backup.json", """{"version":1}"""))
        settleMore()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settleMore()
        (ShadowDialog.getLatestDialog() as? AlertDialog)?.dismiss()
        idleMain()

        DataStore.webdavServer = ""
        val server = WebDavServer()
        server.use {
            DataStore.webdavServer = "http://127.0.0.1:${it.port}"
            DataStore.webdavUsername = "user"
            DataStore.webdavPassword = "pass"
            DataStore.webdavPath = "a/b"
            val before = ShadowProcessPhoenix.rebirths
            val restore = backup.requireView().findViewById<View>(R.id.restore_from_webdav)
            restore.performClick()
            restore.performClick()
            settleUntil {
                val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
                dialog?.isShowing == true && dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null
            }
            clickDialog(AlertDialog.BUTTON_POSITIVE)
            settleUntil { ShadowProcessPhoenix.rebirths > before }
            assertTrue(ShadowProcessPhoenix.rebirths > before)
        }
    }

    private fun driveAssets() {
        val controller = Robolectric.buildActivity(AssetsActivity::class.java).setup()
        val assets = controller.get()
        val dir = assets.getExternalFilesDir(null) ?: assets.filesDir
        val geo = File(dir, "geoip.db")
        geo.writeBytes(byteArrayOf(1))
        val version = File(dir, "geoip.version.txt")
        version.writeText("old")
        DataStore.rulesProvider = 0
        libcore.Libcore.httpBody = JSONObject()
            .put("tag_name", "v9")
            .put("url", "https://example/release")
            .put(
                "assets",
                JSONArray().put(
                    JSONObject()
                        .put("name", "geoip.db")
                        .put("browser_download_url", "https://example/geoip.db")
                ),
            )
            .toString()
        runBlocking { assets.updateAsset(geo, version, "old") }
        idleMain()
        version.writeText("v9")
        runBlocking { assets.updateAsset(geo, version, "v9") }
        idleMain()
        libcore.Libcore.httpBody = JSONObject()
            .put("tag_name", "v2")
            .put("url", "https://example/release")
            .put("assets", JSONArray())
            .toString()
        runCatching { runBlocking { assets.updateAsset(geo, version, "old") } }
        libcore.Libcore.httpBody = ""
        libcore.Libcore.httpHeader = ""
        deliver(assets, "importFile", null)
        deliver(assets, "importFile", textUri("content://coverage/note.txt", "nope"))
        idleMain()
        deliver(assets, "importFile", textUri("content://coverage/custom.db", "db"))
        settleMore()
        controller.pause().stop().destroy()
    }

    private fun driveScanner() {
        val controller = Robolectric.buildActivity(ScannerActivity::class.java).setup()
        val scanner = controller.get()
        idleMain()
        val png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        )
        deliver(scanner, "importCodeFile", listOf(bytesUri("content://coverage/code.png", png)))
        settleMore()
        if (!scanner.isFinishing) {
            deliver(scanner, "importCodeFile", emptyList<Uri>())
            settle()
        }
        controller.pause().stop().destroy()
    }

    private fun drivePlugins() {
        PluginManager.initOverride = null
        assertEquals(null, PluginManager.init(""))
        val dir = File(app.filesDir, "native-lib")
        dir.mkdirs()
        app.applicationInfo.nativeLibraryDir = dir.absolutePath
        val hysteria = File(dir, "libhysteria.so")
        hysteria.writeText("so")
        hysteria.setExecutable(true, false)
        val found = PluginManager.init("hysteria-plugin")
        assertNotNull(found)
        assertTrue(found!!.path.endsWith("libhysteria.so"))
        File(dir, "libhysteria2.so").writeText("so")
        runCatching { PluginManager.init("hysteria2-plugin") }
        runCatching { PluginManager.init("not-a-plugin") }

        val meta = Bundle()
        meta.putString(Plugins.METADATA_KEY_ID, "trojan-go-plugin")
        meta.putString(Plugins.METADATA_KEY_EXECUTABLE_PATH, "libtrojan.so")
        val provider = ProviderInfo()
        provider.authority = Plugins.AUTHORITIES_PREFIX_NEKO_EXE + "trojan"
        provider.exported = true
        provider.packageName = "moe.matsuri.exe.trojan"
        provider.metaData = meta
        provider.applicationInfo = ApplicationInfo()
        provider.applicationInfo.packageName = provider.packageName
        provider.applicationInfo.nativeLibraryDir = dir.absolutePath
        val pkg = PackageInfo()
        pkg.packageName = provider.packageName
        pkg.providers = arrayOf(provider)
        pkg.applicationInfo = provider.applicationInfo
        shadowOf(app.packageManager).installPackage(pkg)
        PackageCache.reload()
        File(dir, "libtrojan.so").writeText("so")
        File(dir, "libtrojan.so").setExecutable(true, false)
        val external = PluginManager.init("trojan-go-plugin")
        assertNotNull(external)
        meta.putInt("bad", 1)
        runCatching { provider.loadString("bad") }
        provider.loadString(Plugins.METADATA_KEY_EXECUTABLE_PATH)
    }

    private fun driveStableProcessCleanup() {
        val pool = GuardedProcessPool { }
        pool.start(listOf("/bin/sh", "-c", "trap '' TERM; exec sleep 30"), mutableMapOf("COVERAGE" to "1"))
        idleMain()
        val job = pool.coroutineContext[Job]!!
        pool.close(GlobalScope + Dispatchers.IO)
        awaitJob(job)
        touchDefaultEnv()
    }

    private fun awaitJob(job: Job) {
        val deadline = System.nanoTime() + 8_000_000_000L
        while (!job.isCompleted && System.nanoTime() < deadline) {
            Thread.sleep(50)
            idleMain()
        }
    }

    private fun touchDefaultEnv() {
        val pool = GuardedProcessPool { }
        val guard = Class.forName("io.nekohasekai.sagernet.bg.GuardedProcessPool\$Guard")
        val ctor = guard.declaredConstructors.first { it.parameterCount >= 4 }
        ctor.isAccessible = true
        ctor.newInstance(pool, listOf("/bin/true"), null, 2, null)
    }

    private fun driveSpeedNotification() {
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        DataStore.showDirectSpeed = false
        val hidden = ServiceNotification(vpn, "coverage", "service-vpn", false)
        runBlocking {
            hidden.postNotificationSpeedUpdate(SpeedDisplayData(1, 2, 3, 4, 5, 6))
        }
        idleMain()
        hidden.destroy()
        DataStore.showDirectSpeed = true
        val shown = ServiceNotification(vpn, "coverage", "service-vpn", true)
        runBlocking {
            shown.postNotificationSpeedUpdate(SpeedDisplayData(8, 9, 0, 0, 1, 1))
            shown.postNotificationWakeLockStatus(true)
        }
        idleMain()
        shown.destroy()
    }

    private fun openMain(): MainActivity {
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, SagerVpnService::class.java),
            vpn.data.binder,
        )
        DataStore.serviceMode = Key.MODE_VPN
        return Robolectric.buildActivity(MainActivity::class.java).setup().get().also {
            idleMain()
            it.supportFragmentManager.executePendingTransactions()
        }
    }

    private fun socks(name: String) = SOCKSBean().apply {
        this.name = name
        serverAddress = "127.0.0.1"
        serverPort = 1080
    }

    private fun textUri(raw: String, text: String): Uri {
        val uri = Uri.parse(raw)
        shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(text.toByteArray()))
        return uri
    }

    private fun bytesUri(raw: String, bytes: ByteArray): Uri {
        val uri = Uri.parse(raw)
        shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        return uri
    }

    private fun zipUri(raw: String, text: String): Uri {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("node.txt"))
            zip.write(text.toByteArray())
            zip.closeEntry()
        }
        return bytesUri(raw, out.toByteArray())
    }

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

    private fun deliverIntent(activity: Activity, intent: Intent) {
        val method = Activity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
        method.isAccessible = true
        method.invoke(activity, intent)
    }

    private fun item(context: Context, id: Int) = MenuBuilder(context).add(0, id, 0, "item")

    private fun clickDialog(which: Int) {
        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog ?: return
        dialog.getButton(which)?.performClick()
        idleMain()
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

    private fun settleMore() {
        repeat(20) {
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

    private class WebDavServer : Closeable {
        private val server = ServerSocket(0)
        val port = server.localPort
        private val body = zipJson("""{"version":1}""")

        init {
            Thread {
                while (!server.isClosed) {
                    try {
                        val socket = server.accept()
                        Thread { handle(socket) }.apply { isDaemon = true }.start()
                    } catch (_: Exception) {
                        break
                    }
                }
            }.apply { isDaemon = true }.start()
        }

        private fun handle(socket: Socket) {
            socket.use { current ->
                current.soTimeout = 3000
                val input = current.getInputStream()
                val request = readLine(input)
                if (request.isEmpty()) return
                val method = request.substringBefore(' ')
                var length = 0
                while (true) {
                    val line = readLine(input)
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", true)) {
                        length = line.substringAfter(':').trim().toIntOrNull() ?: 0
                    }
                }
                var left = length
                val sink = ByteArray(8192)
                while (left > 0) {
                    val n = input.read(sink, 0, minOf(left, sink.size))
                    if (n < 0) break
                    left -= n
                }
                val payload = if (method == "PROPFIND") {
                    "<D:href>/a/b/nekobox_backup_1.0_20240101_120000.zip</D:href>".toByteArray()
                } else {
                    body
                }
                val code = if (method == "PROPFIND") 207 else 200
                val head = "HTTP/1.1 $code OK\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n"
                val out = current.getOutputStream()
                out.write(head.toByteArray())
                out.write(payload)
                out.flush()
            }
        }

        private fun readLine(input: InputStream): String {
            val line = StringBuilder()
            while (true) {
                val value = input.read()
                if (value < 0 || value == '\n'.code) break
                if (value != '\r'.code) line.append(value.toChar())
            }
            return line.toString()
        }

        override fun close() {
            runCatching { server.close() }
        }

        private fun zipJson(json: String): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("nekobox.json"))
                zip.write(json.toByteArray())
                zip.closeEntry()
            }
            return out.toByteArray()
        }
    }
}
