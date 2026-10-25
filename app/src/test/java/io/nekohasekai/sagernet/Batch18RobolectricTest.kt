package io.nekohasekai.sagernet

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.FragmentActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.bg.ShadowDnsResolver
import io.nekohasekai.sagernet.bg.ShadowOsAddress
import io.nekohasekai.sagernet.bg.ShadowProcessPhoenix
import io.nekohasekai.sagernet.bg.ShadowResolvingNetwork
import io.nekohasekai.sagernet.bg.ShadowTunBuilder
import io.nekohasekai.sagernet.bg.VpnService as SagerVpnService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.v2ray.isTLS
import io.nekohasekai.sagernet.fmt.v2ray.setTLS
import io.nekohasekai.sagernet.fmt.v2ray.toUriVMessVLESSTrojan
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.fileDescriptor
import io.nekohasekai.sagernet.ktx.int
import io.nekohasekai.sagernet.ktx.needRestart
import io.nekohasekai.sagernet.ktx.parseNumericAddress
import io.nekohasekai.sagernet.ktx.scrollTo
import io.nekohasekai.sagernet.ui.AboutFragment
import io.nekohasekai.sagernet.ui.ConfigurationFragment
import io.nekohasekai.sagernet.ui.GroupSettingsActivity
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.RouteSettingsActivity
import io.nekohasekai.sagernet.utils.CrashHandler
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.widget.StatsBar
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import libcore.Libcore
import moe.matsuri.nb4a.plugin.Plugins
import moe.matsuri.nb4a.utils.NGUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowNetwork

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
class Batch18RobolectricTest {

    private var mainController: org.robolectric.android.controller.ActivityController<MainActivity>? = null
    private var selectedGroup = 0L

    @Before
    fun drainStaleMain() {
        clearListenerList(ProfileManager, "listeners")
        clearListenerList(ProfileManager, "ruleListeners")
        clearListenerList(GroupManager, "listeners")
        selectedGroup = DataStore.currentGroupId()
        DataStore.groupLayoutMode = 0
        ShadowResolvingNetwork.unknownHost = null
        idleMain()
    }

    @After
    fun restoreOverrides() {
        closeMain()
        DataStore.serviceState = BaseService.State.Idle
        DataStore.selectedProxy = 0L
        DataStore.selectedGroup = selectedGroup
        DataStore.runningTest = false
        DataStore.dirty = false
        DataStore.editingId = 0L
        DataStore.connectionTestConcurrent = 5
        DataStore.groupLayoutMode = 0
        DataStore.serverConfig = ""
        SagerNet.underlyingNetwork = null
        BaseService.Binder.restartProcessOverride = null
        Libcore.httpBody = ""
        ShadowResolvingNetwork.unknownHost = null
        runCatching { ShadowDialog.getLatestDialog()?.dismiss() }
        runCatching { Runtime.getRuntime().exec(arrayOf("pkill", "-f", "echo b18")).waitFor() }
        idleMain()
    }

    @Test(timeout = 120_000)
    fun processPoolCompletesCleanup() {
        val pool = GuardedProcessPool { }
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        runCatching {
            pool.start(listOf("/bin/sh", "-c", "echo b18 >&2; exec sleep 30"))
        }
        Thread.sleep(200)
        pumpDue(400)
        runCatching { pool.close(scope) }
        pumpDue(2_000)
        scope.cancel()
    }

