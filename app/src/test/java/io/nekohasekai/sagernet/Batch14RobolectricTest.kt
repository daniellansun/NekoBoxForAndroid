package io.nekohasekai.sagernet

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Binder
import android.os.Looper
import android.os.RemoteException
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.ISagerNetServiceCallback
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.aidl.TrafficDataBatch
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.bg.ServiceNotification
import io.nekohasekai.sagernet.bg.ShadowDnsResolver
import io.nekohasekai.sagernet.bg.ShadowOsAddress
import io.nekohasekai.sagernet.bg.ShadowProcessPhoenix
import io.nekohasekai.sagernet.bg.ShadowResolvingNetwork
import io.nekohasekai.sagernet.bg.ShadowTunBuilder
import io.nekohasekai.sagernet.bg.proto.ProxyInstance
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.MemoryKvDao
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.database.preference.KeyValuePair
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.byteBuffer
import io.nekohasekai.sagernet.ktx.marshall
import io.nekohasekai.sagernet.ktx.readStringList
import io.nekohasekai.sagernet.ktx.readStringSet
import io.nekohasekai.sagernet.ktx.unmarshall
import io.nekohasekai.sagernet.ktx.writeStringList
import io.nekohasekai.sagernet.ui.AboutFragment
import io.nekohasekai.sagernet.ui.BackupFragment
import io.nekohasekai.sagernet.ui.ConfigurationFragment
import io.nekohasekai.sagernet.ui.GroupFragment
import io.nekohasekai.sagernet.ui.GroupSettingsActivity
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.QuickDisableShortcut
import io.nekohasekai.sagernet.ui.QuickEnableShortcut
import io.nekohasekai.sagernet.ui.RouteSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ConfigEditActivity
import io.nekohasekai.sagernet.ui.ProfileSelectActivity
import io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SocksSettingsActivity
import io.nekohasekai.sagernet.utils.DefaultNetworkListener
import io.nekohasekai.sagernet.widget.OutboundPreference
import io.nekohasekai.sagernet.widget.ReselectableSpinner
import io.nekohasekai.sagernet.widget.StatsBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import libcore.Libcore
import moe.matsuri.nb4a.ui.ExtendedKeyboard
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboCursor
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowSystemClock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.widget.AdapterView
import androidx.core.graphics.Insets
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
class Batch14RobolectricTest {
    private var http: TinyHttp? = null

    @After
    fun restoreOverrides() {
        runCatching { http?.close() }
        http = null
        DataStore.serviceState = BaseService.State.Idle
        DataStore.serviceMode = Key.MODE_VPN
        DataStore.selectedProxy = 0L
        DataStore.baseService = null
        DataStore.vpnService = null
        DataStore.runningTest = false
        DataStore.dirty = false
        DataStore.groupLayoutMode = 0
        DataStore.showGroupInNotification = false
        DataStore.webdavServer = null
        DataStore.editingId = 0L
        SagerConnection.restartingApp = false
        BaseService.Binder.restartProcessOverride = null
        PublicDatabase.kvPairDaoOverride = null
        Libcore.urlTestFailure = null
        Libcore.httpBody = ""
        ShadowProcessPhoenix.reset()
        GroupUpdater.updating.clear()
        GroupUpdater.progress.clear()
        runCatching { ShadowDialog.getLatestDialog()?.dismiss() }
    }

