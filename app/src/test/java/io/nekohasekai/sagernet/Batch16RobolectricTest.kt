package io.nekohasekai.sagernet

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.ISagerNetServiceCallback
import io.nekohasekai.sagernet.bg.BaseService
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
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.KeyValuePair
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.boolean
import io.nekohasekai.sagernet.ktx.int
import io.nekohasekai.sagernet.ktx.long
import io.nekohasekai.sagernet.ktx.string
import io.nekohasekai.sagernet.ktx.stringSet
import io.nekohasekai.sagernet.ktx.stringToInt
import io.nekohasekai.sagernet.ktx.stringToIntIfExists
import io.nekohasekai.sagernet.ktx.stringToLong
import io.nekohasekai.sagernet.ui.AboutFragment
import io.nekohasekai.sagernet.ui.ConfigurationFragment
import io.nekohasekai.sagernet.ui.GroupSettingsActivity
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.RouteSettingsActivity
import io.nekohasekai.sagernet.ui.VpnRequestActivity
import io.nekohasekai.sagernet.ui.profile.ChainSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SocksSettingsActivity
import io.nekohasekai.sagernet.utils.CrashHandler
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.widget.OutboundPreference
import io.nekohasekai.sagernet.widget.StatsBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import libcore.Libcore
import moe.matsuri.nb4a.plugin.Plugins
import moe.matsuri.nb4a.utils.NGUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboCursor
import org.robolectric.shadows.ShadowBuild
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.shadows.ShadowVpnService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.os.RemoteException
import androidx.appcompat.view.ContextThemeWrapper
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
class Batch16RobolectricTest {

    private var mainController: ActivityController<MainActivity>? = null

    @Before
    fun drainStaleMain() {
        idleMain()
    }

