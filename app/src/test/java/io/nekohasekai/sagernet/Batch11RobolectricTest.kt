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
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.ForegroundUpdater
import androidx.work.ProgressUpdater
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.taskexecutor.SerialExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import com.google.common.util.concurrent.Futures
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.bg.ShadowOsAddress
import io.nekohasekai.sagernet.bg.ShadowProcessPhoenix
import io.nekohasekai.sagernet.bg.ShadowResolvingNetwork
import io.nekohasekai.sagernet.bg.ShadowTunBuilder
import io.nekohasekai.sagernet.bg.SubscriptionUpdater
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RoomGroupDirectory
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.database.groupDirectory
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.plugin.PluginManager.loadString
import io.nekohasekai.sagernet.ui.BackupFragment
import io.nekohasekai.sagernet.ui.ConfigurationFragment
import io.nekohasekai.sagernet.ui.GroupSettingsActivity
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.ProfileSelectActivity
import io.nekohasekai.sagernet.ui.RouteSettingsActivity
import io.nekohasekai.sagernet.ui.WebDAVSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ChainSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity
import io.nekohasekai.sagernet.widget.OutboundPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import moe.matsuri.nb4a.plugin.Plugins
import org.junit.After
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
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import io.nekohasekai.sagernet.bg.VpnService as SagerVpnService

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
class Batch11RobolectricTest {

    @After
    fun restoreOverrides() {
        DataStore.serviceState = BaseService.State.Idle
        DataStore.dirty = false
        DataStore.globalMode = false
        DataStore.allowInsecureOnRequest = false
        DataStore.appTLSVersion = ""
        DataStore.webdavServer = ""
        DataStore.webdavUsername = ""
        DataStore.webdavPassword = ""
        DataStore.webdavPath = "NekoBox"
        DataStore.confirmProfileDelete = true
        groupDirectory = RoomGroupDirectory
        SagerDatabase.groupDaoOverride = null
        SagerDatabase.proxyDaoOverride = null
        SagerDatabase.rulesDaoOverride = null
        PublicDatabase.kvPairDaoOverride = null
        PluginManager.initOverride = null
        GuardedProcessPool.pidReader = null
        SubscriptionUpdater.reconfigureOverride = null
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
    fun configurationImportAndLists() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        val activity = openMain()
        activity.displayFragmentWithId(R.id.nav_configuration)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val config = activity.supportFragmentManager.fragments
            .filterIsInstance<ConfigurationFragment>()
            .last()
        val groupId = DataStore.selectedGroupForImport()
        val kept = runBlocking { ProfileManager.createProfile(groupId, socks("keep")) }
        kept.tx = 20
        kept.rx = 10
        kept.status = 2
        kept.error = "down"
        runBlocking { ProfileManager.updateProfile(kept) }
        repeat(22) {
            runBlocking { ProfileManager.createProfile(groupId, socks("dup")) }
        }

        config.onMenuItemClick(item(activity, R.id.action_clear_traffic_statistics))
        settleMore()
        config.onMenuItemClick(item(activity, R.id.action_connection_test_clear_results))
        settleMore()
        config.onMenuItemClick(item(activity, R.id.action_connection_test_delete_unavailable))
        settleMore()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settleMore()
        config.onMenuItemClick(item(activity, R.id.action_remove_duplicate))
        settleMore()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settleMore()

        val sub = ProxyGroup(name = "Batch11 Sub", type = GroupType.SUBSCRIPTION).apply {
            subscription = SubscriptionBean().apply {
                initializeDefaultValues()
                link = "https://example.invalid/batch11"
                autoUpdate = true
            }
        }
        sub.id = SagerDatabase.groupDao.createGroup(sub)
        DataStore.selectedGroup = sub.id
        config.onMenuItemClick(item(activity, R.id.action_update_subscription))
        settleUntil {
            (ShadowDialog.getLatestDialog() as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE) != null
        }
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settleMore()

        DataStore.serviceState = BaseService.State.Connected
        val global = item(activity, R.id.action_global_mode)
        global.isCheckable = true
        global.isChecked = false
        config.onMenuItemClick(global)
        settleMore()
        activity.findViewById<View>(com.google.android.material.R.id.snackbar_action)?.performClick()
        settleMore()
        DataStore.serviceState = BaseService.State.Idle

        val zip = zipWithDirectory("content://coverage/dir.zip")
        deliver(config, "importFile", zip)
        settleMore()
        val out = Uri.parse("content://coverage/exported.json")
        shadowOf(app.contentResolver).registerOutputStream(out, ByteArrayOutputStream())
        deliver(config, "exportConfig", out)
        settle()
        deliver(config, "exportConfig", null)

        DataStore.allowInsecureOnRequest = true
        DataStore.appTLSVersion = "1.3"
        libcore.Libcore.httpBody = ""
        libcore.Libcore.httpHeader = "attachment; filename=\"Airport\""
        val clip = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("p", "clash://install-config?url=http://127.0.0.1/sub"))
        config.onMenuItemClick(item(activity, R.id.action_import_clipboard))
        settleMore()
        finishStarted()

