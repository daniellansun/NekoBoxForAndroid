package io.nekohasekai.sagernet

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.ShadowDnsResolver
import io.nekohasekai.sagernet.bg.ShadowOsAddress
import io.nekohasekai.sagernet.bg.ShadowProcessPhoenix
import io.nekohasekai.sagernet.bg.ShadowResolvingNetwork
import io.nekohasekai.sagernet.bg.ShadowTunBuilder
import io.nekohasekai.sagernet.bg.proto.ProxyInstance
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.trojan_go.TrojanGoBean
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.forEachTry
import io.nekohasekai.sagernet.ktx.listenForPackageChanges
import io.nekohasekai.sagernet.ktx.parseNumericAddress
import io.nekohasekai.sagernet.ktx.scrollTo
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.ui.AboutFragment
import io.nekohasekai.sagernet.ui.ConfigurationFragment
import io.nekohasekai.sagernet.ui.LogcatFragment
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.QuickDisableShortcut
import io.nekohasekai.sagernet.ui.QuickEnableShortcut
import io.nekohasekai.sagernet.ui.RouteSettingsActivity
import io.nekohasekai.sagernet.ui.SettingsPreferenceFragment
import io.nekohasekai.sagernet.ui.StunActivity
import io.nekohasekai.sagernet.ui.WebviewFragment
import io.nekohasekai.sagernet.ui.profile.ChainSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.VMessSettingsActivity
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.widget.AutoCollapseTextView
import io.nekohasekai.sagernet.widget.LinkOrContentPreference
import io.nekohasekai.sagernet.widget.OutboundPreference
import io.nekohasekai.sagernet.widget.ServiceButton
import io.nekohasekai.sagernet.widget.StatsBar
import kotlinx.coroutines.runBlocking
import libcore.Libcore
import libcore.StunResult
import moe.matsuri.nb4a.plugin.Plugins
import moe.matsuri.nb4a.ui.ColorPickerPreference
import moe.matsuri.nb4a.ui.UrlTestPreference
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.shadows.ShadowVpnService
import java.io.File
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import android.view.LayoutInflater
import com.google.android.material.progressindicator.LinearProgressIndicator
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
class Batch15RobolectricTest {

    @After
    fun restoreOverrides() {
        DataStore.serviceState = BaseService.State.Idle
        DataStore.serviceMode = Key.MODE_VPN
        DataStore.selectedProxy = 0L
        DataStore.baseService = null
        DataStore.vpnService = null
        DataStore.runningTest = false
        DataStore.dirty = false
        DataStore.editingId = 0L
        DataStore.enableHevTun = false
        DataStore.connectionTestURL = "https://www.gstatic.com/generate_204"
        BaseService.Binder.restartProcessOverride = null
        PluginManager.initOverride = null
        Libcore.urlTestFailure = null
        Libcore.stunResult = null
        Libcore.httpBody = ""
        ShadowVpnService.reset()
        ShadowProcessPhoenix.reset()
        runCatching { ShadowDialog.getLatestDialog()?.dismiss() }
    }

    @Test(timeout = 180_000)
    fun widgetsShortcutsAndStun() {
        val activity = openMain()
        collapseAndPreferences(activity)
        shortcuts()
        stun()
        activity.finish()
    }