    @After
    fun restoreOverrides() {
        closeMain()
        DataStore.serviceState = BaseService.State.Idle
        DataStore.serviceMode = Key.MODE_VPN
        DataStore.selectedProxy = 0L
        DataStore.baseService = null
        DataStore.vpnService = null
        DataStore.runningTest = false
        DataStore.dirty = false
        DataStore.editingId = 0L
        DataStore.connectionTestConcurrent = 5
        BaseService.Binder.restartProcessOverride = null
        Libcore.httpBody = ""
        PublicDatabase.kvPairDaoOverride = null
        ShadowVpnService.reset()
        ShadowProcessPhoenix.reset()
        runCatching { shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false) }
        runCatching { ShadowDialog.getLatestDialog()?.dismiss() }
        runCatching { Runtime.getRuntime().exec(arrayOf("pkill", "-f", "trap '' TERM")).waitFor() }
    }

    @Test(timeout = 180_000)
    fun stableGuardsAndGlobalMode() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        fastGuardExit()
        runningGuardCleanup()
        val activity = openMain()
        val config = configuration(activity)
        if (config != null) {
            clickGlobalModeReload(activity, config)
            val profile = runBlocking {
                ProfileManager.createProfile(DataStore.currentGroupId(), socks("b16-traffic", "127.0.0.1", 1))
            }
            profile.tx = 4
            activity.connection.service = ThrowingReset()
            config.onMenuItemClick(item(activity, R.id.action_clear_traffic_statistics))
            Thread.sleep(200)
            idleMain()
            activity.connection.service = null
        }
        closeMain()
    }

    @Test(timeout = 180_000)
    fun profileRouteGroupAndChain() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        val groupId = DataStore.currentGroupId()
        val profile = runBlocking { ProfileManager.createProfile(groupId, socks("b16", "10.16.0.1", 1)) }
        idleMain()
        unsavedPositive(profile)
        routeArms(profile)
        groupArms(profile)
        chainContains(groupId)
    }

    @Test(timeout = 180_000)
    fun configurationPingImportAndAbout() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        val activity = openMain()
        val config = configuration(activity)
        if (config != null) {
            importZipAndFailure(config)
            dialogStatuses(config)
            pingBoth(config)
        }
        aboutClicks(activity)
        closeMain()
    }

    @Test(timeout = 180_000)
    fun statsCrashPrefsAndVpn() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        val activity = openMain()
        statsHide(activity)
        closeMain()
        crashReport()
        preferenceProxies()
        ngUtil()
        vpnContract()
        keyguardVpn(29)
    }

    @Test(timeout = 120_000)
    @Config(
        sdk = [33],
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
    fun vpnRequestOnApi33() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        keyguardVpn(33)
    }

    private fun fastGuardExit() {
        runCatching {
            val pool = GuardedProcessPool { }
            pool.start(listOf("/bin/true"))
            repeat(40) {
                Thread.sleep(20)
                idleMain()
            }
        }
    }

    private fun runningGuardCleanup() {
        runCatching {
            val pool = GuardedProcessPool { }
            pool.start(listOf("/bin/sh", "-c", "trap '' TERM; while true; do sleep 30; done"))
            idleMain()
            val scope = CoroutineScope(Dispatchers.Main.immediate)
            pool.close(scope)
            idleFor(1_500)
            repeat(8) {
                Thread.sleep(40)
                idleMain()
            }
            scope.cancel()
        }
    }

    private fun clickGlobalModeReload(activity: MainActivity, config: ConfigurationFragment) {
        DataStore.serviceState = BaseService.State.Connected
        val global = item(activity, R.id.action_global_mode)
        global.isCheckable = true
        global.isChecked = false
        config.onMenuItemClick(global)
        val actionId = com.google.android.material.R.id.snackbar_action
        poll(times = 30, pauseMs = 100) {
            val action = activity.findViewById<View>(actionId)
            if (action != null) {
                action.performClick()
                true
            } else {
                false
            }
        }
        Thread.sleep(300)
        idleMain()
        DataStore.serviceState = BaseService.State.Idle
    }

    private fun unsavedPositive(profile: ProxyEntity) {
        val editor = Robolectric.buildActivity(
            SocksSettingsActivity::class.java,
            Intent().putExtra(ProfileSettingsActivity.EXTRA_PROFILE_ID, profile.id),
        ).setup().get()
        settleUntil {
            editor.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        val prefs = editor.supportFragmentManager.findFragmentById(R.id.settings) as? PreferenceFragmentCompat
        prefs?.onOptionsItemSelected(item(editor, 0))
        DataStore.dirty = true
        editor.onBackPressed()
        idleMain()
        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        repeat(15) {
            Thread.sleep(40)
            idleMain()
        }
        runCatching { ShadowDialog.getLatestDialog()?.dismiss() }
        if (!editor.isFinishing) {
            prefs?.onOptionsItemSelected(item(editor, R.id.action_delete))
            idleMain()
            (ShadowDialog.getLatestDialog() as? AlertDialog)
                ?.getButton(AlertDialog.BUTTON_NEGATIVE)
                ?.performClick()
            idleMain()
        }
        editor.finish()
    }

    private fun routeArms(profile: ProxyEntity) {
        val rule = runBlocking { ProfileManager.createRule(RuleEntity(name = "b16", enabled = true), false) }
        idleMain()
        val editor = Robolectric.buildActivity(
            RouteSettingsActivity::class.java,
            Intent().putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, rule.id),
        ).setup().get()
        settleUntil {
            editor.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        DataStore.routeOutboundRule = profile.id
        if (DataStore.routeOutboundRule == profile.id) {
            editor.outbound.callChangeListener(OutboundPreference.VALUE_SELECT_PROFILE)
            finishStarted()
        }
        editor.onOptionsItemSelected(item(editor, 0))
        val prefs = editor.supportFragmentManager.findFragmentById(R.id.settings) as? PreferenceFragmentCompat
        prefs?.onOptionsItemSelected(item(editor, R.id.action_apply))
        settle()
        prefs?.onOptionsItemSelected(item(editor, R.id.action_delete))
        idleMain()
        (ShadowDialog.getLatestDialog() as? AlertDialog)
            ?.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.performClick()
        settle()
        if (!editor.isFinishing) {
            DataStore.editingId = 9_999_993L
            DataStore.dirty = true
            runBlocking { editor.saveAndExit() }
        }
        editor.finish()

        val back = Robolectric.buildActivity(
            RouteSettingsActivity::class.java,
            Intent().putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, rule.id),
        ).setup().get()
        settleUntil {
            back.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        DataStore.dirty = false
        back.onBackPressed()
        back.finish()
    }

    private fun groupArms(profile: ProxyEntity) {
        val group = runBlocking { GroupManager.createGroup(ProxyGroup(name = "b16")) }
        idleMain()
        val editor = Robolectric.buildActivity(
            GroupSettingsActivity::class.java,
            Intent().putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, group.id),
        ).setup().get()
        settleUntil {
            editor.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        val prefs = editor.supportFragmentManager.findFragmentById(R.id.settings) as? PreferenceFragmentCompat
        DataStore.frontProxy = profile.id
        DataStore.landingProxy = profile.id
        prefs?.findPreference<Preference>(Key.GROUP_FRONT_PROXY)?.callChangeListener("3")
        finishStarted()
        prefs?.findPreference<Preference>(Key.GROUP_LANDING_PROXY)?.callChangeListener("3")
        finishStarted()
        deliver(
            editor,
            "selectProfileForAddLanding",
            ActivityResult(Activity.RESULT_OK, Intent().putExtra(io.nekohasekai.sagernet.ui.ProfileSelectActivity.EXTRA_PROFILE_ID, 8_888_881L)),
        )
        settle()
        editor.onOptionsItemSelected(item(editor, 0))
        DataStore.dirty = false
        editor.onSupportNavigateUp()
        prefs?.onOptionsItemSelected(item(editor, R.id.action_delete))
        idleMain()
        (ShadowDialog.getLatestDialog() as? AlertDialog)
            ?.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.performClick()
        settle()
        if (!editor.isFinishing) {
            DataStore.editingId = 9_999_994L
            DataStore.dirty = true
            runBlocking { editor.saveAndExit() }
        }
        editor.finish()

        val fresh = Robolectric.buildActivity(GroupSettingsActivity::class.java).setup().get()
        settleUntil {
            fresh.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        DataStore.dirty = false
        fresh.onBackPressed()
        val freshPrefs = fresh.supportFragmentManager.findFragmentById(R.id.settings) as? PreferenceFragmentCompat
        freshPrefs?.onOptionsItemSelected(item(fresh, R.id.action_delete))
        fresh.finish()
    }

    private fun chainContains(groupId: Long) {
        val nestedBean = ChainBean().apply { initializeDefaultValues() }
        val nested = runBlocking { ProfileManager.createProfile(groupId, nestedBean) }
        val outerBean = ChainBean().apply {
            initializeDefaultValues()
            proxies = arrayListOf(nested.id)
        }
        val outer = runBlocking { ProfileManager.createProfile(groupId, outerBean) }
        val target = runBlocking {
            ProfileManager.createProfile(groupId, ChainBean().apply { initializeDefaultValues() })
        }
        if (outer.type != ProxyEntity.TYPE_CHAIN || target.type != ProxyEntity.TYPE_CHAIN) return
        idleMain()
        val chain = Robolectric.buildActivity(ChainSettingsActivity::class.java).setup().get()
        chain.testProfileContains(outer, target)
        chain.finish()
    }

    private fun importZipAndFailure(config: ConfigurationFragment) {
        val uri = Uri.parse("content://coverage/b16-nodes.zip")
        val cursor = RoboCursor()
        cursor.setColumnNames(listOf(OpenableColumns.DISPLAY_NAME))
        cursor.setResults(arrayOf(arrayOf<Any>("nodes.zip")))
        shadowOf(app.contentResolver).setCursor(uri, cursor)
        shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(zipBytes("socks5://127.0.0.1:1080#zip16")))
        deliver(config, "importFile", uri)
        Thread.sleep(200)
        idleMain()
        val missing = Uri.parse("content://coverage/b16-missing.txt")
        deliver(config, "importFile", missing)
        Thread.sleep(150)
        idleMain()
    }

    private fun dialogStatuses(config: ConfigurationFragment) {
        val profile = runBlocking {
            ProfileManager.createProfile(DataStore.currentGroupId(), socks("b16-status", "127.0.0.1", 9))
        }
        val dialog = config.TestDialog()
        for (status in intArrayOf(-1, 0, 1, 2, 3)) {
            profile.status = status
            profile.ping = 12
            profile.error = if (status == 3) "failed: ECONNREFUSED" else "down"
            dialog.update(profile)
            idleMain()
        }
    }

    private fun pingBoth(config: ConfigurationFragment) {
        val server = ServerSocket(0)
        try {
            runBlocking {
                ProfileManager.createProfile(
                    DataStore.currentGroupId(),
                    socks("b16-open", "127.0.0.1", server.localPort),
                )
                ProfileManager.createProfile(
                    DataStore.currentGroupId(),
                    socks("b16-closed", "127.0.0.1", 1),
                )
            }
            DataStore.connectionTestConcurrent = 1
            DataStore.runningTest = false
            config.pingTest(false)
            poll(times = 20, pauseMs = 50) { false }
            clickDialog(AlertDialog.BUTTON_NEGATIVE)
            settleUntil { !DataStore.runningTest }
            DataStore.runningTest = false
            config.pingTest(true)
            settle()
            clickDialog(AlertDialog.BUTTON_NEGATIVE)
            settleUntil { !DataStore.runningTest }
        } finally {
            runCatching { server.close() }
            DataStore.runningTest = false
        }
    }

    private fun aboutClicks(activity: MainActivity) {
        activity.displayFragmentWithId(R.id.nav_about)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val about = activity.supportFragmentManager.fragments.filterIsInstance<AboutFragment>().lastOrNull() ?: return
        val content = about.childFragmentManager.fragments.filterIsInstance<AboutFragment.AboutContent>().lastOrNull()
            ?: return
        shadowOf(app.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, false)
        val previous = runCatching { PackageCache.installedPluginPackages }.getOrNull()
        val empty = PackageInfo().apply {
            packageName = "io.coverage.b16.empty"
            versionName = "0"
            providers = emptyArray()
        }
        val plugin = PackageInfo().apply {
            packageName = "io.nekohasekai.sagernet.plugin.b16"
            versionName = "1"
            providers = arrayOf(ProviderInfo().apply {
                packageName = packageName
                metaData = Bundle().apply { putString(Plugins.METADATA_KEY_ID, "trojan-go") }
            })
        }
        PackageCache.installedPluginPackages = mapOf(empty.packageName to empty, plugin.packageName to plugin)
        val list = AboutFragment.AboutContent::class.java
            .getDeclaredMethod("getMaterialAboutList", android.content.Context::class.java)
            .apply { isAccessible = true }
            .invoke(content, activity) as com.danielstone.materialaboutlibrary.model.MaterialAboutList
        for (card in list.cards) {
            for (entry in card.items) {
                val action = entry as? MaterialAboutActionItem ?: continue
                val click = action.onClickAction ?: reflectedClick(action)
                runCatching { click?.onClick() }
                finishStarted()
                idleMain()
            }
        }
        val recycler = content.view?.findViewById<RecyclerView>(R.id.mal_recyclerview)
        if (recycler != null) {
            recycler.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            )
            recycler.layout(0, 0, 1080, 1920)
            idleMain()
            for (index in 0 until recycler.childCount) {
                recycler.getChildAt(index).performClick()
                idleMain()
            }
        }
        Libcore.httpBody = """{"name":"coverage-9.9.9","html_url":"https://example.com/rel"}"""
        content.checkUpdate(false)
        poll(times = 25, pauseMs = 80) {
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
        deliver(content, "requestIgnoreBatteryOptimizations", ActivityResult(Activity.RESULT_OK, null))
        idleMain()
        if (previous != null) PackageCache.installedPluginPackages = previous
        Libcore.httpBody = ""
    }

    private fun reflectedClick(action: MaterialAboutActionItem): com.danielstone.materialaboutlibrary.items.MaterialAboutItemOnClickAction? {
        val field = MaterialAboutActionItem::class.java.getDeclaredField("onClickAction")
        field.isAccessible = true
        return field.get(action) as? com.danielstone.materialaboutlibrary.items.MaterialAboutItemOnClickAction
    }

    private fun statsHide(activity: MainActivity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        runCatching {
            val shown = StatsBar(activity)
            content.addView(shown)
            shown.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
            )
            shown.layout(0, 0, 1080, 160)
            shown.syncMainControls(true, BaseService.State.Stopped, false, true)
            shown.changeState(BaseService.State.Connected)
            idleFor(250)
        }
        runCatching {
            val hidden = StatsBar(activity)
            content.addView(hidden)
            hidden.layout(0, 0, 1080, 160)
            hidden.syncMainControls(true, BaseService.State.Stopped, false, true)
            hidden.layout(0, 0, 0, 0)
            idleFor(250)
        }
        runCatching {
            val plain = StatsBar(ContextThemeWrapper(app, R.style.Theme_SagerNet))
            content.addView(plain)
            plain.layout(0, 0, 200, 48)
            plain.alpha = 0f
            plain.layout(0, 0, 200, 48)
            plain.syncMainControls(true, BaseService.State.Stopped, false, true)
            plain.changeState(BaseService.State.Connected)
            idleMain()
            val collapsed = StatsBar(ContextThemeWrapper(app, R.style.Theme_SagerNet))
            content.addView(collapsed)
            collapsed.layout(0, 0, 200, 48)
            collapsed.syncMainControls(true, BaseService.State.Stopped, false, true)
            collapsed.layout(0, 0, 0, 0)
            idleMain()
        }
    }

    private fun crashReport() {
        CrashHandler.formatThrowable(RuntimeException(""))
        CrashHandler.formatThrowable(IllegalStateException("b16", RuntimeException("cause")))
        val codename = android.os.Build.VERSION.CODENAME
        ShadowBuild.setVersionCodename("UpsideDownCake")
        val previous = PublicDatabase.kvPairDaoOverride
        PublicDatabase.kvPairDaoOverride = object : KeyValuePair.Dao {
            override fun all(): List<KeyValuePair> = throw IllegalStateException("dao")
            override fun get(key: String): KeyValuePair? = null
            override fun put(value: KeyValuePair): Long = 0
            override fun delete(key: String): Int = 0
            override fun reset(): Int = 0
            override fun insert(list: List<KeyValuePair>) {}
        }
        try {
            CrashHandler.buildReportHeader()
            CrashHandler.uncaughtException(Thread.currentThread(), RuntimeException("b16"))
        } finally {
            PublicDatabase.kvPairDaoOverride = previous
            ShadowBuild.setVersionCodename(codename)
        }
    }

    private fun preferenceProxies() {
        val holder = PrefProbe(DataStore.profileCacheStore)
        holder.s = "b16"
        holder.s
        holder.b = true
        holder.b
        holder.i = 3
        holder.i
        holder.ss = setOf("a")
        holder.ss
        holder.si = 2
        holder.si
        holder.sie = 0
        holder.sie = 4
        holder.sie
        holder.l = 8L
        holder.l
        holder.sl = 9L
        holder.sl
    }

    private fun ngUtil() {
        NGUtil.getEditable("b16")
        NGUtil.arrayFind(arrayOf("a", "b"), "b")
        NGUtil.arrayFind(arrayOf("a"), "z")
        NGUtil.parseInt("4")
        NGUtil.parseInt("nope")
        NGUtil.setClipboard(app, "b16-clip")
        NGUtil.getClipboard(app)
        NGUtil.decode("YQ==")
        NGUtil.decode("%%%")
        NGUtil.decode("YQ===")
        NGUtil.encode("a b")
        NGUtil.isIpAddress("")
        NGUtil.isIpAddress("10.0.0.1/24")
        NGUtil.isIpAddress("::ffff:192.168.0.1")
        NGUtil.isIpAddress("[::ffff:192.168.0.1]:80")
        NGUtil.isIpAddress("10.0.0.1:80")
        NGUtil.isIpAddress("2001:db8::1")
        NGUtil.isIpAddress("[2001:db8::1]:8080")
        NGUtil.isPureIpAddress("10.0.0.1")
        NGUtil.isIpv4Address("10.0.0.1")
        NGUtil.isIpv6Address("2001:db8::1")
        NGUtil.getUuid()
        NGUtil.urlDecode("a%20b")
        NGUtil.urlDecode("%")
        NGUtil.urlEncode("a b")
        NGUtil.packagePath(app)
        runCatching { NGUtil.openUri(app, "https://example.com/b16") }
        runCatching { NGUtil.readTextFromAssets(app, "missing-b16.txt") }
        val core = NGUtil::class.java.getDeclaredMethod("isCoreDNSAddress", String::class.java)
        core.isAccessible = true
        core.invoke(NGUtil, "https://dns")
        core.invoke(NGUtil, "tcp://dns")
        core.invoke(NGUtil, "quic://dns")
        core.invoke(NGUtil, "1.1.1.1")
    }

    private fun vpnContract() {
        DataStore.serviceMode = Key.MODE_VPN
        ShadowVpnService.setPrepareResult(Intent("coverage"))
        val contract = VpnRequestActivity.StartService()
        contract.getSynchronousResult(app, null)
        runCatching { contract.createIntent(app, null) }
        contract.parseResult(Activity.RESULT_CANCELED, Intent("denied"))
        contract.parseResult(Activity.RESULT_OK, null)
        ShadowVpnService.setPrepareResult(null)
    }

    private fun keyguardVpn(sdk: Int) {
        DataStore.serviceMode = Key.MODE_VPN
        ShadowVpnService.setPrepareResult(null)
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        val controller = Robolectric.buildActivity(VpnRequestActivity::class.java).create()
        val activity = controller.get()
        activity.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        idleMain()
        runCatching { controller.pause().stop().destroy() }
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        if (sdk < 0) return
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

    private fun item(context: android.content.Context, id: Int) = MenuBuilder(context).add(0, id, 0, "item")

    private fun clickDialog(which: Int) {
        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog ?: return
        dialog.getButton(which)?.performClick()
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
                    message.contains("computing a layout")
                if (!known) throw e
            }
        }
    }

    private fun idleFor(millis: Long) {
        runCatching { ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS) }
        idleMain()
    }

    private fun settle() {
        repeat(6) {
            Thread.sleep(20)
            idleMain()
        }
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

    private fun zipBytes(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("node.txt"))
            zip.write(text.toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
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

    private class PrefProbe(store: PreferenceDataStore) {
        var s by store.string("b16_s")
        var b by store.boolean("b16_b")
        var i by store.int("b16_i")
        var ss by store.stringSet("b16_ss")
        var si by store.stringToInt("b16_si")
        var sie by store.stringToIntIfExists("b16_sie")
        var l by store.long("b16_l")
        var sl by store.stringToLong("b16_sl")
    }

    private class ThrowingReset : ISagerNetService.Stub() {
        override fun getState(): Int = 0
        override fun getProfileName(): String = ""
        override fun registerCallback(cb: ISagerNetServiceCallback?, id: Int) {}
        override fun unregisterCallback(cb: ISagerNetServiceCallback?) {}
        override fun resetTraffic(profileIds: LongArray?) {
            throw RemoteException("reset")
        }
        override fun urlTest(): Int = 0
    }
}
