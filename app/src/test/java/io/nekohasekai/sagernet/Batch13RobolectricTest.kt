package io.nekohasekai.sagernet

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.content.FileProvider
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.ISagerNetServiceCallback
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.aidl.TrafficDataBatch
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.bg.ProxyService
import io.nekohasekai.sagernet.bg.SagerConnection
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
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.fmt.PluginEntry
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.group.GroupInterfaceAdapter
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ui.ConfigurationFragment
import io.nekohasekai.sagernet.ui.GroupFragment
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.RouteFragment
import io.nekohasekai.sagernet.ui.RouteSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SocksSettingsActivity
import io.nekohasekai.sagernet.utils.DefaultNetworkListener
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import libcore.ExchangeContext
import moe.matsuri.nb4a.NativeInterface
import moe.matsuri.nb4a.net.LocalResolverImpl
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowSystemClock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
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
        ShadowDnsResolver::class,
    ],
)
class Batch13RobolectricTest {

    @After
    fun restoreOverrides() {
        DataStore.serviceState = BaseService.State.Idle
        DataStore.serviceMode = Key.MODE_VPN
        DataStore.selectedProxy = 0L
        DataStore.baseService = null
        DataStore.vpnService = null
        DataStore.runningTest = false
        DataStore.dirty = false
        DataStore.rulesProvider = 0
        DataStore.wakeResetConnections = false
        DataStore.acquireWakeLock = false
        DataStore.profileCardStyle = 0
        DataStore.groupLayoutMode = 0
        DataStore.alwaysShowAddress = false
        DataStore.confirmProfileDelete = true
        DataStore.enableHevTun = false
        DataStore.bypassLan = false
        DataStore.proxyApps = false
        SagerConnection.restartingApp = false
        GuardedProcessPool.pidReader = null
        BaseService.Binder.restartProcessOverride = null
        PublicDatabase.kvPairDaoOverride = null
        ShadowProcessPhoenix.reset()
        ShadowDnsResolver.reset()
        ExchangeContext.successFailure = null
        ExchangeContext.errnoFailure = null
        SagerNet.underlyingNetwork = null
        GroupUpdater.updating.clear()
        GroupUpdater.progress.clear()
        runCatching { ShadowDialog.getLatestDialog()?.dismiss() }
        runCatching {
            val cache = FileProvider::class.java.getDeclaredField("sCache")
            cache.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (cache.get(null) as MutableMap<Any, Any>).clear()
        }
    }

    @Test(timeout = 180_000)
    fun shortcutsNativeNetworkAndService() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        val proxyService = Robolectric.buildService(ProxyService::class.java).create().get()
        DataStore.serviceMode = Key.MODE_VPN
        bindVpn(vpn)
        vpn.data.state = BaseService.State.Connected
        DataStore.selectedProxy = 7L
        toggle(Intent.ACTION_CREATE_SHORTCUT, -1L)
        toggle(Intent.ACTION_MAIN, 7L)
        toggle(Intent.ACTION_MAIN, 8L)
        toggle(Intent.ACTION_MAIN, -1L)
        vpn.data.state = BaseService.State.Stopping
        toggle(Intent.ACTION_MAIN, 7L)

