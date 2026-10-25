package io.nekohasekai.sagernet

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.content.FileProvider
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.aidl.TrafficDataBatch
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.ShadowDnsResolver
import io.nekohasekai.sagernet.bg.ShadowOsAddress
import io.nekohasekai.sagernet.bg.ShadowProcessPhoenix
import io.nekohasekai.sagernet.bg.ShadowResolvingNetwork
import io.nekohasekai.sagernet.bg.ShadowTunBuilder
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.ui.AboutFragment
import io.nekohasekai.sagernet.ui.BackupFragment
import io.nekohasekai.sagernet.ui.ConfigurationFragment
import io.nekohasekai.sagernet.ui.GroupFragment
import io.nekohasekai.sagernet.ui.GroupSettingsActivity
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.ProfileSelectActivity
import io.nekohasekai.sagernet.ui.RouteFragment
import io.nekohasekai.sagernet.ui.RouteSettingsActivity
import io.nekohasekai.sagernet.ui.SettingsPreferenceFragment
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.utils.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import libcore.ExchangeContext
import moe.matsuri.nb4a.net.LocalResolverImpl
import moe.matsuri.nb4a.plugin.Plugins
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.ShadowSystemClock
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit
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
        ShadowDnsResolver::class,
    ],
)
class Batch12RobolectricTest {

    @After
    fun restoreOverrides() {
        DataStore.serviceState = BaseService.State.Idle
        DataStore.runningTest = false
        DataStore.dirty = false
        DataStore.globalMode = false
        DataStore.allowInsecureOnRequest = false
        DataStore.appTLSVersion = ""
        DataStore.hideFromRecentApps = false
        DataStore.connectionTestConcurrent = 5
        libcore.Libcore.httpBody = ""
        libcore.Libcore.httpHeader = ""
        PluginManager.initOverride = null
        ShadowProcessPhoenix.reset()
        BaseService.Binder.restartProcessOverride = null
        ShadowDnsResolver.reset()
        SagerNet.underlyingNetwork = null
        GroupUpdater.updating.clear()
        GroupUpdater.progress.clear()
        ShadowApplication.setProcessName(BuildConfig.APPLICATION_ID)
        runCatching {
            val cache = FileProvider::class.java.getDeclaredField("sCache")
            cache.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (cache.get(null) as MutableMap<Any, Any>).clear()
        }
    }

    @Test(timeout = 180_000)
    fun configurationListsAndServiceFailure() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        val activity = openMain()
        activity.displayFragmentWithId(R.id.nav_configuration)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val config = activity.supportFragmentManager.fragments
            .filterIsInstance<ConfigurationFragment>()
            .last()
        settleUntil { runCatching { config.adapter.groupList.isNotEmpty() }.getOrDefault(false) }
        val groupId = DataStore.currentGroupId()
        DataStore.selectedGroup = groupId
        val down = runBlocking { ProfileManager.createProfile(groupId, socks("down", "10.0.0.8")) }
        down.status = 2
        down.error = "down"
        SagerDatabase.proxyDao.updateProxy(down)
        val ping = runBlocking { ProfileManager.createProfile(groupId, socks("ping", "ping.example")) }
        runBlocking {
            ProfileManager.createProfile(groupId, HysteriaBean().apply {
                initializeDefaultValues()
                name = "hy"
                serverAddress = "hy.example"
            })
        }
        val page = attachGroupPage(activity, config)
        val listed = page?.adapter
        if (listed != null) {
            val profiles = SagerDatabase.proxyDao.getByGroup(groupId)
            listed.configurationList.clear()
            profiles.forEach { listed.configurationList[it.id] = it }
            listed.configurationIdList.clear()
            listed.configurationIdList.addAll(profiles.map { it.id })
            if (listed.configurationIdList.size >= 2) {
                listed.move(0, 1)
                listed.commitMove()
                listed.undo(listOf(0 to profiles.first()))
                idleMain()
            }
            val parent = page.configurationListView
            val holder = listed.onCreateViewHolder(parent, 0)
            listed.onBindViewHolder(holder, 0)
            val cached = listed.configurationList.values.first()
            cached.tx += 9
            listed.onViewAttachedToWindow(holder)
            val scroll = RecyclerView::class.java.getDeclaredField("mScrollState")
            scroll.isAccessible = true
            scroll.setInt(parent, RecyclerView.SCROLL_STATE_DRAGGING)
            cached.rx += 4
            listed.onViewAttachedToWindow(holder)
            scroll.setInt(parent, RecyclerView.SCROLL_STATE_IDLE)
        }
        config.onMenuItemClick(item(activity, R.id.action_connection_test_delete_unavailable))
        settleUntil {
            (ShadowDialog.getLatestDialog() as? AlertDialog)
                ?.getButton(AlertDialog.BUTTON_POSITIVE) != null
        }
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settleMore()