    @Test(timeout = 60_000)
    fun vlessAndTrojanUriBranches() {
        val vless = VMessBean().apply {
            alterId = -1
            initializeDefaultValues()
            serverAddress = "10.18.0.1"
            serverPort = 443
            uuid = "11111111-1111-1111-1111-111111111111"
            name = "b18-vless"
            encryption = "xtls-rprx-vision"
            vlessEncryption = "mlkem768x25519plus"
        }
        vless.type = "http"
        vless.host = "h.example"
        vless.path = "/p"
        vless.security = "none"
        runCatching { vless.toUriVMessVLESSTrojan(false) }
        vless.type = "kcp"
        vless.headerType = "srtp"
        vless.mKcpSeed = "seed"
        vless.kcpMtu = 1350
        vless.kcpTti = 20
        vless.kcpCwndMultiplier = 2
        runCatching { vless.toUriVMessVLESSTrojan(false) }
        vless.type = "xhttp"
        vless.host = "xh.example"
        vless.path = "/xh"
        vless.xhttpMode = "stream-one"
        vless.xhttpExtra = """{"path":"/z"}"""
        runCatching { vless.toUriVMessVLESSTrojan(false) }
        vless.type = "grpc"
        vless.path = "svc.name"
        runCatching { vless.toUriVMessVLESSTrojan(false) }
        vless.type = "ws"
        vless.setTLS(true)
        vless.sni = "sni.example"
        vless.alpn = "h2"
        vless.certificates = "cert"
        vless.allowInsecure = true
        vless.utlsFingerprint = "chrome"
        vless.realityPubKey = "pbk"
        vless.realityShortId = "abcd"
        vless.packetEncoding = 1
        vless.wsMaxEarlyData = 2048
        vless.earlyDataHeaderName = "Sec-WebSocket-Protocol"
        runCatching { vless.toUriVMessVLESSTrojan(false) }
        check(vless.isTLS())

        val trojan = TrojanBean().apply {
            initializeDefaultValues()
            password = "secret"
            serverAddress = "10.18.0.2"
            serverPort = 443
            name = "b18-trojan"
            type = "grpc"
            path = "trojan.svc"
            realityPubKey = "tpbk"
            realityShortId = "sid"
            packetEncoding = 1
        }
        runCatching { trojan.toUriVMessVLESSTrojan(true) }
    }

