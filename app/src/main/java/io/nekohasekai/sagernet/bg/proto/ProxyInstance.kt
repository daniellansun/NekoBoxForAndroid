package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.ServiceNotification
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.ktx.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import moe.matsuri.nb4a.utils.JavaUtil

class ProxyInstance(profile: ProxyEntity, var service: BaseService.Interface? = null) :
    BoxInstance(profile) {

    var notTmp = true

    var lastSelectorGroupId = -1L
    var displayProfileName = ServiceNotification.genTitle(profile)

    // for TrafficLooper
    var looper: TrafficLooper? = null

    private val looperLock = Any()
    @Volatile
    private var closed = false

    override fun buildConfig() {
        super.buildConfig()
        lastSelectorGroupId = super.config.selectorGroupId
        //
        // The generated JSON contains outbound credentials. Keep the size for
        // diagnosis and leave the body out of neko.log.
        if (notTmp) Logs.d("sing-box config built (${config.config.length} bytes)")
        if (notTmp && BuildConfig.DEBUG) Logs.d(JavaUtil.gson.toJson(config.trafficMap))
    }

    // only use this in temporary instance
    fun buildConfigTmp() {
        notTmp = false
        buildConfig()
    }

    override suspend fun init() {
        super.init()
        pluginConfigs.forEach { (port, plugin) ->
            Logs.d("plugin config ready for port $port (${plugin.second.length} bytes)")
        }
    }

    override suspend fun loadConfig() {
        super.loadConfig()
    }

    override fun launch() {
        box.setAsMain()
        super.launch() // start box
        val svc = service ?: return
        // Assign and start under one lock so close() always sees a joinable looper
        // (or never starts one). The old DefaultDispatcher hand-off could start the
        // loop after close() had already finished.
        // Own the SupervisorJob so a close-before-start path can cancel it
        // instead of abandoning an unused scope (Test residual on 8261dab).
        val scopeJob = SupervisorJob()
        val created = TrafficLooper(
            svc.data,
            CoroutineScope(Dispatchers.Default + scopeJob),
        )
        synchronized(looperLock) {
            if (closed) {
                scopeJob.cancel()
                return
            }
            looper = created
            created.start()
        }
    }

    override fun close() {
        val toStop = synchronized(looperLock) {
            closed = true
            val current = looper
            looper = null
            current
        }
        // Stop traffic accounting before tearing down the box so the loop cannot
        // queryStats on a closed instance.
        runBlocking {
            toStop?.stop()
        }
        super.close()
    }
}