        DataStore.serviceMode = Key.MODE_PROXY
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, ProxyService::class.java),
            proxyService.onBind(Intent(Action.SERVICE)),
        )
        proxyService.data.state = BaseService.State.Stopped
        val groupId = DataStore.currentGroupId()
        val profile = runBlocking { ProfileManager.createProfile(groupId, socks("svc", "10.13.0.1")) }
        DataStore.selectedProxy = profile.id
        toggle(Intent.ACTION_MAIN, profile.id)

        runCatching { SagerConnection.serviceClass }
        DataStore.serviceMode = "not-a-mode"
        runCatching { SagerConnection.serviceClass }
        DataStore.serviceMode = Key.MODE_VPN
        bindVpn(vpn)

        driveConnection(vpn.data.binder)
        driveNetwork()
        driveResolver()
        driveNative(vpn, profile)
        driveBaseService(vpn, proxyService, profile)
        driveGuardedPool()
    }

    @Test(timeout = 180_000)
    fun listsEditorsAndGroupCallbacks() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        val activity = openMain()
        val groupId = DataStore.currentGroupId()
        SagerDatabase.groupDao.createGroup(ProxyGroup(name = "MoveTarget"))
        val profile = runBlocking { ProfileManager.createProfile(groupId, socks("holder", "10.13.1.1")) }
        profile.tx = 40
        profile.rx = 50
        profile.status = 3
        profile.error = "dial tcp: refused"
        SagerDatabase.proxyDao.updateProxy(profile)
        val longBean = socks("long-name", "very-long-hostname.example.invalid")
        val longProfile = runBlocking { ProfileManager.createProfile(groupId, longBean) }
        activity.displayFragmentWithId(R.id.nav_configuration)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val config = activity.supportFragmentManager.fragments
            .filterIsInstance<ConfigurationFragment>()
            .last()
        settleUntil { runCatching { config.adapter.groupList.isNotEmpty() }.getOrDefault(false) }
        val ungrouped = config.adapter.groupList.firstOrNull { it.ungrouped }
            ?: SagerDatabase.groupDao.allGroups().first { it.ungrouped }
        runBlocking { config.adapter.onRemoved(ungrouped.id, profile.id) }
        idleMain()
        SagerDatabase.proxyDao.deleteByGroup(ungrouped.id)
        runBlocking { config.adapter.onRemoved(ungrouped.id, profile.id) }
        idleMain()
        runBlocking { config.adapter.onRemoved(-1L, profile.id) }

        val page = attachGroupPage(activity, config)
        if (page != null) {
            val listed = page.adapter
            if (listed != null) {
                val profiles = SagerDatabase.proxyDao.getByGroup(page.proxyGroup.id)
                if (profiles.isEmpty()) {
                    runBlocking { ProfileManager.createProfile(page.proxyGroup.id, socks("again", "10.13.1.2")) }
                }
                val shown = SagerDatabase.proxyDao.getByGroup(page.proxyGroup.id)
                listed.reloadProfiles()
                idleMain()
                listed.configurationList.clear()
                shown.forEach { listed.configurationList[it.id] = it }
                listed.configurationIdList.clear()
                listed.configurationIdList.addAll(shown.map { it.id })
                val parent = page.configurationListView
                val holder = listed.onCreateViewHolder(parent, 0)
                listed.onBindViewHolder(holder, 0)
                DataStore.alwaysShowAddress = true
                DataStore.profileCardStyle = 1
                val errored = shown.first().apply {
                    status = 3
                    error = "dial tcp: refused"
                    tx = 9
                    rx = 8
                }
                holder.bind(errored)
                holder.itemView.performClick()
                holder.profileStatus.performClick()
                idleMain()
                runCatching { ShadowDialog.getLatestDialog()?.dismiss() }
                DataStore.serviceState = BaseService.State.Connected
                DataStore.selectedProxy = errored.id
                config.refreshProfileState()
                settle()
                holder.bind(errored)
                holder.bindProfileState(errored)
                holder.bindTraffic(errored)
                DataStore.confirmProfileDelete = false
                holder.removeButton.performClick()
                idleMain()
                DataStore.confirmProfileDelete = true
                if (shown.size > 1) {
                    listed.onBindViewHolder(holder, 0)
                    holder.removeButton.performClick()
                    idleMain()
                    clickDialog(AlertDialog.BUTTON_NEGATIVE)
                    clickDialog(AlertDialog.BUTTON_POSITIVE)
                }
                holder.editButton.performClick()
                finishStarted()
                holder.shareLayout.performClick()
                idleMain()
                holder.onMenuItemClick(item(activity, R.id.action_standard_qr))
                holder.onMenuItemClick(item(activity, R.id.action_standard_clipboard))
                holder.onMenuItemClick(item(activity, R.id.action_universal_qr))
                holder.onMenuItemClick(item(activity, R.id.action_universal_clipboard))
                holder.onMenuItemClick(item(activity, R.id.action_config_export_clipboard))
                holder.onMenuItemClick(item(activity, R.id.action_config_export_file))
                idleMain()
                listed.onViewRecycled(holder)
                listed.filter("holder")
                listed.filter("")
                idleMain()
                val removedId = shown.first().id
                val removeJob = GlobalScope.launch(Dispatchers.Main) {
                    listed.onRemoved(page.proxyGroup.id, removedId)
                    listed.onRemoved(page.proxyGroup.id, -99L)
                    listed.onRemoved(-1L, removedId)
                }
                awaitJob(removeJob)
                config.onQueryTextChange("holder")
                config.onQueryTextChange("")
                page.checkOrderMenu()
                val menu = config.toolbar.menu
                menu.performIdentifierAction(R.id.action_order_by_name, 0)
                menu.performIdentifierAction(R.id.action_order_by_delay, 0)
                menu.performIdentifierAction(R.id.action_order_origin, 0)
                menu.performIdentifierAction(R.id.action_layout_double, 0)
                idleMain()
                menu.performIdentifierAction(R.id.action_card_style_stroke, 0)
                menu.performIdentifierAction(R.id.action_card_style_classic, 0)
                menu.performIdentifierAction(R.id.action_layout_single, 0)
                idleMain()
                val touch = runCatching { touchCallback(page.configurationListView) }.getOrNull()
                if (touch != null) {
                    DataStore.serviceState = BaseService.State.Connected
                    touch.getDragDirs(page.configurationListView, holder)
                    DataStore.serviceState = BaseService.State.Idle
                    touch.getDragDirs(page.configurationListView, holder)
                    touch.getSwipeDirs(page.configurationListView, holder)
                    touch.onMove(page.configurationListView, holder, holder)
                    touch.clearView(page.configurationListView, holder)
                    idleMain()
                }
            }
            val updated = page.proxyGroup
            val groupJob = GlobalScope.launch(Dispatchers.Main) {
                page.adapter?.groupUpdated(updated)
                page.adapter?.groupUpdated(updated.id)
                page.adapter?.groupUpdated(-99L)
            }
            awaitJob(groupJob)
        }

        driveGroupInterface(activity)
        driveGroupAndRoute(activity, longProfile)
        driveProfileAndRouteEditors(activity, groupId)
    }

    @Test(timeout = 180_000)
    fun mainActivityAndBackupImport() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        val activity = openMain()
        deliverIntent(activity, Intent())
        deliverIntent(activity, Intent(Intent.ACTION_VIEW, Uri.parse("socks://user:pass@10.13.2.1:1080#b13")))
        settleUntil { ShadowDialog.getLatestDialog() is AlertDialog }
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()
        deliverIntent(activity, Intent(Intent.ACTION_VIEW, Uri.parse("sn://subscription?url=https://example.invalid/b13&name=B13")))
        settle()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        runCatching { activity.urlTest() }
        DataStore.serviceState = BaseService.State.Connected
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        activity.connection.service = vpn.data.binder
        runCatching { activity.urlTest() }
        activity.missingPlugin("profile", "not-a-plugin")
        idleMain()
        activity.missingPlugin("profile", PluginEntry.TrojanGo.pluginId)
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        idleMain()
        clickList(0)
        activity.missingPlugin("profile", PluginEntry.TrojanGo.pluginId)
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        idleMain()
        clickList(1)
        clickList(2)
        activity.missingPlugin("profile", PluginEntry.Hysteria.pluginId)
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        idleMain()
        clickList(0)
        activity.cbSpeedUpdate(SpeedDisplayData(txRateProxy = 1, rxRateProxy = 2))
        activity.driveBottomBar(12)
        activity.driveBottomBar(-4)
        activity.stateChanged(BaseService.State.Connected, "name", "down")
        idleMain()
        activity.onServiceDisconnected()
        activity.refreshNavMenu(true)
        activity.refreshNavMenu(false)
        val settings = activity.navigation.menu.findItem(R.id.nav_settings)
        settings.isChecked = false
        activity.onNavigationItemSelected(settings)
        idleMain()
        settings.isChecked = true
        activity.onNavigationItemSelected(settings)
        runCatching { activity.displayFragmentWithId(R.id.nav_faq) }
        runCatching { activity.displayFragmentWithId(R.id.nav_tools) }
        idleMain()
        runCatching { activity.displayFragmentWithId(R.id.nav_logcat) }
        idleMain()
        runCatching { activity.displayFragmentWithId(R.id.nav_traffic) }
        idleMain()
        activity.onBinderDied()
        idleMain()

        driveBackup(activity)
    }

    @Test(timeout = 60_000)
    @Config(sdk = [28])
    fun resolverAndProcFsBeforeQ() {
        check(NativeInterface().useProcFS())
        ExchangeContext.successFailure = RuntimeException("success")
        SagerNet.underlyingNetwork = null
        LocalResolverImpl.lookup(ExchangeContext(), "ip", "localhost")
        ExchangeContext.successFailure = null
        awaitIo()
    }

    private fun driveConnection(binder: ISagerNetService) {
        val cold = SagerConnection(SagerConnection.CONNECTION_ID_SHORTCUT, listenForDeath = true)
        val callback = cold.javaClass.getDeclaredField("serviceCallback").apply { isAccessible = true }
            .get(cold) as ISagerNetServiceCallback
        callback.stateChanged(-1, null, null)
        callback.stateChanged(BaseService.State.Stopped.ordinal, "n", "m")
        callback.cbSpeedUpdate(SpeedDisplayData())
        callback.cbTrafficUpdate(TrafficDataBatch())
        callback.cbSelectorUpdate(1L)
        callback.missingPlugin("p", "plugin")
        idleMain()

        var died = 0
        var disconnected = 0
        val listener = object : SagerConnection.Callback {
            override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {}
            override fun onServiceConnected(service: ISagerNetService) {}
            override fun cbSpeedUpdate(stats: SpeedDisplayData) {}
            override fun cbSelectorUpdate(id: Long) {}
            override fun missingPlugin(profileName: String, pluginName: String) {}
            override suspend fun cbTrafficUpdate(data: TrafficDataBatch) {}
            override fun onServiceDisconnected() {
                disconnected++
            }

            override fun onBinderDied() {
                died++
            }
        }
        cold.connect(app, listener)
        cold.connect(app, listener)
        idleMain()
        callback.stateChanged(BaseService.State.Connected.ordinal, "up", null)
        callback.cbSpeedUpdate(SpeedDisplayData(txRateProxy = 3))
        callback.cbTrafficUpdate(TrafficDataBatch(arrayListOf(TrafficData(1, 2, 3))))
        callback.cbSelectorUpdate(4L)
        callback.missingPlugin("profile", "naive-plugin")
        idleMain()
        cold.updateConnectionId(SagerConnection.CONNECTION_ID_TILE)
        cold.onServiceDisconnected(ComponentName(app, SagerVpnService::class.java))
        cold.binderDied()
        idleMain()
        SagerConnection.restartingApp = true
        cold.binderDied()
        SagerConnection.restartingApp = false
        cold.onServiceConnected(ComponentName(app, SagerVpnService::class.java), binder.asBinder())
        idleMain()
        cold.disconnect(app)
        cold.disconnect(app)
        check(disconnected > 0 || died >= 0)
    }

    private fun driveNetwork() {
        val callbackClass = Class.forName("io.nekohasekai.sagernet.utils.DefaultNetworkListener\$Callback")
        val instanceField = callbackClass.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        val callback = instanceField.get(null) as ConnectivityManager.NetworkCallback
        val network = ShadowNetwork.newInstance(13)
        runCatching {
            runBlocking {
                withTimeout(2_000) { DefaultNetworkListener.start("b13") { } }
            }
        }
        callback.onAvailable(network)
        callback.onCapabilitiesChanged(network, NetworkCapabilities())
        runCatching {
            runBlocking { withTimeout(2_000) { DefaultNetworkListener.get() } }
        }
        callback.onLost(network)
        callback.onCapabilitiesChanged(network, NetworkCapabilities())
        runCatching {
            runBlocking { withTimeout(2_000) { DefaultNetworkListener.stop("b13") } }
        }
        val fallback = DefaultNetworkListener::class.java.getDeclaredField("fallback")
        fallback.isAccessible = true
        fallback.set(DefaultNetworkListener, true)
        runCatching {
            runBlocking { withTimeout(2_000) { DefaultNetworkListener.get() } }
        }
        fallback.set(DefaultNetworkListener, false)
    }

    private fun driveResolver() {
        val boom = ExchangeContext()
        ShadowDnsResolver.mode = 0
        ExchangeContext.successFailure = RuntimeException("success")
        LocalResolverImpl.lookup(boom, "ip4", "covered.example")
        ExchangeContext.successFailure = null
        ShadowDnsResolver.mode = 2
        ExchangeContext.errnoFailure = RuntimeException("errno")
        LocalResolverImpl.lookup(boom, "ip6", "covered.example")
        ExchangeContext.errnoFailure = null
        ShadowDnsResolver.mode = 3
        LocalResolverImpl.exchange(boom, byteArrayOf(0))
        ShadowDnsResolver.mode = 0
        LocalResolverImpl.exchange(boom, byteArrayOf(1))
    }

    private fun driveNative(vpn: SagerVpnService, profile: ProxyEntity) {
        PackageCache.reload()
        DataStore.vpnService = vpn
        DataStore.rulesProvider = 0
        val native = NativeInterface()
        check(!native.useProcFS())
        check(native.useOfficialAssets())
        DataStore.rulesProvider = 1
        check(!native.useOfficialAssets())
        runCatching { native.autoDetectInterfaceControl(3) }
        runCatching { native.wifiState() }
        runCatching { native.findConnectionOwner(6, "127.0.0.1", 1, "1.2.3.4", 443) }
        native.packageNameByUid(1000)
        val uid = app.applicationInfo.uid
        runCatching { native.packageNameByUid(uid) }
        runCatching { native.packageNameByUid(999_999) }
        native.uidByPackageName(app.packageName)
        check(native.uidByPackageName("missing.package") == 0)
        DataStore.vpnService = null
        runCatching { native.openTun("{}", "{}") }
        DataStore.vpnService = vpn
        DataStore.bypassLan = false
        DataStore.enableHevTun = false
        DataStore.proxyApps = false
        runCatching {
            val instance = ProxyInstance(profile, vpn)
            instance.buildConfigTmp()
            vpn.data.proxy = instance
            native.openTun("{}", "{}")
            DataStore.baseService = vpn
            native.selector_OnProxySelected("other", "x")
            val tag = instance.config.profileTagMap.values.firstOrNull()
            if (tag != null) {
                native.selector_OnProxySelected("proxy", tag)
                native.selector_OnProxySelected("proxy", "missing-tag")
            }
            idleMain()
            settle()
        }
        native.selector_OnProxySelected("other", "y")
    }

    private fun driveBaseService(vpn: SagerVpnService, proxyService: ProxyService, profile: ProxyEntity) {
        vpn.data.proxy = null
        val power = shadowOf(app.getSystemService(PowerManager::class.java))
        power.setIsDeviceIdleMode(true)
        DataStore.wakeResetConnections = true
        vpn.data.receiver.onReceive(app, Intent(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED))
        power.setIsDeviceIdleMode(false)
        vpn.data.receiver.onReceive(app, Intent(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED))
        vpn.data.receiver.onReceive(app, Intent(Intent.ACTION_SHUTDOWN))
        vpn.data.receiver.onReceive(app, Intent(Action.RESET_UPSTREAM_CONNECTIONS))
        idleMain()
        settle()
        vpn.data.receiver.onReceive(app, Intent("coverage.unknown"))
        idleMain()

        val fresh = Robolectric.buildService(SagerVpnService::class.java).create().get()
        fresh.onBind(Intent(Action.SERVICE))
        fresh.onBind(Intent(android.net.VpnService.SERVICE_INTERFACE))
        runCatching { fresh.data.binder.urlTest() }
        fresh.data.binder.resetTraffic(longArrayOf(profile.id))
        idleMain()
        val throwing = object : ISagerNetServiceCallback.Stub() {
            override fun stateChanged(state: Int, profileName: String?, msg: String?) {
                throw RuntimeException("callback")
            }

            override fun missingPlugin(profileName: String?, pluginName: String?) {}
            override fun cbSpeedUpdate(stats: SpeedDisplayData?) {}
            override fun cbTrafficUpdate(stats: TrafficDataBatch?) {}
            override fun cbSelectorUpdate(id: Long) {}
        }
        fresh.data.binder.registerCallback(throwing, 1)
        fresh.data.binder.registerCallback(
            throwing,
            SagerConnection.CONNECTION_ID_RESTART_BG,
        )
        runBlocking { fresh.data.binder.broadcast { it.stateChanged(1, "n", null) } }
        fresh.data.binder.stateChanged(BaseService.State.Connected, "msg")
        fresh.data.binder.missingPlugin("plugin")
        idleMain()
        fresh.data.binder.unregisterCallback(throwing)
        check(fresh.data.binder.profileName.isNotEmpty() || fresh.data.binder.state >= 0)
        fresh.persistStats()
        DataStore.acquireWakeLock = false
        runBlocking {
            fresh.lateInit()
            fresh.preInit()
        }
        DataStore.acquireWakeLock = true
        runBlocking { fresh.lateInit() }
        fresh.killProcesses()
        DataStore.selectedProxy = profile.id
        fresh.data.state = BaseService.State.Stopped
        fresh.reload()
        fresh.data.state = BaseService.State.Connecting
        fresh.reload()
        fresh.data.state = BaseService.State.Idle
        fresh.reload()
        idleMain()
        fresh.data.state = BaseService.State.Stopped
        fresh.stopRunner(false, "stop")
        fresh.stopRunner(true)
        fresh.onRevoke()
        idleMain()
        settle()
        fresh.data.binder.close()
        check(fresh.data.binder.state == BaseService.State.Idle.ordinal)

        DataStore.serviceMode = Key.MODE_PROXY
        DataStore.selectedProxy = 0L
        proxyService.data.state = BaseService.State.Stopped
        proxyService.onStartCommand(Intent(), 0, 1)
        settle()
        proxyService.data.state = BaseService.State.Stopped
        DataStore.selectedProxy = profile.id
        proxyService.onStartCommand(Intent(), 0, 2)
        settleMore()
        DataStore.serviceMode = Key.MODE_VPN
    }

    private fun driveGuardedPool() {
        val fast = GuardedProcessPool { }
        runCatching { fast.start(listOf("/bin/true")) }
        idleMain()
        fast.close(GlobalScope)
        settle()
        val pool = GuardedProcessPool { }
        val started = runCatching { pool.start(listOf("/bin/sh", "-c", "sleep 2")) }.isSuccess
        if (!started) return
        idleMain()
        pool.close(GlobalScope)
        repeat(8) {
            ShadowSystemClock.advanceBy(200, TimeUnit.MILLISECONDS)
            Thread.sleep(30)
            idleMain()
        }
    }

    private fun driveGroupInterface(activity: MainActivity) {
        val adapter = GroupInterfaceAdapter(activity)
        val group = ProxyGroup(name = "Diff", type = GroupType.SUBSCRIPTION)
        val yes = GlobalScope.launch(Dispatchers.Main) { adapter.confirm("import?") }
        settleUntil { ShadowDialog.getLatestDialog() is AlertDialog }
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        awaitJob(yes)
        val no = GlobalScope.launch(Dispatchers.Main) { adapter.confirm("import?") }
        settleUntil { ShadowDialog.getLatestDialog() is AlertDialog }
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        awaitJob(no)
        val cancel = GlobalScope.launch(Dispatchers.Main) { adapter.confirm("import?") }
        settleUntil { ShadowDialog.getLatestDialog() is AlertDialog }
        runCatching { ShadowDialog.getLatestDialog()?.cancel() }
        awaitJob(cancel)
        val alert = GlobalScope.launch(Dispatchers.Main) { adapter.alert("warn") }
        settleUntil { ShadowDialog.getLatestDialog() is AlertDialog }
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        awaitJob(alert)
        val quiet = GlobalScope.launch(Dispatchers.Main) {
            adapter.onUpdateSuccess(group, 0, emptyList(), emptyMap(), emptyList(), emptyList(), false)
            adapter.onUpdateSuccess(group, 0, emptyList(), emptyMap(), emptyList(), emptyList(), true)
        }
        awaitJob(quiet)
        val diff = GlobalScope.launch(Dispatchers.Main) {
            adapter.onUpdateSuccess(
                group,
                3,
                listOf("added"),
                mapOf("same" to "same", "old" to "new"),
                listOf("gone"),
                listOf("dup"),
                true,
            )
        }
        awaitJob(diff)
        runCatching { ShadowDialog.getLatestDialog()?.dismiss() }
        val failed = GlobalScope.launch(Dispatchers.Main) {
            adapter.onUpdateFailure(group, "failed")
        }
        awaitJob(failed)
    }

    private fun driveGroupAndRoute(activity: MainActivity, profile: ProxyEntity) {
        val subscription = ProxyGroup(name = "Sub13", type = GroupType.SUBSCRIPTION).apply {
            subscription = SubscriptionBean().apply {
                initializeDefaultValues()
                link = "https://example.invalid/b13"
            }
        }
        subscription.id = SagerDatabase.groupDao.createGroup(subscription)
        activity.displayFragmentWithId(R.id.nav_group)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val group = activity.supportFragmentManager.fragments.filterIsInstance<GroupFragment>().first()
        runBlocking { group.groupAdapter.reload() }
        idleMain()
        settleUntil { group.groupAdapter.groupList.isNotEmpty() }
        group.onMenuItemClick(item(activity, R.id.action_new_group))
        finishStarted()
        group.onMenuItemClick(item(activity, R.id.action_update_all))
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        group.onMenuItemClick(item(activity, R.id.action_update_all))
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()
        val basic = group.groupAdapter.groupList.firstOrNull { !it.ungrouped }
        val parent = group.groupListView
        if (basic != null) {
            val index = group.groupAdapter.groupList.indexOf(basic)
            val holder = group.groupAdapter.onCreateViewHolder(parent, 0)
            group.groupAdapter.onBindViewHolder(holder, index)
            val typed = holder as GroupFragment.GroupHolder
            typed.editButton.performClick()
            finishStarted()
            typed.onMenuItemClick(item(activity, R.id.action_export_clipboard))
            typed.onMenuItemClick(item(activity, R.id.action_clear))
            idleMain()
            clickDialog(AlertDialog.BUTTON_POSITIVE)
            settle()
            val callback = runCatching { touchCallback(parent) }.getOrNull()
            if (callback != null) {
                callback.getSwipeDirs(parent, holder)
                callback.getDragDirs(parent, holder)
                GroupUpdater.updating.add(basic.id)
                callback.getSwipeDirs(parent, holder)
                callback.getDragDirs(parent, holder)
                GroupUpdater.updating.remove(basic.id)
            val subIndex = group.groupAdapter.groupList.indexOfFirst { it.type == GroupType.SUBSCRIPTION }
            if (subIndex >= 0) {
                val subHolder = group.groupAdapter.onCreateViewHolder(parent, 0) as GroupFragment.GroupHolder
                group.groupAdapter.onBindViewHolder(subHolder, subIndex)
                subHolder.updateButton.performClick()
                subHolder.optionsButton.performClick()
                subHolder.onMenuItemClick(item(activity, R.id.action_universal_qr))
                subHolder.onMenuItemClick(item(activity, R.id.action_universal_clipboard))
                idleMain()
                runCatching { ShadowDialog.getLatestDialog()?.dismiss() }
            }
                layoutList(parent)
                val attached = parent.findViewHolderForAdapterPosition(index)
                if (attached != null && attached.bindingAdapterPosition >= 0) {
                    callback.onSwiped(attached, ItemTouchHelper.START)
                    idleMain()
                }
            }
        }
        val ungroupedHolder = group.groupAdapter.groupList.indexOfFirst { it.ungrouped }
        if (ungroupedHolder >= 0) {
            val holder = group.groupAdapter.onCreateViewHolder(parent, 0)
            group.groupAdapter.onBindViewHolder(holder, ungroupedHolder)
            runCatching { touchCallback(parent) }.getOrNull()?.let {
                it.getSwipeDirs(parent, holder)
                it.getDragDirs(parent, holder)
            }
        }
        val added = ProxyGroup(name = "B13", type = GroupType.BASIC)
        val addJob = GlobalScope.launch(Dispatchers.Main) { group.groupAdapter.groupAdd(added) }
        awaitJob(addJob)
        val missing = GlobalScope.launch(Dispatchers.Main) {
            group.groupAdapter.groupUpdated(ProxyGroup(id = -5, name = "missing"))
            group.groupAdapter.groupUpdated(-5L)
            group.groupAdapter.groupRemoved(-5L)
        }
        awaitJob(missing)
        if (group.groupAdapter.groupList.isNotEmpty()) {
            val known = group.groupAdapter.groupList.first()
            val updateJob = GlobalScope.launch(Dispatchers.Main) {
                group.groupAdapter.groupUpdated(known)
                group.groupAdapter.groupUpdated(known.id)
            }
            awaitJob(updateJob)
            group.groupAdapter.commit(listOf(0 to known))
            idleMain()
        }

        repeat(2) {
            runBlocking {
                ProfileManager.createRule(
                    RuleEntity(name = "b13-$it", enabled = true, outbound = if (it == 0) -2L else -1L),
                    false,
                )
            }
        }
        activity.displayFragmentWithId(R.id.nav_route)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val route = activity.supportFragmentManager.fragments.filterIsInstance<RouteFragment>().first()
        runBlocking { route.ruleAdapter.reload() }
        idleMain()
        settleUntil { route.ruleAdapter.ruleList.isNotEmpty() }
        route.onMenuItemClick(item(activity, R.id.action_new_route))
        finishStarted()
        route.onMenuItemClick(item(activity, R.id.action_manage_assets))
        finishStarted()
        val ruleParent = route.ruleListView
        val doc = route.ruleAdapter.onCreateViewHolder(ruleParent, 0)
        route.ruleAdapter.onBindViewHolder(doc, 0)
        doc.itemView.performClick()
        val ruleIndex = 1
        if (route.ruleAdapter.ruleList.isNotEmpty()) {
            val holder = route.ruleAdapter.onCreateViewHolder(ruleParent, 1)
            route.ruleAdapter.onBindViewHolder(holder, ruleIndex)
            val typed = holder as RouteFragment.RuleAdapter.RuleHolder
            typed.enableSwitch.isChecked = !typed.enableSwitch.isChecked
            idleMain()
            typed.editButton.performClick()
            finishStarted()
            val callback = runCatching { touchCallback(ruleParent) }.getOrNull()
            if (callback != null) {
                callback.getSwipeDirs(ruleParent, doc)
                callback.getDragDirs(ruleParent, doc)
                callback.getSwipeDirs(ruleParent, holder)
                callback.getDragDirs(ruleParent, holder)
                layoutList(ruleParent)
                val attached = ruleParent.findViewHolderForAdapterPosition(ruleIndex)
                val target = ruleParent.findViewHolderForAdapterPosition(ruleIndex + 1)
                if (attached != null && target != null &&
                    attached.bindingAdapterPosition > 0 && target.bindingAdapterPosition > 0 &&
                    attached.bindingAdapterPosition != target.bindingAdapterPosition
                ) {
                    callback.onMove(ruleParent, attached, target)
                    callback.clearView(ruleParent, attached)
                    idleMain()
                }
                val swiped = ruleParent.findViewHolderForAdapterPosition(ruleIndex)
                if (swiped != null && swiped.bindingAdapterPosition > 0) {
                    callback.onSwiped(swiped, ItemTouchHelper.START)
                    idleMain()
                }
            }
            val rule = route.ruleAdapter.ruleList.first()
            val jobs = GlobalScope.launch(Dispatchers.Main) {
                route.ruleAdapter.onAdd(RuleEntity(name = "added", outbound = 0L))
                route.ruleAdapter.onUpdated(rule)
                route.ruleAdapter.onUpdated(RuleEntity(id = -9, name = "missing"))
                route.ruleAdapter.onRemoved(-9L)
            }
            awaitJob(jobs)
        }
        route.onMenuItemClick(item(activity, R.id.action_reset_route))
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        route.onMenuItemClick(item(activity, R.id.action_reset_route))
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()
        check(profile.id >= 0L)
    }

    private fun driveProfileAndRouteEditors(activity: MainActivity, groupId: Long) {
        SagerDatabase.groupDao.createGroup(ProxyGroup(name = "SecondBasic"))
        val profile = runBlocking { ProfileManager.createProfile(groupId, socks("edit", "10.13.3.1")) }
        val missing = Robolectric.buildActivity(
            SocksSettingsActivity::class.java,
            Intent().putExtra(ProfileSettingsActivity.EXTRA_PROFILE_ID, 999_999L),
        ).setup().get()
        settle()
        missing.finish()
        val fresh = Robolectric.buildActivity(SocksSettingsActivity::class.java).setup().get()
        settleUntil {
            fresh.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        val freshPrefs = fresh.supportFragmentManager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
        freshPrefs.onOptionsItemSelected(item(fresh, R.id.action_delete))
        fresh.finish()

        val editor = Robolectric.buildActivity(
            SocksSettingsActivity::class.java,
            Intent().putExtra(ProfileSettingsActivity.EXTRA_PROFILE_ID, profile.id),
        ).setup().get()
        settleUntil {
            editor.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        val prefs = editor.supportFragmentManager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
        val summary = EditTextPreference(editor)
        summary.text = ""
        ProfileSettingsActivity.PasswordSummaryProvider.provideSummary(summary)
        summary.text = "secret"
        ProfileSettingsActivity.PasswordSummaryProvider.provideSummary(summary)
        RouteSettingsActivity.PasswordSummaryProvider.provideSummary(summary)
        prefs.onOptionsItemSelected(item(editor, R.id.action_apply))
        settle()
        prefs.onOptionsItemSelected(item(editor, R.id.action_custom_outbound_json))
        finishStarted()
        prefs.onOptionsItemSelected(item(editor, R.id.action_custom_config_json))
        finishStarted()
        prefs.onOptionsItemSelected(item(editor, R.id.action_create_shortcut))
        idleMain()
        prefs.onOptionsItemSelected(item(editor, R.id.action_move))
        idleMain()
        ShadowDialog.getLatestDialog()?.findViewById<View>(R.id.group_update)?.performClick()
        settle()
        editor.onPreferenceDataStoreChanged(DataStore.profileCacheStore, Key.SERVER_ADDRESS)
        editor.onBackPressed()
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEUTRAL)
        if (!editor.isFinishing) {
            prefs.onOptionsItemSelected(item(editor, R.id.action_delete))
            idleMain()
            clickDialog(AlertDialog.BUTTON_POSITIVE)
            settle()
        }

        val rule = runBlocking {
            ProfileManager.createRule(RuleEntity(name = "edit-route", enabled = true, outbound = 0L), false)
        }
        val routeSettings = Robolectric.buildActivity(
            RouteSettingsActivity::class.java,
            Intent().putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, rule.id),
        ).setup().get()
        settleUntil {
            routeSettings.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        invokeResume(routeSettings)
        val routePrefs = routeSettings.supportFragmentManager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
        routePrefs.onOptionsItemSelected(item(routeSettings, R.id.action_apply))
        settle()
        routeSettings.onPreferenceDataStoreChanged(DataStore.profileCacheStore, Key.ROUTE_NAME)
        routeSettings.onBackPressed()
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        val created = Robolectric.buildActivity(
            RouteSettingsActivity::class.java,
            Intent()
                .putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, 0L)
                .putExtra(RouteSettingsActivity.EXTRA_PACKAGE_NAME, app.packageName),
        ).setup().get()
        settleUntil {
            created.supportFragmentManager.findFragmentById(R.id.settings) is PreferenceFragmentCompat
        }
        invokeResume(created)
        check(!created.needSave() || created.needSave())
        val createdPrefs = created.supportFragmentManager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
        createdPrefs.onOptionsItemSelected(item(created, R.id.action_delete))
        created.finish()
        val gone = Robolectric.buildActivity(
            RouteSettingsActivity::class.java,
            Intent().putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, 888_888L),
        ).setup().get()
        settle()
        gone.finish()
        activity.finish()
    }

    private fun driveBackup(activity: MainActivity) {
        runBlocking { ProfileManager.createProfile(DataStore.currentGroupId(), socks("bak", "10.13.4.1")) }
        runBlocking { ProfileManager.createRule(RuleEntity(name = "bak", enabled = true), false) }
        val backup = io.nekohasekai.sagernet.ui.BackupFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, backup, "backup13")
            .commitNow()
        idleMain()
        backup.requireView().findViewById<CheckBox>(R.id.backup_rules).isChecked = true
        backup.requireView().findViewById<CheckBox>(R.id.backup_settings).isChecked = true
        PublicDatabase.kvPairDaoOverride = MemoryKvDao()
        val bytes = backup.javaClass.getDeclaredMethod(
            "doBackup",
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }.invoke(backup, true, true, true) as ByteArray
        backup.finishImport(JSONObject(bytes.toString(Charsets.UTF_8)), true, true, true)
        val badName = Uri.parse("content://coverage/backup13.txt")
        shadowOf(app.contentResolver).registerInputStream(badName, ByteArrayInputStream(bytes))
        val badJob = GlobalScope.launch(Dispatchers.Main) { backup.startImport(badName) }
        awaitJob(badJob)
        val badJson = Uri.parse("content://coverage/backup13-bad.json")
        shadowOf(app.contentResolver).registerInputStream(badJson, ByteArrayInputStream("nope".toByteArray()))
        val badJsonJob = GlobalScope.launch(Dispatchers.Main) { backup.startImport(badJson) }
        awaitJob(badJsonJob)
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { stream ->
            stream.putNextEntry(ZipEntry("backup.json"))
            stream.write(bytes)
            stream.closeEntry()
        }
        val zipUri = Uri.parse("content://coverage/backup13.zip")
        shadowOf(app.contentResolver).registerInputStream(zipUri, ByteArrayInputStream(zip.toByteArray()))
        val zipJob = GlobalScope.launch(Dispatchers.Main) { backup.startImport(zipUri) }
        awaitJob(zipJob)
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        val emptyZip = ByteArrayOutputStream()
        ZipOutputStream(emptyZip).close()
        val emptyUri = Uri.parse("content://coverage/backup13-empty.zip")
        shadowOf(app.contentResolver).registerInputStream(emptyUri, ByteArrayInputStream(emptyZip.toByteArray()))
        val emptyJob = GlobalScope.launch(Dispatchers.Main) { backup.startImport(emptyUri) }
        awaitJob(emptyJob)
        val jsonUri = Uri.parse("content://coverage/backup13.json")
        shadowOf(app.contentResolver).registerInputStream(jsonUri, ByteArrayInputStream(bytes))
        val importJob = GlobalScope.launch(Dispatchers.Main) { backup.startImport(jsonUri) }
        awaitJob(importJob)
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settleMore()
        backup.requireView().findViewById<View>(R.id.action_import_file).performClick()
        idleMain()
        backup.requireView().findViewById<View>(R.id.webdav_settings).performClick()
        finishStarted()
        backup.requireView().findViewById<View>(R.id.backup_to_webdav).performClick()
        backup.requireView().findViewById<View>(R.id.restore_from_webdav).performClick()
        idleMain()
    }

    private fun bindVpn(vpn: SagerVpnService) {
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, SagerVpnService::class.java),
            vpn.onBind(Intent(Action.SERVICE)),
        )
    }

    private fun toggle(action: String, profileId: Long) {
        val intent = Intent(action).putExtra("profile", profileId)
        val controller = Robolectric.buildActivity(QuickToggleShortcut::class.java, intent).create()
        idleMain()
        runCatching { controller.pause().stop().destroy() }
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
        config.childFragmentManager.beginTransaction().add(host.id, page, "b13page").commitNow()
        idleMain()
        return page
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

    private fun deliverIntent(activity: MainActivity, intent: Intent) {
        val method = MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
        method.isAccessible = true
        method.invoke(activity, intent)
    }

    private fun invokeResume(activity: Activity) {
        val method = activity.javaClass.getDeclaredMethod("onResume")
        method.isAccessible = true
        method.invoke(activity)
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

    private fun item(context: Context, id: Int) = MenuBuilder(context).add(0, id, 0, "item")

    private fun clickDialog(which: Int) {
        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog ?: return
        dialog.getButton(which)?.performClick()
        idleMain()
    }

    private fun clickList(index: Int) {
        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog ?: return
        val list = dialog.listView ?: return
        list.performItemClick(list.getChildAt(index), index, index.toLong())
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

    private fun settle() {
        repeat(6) {
            Thread.sleep(30)
            idleMain()
        }
    }

    private fun settleMore() {
        repeat(12) {
            Thread.sleep(30)
            idleMain()
        }
    }

    private fun settleUntil(ready: () -> Boolean) {
        repeat(40) {
            if (ready()) return
            Thread.sleep(40)
            idleMain()
        }
    }

    private fun awaitJob(job: Job) {
        val deadline = System.nanoTime() + 8_000_000_000L
        while (!job.isCompleted && System.nanoTime() < deadline) {
            ShadowSystemClock.advanceBy(200, TimeUnit.MILLISECONDS)
            Thread.sleep(20)
            idleMain()
        }
    }

    private fun awaitIo() {
        repeat(6) {
            ShadowSystemClock.advanceBy(50, TimeUnit.MILLISECONDS)
            Thread.sleep(20)
            idleMain()
        }
    }
}