    @Test(timeout = 180_000)
    fun configurationMenusPingAndExport() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        val groupId = DataStore.currentGroupId()
        runBlockingCreate(groupId, "b18-a", "10.18.1.1")
        runBlockingCreate(groupId, "b18-b", "10.18.1.2")
        val activity = openMain()
        val config = configuration(activity) ?: return
        val fragment = config.getCurrentGroupFragment() ?: return
        runCatching { fragment.adapter?.reloadProfiles() }
        idleMain()
        val list = fragment.configurationListView
        list.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        list.layout(0, 0, 1080, 1920)
        idleMain()
        val adapter = fragment.adapter
        if (adapter != null && adapter.itemCount >= 2) {
            runCatching { adapter.move(1, 0) }
            idleMain()
            runCatching { adapter.move(0, 1) }
            idleMain()
        }
        val child = if (list.childCount > 0) list.getChildAt(0) else null
        val holder = if (child != null) list.getChildViewHolder(child) else null
        if (holder != null) {
            val shown = adapter?.let { itemAt(it, 0) }
            if (shown != null) {
                invokeMenu(holder, "showDoubleColumnMenu", holder.itemView, shown)
                clickPopupListener(R.id.action_edit)
                finishStarted()
                invokeMenu(holder, "showDoubleColumnMenu", holder.itemView, shown)
                clickPopupListener(R.id.action_share)
                clickPopupListener(R.id.action_delete)
                dismissDialogs()
            }
            val bad = ProxyEntity()
            bad.type = 999
            setField(holder, "entity", bad)
            runCatching {
                holder.javaClass.getMethod("onMenuItemClick", android.view.MenuItem::class.java)
                    .invoke(holder, item(activity, R.id.action_standard_clipboard))
            }
            idleMain()
        }
        DataStore.serverConfig = """{"b18":true}"""
        val uri = Uri.parse("content://coverage/b18-out.json")
        shadowOf(app.contentResolver).registerOutputStream(uri, object : ByteArrayOutputStream() {
            override fun write(b: ByteArray, off: Int, len: Int) {
                throw IOException("b18-export")
            }
        })
        runCatching { deliver(config, "exportConfig", uri) }
        idleMain()
        blankDomainPing(config)
        runCatching { config.needRestart() }
        idleMain()
        clickSnackAction(activity)
        idleFor(700)
        NGUtil.openUri(activity, "https://example.com/b18")
        finishStarted()
        closeMain()
    }

    @Test(timeout = 120_000)
    fun aboutDialogRouteStatsAndUtils() {
        BaseService.Binder.restartProcessOverride = { ShadowProcessPhoenix.rebirths++ }
        val activity = openMain()
        aboutUpdate(activity)
        routeSuperReturns()
        parcelDescribe()
        statsHideAfterStart(activity)
        attachedScroll()
        runCatching { Socket().fileDescriptor }
        runCatching { FileDescriptor().int }
        runCatching { CrashHandler.buildReportHeader() }
        restartCallbackState()
        closeMain()
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
        runCatching { "::1".parseNumericAddress() }
        runCatching { "2001:db8::18".parseNumericAddress() }
        val utils = Class.forName("io.nekohasekai.sagernet.ktx.UtilsKt")
        val delegate = utils.getDeclaredField("parseNumericAddress\$delegate")
        delegate.isAccessible = true
        val lazy = delegate.get(null)
        val value = lazy.javaClass.getDeclaredField("_value")
        value.isAccessible = true
        value.set(lazy, InetAddress::class.java.getMethod("getByName", String::class.java))
        runCatching { "::1".parseNumericAddress() }
        runCatching { "127.0.0.1".parseNumericAddress() }
    }

    private fun blankDomainPing(config: ConfigurationFragment) {
        val host = "b18.invalid"
        val group = kotlinx.coroutines.runBlocking {
            GroupManager.createGroup(ProxyGroup(name = "b18-domain"))
        }
        DataStore.selectedGroup = group.id
        val profile = kotlinx.coroutines.runBlocking {
            val created = ProfileManager.createProfile(group.id, socks("b18-domain", host, 9))
            val bean = created.socksBean ?: return@runBlocking created
            bean.serverAddress = host
            created.socksBean = bean
            ProfileManager.updateProfile(created)
            created
        }
        ShadowResolvingNetwork.unknownHost = host
        SagerNet.underlyingNetwork = ShadowNetwork.newInstance(18)
        DataStore.connectionTestConcurrent = 1
        DataStore.runningTest = false
        config.pingTest(false)
        poll(40, 50) {
            SagerDatabase.proxyDao.getByGroup(group.id).any { it.status == 2 }
        }
        clickDialog(AlertDialog.BUTTON_NEGATIVE)
        dismissDialogs()
        settleUntil { !DataStore.runningTest }
        DataStore.runningTest = false
        DataStore.selectedGroup = selectedGroup
        SagerNet.underlyingNetwork = null
        ShadowResolvingNetwork.unknownHost = null
        profile.status = profile.status
    }

    private fun aboutUpdate(activity: MainActivity) {
        dismissDialogs()
        activity.displayFragmentWithId(R.id.nav_about)
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        val about = activity.supportFragmentManager.fragments
            .filterIsInstance<AboutFragment>()
            .lastOrNull() ?: return
        var content = about.childFragmentManager.fragments
            .filterIsInstance<AboutFragment.AboutContent>()
            .lastOrNull()
        if (content == null) {
            content = AboutFragment.AboutContent()
            runCatching {
                about.childFragmentManager.beginTransaction()
                    .replace(R.id.about_fragment_holder, content)
                    .commitNowAllowingStateLoss()
            }
            content = about.childFragmentManager.fragments
                .filterIsInstance<AboutFragment.AboutContent>()
                .lastOrNull()
        }
        if (content == null) return
        PackageCache.awaitLoadSync()
        val broken = PackageInfo().apply {
            packageName = "io.nekohasekai.sagernet.plugin.b18.empty"
            versionName = "1"
            providers = emptyArray()
        }
        val plugin = PackageInfo().apply {
            packageName = "io.nekohasekai.sagernet.plugin.b18"
            versionName = "1"
            providers = arrayOf(ProviderInfo().apply {
                packageName = packageName
                metaData = Bundle().apply { putString(Plugins.METADATA_KEY_ID, "trojan-go-b18") }
            })
        }
        val previous = runCatching { PackageCache.installedPluginPackages }.getOrNull()
        PackageCache.installedPluginPackages = mapOf(
            broken.packageName to broken,
            plugin.packageName to plugin,
        )
        runCatching {
            AboutFragment.AboutContent::class.java
                .getDeclaredMethod("getMaterialAboutList", Context::class.java)
                .apply { isAccessible = true }
                .invoke(content, activity)
        }
        if (previous != null) PackageCache.installedPluginPackages = previous
        Libcore.httpBody = """{"name":"coverage-9.9.9","html_url":"https://example.com/b18"}"""
        val before = ShadowDialog.getLatestDialog()
        runCatching { content.checkUpdate(false) }
        poll(30, 80) {
            val dialog = ShadowDialog.getLatestDialog() as? AlertDialog ?: return@poll false
            if (dialog === before || !dialog.isShowing) return@poll false
            val message = dialog.findViewById<TextView>(android.R.id.message)?.text?.toString().orEmpty()
            if (!message.contains("9.9.9")) return@poll false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick() == true
        }
        finishStarted()
        idleMain()
        dismissDialogs()
    }

    private fun routeSuperReturns() {
        val controller = Robolectric.buildActivity(RouteSettingsActivity::class.java).setup()
        idleMain()
        val frag = controller.get().supportFragmentManager.fragments
            .filterIsInstance<RouteSettingsActivity.MyPreferenceFragmentCompat>()
            .lastOrNull()
        if (frag != null) {
            val pref = EditTextPreference(controller.get())
            pref.key = "b18-route"
            pref.dialogTitle = "b18"
            runCatching { frag.onDisplayPreferenceDialog(pref) }
            idleMain()
            dismissDialogs()
        }
        runCatching { controller.pause().stop().destroy() }
        idleMain()
    }

    private fun parcelDescribe() {
        val route = RouteSettingsActivity.ProfileIdArg(7L)
        route.describeContents()
        roundTrip(route)
        val group = GroupSettingsActivity.GroupIdArg(8L)
        group.describeContents()
        roundTrip(group)
    }

    private fun roundTrip(value: android.os.Parcelable) {
        val parcel = Parcel.obtain()
        value.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val creator = value.javaClass.getField("CREATOR").get(null) as android.os.Parcelable.Creator<*>
        creator.createFromParcel(parcel)
        parcel.recycle()
    }

    private fun statsHideAfterStart(activity: MainActivity) {
val values = Class.forName("io.nekohasekai.sagernet.widget.StatsBar\$Transition")
            .getDeclaredMethod("values")
        values.isAccessible = true
        values.invoke(null)
        val host = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val content = host.get().findViewById<ViewGroup>(android.R.id.content)
        val themed = ContextThemeWrapper(host.get(), R.style.Theme_SagerNet)
        val hide = StatsBar(themed)
        content.addView(hide, ViewGroup.LayoutParams(1080, 48))
        hide.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(48, View.MeasureSpec.EXACTLY),
        )
        hide.layout(0, 0, 1080, 48)
        hide.syncMainControls(true, BaseService.State.Stopped, false, true)
        idleMain()

        val shrink = StatsBar(themed)
        content.addView(shrink, ViewGroup.LayoutParams(1080, 48))
        shrink.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(48, View.MeasureSpec.EXACTLY),
        )
        shrink.layout(0, 0, 1080, 48)
        shrink.syncMainControls(true, BaseService.State.Stopped, false, true)
        shrink.layout(0, 0, 1080, 0)
        idleMain()

        val faded = StatsBar(themed)
        content.addView(faded, ViewGroup.LayoutParams(200, 40))
        faded.measure(
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY),
        )
        faded.layout(0, 0, 200, 40)
        faded.alpha = 0f
        setField(faded, "pendingTransition", null)
        faded.layout(0, 0, 200, 40)

        val bar = activity.findViewById<StatsBar>(R.id.stats)
        if (bar != null) {
            bar.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY),
            )
            bar.layout(0, 0, 1080, 80)
            bar.syncMainControls(true, BaseService.State.Stopped, false, true)
            bar.layout(0, 0, 1080, 0)
            idleFor(250)
        }
        runCatching { host.pause().stop().destroy() }
    }

    private fun attachedScroll() {
        val host = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val content = host.get().findViewById<ViewGroup>(android.R.id.content)
        val recycler = RecyclerView(host.get())
        recycler.layoutManager = LinearLayoutManager(host.get())
        content.addView(recycler, ViewGroup.LayoutParams(200, 200))
        recycler.measure(
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        )
        recycler.layout(0, 0, 200, 200)
        recycler.scrollTo(0)
        recycler.scrollTo(RecyclerView.NO_POSITION, true)
        idleFor(400)
        runCatching { host.pause().stop().destroy() }
    }

    private fun restartCallbackState() {
        val type = Class.forName("io.nekohasekai.sagernet.ktx.RestartCallback")
        val ctor = type.declaredConstructors.first()
        ctor.isAccessible = true
        val callback = ctor.newInstance(object : kotlin.jvm.functions.Function0<kotlin.Unit> {
            override fun invoke() = Unit
        })
        val method = type.declaredMethods.first { it.name == "stateChanged" }
        method.isAccessible = true
        method.invoke(callback, BaseService.State.Idle, null, null)
    }

    private fun runBlockingCreate(groupId: Long, name: String, address: String) {
        kotlinx.coroutines.runBlocking {
            ProfileManager.createProfile(groupId, socks(name, address, 1080))
        }
    }

    private fun socks(name: String, address: String, port: Int) = SOCKSBean().apply {
        initializeDefaultValues()
        this.name = name
        serverAddress = address
        serverPort = port
    }

    private fun itemAt(adapter: RecyclerView.Adapter<*>, index: Int): ProxyEntity? {
        val ids = adapter.javaClass.getDeclaredField("configurationIdList")
        ids.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val list = ids.get(adapter) as List<Long>
        if (index !in list.indices) return null
        return ProfileManager.getProfile(list[index])
    }

    private fun invokeMenu(holder: RecyclerView.ViewHolder, name: String, anchor: View, entity: ProxyEntity) {
        runCatching {
            val method = holder.javaClass.getDeclaredMethod(name, View::class.java, ProxyEntity::class.java)
            method.isAccessible = true
            method.invoke(holder, anchor, entity)
        }
        idleMain()
    }

    private fun clickPopupListener(id: Int) {
        val popup = runCatching { org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu() }.getOrNull() ?: return
        val field = popup.javaClass.getDeclaredField("mMenuItemClickListener")
        field.isAccessible = true
        val listener = field.get(popup) as? PopupMenu.OnMenuItemClickListener ?: return
        val menuItem = popup.menu.findItem(id) ?: return
        runCatching { listener.onMenuItemClick(menuItem) }
        idleMain()
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

    private fun configuration(activity: MainActivity): ConfigurationFragment? {
        activity.supportFragmentManager.executePendingTransactions()
        idleMain()
        return activity.supportFragmentManager.fragments.filterIsInstance<ConfigurationFragment>().lastOrNull()
    }

    private fun item(context: Context, id: Int) = MenuBuilder(context).add(0, id, 0, "item")

    private fun clickDialog(which: Int) {
        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog ?: return
        dialog.getButton(which)?.performClick()
        idleMain()
    }

    private fun clickSnackAction(activity: Activity) {
        val actionId = com.google.android.material.R.id.snackbar_action
        val root = activity.window?.decorView ?: return
        poll(20, 40) {
            val action = findView(root, actionId) ?: return@poll false
            action.performClick()
            true
        }
        idleMain()
    }

    private fun findView(root: View, id: Int): View? {
        if (root.id == id) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findView(root.getChildAt(i), id)?.let { return it }
            }
        }
        return null
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

    private fun dismissDialogs() {
        repeat(4) {
            val dialog = ShadowDialog.getLatestDialog() ?: return
            if (!dialog.isShowing) return
            runCatching { dialog.dismiss() }
            idleMain()
        }
    }

    private fun idleMain() {
        repeat(8) {
            try {
                shadowOf(Looper.getMainLooper()).idle()
                return
            } catch (e: RuntimeException) {
                val message = e.message.orEmpty()
                val known = message.contains("synchronization barrier") ||
                    message.contains("Already resumed") ||
                    message.contains("stable ID") ||
                    message.contains("computing a layout") ||
                    message.contains("not attached") ||
                    message.contains("getDialogTitle")
                if (!known) throw e
            }
        }
    }

    private fun idleFor(millis: Long) {
        runCatching { ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS) }
        idleMain()
    }

    private fun pumpDue(budgetMs: Long) {
        val shadow = shadowOf(Looper.getMainLooper())
        val deadline = System.nanoTime() + budgetMs * 1_000_000L
        var steps = 0
        while (System.nanoTime() < deadline && steps < 80) {
            steps++
            val next = runCatching { shadow.nextScheduledTaskTime }.getOrNull()
            if (shadow.isIdle || next == null || next.toMillis() > 30) {
                Thread.sleep(20)
            } else {
                runCatching { shadow.runOneTask() }
            }
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

    private fun clearListenerList(owner: Any, name: String) {
        val field = owner.javaClass.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(owner) as MutableList<Any>).clear()
    }

    private fun setField(target: Any, name: String, value: Any?) {
        val field = generateSequence<Class<*>>(target.javaClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField(name) }.getOrNull() }
            .first()
        field.isAccessible = true
        field.set(target, value)
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
}