        DataStore.connectionTestConcurrent = 1
        DataStore.runningTest = false
        SagerNet.underlyingNetwork = ShadowNetwork.newInstance(1)
        config.onMenuItemClick(item(activity, R.id.action_connection_tcp_ping))
        settleUntil { DataStore.runningTest }
        settleUntil { !DataStore.runningTest }
        DataStore.runningTest = false
        config.onMenuItemClick(item(activity, R.id.action_connection_url_test))
        settleUntil { DataStore.runningTest }
        settleUntil { !DataStore.runningTest }
        DataStore.runningTest = false

        DataStore.selectedProxy = ping.id
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        vpn.onStartCommand(Intent(), 0, 1)
        settleMore()
    }

    @Test(timeout = 180_000)
    fun groupsRoutesAndProfilePickers() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        val activity = openMain()
        val groupId = DataStore.currentGroupId()
        runBlocking { ProfileManager.createProfile(groupId, socks("listed", "10.1.1.1")) }
        SagerDatabase.groupDao.createGroup(ProxyGroup(name = "Moved"))
        val sub = ProxyGroup(name = "Userinfo", type = GroupType.SUBSCRIPTION).apply {
            subscription = SubscriptionBean().apply {
                initializeDefaultValues()
                link = "https://example.invalid/b12"
                subscriptionUserinfo = "upload=1; download=2; total=10; expire=1700000000"
                bytesUsed = 20
                bytesRemaining = 5
            }
        }
        sub.id = SagerDatabase.groupDao.createGroup(sub)
        activity.displayFragmentWithId(R.id.nav_group)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val group = activity.supportFragmentManager.fragments.filterIsInstance<GroupFragment>().first()
        runBlocking { group.groupAdapter.reload() }
        idleMain()
        settleUntil { group.groupAdapter.groupList.size >= 2 }
        if (group.groupAdapter.groupList.size >= 2) {
            group.groupAdapter.move(0, 1)
            group.groupAdapter.commitMove()
            idleMain()
        }
        val parent = group.groupListView
        val subIndex = group.groupAdapter.groupList.indexOfFirst { it.id == sub.id }
        if (subIndex >= 0) {
            val holder = group.groupAdapter.onCreateViewHolder(parent, 0)
            GroupUpdater.updating.add(sub.id)
            group.groupAdapter.onBindViewHolder(holder, subIndex)
            GroupUpdater.progress[sub.id] = GroupUpdater.Progress(4).apply { progress = 1 }
            group.groupAdapter.onBindViewHolder(holder, subIndex)
            GroupUpdater.updating.remove(sub.id)
            sub.subscription!!.bytesUsed = 0
            group.groupAdapter.onBindViewHolder(holder, subIndex)
            val typed = holder as GroupFragment.GroupHolder
            runCatching { typed.optionsButton.performClick() }
            typed.onMenuItemClick(item(activity, R.id.action_export_file))
            idleMain()
            val uri = Uri.parse("content://coverage/group-export.txt")
            shadowOf(app.contentResolver).registerOutputStream(uri, ByteArrayOutputStream())
            deliver(group, "exportProfiles", uri)
            settle()
            deliver(group, "exportProfiles", Uri.parse("content://coverage/group-export-missing"))
            settle()
        }
        val added = ProxyGroup(name = "Added")
        val addJob = GlobalScope.launch(Dispatchers.Main) { group.groupAdapter.groupAdd(added) }
        awaitJob(addJob)
        val removeId = group.groupAdapter.groupList.lastOrNull()?.id ?: sub.id
        val removeJob = GlobalScope.launch(Dispatchers.Main) {
            group.groupAdapter.groupRemoved(removeId)
        }
        awaitJob(removeJob)
        group.groupAdapter.undo(listOf(0 to ProxyGroup(name = "undo")))
        idleMain()

        repeat(2) {
            runBlocking { ProfileManager.createRule(RuleEntity(name = "b12-$it", enabled = true), false) }
        }
        activity.displayFragmentWithId(R.id.nav_route)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val route = activity.supportFragmentManager.fragments.filterIsInstance<RouteFragment>().first()
        runBlocking { route.ruleAdapter.reload() }
        idleMain()
        settleUntil { route.ruleAdapter.ruleList.size >= 2 }
        if (route.ruleAdapter.ruleList.size >= 2) {
            route.ruleAdapter.move(1, 2)
            route.ruleAdapter.commitMove()
            idleMain()
        }
        val ruleParent = route.ruleListView
        val doc = route.ruleAdapter.onCreateViewHolder(ruleParent, 0)
        route.ruleAdapter.onBindViewHolder(doc, 0)
        runCatching { doc.itemView.performClick() }
        val removed = route.ruleAdapter.ruleList.firstOrNull()
        if (removed != null) {
            val job = GlobalScope.launch(Dispatchers.Main) { route.ruleAdapter.onRemoved(removed.id) }
            awaitJob(job)
            route.ruleAdapter.undo(listOf(1 to removed))
            idleMain()
            val clearJob = GlobalScope.launch(Dispatchers.Main) { route.ruleAdapter.onCleared() }
            awaitJob(clearJob)
            route.ruleAdapter.commit(listOf(1 to removed))
            idleMain()
        }

        val profile = runBlocking { ProfileManager.createProfile(groupId, socks("picked", "10.2.2.2")) }
        val picked = ActivityResult(
            Activity.RESULT_OK,
            Intent().putExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, profile.id),
        )
        val groupSettings = Robolectric.buildActivity(GroupSettingsActivity::class.java, Intent()).setup().get()
        settleUntil {
            groupSettings.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        deliver(groupSettings, "selectProfileForAddFront", picked)
        settle()
        deliver(groupSettings, "selectProfileForAddLanding", picked)
        settle()
        groupSettings.finish()

        val routeSettings = Robolectric.buildActivity(
            RouteSettingsActivity::class.java,
            Intent().putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, 0L),
        ).setup().get()
        settleUntil {
            routeSettings.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        deliver(routeSettings, "selectProfileForAdd", picked)
        settle()
        deliver(routeSettings, "selectAppList", ActivityResult(Activity.RESULT_OK, null))
        settle()
        val editorPrefs = routeSettings.supportFragmentManager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
        editorPrefs.onOptionsItemSelected(item(routeSettings, R.id.action_delete))
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()
        routeSettings.finish()
    }

    @Test(timeout = 180_000)
    fun mainAboutSettingsAndBackup() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        installPlugin("moe.matsuri.exe.about", "about-plugin", "libabout.so", executable = true)
        PackageCache.reload()
        shadowOf(app.getSystemService(android.os.PowerManager::class.java)).setIgnoringBatteryOptimizations(
            app.packageName,
            false,
        )
        val activity = openMain()
        DataStore.serviceState = BaseService.State.Connected
        activity.binding.fab.performClick()
        idleMain()
        runCatching { activity.binding.stats.performClick() }
        idleMain()
        activity.applyHideFromRecentApps(true)
        activity.applyHideFromRecentApps(false)
        DataStore.hideFromRecentApps = true
        val resume = MainActivity::class.java.getDeclaredMethod("onResume")
        resume.isAccessible = true
        resume.invoke(activity)
        val profileId = runBlocking {
            ProfileManager.createProfile(DataStore.currentGroupId(), socks("sel", "10.3.3.3")).id
        }
        activity.cbSelectorUpdate(profileId)
        idleMain()
        runBlocking {
            activity.cbTrafficUpdate(TrafficDataBatch(arrayListOf(TrafficData(profileId, 3, 4))))
        }
        idleMain()
        activity.onPreferenceDataStoreChanged(DataStore.configurationStore, Key.SHOW_BOTTOM_BAR)
        activity.onPreferenceDataStoreChanged(DataStore.configurationStore, Key.PROXY_APPS)
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        activity.onPreferenceDataStoreChanged(DataStore.configurationStore, Key.SERVICE_MODE)
        idleMain()
        activity.binding.drawerLayout.open()
        activity.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        activity.binding.drawerLayout.close()
        activity.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        activity.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        activity.displayFragmentWithId(R.id.nav_group)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        activity.onBackPressedDispatcher.onBackPressed()
        idleMain()
        activity.onBackPressedDispatcher.onBackPressed()
        idleMain()
        runBlocking { activity.importSubscription(Uri.parse("sn://subscription?not-zlib")) }
        settle()
        runBlocking {
            activity.importSubscription(Uri.parse("sn://subscription?url=https://example.invalid/b12&name=Batch12"))
        }
        settleUntil {
            (ShadowDialog.getLatestDialog() as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE) != null
        }
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        runBlocking { activity.importProfile(Uri.parse("socks://127.0.0.1:1080#b12")) }
        settleUntil {
            (ShadowDialog.getLatestDialog() as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE) != null
        }
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()

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
        libcore.Libcore.httpBody = """{"name":"9.9.9-batch12","html_url":"https://example.invalid/rel"}"""
        about.checkUpdate(false)
        settleUntil {
            (ShadowDialog.getLatestDialog() as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE) != null
        }
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        finishStarted()
        about.checkUpdate(true)
        settle()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        libcore.Libcore.httpBody =
            """{"name":"${BuildConfig.VERSION_NAME}","html_url":"https://example.invalid/same"}"""
        about.checkUpdate(false)
        settle()
        libcore.Libcore.httpBody = "not-json"
        about.checkUpdate(false)
        settle()
        libcore.Libcore.httpBody = ""
        about.view?.findViewById<RecyclerView>(R.id.mal_recyclerview)?.let { list ->
            for (i in 0 until list.childCount) {
                runCatching { list.getChildAt(i).performClick() }
            }
        }
        deliver(
            about,
            "requestIgnoreBatteryOptimizations",
            ActivityResult(Activity.RESULT_CANCELED, null),
        )
        idleMain()

        activity.displayFragmentWithId(R.id.nav_settings)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val prefs = activity.supportFragmentManager.findFragmentById(R.id.settings) as SettingsPreferenceFragment
        val summary = SettingsPreferenceFragment.ListSummaryProvider(1)
        val bypass = prefs.findPreference<EditTextPreference>(Key.HTTP_PROXY_BYPASS)!!
        bypass.text = ""
        summary.provideSummary(bypass)
        bypass.text = "one.example"
        summary.provideSummary(bypass)
        bypass.text = "a.example\nb.example"
        summary.provideSummary(bypass)
        prefs.findPreference<Preference>(Key.RULES_PROVIDER)!!.callChangeListener("4")
        prefs.findPreference<Preference>(Key.RULES_PROVIDER)!!.callChangeListener("0")
        prefs.findPreference<Preference>(Key.ENABLE_HEV_TUN)!!.callChangeListener(true)
        prefs.findPreference<Preference>(Key.ENABLE_HEV_TUN)!!.callChangeListener(false)
        prefs.findPreference<Preference>(Key.ENABLE_CLASH_API)!!.callChangeListener(true)
        prefs.findPreference<Preference>(Key.HIDE_FROM_RECENT_APPS)!!.callChangeListener(true)
        DataStore.serviceState = BaseService.State.Connected
        prefs.findPreference<Preference>(Key.SERVICE_MODE)!!.callChangeListener("proxy")
        prefs.findPreference<Preference>("localProxySettings")!!.performClick()
        idleMain()
        val dialog = ShadowDialog.getLatestDialog()
        dialog?.findViewById<TextInputEditText>(R.id.proxyPort)?.setText("0")
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        prefs.findPreference<Preference>("localProxySettings")!!.performClick()
        idleMain()
        val again = ShadowDialog.getLatestDialog()
        again?.findViewById<TextInputEditText>(R.id.proxyPort)?.setText("2080")
        again?.findViewById<TextInputEditText>(R.id.proxyUsername)?.setText("user")
        again?.findViewById<TextInputEditText>(R.id.proxyPassword)?.setText("secret")
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        File(app.cacheDir, "neko.log").writeText("log")
        File(app.cacheDir, "junk.txt").writeText("x")
        File(app.cacheDir, "subdir").mkdirs()
        File(app.cacheDir, "subdir/a.txt").writeText("a")
        prefs.findPreference<Preference>(Key.CLEAR_CACHE)!!.performClick()
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()

        val backup = BackupFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(R.id.fragment_holder, backup, "backup12")
            .commitNow()
        idleMain()
        backup.requireView().findViewById<View>(R.id.action_export).performClick()
        settleMore()
        runCatching { backup.requireView().findViewById<View>(R.id.action_share).performClick() }
        settle()
        val exported = Uri.parse("content://coverage/backup12.json")
        shadowOf(app.contentResolver).registerOutputStream(exported, ByteArrayOutputStream())
        deliver(backup, "exportSettings", exported)
        settle()
        deliver(backup, "exportSettings", Uri.parse("content://coverage/backup12-missing"))
        settle()
        val flag = BackupFragment::class.java.getDeclaredField("isRestoreInProgress")
        flag.isAccessible = true
        flag.setBoolean(backup, true)
        activity.supportFragmentManager.beginTransaction().remove(backup).commitNow()

        runCatching {
            prefs.findPreference<Preference>(Key.NIGHT_THEME)!!.callChangeListener("1")
            prefs.findPreference<Preference>(Key.APP_LANGUAGE)!!.callChangeListener("en")
            prefs.findPreference<Preference>(Key.APP_THEME)!!.callChangeListener(Theme.TEAL)
        }
    }

    @Test(timeout = 120_000)
    fun pluginFailuresAndResolver() {
        withApplicationInfo(null) {
            runCatching { PluginManager.init("hysteria-plugin") }
        }
        val dir = File(app.filesDir, "native-lib-12")
        dir.mkdirs()
        app.applicationInfo.nativeLibraryDir = dir.absolutePath
        installPlugin(
            "moe.matsuri.exe.badso",
            "bad-plugin",
            "libmissing.so",
            executable = false,
            directory = dir,
            writeFile = false,
        )
        PackageCache.reload()
        runCatching { PluginManager.init("bad-plugin") }
        installPlugin("moe.matsuri.exe.badtype", "ignored", "libmissing.so", executable = false, booleanId = true)
        PackageCache.reload()
        runCatching { PluginManager.init("ignored") }

        SagerNet.underlyingNetwork = ShadowNetwork.newInstance(7)
        runCatching { LocalResolverImpl.networkHandle() }
        LocalResolverImpl.raw()
        val ctx = ExchangeContext()
        cycleDns { LocalResolverImpl.lookup(ctx, "ip4", "example.com") }
        cycleDns { LocalResolverImpl.lookup(ctx, "ip6", "example.com") }
        cycleDns { LocalResolverImpl.lookup(ctx, "ip", "example.com") }
        cycleDns { LocalResolverImpl.exchange(ctx, byteArrayOf(1, 2, 3)) }
    }

    @Test(timeout = 60_000)
    @Config(
        sdk = [28],
        application = CoverageApplication::class,
        instrumentedPackages = ["com.jakewharton.processphoenix"],
        shadows = [
            ShadowTunBuilder::class,
            ShadowResolvingNetwork::class,
            ShadowOsAddress::class,
            ShadowProcessPhoenix::class,
        ],
    )
    fun localResolverBeforeQ() {
        check(!LocalResolverImpl.raw())
        check(LocalResolverImpl.networkHandle() == 0L)
        val ctx = ExchangeContext()
        LocalResolverImpl.lookup(ctx, "ip", "localhost")
        awaitIo()
        LocalResolverImpl.lookup(ctx, "ip", " ")
        awaitIo()
        SagerNet.underlyingNetwork = ShadowNetwork.newInstance(3)
        LocalResolverImpl.lookup(ctx, "ip", "covered.example")
        awaitIo()
        LocalResolverImpl.lookup(ctx, "ip", " ")
        awaitIo()
        SagerNet.underlyingNetwork = null
    }

    private fun withApplicationInfo(replacement: ApplicationInfo?, block: () -> Unit) {
        val loaded = generateSequence<Class<*>>(app.javaClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField("mLoadedApk") }.getOrNull() }
            .firstOrNull() ?: return
        loaded.isAccessible = true
        val apk = loaded.get(app) ?: return
        val info = apk.javaClass.getDeclaredField("mApplicationInfo")
        info.isAccessible = true
        val previous = info.get(apk)
        info.set(apk, replacement)
        try {
            block()
        } finally {
            info.set(apk, previous)
        }
    }

    private fun installPlugin(
        packageName: String,
        pluginId: String,
        soName: String,
        executable: Boolean,
        booleanId: Boolean = false,
        directory: File? = null,
        writeFile: Boolean = true,
    ) {
        val dir = directory ?: File(app.filesDir, packageName).apply { mkdirs() }
        val so = File(dir, soName)
        if (writeFile) {
            so.writeText("x")
            so.setExecutable(executable, false)
        } else if (so.exists()) {
            so.delete()
        }
        val meta = Bundle()
        if (booleanId) {
            meta.putBoolean(Plugins.METADATA_KEY_ID, true)
        } else {
            meta.putString(Plugins.METADATA_KEY_ID, pluginId)
            meta.putString(Plugins.METADATA_KEY_EXECUTABLE_PATH, soName)
        }
        val provider = ProviderInfo()
        provider.authority = Plugins.AUTHORITIES_PREFIX_NEKO_EXE + packageName.substringAfterLast('.')
        provider.exported = true
        provider.packageName = packageName
        provider.metaData = meta
        provider.applicationInfo = ApplicationInfo()
        provider.applicationInfo.packageName = packageName
        provider.applicationInfo.nativeLibraryDir = dir.absolutePath
        val pkg = PackageInfo()
        pkg.packageName = packageName
        pkg.versionName = "1.2.3"
        pkg.providers = arrayOf(provider)
        pkg.applicationInfo = provider.applicationInfo
        shadowOf(app.packageManager).installPackage(pkg)
    }

    private fun attachGroupPage(
        activity: MainActivity,
        config: ConfigurationFragment,
    ): ConfigurationFragment.GroupFragment? {
        if (runCatching { config.adapter.groupList.isEmpty() }.getOrDefault(true)) return null
        val page = config.adapter.createFragment(0) as ConfigurationFragment.GroupFragment
        val host = FrameLayout(activity).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600)
        }
        (config.requireView() as ViewGroup).addView(host)
        config.childFragmentManager.beginTransaction().add(host.id, page, "b12page").commitNow()
        idleMain()
        return page
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
        return activity
    }

    private fun socks(name: String, address: String) = SOCKSBean().apply {
        initializeDefaultValues()
        this.name = name
        serverAddress = address
        serverPort = 1
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
            ShadowSystemClock.advanceBy(100, TimeUnit.MILLISECONDS)
            Thread.sleep(20)
            idleMain()
        }
    }

    private fun cycleDns(block: () -> Unit) {
        ShadowDnsResolver.reset()
        repeat(4) { block() }
    }

    private fun awaitIo() {
        repeat(8) {
            ShadowSystemClock.advanceBy(50, TimeUnit.MILLISECONDS)
            Thread.sleep(40)
            idleMain()
        }
    }
}
