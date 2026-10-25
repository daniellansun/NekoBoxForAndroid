package io.nekohasekai.sagernet

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.system.OsConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.ChipGroup
import com.google.zxing.BarcodeFormat
import com.google.zxing.Result
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.bg.ShadowOsAddress
import io.nekohasekai.sagernet.bg.ShadowResolvingNetwork
import io.nekohasekai.sagernet.bg.ShadowTunBuilder
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RoomGroupDirectory
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.groupDirectory
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ui.AppListActivity
import io.nekohasekai.sagernet.ui.AppManagerActivity
import io.nekohasekai.sagernet.ui.AssetsActivity
import io.nekohasekai.sagernet.ui.BackupFragment
import io.nekohasekai.sagernet.ui.ConfigurationFragment
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.MessageStore
import io.nekohasekai.sagernet.ui.ScannerActivity
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import moe.matsuri.nb4a.hevtun.HevTunRuntime
import org.junit.After
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
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
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
class Batch9RobolectricTest {

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
        DataStore.individual = ""
        DataStore.routePackages = ""
        DataStore.webdavServer = ""
        DataStore.webdavUsername = ""
        DataStore.webdavPassword = ""
        DataStore.webdavPath = "NekoBox"
        DataStore.confirmProfileDelete = true
        DataStore.groupLayoutMode = 0
        DataStore.profileCardStyle = 0
        DataStore.alwaysShowAddress = false
        DataStore.bypassLan = false
        DataStore.appendHttpProxy = false
        DataStore.enableFakeDns = true
        DataStore.rulesProvider = 0
        groupDirectory = RoomGroupDirectory
        SagerDatabase.groupDaoOverride = null
        SagerDatabase.proxyDaoOverride = null
        SagerDatabase.rulesDaoOverride = null
        PublicDatabase.kvPairDaoOverride = null
        PluginManager.initOverride = null
        GuardedProcessPool.pidReader = null
        ShadowOsAddress.killErrno = 0
        ShadowOsAddress.killCalls = 0
        HevTunRuntime.resetNativeHooks()
        SagerNet.underlyingNetwork = null
        ShadowTunBuilder.establishReturnsNull = false
        ShadowApplication.setProcessName(BuildConfig.APPLICATION_ID)
        runCatching {
            val cache = Class.forName("androidx.core.content.FileProvider")
                .getDeclaredField("sCache")
            cache.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (cache.get(null) as MutableMap<Any, Any>).clear()
        }
    }

    @Test(timeout = 180_000)
    fun screensBackupsAndTun() {
        idleMain()
        PublicDatabase.kvPairDaoOverride = TestKvDao()
        installExtraPackages()
        PackageCache.reload()
        val activity = openMain()
        MessageStore.setCurrentActivity(activity)
        driveGroupList(activity)
        driveWebDav(activity)
        driveAssets()
        driveAppList()
        driveAppManager()
        driveScanner()
        driveHevStart()
        idleMain()
    }

    @Test(timeout = 120_000)
    @Config(
        sdk = [21],
        application = CoverageApplication::class,
        shadows = [ShadowTunBuilder::class, ShadowResolvingNetwork::class, ShadowOsAddress::class],
    )
    fun preApi24ProcessKill() {
        idleMain()
        val fast = GuardedProcessPool { }
        fast.start(listOf("/bin/true"))
        settle()
        fast.close(GlobalScope + Dispatchers.IO)
        settle()

        GuardedProcessPool.pidReader = { throw NoSuchFieldException("coverage") }
        closeRunningPool(listOf("/bin/sh", "-c", "sleep 30"))

        ShadowOsAddress.killErrno = OsConstants.ESRCH
        GuardedProcessPool.pidReader = { 2_000_000_001 }
        closeRunningPool(listOf("/bin/sh", "-c", "sleep 0.2"))

        ShadowOsAddress.killErrno = OsConstants.EPERM
        closeRunningPool(listOf("/bin/sh", "-c", "sleep 30"))

        ShadowOsAddress.killErrno = 0
        closeRunningPool(listOf("/bin/sh", "-c", "sleep 30"))
        assertTrue(ShadowOsAddress.killCalls > 0)
    }

    private fun closeRunningPool(cmd: List<String>) {
        val pool = GuardedProcessPool { }
        pool.start(cmd, mutableMapOf("COVERAGE" to "1"))
        idleMain()
        pool.close(GlobalScope + Dispatchers.IO)
        settle()
    }

    private fun installExtraPackages() {
        val shadow = shadowOf(app.packageManager)
        shadow.installPackage(packageInfo("android", 1000, ApplicationInfo.FLAG_SYSTEM, false))
        shadow.installPackage(packageInfo("com.example.inet", 10001, 0, true))
        val names = app.packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .map { it.packageName }
        assertTrue(names.contains("android"))
        assertTrue(names.contains("com.example.inet"))
    }

    private fun packageInfo(name: String, uid: Int, flags: Int, internet: Boolean): PackageInfo {
        return PackageInfo().apply {
            packageName = name
            if (internet) requestedPermissions = arrayOf(Manifest.permission.INTERNET)
            applicationInfo = ApplicationInfo().apply {
                packageName = name
                this.uid = uid
                this.flags = flags
                nonLocalizedLabel = name
            }
        }
    }

    private fun openMain(): MainActivity {
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, SagerVpnService::class.java),
            vpn.data.binder,
        )
        DataStore.serviceMode = Key.MODE_VPN
        DataStore.alwaysShowAddress = true
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idleMain()
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        return activity
    }

    private fun driveGroupList(activity: MainActivity) {
        val groupId = DataStore.selectedGroupForImport()
        val first = runBlocking { ProfileManager.createProfile(groupId, socks("Local Coverage")) }
        runBlocking { ProfileManager.createProfile(groupId, socks("Other Node")) }
        activity.displayFragmentWithId(R.id.nav_configuration)
        activity.supportFragmentManager.executePendingTransactions()
        layout(activity)
        idleMain()
        val config = activity.supportFragmentManager.fragments
            .filterIsInstance<ConfigurationFragment>()
            .first()
        settleUntil { config.adapter.groupList.isNotEmpty() }
        layout(activity)
        idleMain()
        val page = attachGroupPage(activity, config) ?: return
        DataStore.selectedGroup = page.proxyGroup.id
        page.proxyGroup.order = GroupOrder.BY_NAME
        page.adapter?.reloadProfiles()
        idleMain()
        page.proxyGroup.order = GroupOrder.BY_DELAY
        page.adapter?.reloadProfiles()
        idleMain()
        page.proxyGroup.order = GroupOrder.ORIGIN
        page.adapter?.reloadProfiles()
        idleMain()
        val adapter = page.adapter ?: return
        settleUntil { adapter.configurationIdList.size >= 2 }
        val recycler = page.configurationListView
        recycler.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
        )
        recycler.layout(0, 0, 1080, 400)
        idleMain()

        val holder = adapter.onCreateViewHolder(recycler, 0)
        adapter.onBindViewHolder(holder, 0)
        val entity = adapter.configurationList[adapter.configurationIdList[0]]!!
        entity.status = 1
        entity.ping = 12
        entity.tx = 100
        entity.rx = 40
        holder.bind(entity)
        entity.status = 2
        entity.error = "timeout"
        holder.bind(entity)
        entity.status = 3
        entity.error = "connection refused"
        holder.bind(entity)
        holder.profileStatus.performClick()
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        entity.status = 0
        holder.bind(entity)
        DataStore.profileCardStyle = 1
        holder.bind(entity)
        DataStore.profileCardStyle = 0
        holder.bind(entity)

        DataStore.serviceState = BaseService.State.Connected
        DataStore.selectedProxy = entity.id
        config.refreshProfileState()
        idleMain()
        holder.view.performClick()
        settle()
        holder.bindProfileState(entity)
        holder.bindTraffic(entity)
        adapter.onBindViewHolder(holder, 0, listOf(Any()))
        runBlocking { adapter.onUpdated(entity, true) }
        idleMain()
        runBlocking { adapter.onUpdated(entity, false) }
        idleMain()
        runBlocking { adapter.onUpdated(listOf(TrafficData(entity.id, 200, 80))) }
        idleMain()
        markScrolling(recycler)
        runBlocking { adapter.onUpdated(listOf(TrafficData(entity.id, 300, 90))) }
        idleMain()
        adapter.flushPendingTrafficUpdates()
        markIdle(recycler)
        adapter.onViewAttachedToWindow(holder)
        adapter.refreshProfileState(setOf(entity.id))
        idleMain()
        adapter.onViewRecycled(holder)

        config.onQueryTextChange("local")
        idleMain()
        config.onQueryTextChange("")
        idleMain()
        config.onQueryTextSubmit("local")
        adapter.filter("local")
        adapter.filter("")
        idleMain()
        restoreIds(adapter, page.proxyGroup.id)
        if (adapter.configurationIdList.size >= 2) {
            adapter.move(0, 1)
            adapter.commitMove()
            idleMain()
        }
        DataStore.groupLayoutMode = 1
        page.switchLayoutMode()
        idleMain()
        restoreIds(adapter, page.proxyGroup.id)
        if (adapter.configurationIdList.size >= 2) {
            adapter.move(1, 0)
            adapter.commitMove()
            idleMain()
        }
        adapter.neighbourHasMiddleRow(0)
        adapter.refreshSameRowNeighbours(0)
        adapter.refreshFromPosition(0)
        idleMain()
        if (recycler.childCount > 0) {
            driveTouchHelper(recycler)
        }
        dispatchScroll(recycler)

        page.checkOrderMenu()
        invokeMenu(config, R.id.action_order_by_name)
        invokeMenu(config, R.id.action_order_by_delay)
        invokeMenu(config, R.id.action_order_origin)
        invokeMenu(config, R.id.action_layout_single)
        invokeMenu(config, R.id.action_layout_double)
        invokeMenu(config, R.id.action_card_style_classic)
        invokeMenu(config, R.id.action_card_style_stroke)
        idleMain()
        config.switchAllGroupFragmentsLayout()
        config.refreshAllGroupFragmentsCardStyle()
        config.toolbar.performClick()
        config.onKeyDown(android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN))

        holder.onMenuItemClick(item(activity, R.id.action_standard_clipboard))
        holder.onMenuItemClick(item(activity, R.id.action_standard_qr))
        holder.onMenuItemClick(item(activity, R.id.action_universal_clipboard))
        holder.onMenuItemClick(item(activity, R.id.action_universal_qr))
        holder.onMenuItemClick(item(activity, R.id.action_config_export_clipboard))
        runCatching { holder.onMenuItemClick(item(activity, R.id.action_config_export_file)) }
        runCatching { holder.shareLayout.performClick() }
        runCatching { holder.doubleColumnMenuButton.performClick() }
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)

        DataStore.confirmProfileDelete = true
        holder.removeButton.performClick()
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        DataStore.confirmProfileDelete = false
        holder.removeButton.performClick()
        idleMain()
        page.undoManager.flush()
        idleMain()

        val added = runBlocking { ProfileManager.createProfile(page.proxyGroup.id, socks("Added Later")) }
        idleMain()
        if (added.id in adapter.configurationIdList) {
            runBlocking { adapter.onRemoved(added.groupId, added.id) }
            idleMain()
        }
        runBlocking { adapter.groupUpdated(page.proxyGroup) }
        idleMain()
        runBlocking { adapter.groupUpdated(page.proxyGroup.id) }
        idleMain()
        adapter.clearTrafficStatistics()
        adapter.clearTestResults()

        entity.tx = 50
        entity.rx = 50
        entity.status = 3
        entity.error = "down"
        SagerDatabase.proxyDao.updateProxy(entity)
        config.onMenuItemClick(item(activity, R.id.action_clear_traffic_statistics))
        settle()
        config.onMenuItemClick(item(activity, R.id.action_connection_test_clear_results))
        settle()
        entity.status = 2
        entity.error = "unavailable"
        SagerDatabase.proxyDao.updateProxy(entity)
        config.onMenuItemClick(item(activity, R.id.action_connection_test_delete_unavailable))
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        settle()

        val extraId = SagerDatabase.groupDao.createGroup(ProxyGroup(name = "extra-group"))
        DataStore.editingGroup = extraId
        config.onPreferenceDataStoreChanged(DataStore.profileCacheStore, Key.PROFILE_GROUP)
        idleMain()

        val state = Bundle()
        page.onSaveInstanceState(state)
        page.onViewStateRestored(state)
        idleMain()

        DataStore.serviceState = BaseService.State.Idle
        driveSelectMode(activity, first)
    }

    private fun attachGroupPage(
        activity: MainActivity,
        config: ConfigurationFragment,
    ): ConfigurationFragment.GroupFragment? {
        val existing = config.childFragmentManager.fragments
            .filterIsInstance<ConfigurationFragment.GroupFragment>()
            .firstOrNull { it.view != null && proxyGroupReady(it) }
        if (existing != null) return existing
        if (config.adapter.groupList.isEmpty()) return null
        val page = config.adapter.createFragment(0) as ConfigurationFragment.GroupFragment
        val host = FrameLayout(activity).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600)
        }
        (config.requireView() as ViewGroup).addView(host)
        config.childFragmentManager.beginTransaction()
            .add(host.id, page, "f" + page.proxyGroup.id)
            .commitNow()
        idleMain()
        layout(activity)
        if (!page.isAdded || page.view == null) return null
        return page
    }

    private fun restoreIds(
        adapter: ConfigurationFragment.GroupFragment.ConfigurationAdapter,
        groupId: Long,
    ) {
        val profiles = SagerDatabase.proxyDao.getByGroup(groupId)
        adapter.configurationList.clear()
        profiles.forEach { adapter.configurationList[it.id] = it }
        adapter.configurationIdList.clear()
        adapter.configurationIdList.addAll(profiles.map { it.id })
    }

    private fun proxyGroupReady(page: ConfigurationFragment.GroupFragment): Boolean {
        return try {
            page.proxyGroup.id
            true
        } catch (_: UninitializedPropertyAccessException) {
            false
        }
    }

    private fun driveSelectMode(activity: MainActivity, profile: ProxyEntity) {
        val selecting = ConfigurationFragment(true, profile, R.string.menu_configuration)
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, selecting, "select-config")
            .commitNow()
        idleMain()
        settleUntil { runCatching { selecting.adapter.groupList.isNotEmpty() }.getOrDefault(false) }
        val page = attachGroupPage(activity, selecting) ?: return
        val adapter = page.adapter ?: return
        adapter.reloadProfiles()
        idleMain()
        if (adapter.itemCount == 0) return
        val recycler = page.configurationListView
        val holder = adapter.onCreateViewHolder(recycler, 0)
        adapter.onBindViewHolder(holder, 0)
        holder.removeButton.performClick()
        runCatching { activity.supportFragmentManager.beginTransaction().remove(selecting).commitNow() }
    }

    private fun driveTouchHelper(recycler: RecyclerView) {
        val helper = (0 until recycler.itemDecorationCount)
            .map { recycler.getItemDecorationAt(it) }
            .filterIsInstance<ItemTouchHelper>()
            .firstOrNull() ?: return
        val callback = Reflection.get(helper, "mCallback") as ItemTouchHelper.Callback
        val first = recycler.getChildViewHolder(recycler.getChildAt(0))
        val second = if (recycler.childCount > 1) recycler.getChildViewHolder(recycler.getChildAt(1)) else first
        DataStore.serviceState = BaseService.State.Stopped
        DataStore.groupLayoutMode = 0
        callback.getMovementFlags(recycler, first)
        Reflection.invoke(callback, "getDragDirs", recycler, first)
        Reflection.invoke(callback, "getSwipeDirs", recycler, first)
        DataStore.groupLayoutMode = 1
        callback.getMovementFlags(recycler, first)
        Reflection.invoke(callback, "getDragDirs", recycler, first)
        DataStore.serviceState = BaseService.State.Idle
        Reflection.invoke(callback, "getDragDirs", recycler, first)
        callback.onMove(recycler, first, second)
        callback.clearView(recycler, first)
        callback.onSwiped(first, ItemTouchHelper.UP)
        DataStore.serviceState = BaseService.State.Connected
    }

    private fun dispatchScroll(recycler: RecyclerView) {
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 20f, 20f, 0)
        val move = MotionEvent.obtain(0, 40, MotionEvent.ACTION_MOVE, 20f, 120f, 0)
        runCatching {
            recycler.dispatchTouchEvent(down)
            recycler.dispatchTouchEvent(move)
        }
        down.recycle()
        move.recycle()
        recycler.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY),
        )
        recycler.layout(0, 0, 1080, 4000)
        val down2 = MotionEvent.obtain(0, 80, MotionEvent.ACTION_DOWN, 20f, 20f, 0)
        val move2 = MotionEvent.obtain(0, 120, MotionEvent.ACTION_MOVE, 20f, 160f, 0)
        runCatching {
            recycler.dispatchTouchEvent(down2)
            recycler.dispatchTouchEvent(move2)
        }
        down2.recycle()
        move2.recycle()
    }

    private fun invokeMenu(config: ConfigurationFragment, id: Int) {
        val item = config.toolbar.menu.findItem(id) as? MenuItemImpl ?: return
        runCatching { item.invoke() }
    }

    private fun driveWebDav(activity: MainActivity) {
        val backup = BackupFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(R.id.fragment_holder, backup, "backup9")
            .commitNow()
        idleMain()
        val root = backup.requireView()
        DataStore.webdavUsername = "user"
        DataStore.webdavPassword = "pass"
        DataStore.webdavPath = "a/b"
        root.findViewById<View>(R.id.backup_to_webdav).performClick()
        root.findViewById<View>(R.id.restore_from_webdav).performClick()

        webDav(WebMode.CREATE) { clickBackup(root) }
        webDav(WebMode.EXISTS) { clickBackup(root) }
        webDav(WebMode.AUTH) { clickBackup(root) }
        webDav(WebMode.BROKEN) { clickBackup(root) }
        webDav(WebMode.LIST_ZIP) { clickRestore(root) }
        webDav(WebMode.LIST_JSON) { clickRestore(root) }
        webDav(WebMode.LIST_HREF) { clickRestore(root) }
        webDav(WebMode.LIST_LOWER) { clickRestore(root) }
        webDav(WebMode.LIST_EMPTY) { clickRestore(root) }
        webDav(WebMode.AUTH) { clickRestore(root) }
    }

    private fun clickBackup(root: View) {
        val button = root.findViewById<View>(R.id.backup_to_webdav)
        button.performClick()
        button.performClick()
        settleMore()
    }

    private fun clickRestore(root: View) {
        (ShadowDialog.getLatestDialog() as? AlertDialog)?.dismiss()
        val button = root.findViewById<View>(R.id.restore_from_webdav)
        button.performClick()
        button.performClick()
        repeat(40) {
            idleMain()
            val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
            if (dialog?.isShowing == true && dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                idleMain()
                return
            }
            Thread.sleep(50)
        }
    }

    private fun webDav(mode: WebMode, block: () -> Unit) {
        WebDavServer(mode).use { server ->
            DataStore.webdavServer = "http://127.0.0.1:${server.port}"
            block()
        }
    }

    private fun driveAssets() {
        val dirReady = File(app.getExternalFilesDir(null) ?: app.filesDir, "geoip.db")
        dirReady.parentFile?.mkdirs()
        dirReady.writeBytes(byteArrayOf(1, 2, 3))
        File(dirReady.parentFile, "geoip.version.txt").writeText("v1")
        File(dirReady.parentFile, "geosite.db").writeBytes(byteArrayOf(4))
        File(dirReady.parentFile, "custom.db").writeText("custom")
        val controller = Robolectric.buildActivity(AssetsActivity::class.java).setup()
        val assets = controller.get()
        idleMain()
        val refresh = assets.layout.refreshLayout
        val listener = Reflection.get(refresh, "mListener") as SwipeRefreshLayout.OnRefreshListener
        listener.onRefresh()
        idleMain()
        val parent = assets.findViewById<RecyclerView>(R.id.recycler_view)
        val holder = assets.adapter.onCreateViewHolder(parent, 0)
        assets.adapter.onBindViewHolder(holder, 0)
        holder.binding.rulesUpdate.performClick()
        settle()
        DataStore.rulesProvider = 1
        holder.binding.rulesUpdate.performClick()
        settle()
        DataStore.rulesProvider = 4
        holder.binding.rulesUpdate.performClick()
        settle()
        if (assets.adapter.itemCount > 1) {
            assets.adapter.onBindViewHolder(holder, 1)
            holder.binding.rulesUpdate.performClick()
            settle()
        }
        val customIndex = assets.adapter.assets.indexOfFirst { it.name == "custom.db" }
        if (customIndex >= 2) {
            val file = assets.adapter.assets[customIndex]
            assets.adapter.remove(customIndex)
            assets.adapter.undo(listOf(customIndex to file))
            idleMain()
            assets.adapter.remove(customIndex)
            assets.adapter.commit(listOf(customIndex to file))
            idleMain()
        }
        assets.onOptionsItemSelected(item(assets, R.id.action_import_file))
        controller.pause().resume()
        idleMain()
        assets.onBackPressed()
    }

    private fun driveAppList() {
        DataStore.routePackages = "com.example.inet"
        val controller = Robolectric.buildActivity(AppListActivity::class.java).setup()
        val apps = controller.get()
        idleMain()
        val list = apps.findViewById<RecyclerView>(R.id.list)
        settleUntil { (list.adapter?.itemCount ?: 0) > 0 }
        val adapter = list.adapter as? RecyclerView.Adapter<RecyclerView.ViewHolder> ?: return
        if (adapter.itemCount > 0) {
            val holder = adapter.createViewHolder(list, 0)
            adapter.bindViewHolder(holder, 0)
            holder.itemView.performClick()
            adapter.onBindViewHolder(holder, 0, mutableListOf<Any>("switch"))
            idleMain()
        }
        apps.findViewById<EditText>(R.id.search).setText("inet")
        idleMain()
        apps.findViewById<EditText>(R.id.search).setText("")
        apps.findViewById<View>(R.id.show_system_apps).performClick()
        idleMain()
        apps.findViewById<View>(R.id.open_settings)?.performClick()
        apps.onOptionsItemSelected(item(apps, R.id.action_invert_selections))
        settle()
        apps.onOptionsItemSelected(item(apps, R.id.action_clear_selections))
        settle()
        apps.onOptionsItemSelected(item(apps, R.id.action_export_clipboard))
        SagerNet.trySetPrimaryClip("false\ncom.example.inet")
        apps.onOptionsItemSelected(item(apps, R.id.action_import_clipboard))
        idleMain()
        apps.onOptionsItemSelected(item(apps, R.id.action_import_clipboard))
        apps.onKeyUp(android.view.KeyEvent.KEYCODE_MENU, null)
        apps.onSupportNavigateUp()
        runCatching { controller.pause().stop().destroy() }
    }

    private fun driveAppManager() {
        DataStore.proxyApps = false
        DataStore.individual = "com.example.inet"
        DataStore.bypass = false
        val controller = Robolectric.buildActivity(AppManagerActivity::class.java).setup()
        val apps = controller.get()
        idleMain()
        val list = apps.findViewById<RecyclerView>(R.id.list)
        settleUntil { (list.adapter?.itemCount ?: 0) > 0 }
        val adapter = list.adapter as? RecyclerView.Adapter<RecyclerView.ViewHolder> ?: return
        if (adapter.itemCount > 0) {
            val holder = adapter.createViewHolder(list, 0)
            adapter.bindViewHolder(holder, 0)
            holder.itemView.performClick()
            adapter.onBindViewHolder(holder, 0, mutableListOf<Any>("switch"))
            idleMain()
        }
        apps.findViewById<EditText>(R.id.search).setText("example")
        idleMain()
        apps.findViewById<EditText>(R.id.search).setText("10001")
        idleMain()
        apps.findViewById<View>(R.id.show_system_apps).performClick()
        idleMain()
        apps.findViewById<ChipGroup>(R.id.bypassGroup).check(R.id.appProxyModeBypass)
        idleMain()
        apps.findViewById<ChipGroup>(R.id.bypassGroup).check(R.id.appProxyModeOn)
        idleMain()
        apps.findViewById<View>(R.id.autoSelectProxyApps).performClick()
        idleMain()
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        apps.findViewById<View>(R.id.autoSelectProxyApps).performClick()
        idleMain()
        clickDialog(AlertDialog.BUTTON_POSITIVE)
        idleMain()
        apps.onOptionsItemSelected(item(apps, R.id.action_invert_selections))
        settle()
        apps.onOptionsItemSelected(item(apps, R.id.action_clear_selections))
        settle()
        apps.onOptionsItemSelected(item(apps, R.id.action_export_clipboard))
        SagerNet.trySetPrimaryClip("true\nandroid")
        apps.onOptionsItemSelected(item(apps, R.id.action_import_clipboard))
        idleMain()
        SagerNet.trySetPrimaryClip("")
        apps.onOptionsItemSelected(item(apps, R.id.action_import_clipboard))
        apps.onKeyUp(android.view.KeyEvent.KEYCODE_MENU, null)
        apps.findViewById<View>(R.id.open_settings)?.performClick()
        apps.findViewById<ChipGroup>(R.id.bypassGroup).check(R.id.appProxyModeDisable)
        idleMain()
        runCatching { controller.pause().stop().destroy() }
        DataStore.proxyApps = false
    }

    private fun driveScanner() {
        val controller = Robolectric.buildActivity(ScannerActivity::class.java).setup()
        val scanner = controller.get()
        idleMain()
        val points = arrayOf<com.google.zxing.ResultPoint>()
        val socks = Result("socks5://127.0.0.1:1080#coverage", null, points, BarcodeFormat.QR_CODE)
        scanner.onScanResultCallback(socks, true)
        settle()
        scanner.onScanResultCallback(null, true)
        settle()
        scanner.onScanResultCallback(Result("not-a-proxy", null, points, BarcodeFormat.QR_CODE), true)
        settle()
        scanner.onScanResultCallback(
            Result("sn://subscription?url=http://example.com/sub", null, points, BarcodeFormat.QR_CODE),
            true,
        )
        settle()
        scanner.onOptionsItemSelected(item(scanner, R.id.action_import_file))
        runCatching {
            scanner.requestCameraPermissionResult(
                arrayOf(Manifest.permission.CAMERA),
                intArrayOf(PackageManager.PERMISSION_GRANTED),
            )
        }
        runCatching { findFlash(scanner)?.performClick() }
        scanner.onScanResultCallback(Result("socks4://127.0.0.1:1080", null, points, BarcodeFormat.QR_CODE), false)
        settle()
        scanner.onScanResultCallback(socks)
        runCatching {
            scanner.requestCameraPermissionResult(
                arrayOf(Manifest.permission.CAMERA),
                intArrayOf(PackageManager.PERMISSION_DENIED),
            )
        }
        runCatching { controller.pause().stop().destroy() }
    }

    private fun findFlash(scanner: ScannerActivity): View? {
        return scanner.findViewById(R.id.ivFlashlight)
    }

    private fun driveHevStart() {
        installExtraPackages()
        HevTunRuntime.startNative = { _, _ -> true }
        HevTunRuntime.stopNative = { }
        HevTunRuntime.runningNative = { false }
        val profile = runBlocking {
            ProfileManager.createProfile(DataStore.selectedGroupForImport(), socks("hev"))
        }
        val vpn = Robolectric.buildService(SagerVpnService::class.java).create().get()
        val instance = ProxyInstance(profile, vpn)
        runBlocking { instance.init() }
        vpn.data.proxy = instance
        DataStore.bypassLan = true
        DataStore.enableHevTun = true
        DataStore.enableFakeDns = true
        DataStore.ipv6Mode = IPv6Mode.ENABLE
        DataStore.proxyApps = false
        DataStore.bypass = true
        DataStore.appendHttpProxy = true
        DataStore.httpProxyBypass = "example.com\n# comment\n"
        DataStore.meteredNetwork = true
        SagerNet.underlyingNetwork = ShadowNetwork.newInstance(1)
        vpn.upstreamInterfaceName = "wlan0"
        runBlocking { vpn.startProcesses() }
        idleMain()
        assertTrue((vpn.conn?.fd ?: 0) != 0)
        runCatching { vpn.acquireWakeLock() }
        vpn.updateUnderlyingNetwork()
        runCatching { vpn.updateUnderlyingNetwork(vpn.Builder()) }
        DataStore.proxyApps = true
        DataStore.bypass = true
        DataStore.individual = "com.example.inet\nandroid"
        DataStore.enableHevTun = false
        assertTrue(vpn.establishTun() > 0)
        driveAllAppsFilter(vpn)
        runCatching { vpn.onStartCommand(Intent(android.net.VpnService.SERVICE_INTERFACE), 0, 1) }
        vpn.killProcesses()
        idleMain()
        runCatching { vpn.onRevoke() }
        runCatching { instance.close() }
        HevTunRuntime.resetNativeHooks()
        DataStore.enableHevTun = false
        DataStore.ipv6Mode = IPv6Mode.DISABLE
        DataStore.proxyApps = false
    }

    private fun driveAllAppsFilter(vpn: SagerVpnService) {
        val previous = vpn.data.proxy
        PluginManager.initOverride = { PluginManager.InitResult("/bin/true", android.content.pm.ProviderInfo()) }
        val profile = runBlocking {
            ProfileManager.createProfile(DataStore.selectedGroupForImport(), HysteriaBean().apply {
                protocolVersion = 1
                protocol = HysteriaBean.PROTOCOL_FAKETCP
                serverAddress = "127.0.0.1"
                serverPorts = "443"
                name = "fake-apps"
            })
        }
        val instance = ProxyInstance(profile, vpn)
        runBlocking { instance.init() }
        vpn.data.proxy = instance
        DataStore.proxyApps = false
        DataStore.bypass = true
        DataStore.enableHevTun = false
        assertTrue(vpn.establishTun() > 0)
        DataStore.proxyApps = true
        DataStore.bypass = true
        DataStore.individual = app.packageName
        assertTrue(vpn.establishTun() > 0)
        vpn.data.proxy = previous
        PluginManager.initOverride = null
        runCatching { instance.close() }
    }

    private fun socks(name: String) = SOCKSBean().apply {
        this.name = name
        serverAddress = "127.0.0.1"
        serverPort = 1080
    }

    private fun markScrolling(recycler: RecyclerView) {
        runCatching { Reflection.set(recycler, "mScrollState", RecyclerView.SCROLL_STATE_DRAGGING) }
    }

    private fun markIdle(recycler: RecyclerView) {
        runCatching { Reflection.set(recycler, "mScrollState", RecyclerView.SCROLL_STATE_IDLE) }
    }

    private fun item(activity: android.content.Context, id: Int) =
        MenuBuilder(activity).add(0, id, 0, "item")

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

    private enum class WebMode {
        CREATE, EXISTS, AUTH, BROKEN, LIST_ZIP, LIST_JSON, LIST_HREF, LIST_LOWER, LIST_EMPTY
    }

    private class WebDavServer(private val mode: WebMode) : Closeable {
        private val server = ServerSocket(0)
        val port = server.localPort
        private val body: ByteArray = when (mode) {
            WebMode.LIST_JSON -> """{"version":1}""".toByteArray()
            else -> Batch9RobolectricTest.zipBytes("""{"version":1}""")
        }

        init {
            server.soTimeout = 0
            Thread {
                while (!server.isClosed) {
                    try {
                        val socket = server.accept()
                        Thread { handle(socket) }.apply { isDaemon = true }.start()
                    } catch (_: Exception) {
                        break
                    }
                }
            }.apply { isDaemon = true; start() }
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
                val (code, payload, type) = response(method)
                val head = "HTTP/1.1 $code OK\r\nContent-Length: ${payload.size}\r\nContent-Type: $type\r\nConnection: close\r\n\r\n"
                val out = current.getOutputStream()
                out.write(head.toByteArray())
                out.write(payload)
                out.flush()
            }
        }

        private fun response(method: String): Triple<Int, ByteArray, String> {
            if (mode == WebMode.AUTH) return Triple(401, "no".toByteArray(), "text/plain")
            if (mode == WebMode.BROKEN) return Triple(500, "err".toByteArray(), "text/plain")
            if (method == "PROPFIND") {
                return when (mode) {
                    WebMode.CREATE -> Triple(404, ByteArray(0), "text/plain")
                    WebMode.LIST_EMPTY -> Triple(207, "<D:multistatus></D:multistatus>".toByteArray(), "application/xml")
                    WebMode.LIST_JSON -> Triple(207, href("d:href", "nekobox_backup_1.0_20240102_120000.json"), "application/xml")
                    WebMode.LIST_HREF -> Triple(207, href("href", "nekobox_backup_1.0_20240103_120000.zip"), "application/xml")
                    WebMode.LIST_LOWER -> Triple(207, href("d:href", "nekobox_backup_1.0_20240104_120000.zip"), "application/xml")
                    else -> Triple(207, href("D:href", "nekobox_backup_1.0_20240101_120000.zip"), "application/xml")
                }
            }
            if (method == "GET") return Triple(200, body, "application/octet-stream")
            return Triple(201, ByteArray(0), "text/plain")
        }

        private fun href(tag: String, name: String): ByteArray {
            return "<$tag>/a/b/$name</$tag>".toByteArray()
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

    private object Reflection {
        fun get(target: Any, name: String): Any? {
            val field = findField(target.javaClass, name)
            field.isAccessible = true
            return field.get(target)
        }

        fun set(target: Any, name: String, value: Int) {
            val field = findField(target.javaClass, name)
            field.isAccessible = true
            field.setInt(target, value)
        }

        private fun findField(type: Class<*>, name: String): java.lang.reflect.Field {
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

        fun invoke(target: Any, name: String, vararg args: Any?) {
            val types = arrayOf(RecyclerView::class.java, RecyclerView.ViewHolder::class.java)
            val method = target.javaClass.getDeclaredMethod(name, *types)
            method.isAccessible = true
            method.invoke(target, *args)
        }
    }

    companion object {
        fun zipBytes(json: String): ByteArray {
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