    @Test(timeout = 180_000)
    fun editorsRoutesAndChain() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        val groupId = DataStore.currentGroupId()
        val profile = runBlocking { ProfileManager.createProfile(groupId, socks("b15", "10.15.0.1")) }
        profile.tx = 20
        profile.rx = 10
        driveProfile(profile)
        driveVmess()
        driveRoute(profile)
        driveChain(groupId, profile)
    }

    @Test(timeout = 180_000)
    fun listsAboutAndStats() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        bindVpn(vpn)
        val activity = openMain()
        driveLists(activity)
        driveAbout(activity)
        driveStats(activity, vpn)
        activity.finish()
    }

    @Test(timeout = 180_000)
    fun serviceCatchesHostAndPluginErrors() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        DataStore.enableHevTun = false
        DataStore.serviceMode = Key.MODE_VPN
        ShadowVpnService.setPrepareResult(null)
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        val bean = TrojanGoBean().apply {
            initializeDefaultValues()
            serverAddress = "10.15.0.9"
            serverPort = 443
        }
        val profile = runBlocking { ProfileManager.createProfile(DataStore.currentGroupId(), bean) }
        DataStore.selectedProxy = profile.id
        startWith(vpn, UnknownHostException("host"))
        startWith(vpn, PluginManager.PluginNotFoundException("trojan-go-plugin"))
        startWith(vpn, coreproxyerror("go"))
        startWith(vpn, IllegalStateException("plain"))
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
    fun registerReceiverOnApi33() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        DataStore.enableHevTun = false
        DataStore.serviceMode = Key.MODE_VPN
        ShadowVpnService.setPrepareResult(null)
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        val profile = runBlocking {
            ProfileManager.createProfile(DataStore.currentGroupId(), socks("api33", "10.15.0.33"))
        }
        DataStore.selectedProxy = profile.id
        vpn.data.state = BaseService.State.Stopped
        runCatching { vpn.onStartCommand(Intent(), 0, 1) }
        idleMain()
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
        runCatching { "127.0.0.1".parseNumericAddress() }
        runCatching { "2001:db8::1".parseNumericAddress() }
        runCatching { null.parseNumericAddress() }
    }

    private fun collapseAndPreferences(activity: MainActivity) {
        val text = AutoCollapseTextView(activity)
        text.text = "shown"
        text.text = ""
        val focus = AutoCollapseTextView::class.java.getDeclaredMethod(
            "onFocusChanged",
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            android.graphics.Rect::class.java,
        )
        focus.isAccessible = true
        focus.invoke(text, true, View.FOCUS_DOWN, null)
        focus.invoke(text, false, View.FOCUS_DOWN, null)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1f, 1f, 0)
        text.onTouchEvent(down)
        down.recycle()
        runCatching { listOf("a", "b").forEachTry { if (it == "b") error("b") } }
        runCatching { listOf(1).forEachTry { error("once") } }
        val list = RecyclerView(activity)
        list.scrollTo(0, true)
        list.scrollTo(0, false)
        idleFor(350)
        activity.listenForPackageChanges(true) {}
        activity.sendBroadcast(Intent(Intent.ACTION_PACKAGE_ADDED).setData(Uri.parse("package:io.coverage")))
        idleMain()

        activity.displayFragmentWithId(R.id.nav_settings)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        activity.supportFragmentManager.executePendingTransactions()
        settleUntil {
            activity.supportFragmentManager.findFragmentById(R.id.settings) is SettingsPreferenceFragment
        }
        val settings = activity.supportFragmentManager.findFragmentById(R.id.settings) as? SettingsPreferenceFragment
            ?: error(
                "settings preferences missing: " +
                    activity.supportFragmentManager.fragments.joinToString { it.javaClass.simpleName },
            )
        val theme = settings.findPreference<ColorPickerPreference>(Key.APP_THEME)!!
        theme.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ -> true }
        val widget = LinearLayout(activity).apply { id = android.R.id.widget_frame }
        theme.onBindViewHolder(preferenceHolder(FrameLayout(activity).apply { addView(widget) }))
        theme.performClick()
        idleMain()
        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
        val grid = dialog?.window?.decorView?.let { findFirst(it, GridLayout::class.java) } as? GridLayout
        grid?.getChildAt(0)?.performClick()
        idleMain()

        val url = settings.findPreference<UrlTestPreference>("connectionTestURL")!!
        val urlRoot = LayoutInflater.from(activity).inflate(R.layout.layout_urltest_preference_dialog, null)
        val urlEdit = urlRoot.findViewById<EditText>(android.R.id.edit)
        bindEdit(url).onBindEditText(urlEdit)
        urlRoot.findViewById<EditText>(R.id.edit_concurrent).setText("")
        urlRoot.findViewById<EditText>(R.id.edit_timeout).setText("-1")
        url.callChangeListener("http://example.com")
        urlRoot.findViewById<EditText>(R.id.edit_concurrent).setText("4")
        urlRoot.findViewById<EditText>(R.id.edit_timeout).setText("1500")
        url.callChangeListener("https://example.com")

        val link = LinkOrContentPreference(activity)
        val linkRoot = LayoutInflater.from(activity).inflate(R.layout.layout_urltest_preference_dialog, null)
        val linkEdit = linkRoot.findViewById<EditText>(android.R.id.edit)
        bindEdit(link).onBindEditText(linkEdit)
        linkEdit.setText("")
        linkEdit.setText("content://coverage/a")
        linkEdit.setText("http://example.com/plain")
        linkEdit.setText("https://example.com/secure")
        linkEdit.setText("https://example.com/a\nb")
        linkEdit.setText("::::")

        val outbound = OutboundPreference(activity)
        outbound.key = "coverageOutbound"
        outbound.preferenceDataStore = DataStore.profileCacheStore
        outbound.value = "0"
        outbound.setValue("0")
        outbound.value = OutboundPreference.VALUE_SELECT_PROFILE
        DataStore.profileCacheStore.putLong(outbound.key + "Long", 0L)
        outbound.summary
        val stored = runBlocking { ProfileManager.createProfile(DataStore.currentGroupId(), socks("out", "10.15.0.2")) }
        DataStore.profileCacheStore.putLong(outbound.key + "Long", stored.id)
        outbound.summary
        val row = LayoutInflater.from(activity).inflate(R.layout.preference_dropdown_reselectable, null)
        outbound.onBindViewHolder(preferenceHolder(row))
        val spinner = row.findViewById<android.widget.Spinner>(R.id.spinner)
        spinner.onItemSelectedListener?.onItemSelected(spinner, null, 0, 0)
        idleMain()
        val opened = OutboundPreference::class.java.getDeclaredField("dropdownOpened")
        opened.isAccessible = true
        opened.setBoolean(outbound, true)
        spinner.onItemSelectedListener?.onItemSelected(spinner, null, 3, 0)
        opened.setBoolean(outbound, true)
        spinner.onItemSelectedListener?.onItemSelected(spinner, null, 0, 0)
        spinner.onItemSelectedListener?.onItemSelected(spinner, null, -1, 0)
        spinner.onItemSelectedListener?.onNothingSelected(spinner)
        val spinnerField = androidx.preference.DropDownPreference::class.java.getDeclaredField("mSpinner")
        spinnerField.isAccessible = true
        if (spinnerField.get(outbound) == null) spinnerField.set(outbound, spinner)
        outbound.performClick()
        runCatching {
            val button = ServiceButton(activity)
            button.initProgress(LinearProgressIndicator(activity))
            button.changeState(BaseService.State.Stopped, BaseService.State.Idle, false)
            button.changeState(BaseService.State.Connecting, BaseService.State.Stopped, true)
            button.changeState(BaseService.State.Connected, BaseService.State.Connecting, true)
            button.changeState(BaseService.State.Stopping, BaseService.State.Connected, true)
            button.onAnimationEnd(null, false, 1f, 0f)
            button.onAnimationEnd(null, true, 1f, 0f)
        }
    }

    private fun shortcuts() {
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        bindVpn(vpn)
        vpn.data.state = BaseService.State.Stopped
        val enable = Robolectric.buildActivity(QuickEnableShortcut::class.java).create()
        enable.get().stateChanged(BaseService.State.Stopped, null, null)
        idleMain()
        runCatching { enable.pause().stop().destroy() }
        val disable = Robolectric.buildActivity(QuickDisableShortcut::class.java).create()
        disable.get().stateChanged(BaseService.State.Connected, "n", null)
        idleMain()
        runCatching { disable.pause().stop().destroy() }
    }

    private fun stun() {
        val failed = Robolectric.buildActivity(StunActivity::class.java).setup().get()
        failed.findViewById<View>(R.id.stunTest).performClick()
        awaitIo()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        val cancelled = Robolectric.buildActivity(StunActivity::class.java).setup().get()
        cancelled.doTest()
        awaitIo()
        (ShadowDialog.getLatestDialog() as? AlertDialog)?.cancel()
        idleMain()
        Libcore.stunResult = StunResult().apply {
            success = true
            text = "full cone"
        }
        val ok = Robolectric.buildActivity(StunActivity::class.java).setup().get()
        ok.findViewById<View>(R.id.stunTest).performClick()
        awaitIo()
        Libcore.stunResult = null
        failed.finish()
        cancelled.finish()
        ok.finish()
    }

    private fun driveProfile(profile: ProxyEntity) {
        val unsaved = Robolectric.buildActivity(
            SocksSettingsActivity::class.java,
            Intent().putExtra(ProfileSettingsActivity.EXTRA_PROFILE_ID, profile.id),
        ).setup().get()
        settleUntil {
            unsaved.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        DataStore.dirty = true
        unsaved.onBackPressed()
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        runCatching { ShadowDialog.getLatestDialog()?.dismiss() }
        idleMain()
        unsaved.finish()

        val editor = Robolectric.buildActivity(
            SocksSettingsActivity::class.java,
            Intent().putExtra(ProfileSettingsActivity.EXTRA_PROFILE_ID, profile.id),
        ).setup().get()
        settleUntil {
            editor.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        editor.onSupportNavigateUp()
        val prefs = editor.supportFragmentManager.findFragmentById(R.id.settings) as ProfileSettingsActivity.MyPreferenceFragmentCompat
        val blank = EditTextPreference(editor)
        ProfileSettingsActivity.PasswordSummaryProvider.provideSummary(blank)
        blank.text = "secret"
        ProfileSettingsActivity.PasswordSummaryProvider.provideSummary(blank)
        prefs.onOptionsItemSelected(item(editor, R.id.action_delete))
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()
        editor.finish()

        val missing = Robolectric.buildActivity(
            SocksSettingsActivity::class.java,
            Intent().putExtra(ProfileSettingsActivity.EXTRA_PROFILE_ID, 9_999_991L),
        ).setup().get()
        runBlocking { missing.saveAndExit() }
        missing.finish()
    }

    private fun driveVmess() {
        val editor = Robolectric.buildActivity(VMessSettingsActivity::class.java).setup().get()
        settleUntil {
            editor.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        val prefs = editor.supportFragmentManager.findFragmentById(R.id.settings) as? PreferenceFragmentCompat
            ?: return
        listOf("tcp", "kcp", "http", "ws", "grpc", "httpupgrade", "xhttp").forEach { network ->
            prefs.findPreference<Preference>("type")?.callChangeListener(network)
        }
        prefs.findPreference<Preference>("security")?.callChangeListener("tls")
        prefs.findPreference<Preference>("security")?.callChangeListener("")
        prefs.findPreference<Preference>("muxMode")?.callChangeListener("0")
        prefs.findPreference<Preference>("muxMode")?.callChangeListener("1")
        prefs.findPreference<Preference>("muxBrutal")?.callChangeListener(true)
        prefs.findPreference<Preference>("muxBrutal")?.callChangeListener(false)
        editor.finish()
    }

    private fun driveRoute(profile: ProxyEntity) {
        val created = Robolectric.buildActivity(
            RouteSettingsActivity::class.java,
            Intent()
                .putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, 0L)
                .putExtra(RouteSettingsActivity.EXTRA_PACKAGE_NAME, "com.android.settings"),
        ).setup().get()
        settleUntil {
            created.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        created.run {
            RuleEntity(outbound = 0L).init()
            RuleEntity(outbound = -1L).init()
            RuleEntity(outbound = -2L).init()
            RuleEntity(outbound = profile.id).init()
            val entity = RuleEntity()
            DataStore.routeOutbound = 0
            entity.serialize()
            DataStore.routeOutbound = 1
            entity.serialize()
            DataStore.routeOutbound = 2
            entity.serialize()
            DataStore.routeOutbound = 3
            DataStore.routeOutboundRule = profile.id
            DataStore.routePackages = "\ncom.android.settings\n\n"
            entity.serialize()
        }
        created.outbound.callChangeListener(OutboundPreference.VALUE_SELECT_PROFILE)
        finishStarted()
        deliver(
            created,
            "selectProfileForAdd",
            ActivityResult(Activity.RESULT_OK, Intent().putExtra(io.nekohasekai.sagernet.ui.ProfileSelectActivity.EXTRA_PROFILE_ID, profile.id)),
        )
        awaitIo()
        deliver(
            created,
            "selectProfileForAdd",
            ActivityResult(Activity.RESULT_OK, Intent().putExtra(io.nekohasekai.sagernet.ui.ProfileSelectActivity.EXTRA_PROFILE_ID, 8_888_888L)),
        )
        awaitIo()
        created.apps.performClick()
        finishStarted()
        created.displayPreferenceDialog(EditTextPreference(created))
        val summary = EditTextPreference(created)
        RouteSettingsActivity.PasswordSummaryProvider.provideSummary(summary)
        summary.text = "pw"
        RouteSettingsActivity.PasswordSummaryProvider.provideSummary(summary)
        created.onPreferenceDataStoreChanged(DataStore.profileCacheStore, Key.PROFILE_DIRTY)
        created.onPreferenceDataStoreChanged(DataStore.profileCacheStore, Key.ROUTE_OUTBOUND)
        DataStore.dirty = true
        runBlocking { created.saveAndExit() }
        created.onSupportNavigateUp()
        created.finish()

        val rule = runBlocking { ProfileManager.createRule(RuleEntity(name = "b15", enabled = true), false) }
        val editor = Robolectric.buildActivity(
            RouteSettingsActivity::class.java,
            Intent().putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, rule.id),
        ).setup().get()
        settleUntil {
            editor.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        val prefs = editor.supportFragmentManager.findFragmentById(R.id.settings) as? PreferenceFragmentCompat
        DataStore.dirty = true
        editor.onBackPressed()
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()
        if (prefs != null && !editor.isFinishing) {
            prefs.onOptionsItemSelected(item(editor, R.id.action_delete))
            idleMain()
            clickDialog(AlertDialog.BUTTON_NEGATIVE)
        }
        editor.finish()
    }

    private fun driveChain(groupId: Long, profile: ProxyEntity) {
        val second = runBlocking { ProfileManager.createProfile(groupId, socks("b15b", "10.15.0.3")) }
        second.tx = 4
        second.rx = 6
        val chain = runBlocking {
            ProfileManager.createProfile(groupId, ChainBean().apply {
                initializeDefaultValues()
                name = "chain15"
                proxies = arrayListOf(profile.id, second.id)
            })
        }
        val nested = runBlocking {
            ProfileManager.createProfile(groupId, ChainBean().apply {
                initializeDefaultValues()
                proxies = arrayListOf(chain.id)
            })
        }
        val activity = Robolectric.buildActivity(
            ChainSettingsActivity::class.java,
            Intent().putExtra(ProfileSettingsActivity.EXTRA_PROFILE_ID, chain.id),
        ).setup().get()
        settleUntil { activity.configurationAdapter.itemCount >= 3 }
        val list = activity.configurationList
        val callback = touchCallback(list)
        val add = activity.configurationAdapter.onCreateViewHolder(list, 0)
        val holder = activity.configurationAdapter.onCreateViewHolder(list, 1)
        if (callback != null) {
            callback.getMovementFlags(list, add)
            callback.getMovementFlags(list, holder)
        }
        list.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
        )
        list.layout(0, 0, 400, 600)
        idleMain()
        val from = list.findViewHolderForAdapterPosition(2)
        val to = list.findViewHolderForAdapterPosition(1)
        if (callback != null && from != null && to != null) {
            callback.onMove(list, from, to)
            callback.onSwiped(from, ItemTouchHelper.START)
        } else if (activity.proxyList.size >= 2) {
            activity.configurationAdapter.move(2, 1)
            activity.configurationAdapter.remove(1)
        }
        activity.proxyList.firstOrNull()?.let { entity ->
            entity.tx = 8
            entity.rx = 9
            val bound = activity.configurationAdapter.onCreateViewHolder(list, 1)
            activity.configurationAdapter.onBindViewHolder(bound, 1)
            (bound as? ChainSettingsActivity.ProfileHolder)?.editButton?.performClick()
            finishStarted()
        }
        (activity.configurationAdapter.onCreateViewHolder(list, 0) as? ChainSettingsActivity.AddHolder)
            ?.bind()
        activity.configurationList.getChildAt(0)?.performClick()
        finishStarted()
        val loaded = runBlocking { ProfileManager.getProfile(chain.id) }
        val nestedEntity = runBlocking { ProfileManager.getProfile(nested.id) }
        if (loaded != null) {
            activity.testProfileContains(profile, profile)
            activity.testProfileContains(loaded, loaded)
            activity.testProfileContains(loaded, profile)
            if (nestedEntity != null) {
                activity.testProfileContains(nestedEntity, loaded)
                val outer = runBlocking {
                    ProfileManager.createProfile(groupId, ChainBean().apply {
                        initializeDefaultValues()
                        proxies = arrayListOf(nested.id)
                    })
                }
                activity.testProfileContains(outer, loaded)
                activity.testProfileAllowed(outer)
            }
            activity.testProfileAllowed(loaded)
            activity.testProfileAllowed(profile)
        }
        activity.replacing = 0
        deliver(
            activity,
            "selectProfileForAdd",
            ActivityResult(Activity.RESULT_OK, Intent().putExtra(io.nekohasekai.sagernet.ui.ProfileSelectActivity.EXTRA_PROFILE_ID, chain.id)),
        )
        awaitIo()
        runCatching { ShadowDialog.getLatestDialog()?.dismiss() }
        activity.replacing = 1
        deliver(
            activity,
            "selectProfileForAdd",
            ActivityResult(Activity.RESULT_OK, Intent().putExtra(io.nekohasekai.sagernet.ui.ProfileSelectActivity.EXTRA_PROFILE_ID, second.id)),
        )
        awaitIo()
        activity.replacing = 0
        deliver(
            activity,
            "selectProfileForAdd",
            ActivityResult(Activity.RESULT_OK, Intent().putExtra(io.nekohasekai.sagernet.ui.ProfileSelectActivity.EXTRA_PROFILE_ID, profile.id)),
        )
        awaitIo()
        activity.finish()
    }

    private fun driveLists(activity: MainActivity) {
        activity.displayFragmentWithId(R.id.nav_configuration)
        idleMain()
        val config = activity.supportFragmentManager.fragments.filterIsInstance<ConfigurationFragment>().lastOrNull()
        if (config != null) {
            SagerNet.trySetPrimaryClip("")
            config.onMenuItemClick(item(activity, R.id.action_import_clipboard))
            idleMain()
            SagerNet.trySetPrimaryClip("not a proxy")
            config.onMenuItemClick(item(activity, R.id.action_import_clipboard))
            awaitIo()
            config.onMenuItemClick(item(activity, R.id.action_new_socks))
            finishStarted()
            config.onMenuItemClick(item(activity, R.id.action_scan_qr_code))
            finishStarted()
            config.getCurrentGroupFragment()
        }
        File(app.cacheDir, "neko.log").writeText("INFO[ start\nERROR[ bad\nWARN[ care\nplain\n")
        activity.displayFragmentWithId(R.id.nav_logcat)
        idleMain()
        val log = activity.supportFragmentManager.fragments.filterIsInstance<LogcatFragment>().lastOrNull()
        if (log != null) {
            log.onMenuItemClick(item(activity, R.id.action_refresh))
            idleFor(200)
            log.onMenuItemClick(item(activity, R.id.action_clear_logcat))
            awaitIo()
            log.onMenuItemClick(item(activity, R.id.action_send_logcat))
            awaitIo()
            finishStarted()
        }
        runCatching {
            activity.displayFragmentWithId(R.id.nav_traffic)
            idleMain()
            val web = activity.supportFragmentManager.fragments.filterIsInstance<WebviewFragment>().lastOrNull()
                ?: return@runCatching
            web.onMenuItemClick(item(activity, R.id.action_set_url))
            idleMain()
            val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
            val edit = dialog?.window?.decorView?.let { findFirst(it, EditText::class.java) } as? EditText
            edit?.setText("http://127.0.0.1:9090")
            clickDialog(AlertDialog.BUTTON_POSITIVE)
            web.onMenuItemClick(item(activity, R.id.close))
        }
    }

    private fun driveAbout(activity: MainActivity) {
        activity.displayFragmentWithId(R.id.nav_about)
        settle()
        val about = activity.supportFragmentManager.fragments.filterIsInstance<AboutFragment>().lastOrNull() ?: return
        val content = about.childFragmentManager.fragments.filterIsInstance<AboutFragment.AboutContent>().lastOrNull()
            ?: return
        aboutList(content, activity)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, false)
        val empty = PackageInfo().apply {
            packageName = "io.coverage.empty"
            versionName = "0"
            providers = emptyArray()
        }
        val plugin = PackageInfo().apply {
            packageName = "io.coverage.plugin"
            versionName = "1.2"
            providers = arrayOf(ProviderInfo().apply {
                packageName = packageName
                metaData = Bundle().apply { putString(Plugins.METADATA_KEY_ID, "trojan-go") }
            })
        }
        PackageCache.installedPluginPackages = mapOf(empty.packageName to empty, plugin.packageName to plugin)
        val cards = aboutList(content, activity).cards
        for (card in cards) {
            for (entry in card.items) {
                val action = entry as? MaterialAboutActionItem ?: continue
                runCatching { action.onClickAction?.onClick() }
                finishStarted()
                idleMain()
            }
        }
        Libcore.httpBody = """{"name":"coverage-9.9.9","html_url":"https://example.com/rel"}"""
        content.checkUpdate(false)
        awaitIo()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        deliver(content, "requestIgnoreBatteryOptimizations", ActivityResult(Activity.RESULT_OK, null))
        idleMain()
        Libcore.httpBody = ""
    }

    private fun driveStats(activity: MainActivity, vpn: SagerVpnService) {
        val stats = activity.binding.stats
        stats.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
        )
        stats.layout(0, 0, 1080, 160)
        stats.allowShow = true
        stats.useExternalScrollDriver = true
        stats.syncMainControls(true, BaseService.State.Connected, true, false)
        stats.onListScrolled(0)
        stats.onListScrolled(3)
        stats.onListScrolled(20)
        stats.onListScrolled(20)
        stats.onListScrolled(-20)
        stats.useExternalScrollDriver = false
        val parent = stats.parent as? CoordinatorLayout ?: CoordinatorLayout(activity)
        runCatching {
            stats.getBehavior().onStartNestedScroll(
                parent, stats, stats, stats, View.SCROLL_AXIS_VERTICAL, 0,
            )
        }
        stats.alpha = 0f
        stats.layout(0, 0, 1080, 160)
        stats.syncMainControls(true, BaseService.State.Stopped, false, true)
        ShadowSystemClock.advanceBy(200, TimeUnit.MILLISECONDS)
        idleFor(250)

        val profile = runBlocking { ProfileManager.createProfile(DataStore.currentGroupId(), socks("stat", "10.15.0.4")) }
        val instance = ProxyInstance(profile, vpn)
        runCatching { runBlocking { instance.init() } }
        if (instance.isInitialized()) vpn.data.proxy = instance
        DataStore.serviceState = BaseService.State.Connected
        activity.connection.service = vpn.data.binder
        DataStore.connectionTestURL = "https://example.com/test"
        stats.changeState(BaseService.State.Connected)
        stats.testConnection()
        awaitIo()
        DataStore.connectionTestURL = "http://example.com/test"
        stats.testConnection()
        awaitIo()
        Libcore.urlTestFailure = IllegalStateException("url")
        stats.testConnection()
        awaitIo()
        Libcore.urlTestFailure = null
        vpn.data.proxy = null

        val bar = StatsBar(app)
        bar.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
        )
        bar.layout(0, 0, 1080, 160)
        bar.allowShow = true
        bar.syncMainControls(true, BaseService.State.Stopped, false, true)
        bar.layout(0, 0, 0, 0)
        idleMain()
        val shown = StatsBar(app)
        shown.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
        )
        shown.layout(0, 0, 1080, 160)
        shown.allowShow = true
        shown.syncMainControls(true, BaseService.State.Stopped, false, true)
        shown.syncMainControls(true, BaseService.State.Connected, true, false)
        idleMain()
    }

    private fun startWith(vpn: SagerVpnService, failure: Throwable) {
        vpn.data.state = BaseService.State.Stopped
        PluginManager.initOverride = { throw failure }
        runCatching { vpn.onStartCommand(Intent(), 0, 1) }
        awaitStopped(vpn)
        PluginManager.initOverride = null
    }

    private fun awaitStopped(vpn: SagerVpnService) {
        val deadline = System.nanoTime() + 8_000_000_000L
        while (vpn.data.state != BaseService.State.Stopped && System.nanoTime() < deadline) {
            ShadowSystemClock.advanceBy(50, TimeUnit.MILLISECONDS)
            Thread.sleep(20)
            idleMain()
        }
        idleMain()
    }

    private fun preferenceHolder(view: View): PreferenceViewHolder {
        val ctor = PreferenceViewHolder::class.java.getDeclaredConstructor(View::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(view) as PreferenceViewHolder
    }

    private fun bindEdit(preference: EditTextPreference): EditTextPreference.OnBindEditTextListener {
        val method = EditTextPreference::class.java.getDeclaredMethod("getOnBindEditTextListener")
        method.isAccessible = true
        return method.invoke(preference) as EditTextPreference.OnBindEditTextListener
    }

    private fun aboutList(content: AboutFragment.AboutContent, activity: Activity) =
        AboutFragment.AboutContent::class.java
            .getDeclaredMethod("getMaterialAboutList", android.content.Context::class.java)
            .apply { isAccessible = true }
            .invoke(content, activity) as com.danielstone.materialaboutlibrary.model.MaterialAboutList

    private fun touchCallback(view: RecyclerView): ItemTouchHelper.Callback? {
        val decorations = RecyclerView::class.java.getDeclaredField("mItemDecorations")
        decorations.isAccessible = true
        val helper = (decorations.get(view) as List<*>).filterIsInstance<ItemTouchHelper>().firstOrNull()
            ?: return null
        val callback = ItemTouchHelper::class.java.getDeclaredField("mCallback")
        callback.isAccessible = true
        return callback.get(helper) as ItemTouchHelper.Callback
    }

    private fun findFirst(root: View, type: Class<out View>): View? {
        if (type.isInstance(root)) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findFirst(root.getChildAt(index), type)?.let { return it }
            }
        }
        return null
    }

    private fun socks(name: String, address: String) = SOCKSBean().apply {
        initializeDefaultValues()
        this.name = name
        serverAddress = address
        serverPort = 1
    }

    private fun bindVpn(vpn: SagerVpnService) {
        shadowOf(app).setComponentNameAndServiceForBindService(
            android.content.ComponentName(app, SagerVpnService::class.java),
            vpn.onBind(Intent(Action.SERVICE)),
        )
    }

    private fun openMain(): MainActivity {
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        bindVpn(vpn)
        DataStore.serviceMode = Key.MODE_VPN
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idleMain()
        return activity
    }

    private fun item(context: android.content.Context, id: Int) =
        androidx.appcompat.view.menu.MenuBuilder(context).add(0, id, 0, "item")

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
        try {
            shadowOf(Looper.getMainLooper()).idle()
        } catch (e: IllegalStateException) {
            val message = e.message.orEmpty()
            if (!message.contains("synchronization barrier") && !message.contains("Already resumed")) {
                throw e
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

    private fun awaitIo() {
        repeat(10) {
            ShadowSystemClock.advanceBy(40, TimeUnit.MILLISECONDS)
            Thread.sleep(15)
            idleMain()
        }
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

    private class coreproxyerror(message: String) : RuntimeException(message)
}