        val decode = ConfigurationFragment::class.java.getDeclaredMethod("decodeProfileTitle", String::class.java)
        decode.isAccessible = true
        decode.invoke(config, "")
        decode.invoke(config, "Hello%20Air")
        decode.invoke(config, "%")
        val disposition = ConfigurationFragment::class.java.getDeclaredMethod(
            "parseContentDisposition",
            String::class.java,
        )
        disposition.isAccessible = true
        disposition.invoke(config, "")
        disposition.invoke(config, "attachment; filename=\"Air\"")
        disposition.invoke(config, "no-filename")
        libcore.Libcore.httpBody = ""
        libcore.Libcore.httpHeader = ""
    }

    @Test(timeout = 180_000)
    fun settingsChainWebdavAndBackup() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        driveGroupPreferences()
        driveRoutePreferences()
        driveChain()
        driveWebDavSettings()
        val activity = openMain()
        driveBackupEdges(activity)
    }

    @Test(timeout = 120_000)
    fun subscriptionAndPluginFailures() {
        driveSubscriptionUpdater()
        drivePluginFailures()
        touchPidLazy()
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
    fun pidLazyOnApi21() {
        touchPidLazy()
        GuardedProcessPool.pidReader = null
        val pool = GuardedProcessPool { }
        pool.start(listOf("/bin/sh", "-c", "sleep 30"))
        idleMain()
        val job = pool.coroutineContext[Job]!!
        pool.close(GlobalScope + Dispatchers.IO)
        awaitJob(job)
    }

    private fun driveGroupPreferences() {
        val created = Robolectric.buildActivity(
            GroupSettingsActivity::class.java,
            Intent().putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, 0L),
        ).setup()
        val group = created.get()
        settleUntil {
            group.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        val prefs = group.supportFragmentManager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
        prefs.findPreference<Preference>(Key.GROUP_TYPE)?.callChangeListener("1")
        prefs.findPreference<Preference>(Key.GROUP_TYPE)?.callChangeListener("0")
        prefs.findPreference<Preference>(Key.SUBSCRIPTION_AUTO_UPDATE)?.callChangeListener(true)
        prefs.findPreference<Preference>(Key.SUBSCRIPTION_AUTO_UPDATE)?.callChangeListener(false)
        prefs.findPreference<EditTextPreference>(Key.SUBSCRIPTION_AUTO_UPDATE_DELAY)?.callChangeListener("nope")
        prefs.findPreference<EditTextPreference>(Key.SUBSCRIPTION_AUTO_UPDATE_DELAY)?.callChangeListener("10")
        prefs.findPreference<EditTextPreference>(Key.SUBSCRIPTION_AUTO_UPDATE_DELAY)?.callChangeListener("20")
        prefs.findPreference<Preference>(Key.SUBSCRIPTION_FILTER_MODE)?.callChangeListener("1")
        prefs.findPreference<Preference>(Key.SUBSCRIPTION_FILTER_MODE)?.callChangeListener("0")
        prefs.findPreference<Preference>(Key.GROUP_FRONT_PROXY)?.callChangeListener(OutboundPreference.VALUE_SELECT_PROFILE)
        prefs.findPreference<Preference>(Key.GROUP_FRONT_PROXY)?.callChangeListener("0")
        prefs.findPreference<Preference>(Key.GROUP_LANDING_PROXY)?.callChangeListener(OutboundPreference.VALUE_SELECT_PROFILE)
        prefs.findPreference<Preference>(Key.GROUP_LANDING_PROXY)?.callChangeListener("1")
        finishStarted()
        val password = EditTextPreference(group)
        password.text = "secret"
        GroupSettingsActivity.PasswordSummaryProvider.provideSummary(password)
        password.text = ""
        GroupSettingsActivity.PasswordSummaryProvider.provideSummary(password)
        DataStore.dirty = true
        group.onBackPressed()
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEUTRAL)
        group.onBackPressed()
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        created.pause().stop().destroy()

        val missing = Robolectric.buildActivity(
            GroupSettingsActivity::class.java,
            Intent().putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, 9_999L),
        ).setup().get()
        settleMore()
        missing.finish()
    }

    private fun driveRoutePreferences() {
        val created = Robolectric.buildActivity(
            RouteSettingsActivity::class.java,
            Intent().putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, 0L),
        ).setup()
        val route = created.get()
        settleUntil {
            route.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        route.outbound.callChangeListener(OutboundPreference.VALUE_SELECT_PROFILE)
        route.outbound.callChangeListener("0")
        route.apps.performClick()
        finishStarted()
        DataStore.dirty = true
        route.onBackPressed()
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        created.pause().stop().destroy()

        DataStore.routeName = "saved"
        DataStore.routeDomain = "example.com"
        DataStore.dirty = true
        val saved = Robolectric.buildActivity(
            RouteSettingsActivity::class.java,
            Intent().putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, 0L),
        ).setup().get()
        settleUntil {
            saved.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        val prefs = saved.supportFragmentManager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
        prefs.onOptionsItemSelected(item(saved, R.id.action_apply))
        settleMore()
        val ruleId = DataStore.editingId
        saved.finish()

        if (ruleId != 0L) {
            val editor = Robolectric.buildActivity(
                RouteSettingsActivity::class.java,
                Intent().putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, ruleId),
            ).setup().get()
            settleUntil {
                editor.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
            }
            val child = editor.supportFragmentManager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
            child.onOptionsItemSelected(item(editor, R.id.action_delete))
            idleMain()
            clickDialog(AlertDialog.BUTTON_NEGATIVE)
            child.onOptionsItemSelected(item(editor, R.id.action_delete))
            idleMain()
            clickDialog(AlertDialog.BUTTON_POSITIVE)
            settle()
            editor.finish()
        }

        val missing = Robolectric.buildActivity(
            RouteSettingsActivity::class.java,
            Intent().putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, 9_999L),
        ).setup().get()
        settleMore()
        missing.finish()
    }

    private fun driveChain() {
        val groupId = DataStore.selectedGroupForImport()
        val first = runBlocking { ProfileManager.createProfile(groupId, socks("chain-a")) }
        val second = runBlocking { ProfileManager.createProfile(groupId, socks("chain-b")) }
        val inner = runBlocking {
            ProfileManager.createProfile(groupId, ChainBean().apply { name = "inner" })
        }
        val mid = runBlocking {
            ProfileManager.createProfile(groupId, ChainBean().apply {
                name = "mid"
                proxies = arrayListOf(inner.id)
            })
        }
        val outer = runBlocking {
            ProfileManager.createProfile(groupId, ChainBean().apply {
                name = "outer"
                proxies = arrayListOf(mid.id)
            })
        }
        val chain = Robolectric.buildActivity(
            ChainSettingsActivity::class.java,
            Intent().putExtra(ProfileSettingsActivity.EXTRA_PROFILE_ID, outer.id),
        ).setup().get()
        settleUntil {
            chain.supportFragmentManager.findFragmentById(R.id.settings) != null
        }
        assertTrue(chain.testProfileContains(outer, inner))
        assertTrue(!chain.testProfileAllowed(outer))
        assertTrue(chain.testProfileAllowed(first))
        DataStore.serverProtocol = "${first.id},${second.id}"
        runBlocking { chain.configurationAdapter.reload() }
        idleMain()
        val list = chain.configurationList
        val add = chain.configurationAdapter.onCreateViewHolder(list, 0)
        chain.configurationAdapter.onBindViewHolder(add, 0)
        add.itemView.performClick()
        finishStarted()
        chain.replacing = 0
        deliver(
            chain,
            "selectProfileForAdd",
            ActivityResult(
                Activity.RESULT_OK,
                Intent().putExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, first.id),
            ),
        )
        settleMore()
        if (chain.proxyList.isNotEmpty()) {
            chain.replacing = 1
            deliver(
                chain,
                "selectProfileForAdd",
                ActivityResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, second.id),
                ),
            )
            settleMore()
            chain.replacing = 0
            deliver(
                chain,
                "selectProfileForAdd",
                ActivityResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, outer.id),
                ),
            )
            settleMore()
            clickDialog(AlertDialog.BUTTON_POSITIVE)
        }
        if (chain.proxyList.size >= 2) {
            chain.configurationAdapter.move(1, 2)
            chain.configurationAdapter.remove(1)
        }
        val prefs = chain.supportFragmentManager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
        DataStore.profileName = "batch11-chain"
        DataStore.dirty = true
        prefs.onOptionsItemSelected(item(chain, R.id.action_apply))
        settleMore()
        runCatching { prefs.onOptionsItemSelected(item(chain, R.id.action_custom_config_json)) }
        runCatching { prefs.onOptionsItemSelected(item(chain, R.id.action_create_shortcut)) }
        finishStarted()
        chain.finish()
    }

    private fun driveWebDavSettings() {
        val controller = Robolectric.buildActivity(WebDAVSettingsActivity::class.java).setup()
        val activity = controller.get()
        settleUntil {
            activity.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        val prefs = activity.supportFragmentManager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
        val test = prefs.findPreference<Preference>("webdavTest")!!
        DataStore.webdavServer = ""
        test.performClick()
        settle()
        test.performClick()
        settle()
        DataStore.webdavServer = "not a url"
        Thread.sleep(1100)
        test.performClick()
        settleMore()
        StatusServer(401).use { server ->
            DataStore.webdavServer = "http://127.0.0.1:${server.port}"
            DataStore.webdavUsername = "user"
            DataStore.webdavPassword = "pass"
            DataStore.webdavPath = ""
            Thread.sleep(1100)
            test.performClick()
            settleMore()
        }
        StatusServer(200, mkcol = 405).use { server ->
            DataStore.webdavServer = "http://127.0.0.1:${server.port}"
            DataStore.webdavPath = "a/b"
            Thread.sleep(1100)
            test.performClick()
            settleMore()
        }
        StatusServer(500).use { server ->
            DataStore.webdavServer = "http://127.0.0.1:${server.port}/missing"
            DataStore.webdavPath = ""
            Thread.sleep(1100)
            test.performClick()
            settleMore()
        }
        activity.onSupportNavigateUp()
        controller.pause().stop().destroy()
    }

    private fun driveBackupEdges(activity: MainActivity) {
        val backup = BackupFragment()
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_holder, backup, "backup11")
            .commitNow()
        idleMain()
        val root = backup.requireView()
        DataStore.webdavServer = ""
        root.findViewById<View>(R.id.backup_to_webdav).performClick()
        root.findViewById<View>(R.id.restore_from_webdav).performClick()
        idleMain()
        root.findViewById<View>(R.id.webdav_settings).performClick()
        finishStarted()
        root.findViewById<View>(R.id.action_export).performClick()
        settleMore()
        runCatching { root.findViewById<View>(R.id.action_share).performClick() }
        settleMore()
        DataStore.webdavServer = "ftp://bad"
        root.findViewById<View>(R.id.backup_to_webdav).performClick()
        root.findViewById<View>(R.id.backup_to_webdav).performClick()
        settleMore()
        StatusServer(401).use { server ->
            DataStore.webdavServer = "http://127.0.0.1:${server.port}"
            DataStore.webdavUsername = "user"
            DataStore.webdavPassword = "pass"
            DataStore.webdavPath = "a/b"
            root.findViewById<View>(R.id.backup_to_webdav).performClick()
            settleMore()
        }
    }

    private fun driveSubscriptionUpdater() {
        SubscriptionUpdater.reconfigureOverride = null
        val job = GlobalScope.launch(Dispatchers.IO) {
            runCatching { SubscriptionUpdater.reconfigureUpdater() }
        }
        awaitJob(job)
        if (!job.isCompleted) job.cancel()

        val now = (System.currentTimeMillis() / 1000L).toInt()
        fun sub(name: String, body: SubscriptionBean.() -> Unit) = ProxyGroup(
            name = name,
            type = GroupType.SUBSCRIPTION,
        ).apply {
            subscription = SubscriptionBean().apply {
                initializeDefaultValues()
                link = "https://example.invalid/$name"
                autoUpdate = true
                autoUpdateDelay = 1440
                lastUpdated = 0
                body()
            }
            id = SagerDatabase.groupDao.createGroup(this)
        }
        sub("off") { autoUpdate = false }
        sub("connected-only") {
            updateWhenConnectedOnly = true
            lastUpdated = now
            autoUpdateDelay = 15
        }
        sub("recent") {
            lastUpdated = now
            autoUpdateDelay = 15
        }
        sub("stale") {
            lastUpdated = 0
            autoUpdateDelay = 1
            link = "https://example.invalid/stale"
        }
        DataStore.serviceState = BaseService.State.Stopped
        val worker = newUpdateTask()
        runBlocking { worker.doWork() }
        idleMain()
        DataStore.serviceState = BaseService.State.Connected
        runBlocking { worker.doWork() }
        idleMain()
        val again = GlobalScope.launch(Dispatchers.IO) {
            runCatching { SubscriptionUpdater.reconfigureUpdater() }
        }
        awaitJob(again)
        if (!again.isCompleted) again.cancel()
        SubscriptionUpdater.reconfigureOverride = {}
    }

    private fun newUpdateTask(): SubscriptionUpdater.UpdateTask {
        val executor = Executors.newSingleThreadExecutor()
        val serial = object : SerialExecutor {
            override fun execute(command: Runnable) = executor.execute(command)
            override fun hasPendingTasks() = false
        }
        val tasks = object : TaskExecutor {
            override fun getMainThreadExecutor() = executor
            override fun getSerialTaskExecutor() = serial
        }
        val params = WorkerParameters(
            UUID.randomUUID(),
            Data.EMPTY,
            emptyList(),
            WorkerParameters.RuntimeExtras(),
            1,
            0,
            executor,
            tasks,
            WorkerFactory.getDefaultWorkerFactory(),
            ProgressUpdater { _, _, _ -> Futures.immediateFuture(null) },
            ForegroundUpdater { _, _, _ -> Futures.immediateFuture(null) },
        )
        return SubscriptionUpdater.UpdateTask(app, params)
    }

    private fun drivePluginFailures() {
        PluginManager.initOverride = null
        val missing = runCatching { PluginManager.init("no-such-plugin") }.exceptionOrNull()
        if (missing is PluginManager.PluginNotFoundException) {
            assertNotNull(missing.localizedMessage)
        }
        PluginManager.PluginNotFoundException("x").localizedMessage
        val dir = File(app.filesDir, "native-lib-11")
        dir.mkdirs()
        app.applicationInfo.nativeLibraryDir = dir.absolutePath
        val meta = Bundle()
        meta.putString(Plugins.METADATA_KEY_ID, "bad-plugin")
        meta.putString(Plugins.METADATA_KEY_EXECUTABLE_PATH, "libmissing.so")
        val so = File(dir, "libmissing.so")
        so.writeText("x")
        so.setExecutable(false, false)
        val provider = ProviderInfo()
        provider.authority = Plugins.AUTHORITIES_PREFIX_NEKO_EXE + "bad"
        provider.exported = true
        provider.packageName = "moe.matsuri.exe.bad"
        provider.metaData = meta
        provider.applicationInfo = ApplicationInfo()
        provider.applicationInfo.packageName = provider.packageName
        provider.applicationInfo.nativeLibraryDir = dir.absolutePath
        val pkg = PackageInfo()
        pkg.packageName = provider.packageName
        pkg.providers = arrayOf(provider)
        pkg.applicationInfo = provider.applicationInfo
        shadowOf(app.packageManager).installPackage(pkg)
        runCatching { PluginManager.init("bad-plugin") }
        meta.putInt("label", R.string.app_name)
        provider.applicationInfo = app.applicationInfo
        runCatching { provider.loadString("label") }
        meta.putBoolean("flag", true)
        runCatching { provider.loadString("flag") }
    }

    private fun touchPidLazy() {
        val outer = GuardedProcessPool::class.java
        val companionField = outer.getDeclaredField("Companion")
        companionField.isAccessible = true
        val companion = companionField.get(null)
        val getter = companion.javaClass.declaredMethods.first { it.name == "getPid" }
        getter.isAccessible = true
        runCatching { getter.invoke(companion) }
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

    private fun zipWithDirectory(raw: String): Uri {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("dir/"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("readme.txt"))
            zip.write("hello".toByteArray())
            zip.closeEntry()
        }
        val uri = Uri.parse(raw)
        shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(out.toByteArray()))
        return uri
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
        repeat(16) {
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

    private fun awaitJob(job: Job) {
        val deadline = System.nanoTime() + 8_000_000_000L
        while (!job.isCompleted && System.nanoTime() < deadline) {
            Thread.sleep(50)
            idleMain()
        }
    }

    private class StatusServer(private val propfind: Int, private val mkcol: Int = propfind) : Closeable {
        private val server = ServerSocket(0)
        val port = server.localPort

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
                val sink = ByteArray(1024)
                while (left > 0) {
                    val n = input.read(sink, 0, minOf(left, sink.size))
                    if (n < 0) break
                    left -= n
                }
                val code = if (method == "MKCOL") mkcol else propfind
                val head = "HTTP/1.1 $code OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                current.getOutputStream().write(head.toByteArray())
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
    }
}
