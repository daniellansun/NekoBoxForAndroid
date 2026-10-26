package io.nekohasekai.sagernet

import android.content.Intent
import android.os.Looper
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.ProxyService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.MemoryProxyDao
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds startup inside [BaseService.Interface.connectPause] and checks that
 * [BaseService.Interface.stopRunner] cancels that job. The pause is null in
 * production, so this does not start a core or open a VPN.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = CoverageApplication::class)
class ConnectingJobCancelTest {

    @After
    fun clearPause() {
        BaseService.Interface.connectPause = null
        val service = DataStore.baseService
        if (service != null && service.data.state.canStop) {
            runCatching { service.stopRunner(false, "test cleanup") }
            idleMain()
        }
        DataStore.baseService = null
        DataStore.vpnService = null
        DataStore.serviceState = BaseService.State.Stopped
        DataStore.selectedProxy = 0L
    }

    @Test(timeout = 60_000)
    fun stopWhileConnectingCancelsStartupAndLeavesStopped() {
        BaseService.Interface.connectPause = null
        val entered = AtomicBoolean(false)
        val cancelCause = AtomicReference<Throwable>(null)
        BaseService.Interface.connectPause = {
            entered.set(true)
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { cause ->
                    cancelCause.set(cause)
                }
            }
        }

        val dao = SagerDatabase.proxyDao as MemoryProxyDao
        dao.rows.add(
            ProxyEntity(
                id = 7L,
                socksBean = SOCKSBean().apply {
                    initializeDefaultValues()
                    name = "pause"
                    serverAddress = "127.0.0.1"
                    serverPort = 1
                },
            ),
        )
        DataStore.selectedProxy = 7L
        DataStore.serviceState = BaseService.State.Stopped

        val service = Robolectric.buildService(ProxyService::class.java).create().get()
        try {
            val startResult = service.onStartCommand(Intent(), 0, 1)
            assertEquals(android.app.Service.START_NOT_STICKY, startResult)
            assertTrue(entered.get())
            assertEquals(BaseService.State.Connecting, service.data.state)
            assertEquals(BaseService.State.Connecting, DataStore.serviceState)
            val job = service.data.connectingJob
            assertNotNull(job)
            assertTrue(job!!.isActive)
            assertNotNull(service.data.proxy)
            assertFalse(service.data.proxy!!.isInitialized())
            assertNull(service.data.notification)
            assertNull(cancelCause.get())

            service.stopRunner(false, "cancelled")
            idleUntil { service.data.state == BaseService.State.Stopped }

            val cause = cancelCause.get()
            assertTrue(
                "startup suspend was not cancelled: $cause",
                cause is CancellationException,
            )
            assertFalse(job.isActive)
            assertTrue(job.isCompleted)
            assertEquals(BaseService.State.Stopped, service.data.state)
            assertEquals(BaseService.State.Stopped, DataStore.serviceState)
            assertNull(service.data.connectingJob)
            assertNull(service.data.proxy)
            assertNull(service.data.notification)
            assertNull(DataStore.baseService)
        } finally {
            BaseService.Interface.connectPause = null
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

    private fun idleUntil(ready: () -> Boolean) {
        if (ready()) return
        repeat(50) {
            idleMain()
            if (ready()) return
            Thread.sleep(20)
        }
    }
}