    @Test(timeout = 180_000)
    fun shortcutsKryoSpinnerAndConnection() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        kryoRoundTrip()
        reselectableSpinner()
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        bindVpn(vpn)
        DataStore.serviceMode = Key.MODE_VPN
        vpn.data.state = BaseService.State.Stopped
        shortcut(QuickEnableShortcut::class.java)
        vpn.data.state = BaseService.State.Connected
        shortcut(QuickEnableShortcut::class.java)
        vpn.data.state = BaseService.State.Connected
        shortcut(QuickDisableShortcut::class.java)
        vpn.data.state = BaseService.State.Stopped
        shortcut(QuickDisableShortcut::class.java)
        driveConnectionDefaults(vpn.data.binder)
        driveNetworkGaps()
        driveFastProcessExit()
    }

    @Test(timeout = 180_000)
    fun editorsServiceStatsAndBackup() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        bindVpn(vpn)
        val activity = openMain()
        val groupId = DataStore.currentGroupId()
        val profile = runBlocking { ProfileManager.createProfile(groupId, socks("b14", "10.14.0.1")) }
        driveSelectorReload(vpn, profile)
        driveLinkChange(vpn)
        driveNotification(vpn, profile)
        driveStats(activity)
        driveConfigEditor()
        driveProfileDialogs(activity, groupId, profile)
        driveGroupAndRouteDialogs(profile)
        driveBackup(activity)
        activity.finish()
    }

    @Test(timeout = 180_000)
    fun listsMenusAndAbout() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        bindVpn(vpn)
        val activity = openMain()
        driveMainExtras(activity, vpn)
        driveConfigurationExtras(activity)
        driveGroupList(activity)
        driveAbout(activity)
        activity.finish()
    }

    @Test(timeout = 180_000)
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
    fun notificationPermissionOnApi33() {
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        bindVpn(vpn)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idleMain()
        activity.finish()
    }

    @Test(timeout = 120_000)
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
    fun shortcutsBeforeReportApi() {
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        bindVpn(vpn)
        vpn.data.state = BaseService.State.Stopped
        shortcut(QuickEnableShortcut::class.java)
        shortcut(QuickDisableShortcut::class.java)
        val hidden = ServiceNotification(vpn, "old", "service-vpn", false)
        idleMain()
        hidden.destroy()
        val register = DefaultNetworkListener::class.java.getDeclaredMethod("register")
        register.isAccessible = true
        runCatching { register.invoke(DefaultNetworkListener) }
    }

    private fun kryoRoundTrip() {
        val raw = ByteArrayOutputStream()
        val output = raw.byteBuffer()
        output.writeStringList(listOf("a", "b"))
        output.writeStringList(emptyList())
        output.writeStringList(linkedSetOf("a", "b"))
        output.writeStringList(emptySet())
        output.flush()
        val input = ByteArrayInputStream(raw.toByteArray()).byteBuffer()
        check(input.readStringList() == listOf("a", "b"))
        check(input.readStringList().isEmpty())
        check(input.readStringSet() == linkedSetOf("a", "b"))
        check(input.readStringSet().isEmpty())
        val bytes = BatchNote("kryo").marshall()
        val parcel = bytes.unmarshall()
        check(parcel.readString() == "kryo")
        parcel.recycle()
        val restored = bytes.unmarshall { BatchNote(it) }
        check(restored.text == "kryo")
    }

    private fun reselectableSpinner() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().get()
        val spinner = ReselectableSpinner(host)
        spinner.adapter = ArrayAdapter(host, android.R.layout.simple_spinner_item, mutableListOf("one", "two"))
        host.setContentView(spinner)
        var closed = 0
        spinner.onPopupClosed = { closed++ }
        spinner.onWindowFocusChanged(false)
        spinner.onWindowFocusChanged(true)
        check(closed == 1)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {}
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinner.setSelection(0)
        spinner.setSelection(0, true)
        spinner.setSelection(1)
        spinner.setSelection(1, false)
        val empty = ReselectableSpinner(host)
        empty.adapter = ArrayAdapter(host, android.R.layout.simple_spinner_item, mutableListOf<String>())
        empty.onItemSelectedListener = spinner.onItemSelectedListener
        runCatching {
            empty.setSelection(-1)
            empty.setSelection(-1, true)
        }
        val plain = StatsBar(app)
        plain.allowShow = true
        plain.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
        )
        plain.layout(0, 0, 1080, 160)
        plain.syncMainControls(true, BaseService.State.Stopped, false, true)
        idleFor(200)
        plain.useExternalScrollDriver = true
        plain.useExternalScrollDriver = true
        plain.onListScrolled(0)
        plain.onListScrolled(40)
        plain.onListScrolled(40)
        plain.onListScrolled(-40)
        plain.onListScrolled(-40)
        host.finish()
    }

    private fun driveConnectionDefaults(binder: ISagerNetService) {
        val bare = object : SagerConnection.Callback {
            override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {}
            override fun onServiceConnected(service: ISagerNetService) {}
        }
        bare.cbSpeedUpdate(SpeedDisplayData())
        bare.cbSelectorUpdate(3L)
        bare.missingPlugin("profile", "plugin")
        runBlocking { bare.cbTrafficUpdate(TrafficDataBatch()) }
        bare.onServiceDisconnected()
        bare.onBinderDied()

        val connection = SagerConnection(SagerConnection.CONNECTION_ID_SHORTCUT, listenForDeath = true)
        val throwing = ThrowingService()
        connection.onServiceConnected(ComponentName(app, SagerVpnService::class.java), throwing.asBinder())
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_TILE)
        val registered = SagerConnection::class.java.getDeclaredField("callbackRegistered")
        registered.isAccessible = true
        registered.setBoolean(connection, true)
        connection.onServiceDisconnected(null)
        val raw = Binder()
        val binderField = SagerConnection::class.java.getDeclaredField("binder")
        binderField.isAccessible = true
        binderField.set(connection, raw)
        val active = SagerConnection::class.java.getDeclaredField("connectionActive")
        active.isAccessible = true
        active.setBoolean(connection, true)
        connection.disconnect(app)
        val live = SagerConnection(SagerConnection.CONNECTION_ID_SHORTCUT, listenForDeath = true)
        live.connect(app, bare)
        idleMain()
        SagerConnection.restartingApp = false
        live.binderDied()
        idleMain()
        live.disconnect(app)
        val idleBinder = BaseService.Binder()
        check(idleBinder.state == BaseService.State.Idle.ordinal)
        idleBinder.close()
        val remote = object : ISagerNetServiceCallback.Stub() {
            override fun stateChanged(state: Int, profileName: String?, msg: String?) {
                throw RemoteException("callback")
            }

            override fun missingPlugin(profileName: String?, pluginName: String?) {}
            override fun cbSpeedUpdate(stats: SpeedDisplayData?) {}
            override fun cbTrafficUpdate(stats: TrafficDataBatch?) {}
            override fun cbSelectorUpdate(id: Long) {}
        }
        val service = Robolectric.buildService(SagerVpnService::class.java).create().get()
        service.data.binder.registerCallback(remote, 3)
        runBlocking { service.data.binder.broadcast { it.stateChanged(1, "n", null) } }
        val callbacks = BaseService.Binder::class.java.getDeclaredField("callbacks")
        callbacks.isAccessible = true
        val list = callbacks.get(service.data.binder)
        val died = list.javaClass.methods.first { it.name == "onCallbackDied" && it.parameterTypes.size == 2 }
        died.isAccessible = true
        died.invoke(list, remote, 3)
        check(binder.state >= 0 || idleBinder.state == 0)
    }

    private fun driveNetworkGaps() {
        val callbackClass = Class.forName("io.nekohasekai.sagernet.utils.DefaultNetworkListener\$Callback")
        val callback = callbackClass.getDeclaredField("INSTANCE").apply { isAccessible = true }
            .get(null) as android.net.ConnectivityManager.NetworkCallback
        val network = ShadowNetwork.newInstance(14)
        runCatching {
            runBlocking { withTimeout(2_000) { DefaultNetworkListener.start("b14") { } } }
        }
        callback.onAvailable(network)
        idleMain()
        callback.onLost(network)
        idleMain()
        runCatching {
            runBlocking { withTimeout(2_000) { DefaultNetworkListener.stop("b14") } }
        }
        val cm = shadowOf(app.getSystemService(android.net.ConnectivityManager::class.java))
        cm.clearAllNetworks()
        cm.setActiveNetworkInfo(null)
        val fallback = DefaultNetworkListener::class.java.getDeclaredField("fallback")
        fallback.isAccessible = true
        fallback.set(DefaultNetworkListener, true)
        runCatching {
            runBlocking { withTimeout(2_000) { DefaultNetworkListener.get() } }
        }
        fallback.set(DefaultNetworkListener, false)
        val register = DefaultNetworkListener::class.java.getDeclaredMethod("register")
        register.isAccessible = true
        runCatching { register.invoke(DefaultNetworkListener) }
    }

    private fun driveFastProcessExit() {
        val pool = GuardedProcessPool { }
        runCatching { pool.start(listOf("/bin/true")) }
        idleFor(400)
        pool.close(GlobalScope)
        idleMain()
    }

    private fun driveSelectorReload(vpn: SagerVpnService, profile: ProxyEntity) {
        val selector = SagerDatabase.groupDao.createGroup(ProxyGroup(name = "Selector14", isSelector = true))
        val selected = runBlocking { ProfileManager.createProfile(selector, socks("sel", "10.14.0.2")) }
        val instance = ProxyInstance(selected, vpn)
        val ready = runCatching {
            runBlocking { instance.init() }
            instance.isInitialized()
        }.getOrDefault(false)
        if (!ready) return
        vpn.data.proxy = instance
        DataStore.selectedProxy = selected.id
        vpn.data.state = BaseService.State.Connected
        runCatching { vpn.reload() }
        idleMain()
        DataStore.selectedProxy = profile.id
        runCatching { vpn.canReloadSelector() }
        Libcore.urlTestFailure = IllegalStateException("url")
        runCatching { vpn.data.binder.urlTest() }
        Libcore.urlTestFailure = null
        vpn.data.proxy = null
    }

    private fun driveLinkChange(vpn: SagerVpnService) {
        val cm = shadowOf(app.getSystemService(android.net.ConnectivityManager::class.java))
        val network = ShadowNetwork.newInstance(21)
        val first = LinkProperties()
        first.interfaceName = "wlan0"
        cm.setLinkProperties(network, first)
        runCatching { runBlocking { vpn.preInit() } }
        val callbackClass = Class.forName("io.nekohasekai.sagernet.utils.DefaultNetworkListener\$Callback")
        val callback = callbackClass.getDeclaredField("INSTANCE").apply { isAccessible = true }
            .get(null) as android.net.ConnectivityManager.NetworkCallback
        callback.onAvailable(network)
        idleMain()
        val second = LinkProperties()
        second.interfaceName = "rmnet0"
        cm.setLinkProperties(network, second)
        DataStore.networkChangeResetConnections = true
        callback.onCapabilitiesChanged(network, NetworkCapabilities())
        idleMain()
        runCatching { runBlocking { withTimeout(2_000) { DefaultNetworkListener.stop(vpn) } } }
    }

    private fun driveNotification(vpn: SagerVpnService, profile: ProxyEntity) {
        DataStore.showGroupInNotification = true
        ServiceNotification.genTitle(profile)
        DataStore.showDirectSpeed = false
        val note = ServiceNotification(vpn, "b14", "service-vpn", true)
        idleMain()
        vpn.data.state = BaseService.State.Connected
        note.onReceive(app, Intent(Intent.ACTION_SCREEN_OFF))
        note.onReceive(app, Intent(Intent.ACTION_SCREEN_ON))
        vpn.data.state = BaseService.State.Stopped
        note.onReceive(app, Intent(Intent.ACTION_SCREEN_ON))
        runBlocking {
            note.postNotificationTitle("renamed")
            note.postNotificationWakeLockStatus(false)
        }
        idleMain()
        note.destroy()
    }

    private fun driveStats(activity: MainActivity) {
        val stats = activity.binding.stats
        stats.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
        )
        stats.layout(0, 0, 1080, 160)
        stats.allowShow = true
        stats.useExternalScrollDriver = true
        stats.syncMainControls(true, BaseService.State.Connected, true, false)
        stats.syncMainControls(true, BaseService.State.Connected, true, true)
        stats.syncMainControls(true, BaseService.State.Stopped, false, true)
        stats.syncMainControls(false, BaseService.State.Stopped, false, false)
        stats.onListScrolled(30)
        stats.onListScrolled(30)
        stats.onListScrolled(-30)
        stats.onListScrolled(-30)
        stats.changeState(BaseService.State.Connecting)
        stats.changeState(BaseService.State.Stopping)
        stats.changeState(BaseService.State.Stopped)
        stats.changeState(BaseService.State.Connected)
        stats.getBehavior().slideUp(stats)
        DataStore.serviceState = BaseService.State.Connected
        activity.connection.service = activity.connection.service
        stats.testConnection()
        idleFor(300)
        DataStore.connectionTestURL = "http://example.com/test"
        stats.testConnection()
        idleFor(300)
        DataStore.connectionTestURL = "https://example.com/test"
    }

    private fun driveConfigEditor() {
        DataStore.profileCacheStore.putString(Key.SERVER_CONFIG, "")
        val editor = Robolectric.buildActivity(ConfigEditActivity::class.java).setup().get()
        idleMain()
        editor.binding.editor.setTextContent("{")
        editor.binding.actionFormat.performClick()
        idleMain()
        runCatching { ShadowDialog.getLatestDialog()?.dismiss() }
        editor.binding.editor.setTextContent("")
        editor.formatText()
        editor.binding.editor.setTextContent("""{"a":1}""")
        editor.binding.actionFormat.performClick()
        editor.binding.actionUndo.performClick()
        editor.binding.actionRedo.performClick()
        editor.binding.actionTab.performClick()
        val keyboard = editor.findViewById<ExtendedKeyboard>(R.id.extended_keyboard)
        layoutList(keyboard)
        keyboard.getChildAt(0)?.performClick()
        val container = editor.findViewById<View>(R.id.keyboard_container)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 40))
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 0, 0, 8))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        ViewCompat.dispatchApplyWindowInsets(container, insets)
        ViewCompat.dispatchApplyWindowInsets(editor.binding.root, insets)
        val hidden = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 0, 0, 8))
            .setVisible(WindowInsetsCompat.Type.ime(), false)
            .build()
        ViewCompat.dispatchApplyWindowInsets(container, hidden)
        editor.onBackPressed()
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEUTRAL)
        editor.onOptionsItemSelected(item(editor, R.id.action_apply))
        settle()
        if (!editor.isFinishing) editor.finish()

        DataStore.configurationStore.putString("coverageKey", """{"b":2}""")
        val stored = Robolectric.buildActivity(
            ConfigEditActivity::class.java,
            Intent().putExtra("key", "coverageKey").putExtra("useConfigStore", "1"),
        ).setup().get()
        idleMain()
        stored.binding.editor.setTextContent("""{"b":3}""")
        stored.onSupportNavigateUp()
        stored.onBackPressed()
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        if (!stored.isFinishing) {
            stored.onBackPressed()
            idleMain()
            clickDialog(AlertDialog.BUTTON_POSITIVE)
            settle()
        }
        stored.finish()
    }

    private fun driveProfileDialogs(activity: MainActivity, groupId: Long, profile: ProxyEntity) {
        SagerDatabase.groupDao.createGroup(ProxyGroup(name = "OtherBasic14"))
        DataStore.selectedProxy = profile.id
        val editor = Robolectric.buildActivity(
            SocksSettingsActivity::class.java,
            Intent().putExtra(ProfileSettingsActivity.EXTRA_PROFILE_ID, profile.id),
        ).setup().get()
        settleUntil {
            editor.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        val prefs = editor.supportFragmentManager.findFragmentById(R.id.settings) as ProfileSettingsActivity.MyPreferenceFragmentCompat
        prefs.callbackCustom = { }
        prefs.callbackCustomOutbound = { }
        deliver(prefs, "resultCallbackCustom", ActivityResult(Activity.RESULT_OK, null))
        deliver(prefs, "resultCallbackCustomOutbound", ActivityResult(Activity.RESULT_OK, null))
        editor.run { prefs.displayPreferenceDialog(EditTextPreference(editor)) }
        DataStore.dirty = true
        editor.onBackPressed()
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()
        if (!editor.isFinishing) {
            DataStore.selectedProxy = profile.id
            runBlocking { editor.saveAndExit() }
        }
        if (!editor.isFinishing) {
            prefs.onOptionsItemSelected(item(editor, R.id.action_delete))
            idleMain()
            clickDialog(AlertDialog.BUTTON_POSITIVE)
            settle()
        }
        editor.finish()
        check(activity.hasWindowFocus() || groupId >= 0L)
    }

    private fun driveGroupAndRouteDialogs(profile: ProxyEntity) {
        val created = Robolectric.buildActivity(
            GroupSettingsActivity::class.java,
            Intent()
                .putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, 0L)
                .putExtra(GroupSettingsActivity.EXTRA_FROM_CLIPBOARD, true)
                .putExtra(GroupSettingsActivity.EXTRA_GROUP_SUBSCRIPTION_LINK, "https://example.com/b14")
                .putExtra(GroupSettingsActivity.EXTRA_GROUP_NAME, "Clip14"),
        ).setup().get()
        settleUntil {
            created.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        val prefs = created.supportFragmentManager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
        prefs.findPreference<Preference>(Key.GROUP_FRONT_PROXY)
            ?.callChangeListener(OutboundPreference.VALUE_SELECT_PROFILE)
        finishStarted()
        prefs.findPreference<Preference>(Key.GROUP_LANDING_PROXY)
            ?.callChangeListener(OutboundPreference.VALUE_SELECT_PROFILE)
        finishStarted()
        deliver(
            created,
            "selectProfileForAddFront",
            ActivityResult(Activity.RESULT_OK, Intent().putExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, profile.id)),
        )
        deliver(
            created,
            "selectProfileForAddFront",
            ActivityResult(Activity.RESULT_OK, Intent().putExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, 0L)),
        )
        deliver(
            created,
            "selectProfileForAddLanding",
            ActivityResult(Activity.RESULT_OK, Intent().putExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, profile.id)),
        )
        DataStore.frontProxyTmp = OutboundPreference.VALUE_SELECT_PROFILE.toInt()
        DataStore.landingProxyTmp = OutboundPreference.VALUE_SELECT_PROFILE.toInt()
        DataStore.frontProxy = profile.id
        DataStore.landingProxy = profile.id
        DataStore.groupType = io.nekohasekai.sagernet.GroupType.SUBSCRIPTION
        DataStore.subscriptionLink = "https://example.com/b14"
        DataStore.dirty = true
        runBlocking { created.saveAndExit() }
        settle()
        created.finish()

        val existing = SagerDatabase.groupDao.createGroup(
            ProxyGroup(name = "Keep14", type = io.nekohasekai.sagernet.GroupType.SUBSCRIPTION).apply {
                subscription = SubscriptionBean().apply {
                    link = "https://example.com/old"
                    subscriptionUserinfo = "upload=1"
                }
            },
        )
        val editor = Robolectric.buildActivity(
            GroupSettingsActivity::class.java,
            Intent().putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, existing),
        ).setup().get()
        settleUntil {
            editor.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        val groupPrefs = editor.supportFragmentManager.findFragmentById(R.id.settings) as? PreferenceFragmentCompat
        groupPrefs?.onOptionsItemSelected(item(editor, R.id.action_scan_qr_code))
        DataStore.subscriptionLink = "https://example.com/new"
        DataStore.groupType = io.nekohasekai.sagernet.GroupType.SUBSCRIPTION
        DataStore.dirty = true
        editor.onBackPressed()
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()
        if (!editor.isFinishing) {
            DataStore.editingId = 9_999_999L
            DataStore.dirty = true
            runBlocking { editor.saveAndExit() }
        }
        editor.finish()

        val missing = Robolectric.buildActivity(
            GroupSettingsActivity::class.java,
            Intent().putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, 8_888_888L),
        ).setup().get()
        settle()
        missing.finish()

        val rule = runBlocking {
            ProfileManager.createRule(RuleEntity(name = "b14", enabled = true, domains = "a.com"), false)
        }
        val route = Robolectric.buildActivity(
            RouteSettingsActivity::class.java,
            Intent().putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, rule.id),
        ).setup().get()
        settleUntil {
            route.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        val routePrefs = route.supportFragmentManager.findFragmentById(R.id.settings) as? PreferenceFragmentCompat
            ?: return
        DataStore.dirty = true
        route.onBackPressed()
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        if (!route.isFinishing) {
            routePrefs.onOptionsItemSelected(item(route, R.id.action_delete))
            idleMain()
            clickDialog(AlertDialog.BUTTON_POSITIVE)
            settle()
        }
        val goneRule = runBlocking {
            ProfileManager.createRule(RuleEntity(name = "gone", enabled = true), false)
        }
        val routeGone = Robolectric.buildActivity(
            RouteSettingsActivity::class.java,
            Intent().putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, goneRule.id),
        ).setup().get()
        settle()
        runBlocking { ProfileManager.deleteRule(goneRule.id) }
        DataStore.editingId = goneRule.id
        DataStore.dirty = true
        runBlocking { routeGone.saveAndExit() }
        routeGone.finish()
        route.finish()
    }

    private fun driveBackup(activity: MainActivity) {
        runBlocking { ProfileManager.createProfile(DataStore.currentGroupId(), socks("bak14", "10.14.4.1")) }
        runBlocking { ProfileManager.createRule(RuleEntity(name = "bak14", enabled = true), false) }
        SagerDatabase.groupDao.allGroups().forEach { it.subscription?.initializeDefaultValues() }
        val dao = MemoryKvDao()
        dao.put(KeyValuePair("coverage").put("value"))
        PublicDatabase.kvPairDaoOverride = dao
        val backup = BackupFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, backup, "backup14").commitNow()
        idleMain()
        val bytes = backup.javaClass.getDeclaredMethod(
            "doBackup",
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }.invoke(backup, true, true, true) as ByteArray
        val field = BackupFragment::class.java.getDeclaredField("backupData")
        field.isAccessible = true
        field.set(backup, bytes)
        val outUri = Uri.parse("content://coverage/backup14-out.json")
        shadowOf(app.contentResolver).registerOutputStream(outUri, ByteArrayOutputStream())
        deliver(backup, "exportSettings", outUri)
        awaitIo()
        val boom = Uri.parse("content://coverage/backup14-boom.json")
        shadowOf(app.contentResolver).registerOutputStream(boom, object : OutputStream() {
            override fun write(b: Int) = throw java.io.IOException("boom")
        })
        deliver(backup, "exportSettings", boom)
        awaitIo()
        backup.javaClass.declaredMethods.filter { it.name == "showMessage" }.forEach { method ->
            method.isAccessible = true
            runCatching {
                when (method.parameterTypes.size) {
                    1 -> if (method.parameterTypes[0] == String::class.java) {
                        method.invoke(backup, "hello")
                    } else {
                        method.invoke(backup, R.string.action_export_msg)
                    }
                    2 -> method.invoke(backup, R.string.backup_not_file, arrayOf<Any>("x"))
                }
            }
        }

        http = TinyHttp()
        DataStore.webdavUsername = "user"
        DataStore.webdavPassword = "pass"
        DataStore.webdavServer = http!!.base
        http!!.mode = 0
        backup.requireView().findViewById<View>(R.id.backup_to_webdav).performClick()
        awaitIo()
        settleMore()
        http!!.mode = 1
        backup.requireView().findViewById<View>(R.id.backup_to_webdav).performClick()
        awaitIo()
        settleMore()
        http!!.mode = 2
        backup.requireView().findViewById<View>(R.id.restore_from_webdav).performClick()
        awaitIo()
        settleMore()
        http!!.mode = 3
        backup.requireView().findViewById<View>(R.id.restore_from_webdav).performClick()
        awaitIo()
        settleMore()
        DataStore.webdavServer = "http://%"
        backup.requireView().findViewById<View>(R.id.backup_to_webdav).performClick()
        awaitIo()

        val named = Uri.parse("content://coverage/no-extension")
        val cursor = RoboCursor()
        cursor.setColumnNames(listOf(OpenableColumns.DISPLAY_NAME))
        cursor.setResults(arrayOf(arrayOf<Any>("from-cursor.json")))
        shadowOf(app.contentResolver).setCursor(named, cursor)
        shadowOf(app.contentResolver).registerInputStream(
            named,
            ByteArrayInputStream("""{"version":1,"profiles":["@@@@"]}""".toByteArray()),
        )
        val namedJob = GlobalScope.launch(Dispatchers.Main) { backup.startImport(named) }
        awaitJob(namedJob)
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settleMore()

        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { stream ->
            stream.putNextEntry(ZipEntry("readme.txt"))
            stream.write("hi".toByteArray())
            stream.closeEntry()
        }
        val zipUri = Uri.parse("content://coverage/backup14-txt.zip")
        shadowOf(app.contentResolver).registerInputStream(zipUri, ByteArrayInputStream(zip.toByteArray()))
        val zipJob = GlobalScope.launch(Dispatchers.Main) { backup.startImport(zipUri) }
        awaitJob(zipJob)
        idleMain()
    }

    private fun driveMainExtras(activity: MainActivity, vpn: SagerVpnService) {
        activity.refreshNavMenu(true)
        activity.refreshNavMenu(false)
        runCatching { activity.displayFragmentWithId(0) }
        val current = MainActivity::class.java.getDeclaredField("currentMainFragment")
        current.isAccessible = true
        current.set(activity, null)
        val post = MainActivity::class.java.getDeclaredMethod("onPostResume")
        post.isAccessible = true
        post.invoke(activity)
        runCatching {
            val task = ActivityManager.AppTask::class.java.declaredConstructors.first()
            task.isAccessible = true
            val manager = app.getSystemService(ActivityManager::class.java)
            shadowOf(manager).setAppTasks(listOf(task.newInstance() as ActivityManager.AppTask))
            activity.applyHideFromRecentApps(true)
            activity.applyHideFromRecentApps(false)
        }
        activity.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        activity.binding.drawerLayout.open()
        activity.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        activity.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        DataStore.serviceState = BaseService.State.Connected
        activity.connection.service = vpn.data.binder
        runCatching { activity.urlTest() }
        activity.onServiceConnected(ThrowingService())
        deliver(activity, "connect", true)
        deliver(activity, "connect", false)
        DataStore.serviceState = BaseService.State.Connected
        activity.onPreferenceDataStoreChanged(DataStore.configurationStore, Key.PROXY_APPS)
        idleMain()
        activity.findViewById<View>(com.google.android.material.R.id.snackbar_action)?.performClick()
        activity.onPreferenceDataStoreChanged(DataStore.configurationStore, Key.SERVICE_MODE)
        activity.onPreferenceDataStoreChanged(DataStore.configurationStore, Key.SHOW_BOTTOM_BAR)
        idleMain()
        runBlocking { activity.importProfile(Uri.parse("not a proxy")) }
        awaitIo()
        runBlocking {
            activity.importSubscription(Uri.parse("sn://subscription?url=https://example.com/sub&name=B14"))
        }
        awaitIo()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()
        runBlocking { activity.importSubscription(Uri.parse("sn://subscription?data=%%%%")) }
        awaitIo()
        val view = Robolectric.buildActivity(
            MainActivity::class.java,
            Intent(Intent.ACTION_VIEW, Uri.parse("not-a-profile")),
        ).setup().get()
        idleMain()
        view.finish()
    }

    private fun driveConfigurationExtras(activity: MainActivity) {
        activity.displayFragmentWithId(R.id.nav_configuration)
        idleMain()
        val config = activity.supportFragmentManager.fragments.filterIsInstance<ConfigurationFragment>().lastOrNull()
            ?: return
        settleUntil { runCatching { config.adapter.groupList.isNotEmpty() }.getOrDefault(false) }
        DataStore.serviceState = BaseService.State.Connected
        config.onMenuItemClick(item(activity, R.id.action_global_mode))
        idleFor(700)
        activity.findViewById<View>(com.google.android.material.R.id.snackbar_action)?.performClick()
        idleMain()
        runCatching { config.urlTest() }
        idleFor(200)
        runCatching { ShadowDialog.getLatestDialog()?.dismiss() }
        DataStore.runningTest = false
        val page = attachGroupPage(activity, config) ?: return
        val listed = page.adapter ?: return
        val shown = SagerDatabase.proxyDao.getByGroup(page.proxyGroup.id).ifEmpty {
            listOf(runBlocking { ProfileManager.createProfile(page.proxyGroup.id, socks("page", "10.14.1.1")) })
        }
        listed.reloadProfiles()
        idleMain()
        DataStore.groupLayoutMode = 1
        val setup = page.javaClass.getDeclaredMethod("setupLayoutManager")
        setup.isAccessible = true
        setup.invoke(page)
        val parent = page.configurationListView
        val holder = listed.onCreateViewHolder(parent, 0)
        holder.bind(shown.first())
        holder.itemView.performClick()
        idleMain()
        DataStore.serviceState = BaseService.State.Connected
        holder.itemView.performClick()
        idleMain()
        holder.view.findViewById<View>(R.id.double_column_menu)?.performClick()
        idleMain()
        holder.onMenuItemClick(item(activity, R.id.action_delete))
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        holder.onMenuItemClick(item(activity, R.id.action_share))
        idleMain()
        runCatching { ShadowDialog.getLatestDialog()?.dismiss() }
        val listeners = RecyclerView::class.java.getDeclaredField("mScrollListeners")
        listeners.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val scrollers = listeners.get(parent) as List<RecyclerView.OnScrollListener>
        scrollers.forEach { it.onScrollStateChanged(parent, RecyclerView.SCROLL_STATE_IDLE) }
        runCatching { page.checkOrderMenu() }
        DataStore.groupLayoutMode = 0
        val uri = Uri.parse("content://coverage/config-export-missing")
        DataStore.serverConfig = "{}"
        deliver(config, "exportConfig", uri)
        awaitIo()
    }

    private fun driveGroupList(activity: MainActivity) {
        activity.displayFragmentWithId(R.id.nav_group)
        idleMain()
        val group = activity.supportFragmentManager.fragments.filterIsInstance<GroupFragment>().lastOrNull() ?: return
        SagerDatabase.groupDao.createGroup(ProxyGroup(name = "MoveA14"))
        SagerDatabase.groupDao.createGroup(ProxyGroup(name = "MoveB14"))
        runBlocking { group.groupAdapter.reload() }
        idleMain()
        layoutList(group.groupListView)
        val callback = runCatching { touchCallback(group.groupListView) }.getOrNull() ?: return
        val holders = (0 until group.groupAdapter.itemCount).mapNotNull {
            group.groupListView.findViewHolderForAdapterPosition(it)
        }
        if (holders.size >= 2) {
            val from = holders.last()
            val to = holders[holders.size - 2]
            if (from.bindingAdapterPosition != to.bindingAdapterPosition) {
                callback.onMove(group.groupListView, from, to)
                callback.clearView(group.groupListView, from)
                idleMain()
            }
        }
        val basic = SagerDatabase.groupDao.allGroups().first { !it.ungrouped }
        val parent = group.groupListView
        val holder = group.groupAdapter.onCreateViewHolder(parent, 0)
        group.groupAdapter.onBindViewHolder(holder, 0)
        holder.bind(basic)
        holder.optionsButton.performClick()
        idleMain()
        val selected = GroupFragment::class.java.getDeclaredField("selectedGroup")
        selected.isAccessible = true
        selected.set(group, basic)
        val boom = Uri.parse("content://coverage/group-export-boom")
        shadowOf(app.contentResolver).registerOutputStream(boom, object : OutputStream() {
            override fun write(b: Int) = throw java.io.IOException("boom")
        })
        deliver(group, "exportProfiles", boom)
        awaitIo()
        group.onMenuItemClick(item(activity, R.id.action_update_all))
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()
    }

    private fun driveAbout(activity: MainActivity) {
        activity.displayFragmentWithId(R.id.nav_about)
        idleMain()
        settle()
        val about = activity.supportFragmentManager.fragments.filterIsInstance<AboutFragment>().lastOrNull() ?: return
        val content = about.childFragmentManager.fragments.filterIsInstance<AboutFragment.AboutContent>().lastOrNull()
            ?: return
        val recycler = content.view?.findViewById<RecyclerView>(R.id.mal_recyclerview)
        if (recycler != null) {
            layoutList(recycler)
            repeat(recycler.childCount) { index ->
                recycler.getChildAt(index)?.performClick()
            }
            idleMain()
        }
        Libcore.httpBody = """{"name":"coverage-9.9.9","html_url":"https://example.com/rel"}"""
        content.checkUpdate(false)
        awaitIo()
        settle()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        Libcore.httpBody = """{"name":"${BuildConfig.VERSION_NAME}","html_url":"https://example.com/same"}"""
        content.checkUpdate(true)
        awaitIo()
        Libcore.httpBody = "not-json"
        content.checkUpdate(false)
        awaitIo()
        Libcore.httpBody = ""
        deliver(content, "requestIgnoreBatteryOptimizations", ActivityResult(Activity.RESULT_OK, null))
        idleMain()
    }

    private fun shortcut(type: Class<out Activity>) {
        val controller = Robolectric.buildActivity(type).create()
        idleMain()
        runCatching { controller.pause().stop().destroy() }
    }

    private fun bindVpn(vpn: SagerVpnService) {
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, SagerVpnService::class.java),
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

    private fun socks(name: String, address: String) = SOCKSBean().apply {
        initializeDefaultValues()
        this.name = name
        serverAddress = address
        serverPort = 1
    }

    private fun attachGroupPage(
        activity: MainActivity,
        config: ConfigurationFragment,
    ): ConfigurationFragment.GroupFragment? {
        if (runCatching { config.adapter.groupList.isEmpty() }.getOrDefault(true)) return null
        val page = config.adapter.createFragment(0) as ConfigurationFragment.GroupFragment
        val host = FrameLayout(activity).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 800)
        }
        (config.requireView() as ViewGroup).addView(host)
        config.childFragmentManager.beginTransaction().add(host.id, page, "b14page").commitNow()
        idleMain()
        return page
    }

    private fun touchCallback(view: RecyclerView): ItemTouchHelper.SimpleCallback {
        val decorations = RecyclerView::class.java.getDeclaredField("mItemDecorations")
        decorations.isAccessible = true
        val helper = (decorations.get(view) as List<*>).filterIsInstance<ItemTouchHelper>().first()
        val callback = ItemTouchHelper::class.java.getDeclaredField("mCallback")
        callback.isAccessible = true
        return callback.get(helper) as ItemTouchHelper.SimpleCallback
    }

    private fun layoutList(view: RecyclerView) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1080, 1600)
        idleMain()
    }

    private fun item(context: Context, id: Int) =
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

    private fun settleMore() {
        repeat(8) {
            ShadowSystemClock.advanceBy(50, TimeUnit.MILLISECONDS)
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

    private fun awaitJob(job: Job) {
        val deadline = System.nanoTime() + 8_000_000_000L
        while (!job.isCompleted && System.nanoTime() < deadline) {
            ShadowSystemClock.advanceBy(100, TimeUnit.MILLISECONDS)
            Thread.sleep(20)
            idleMain()
        }
    }

    private fun awaitIo() {
        repeat(8) {
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

    private class BatchNote(val text: String) : android.os.Parcelable {
        constructor(parcel: android.os.Parcel) : this(parcel.readString() ?: "")

        override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
            parcel.writeString(text)
        }

        override fun describeContents() = 0
    }

    private class ThrowingService : ISagerNetService.Stub() {
        override fun getState(): Int = throw RemoteException("state")
        override fun getProfileName(): String = "idle"
        override fun registerCallback(cb: ISagerNetServiceCallback?, id: Int) {
            throw RemoteException("register")
        }

        override fun unregisterCallback(cb: ISagerNetServiceCallback?) {
            throw RemoteException("unregister")
        }

        override fun resetTraffic(profileIds: LongArray?) {}
        override fun urlTest(): Int = 0
    }

    private class TinyHttp : Closeable {
        private val socket = ServerSocket(0)
        val base = "http://127.0.0.1:${socket.localPort}"

        @Volatile
        var mode = 0

        private val thread = Thread {
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    client.soTimeout = 2000
                    handle(client)
                } catch (_: Exception) {
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        private fun handle(client: Socket) {
            val input = client.getInputStream()
            val request = readAsciiLine(input)
            var length = 0
            while (true) {
                val line = readAsciiLine(input) ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    length = line.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }
            var left = length
            while (left > 0) {
                val chunk = ByteArray(minOf(left, 4096))
                val read = input.read(chunk)
                if (read < 0) break
                left -= read
            }
            val method = request?.substringBefore(' ') ?: return
            val (code, body) = payload(method)
            val head = "HTTP/1.1 $code OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
            val out = client.getOutputStream()
            out.write(head.toByteArray())
            out.write(body)
            out.flush()
            client.close()
        }

        private fun payload(method: String): Pair<Int, ByteArray> {
            val listing = "<d:href>/Nekobox/nekobox_backup_1.0_20200101_010101.zip</d:href>".toByteArray()
            return when (mode) {
                0 -> if (method == "MKCOL") 500 to "mkcol".toByteArray() else 404 to "missing".toByteArray()
                1 -> if (method == "PUT") 500 to "put".toByteArray() else 207 to "<ok/>".toByteArray()
                2 -> if (method == "GET") 500 to "get".toByteArray() else 207 to listing
                else -> if (method == "GET") 200 to zipText() else 207 to listing
            }
        }

        private fun zipText(): ByteArray {
            val raw = ByteArrayOutputStream()
            ZipOutputStream(raw).use { stream ->
                stream.putNextEntry(ZipEntry("readme.txt"))
                stream.write("hi".toByteArray())
                stream.closeEntry()
            }
            return raw.toByteArray()
        }

        private fun readAsciiLine(input: InputStream): String? {
            val out = ByteArrayOutputStream()
            while (true) {
                val value = input.read()
                if (value < 0) return if (out.size() == 0) null else out.toString()
                if (value == '\n'.code) break
                if (value != '\r'.code) out.write(value)
            }
            return out.toString()
        }

        override fun close() {
            runCatching { socket.close() }
            thread.interrupt()
        }
    }
}
