package moe.matsuri.nb4a.hevtun

import android.content.Context
import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.bg.VpnService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST
import java.io.File

/**
 * Manages the in-process hev-socks5-tunnel runtime. The tunnel reads packets
 * from the VPN file descriptor and forwards TCP/UDP connections into the
 * sing-box mixed inbound on loopback.
 */
object HevTunRuntime {

    private const val CONFIG_FILE = "hev-socks5-tunnel.yaml"

    private var running = false

    /**
     * Native entry points. Production calls [HevTunNative]. Tests replace these
     * so the start/stop state machine can run without libhev-socks5-tunnel.so.
     */
    internal var runningNative: () -> Boolean = { HevTunNative.TProxyIsRunning() }
    internal var startNative: (String, Int) -> Boolean =
        { path, fd -> HevTunNative.TProxyStartService(path, fd) }
    internal var stopNative: () -> Unit = { HevTunNative.TProxyStopService() }

    internal fun resetNativeHooks() {
        runningNative = { HevTunNative.TProxyIsRunning() }
        startNative = { path, fd -> HevTunNative.TProxyStartService(path, fd) }
        stopNative = { HevTunNative.TProxyStopService() }
    }

    fun isRunning(): Boolean {
        return try {
            runningNative()
        } catch (e: UnsatisfiedLinkError) {
            false
        } catch (e: ExceptionInInitializerError) {
            // JVM unit tests (and a missing .so) wrap loadLibrary in this error.
            false
        }
    }

    @Synchronized
    fun start(context: Context, tunFd: Int) {
        stop()
        val configFile = File(context.filesDir, CONFIG_FILE)
        configFile.writeText(buildConfig())
        check(startNative(configFile.absolutePath, tunFd)) {
            "Failed to start hev-socks5-tunnel"
        }
        running = true
    }

    @Synchronized
    fun stop() {
        if (!running) return
        try {
            stopNative()
        } finally {
            running = false
        }
    }

    internal fun buildConfig(): String {
        val useAuth = DataStore.mixedInboundHasAuth
        return buildString {
            appendLine("tunnel:")
            appendLine("  mtu: ${DataStore.mtu}")
            appendLine("  ipv4: '${VpnService.PRIVATE_VLAN4_CLIENT}'")
            if (DataStore.ipv6Mode != IPv6Mode.DISABLE) {
                appendLine("  ipv6: '${VpnService.PRIVATE_VLAN6_CLIENT}'")
            }
            appendLine("socks5:")
            appendLine("  port: ${DataStore.mixedPort}")
            appendLine("  address: '$LOCALHOST'")
            appendLine("  udp: 'udp'")
            appendLine("  pipeline: true")
            if (useAuth) {
                appendLine("  username: '${DataStore.mixedUsername.yamlEscape()}'")
                appendLine("  password: '${DataStore.mixedSecret.yamlEscape()}'")
            }
            if (DataStore.enableFakeDns) {
                appendLine("mapdns:")
                appendLine("  address: '${VpnService.PRIVATE_VLAN4_ROUTER}'")
                appendLine("  port: 53")
                appendLine("  network: '${VpnService.HEV_MAPDNS_VLAN4}'")
                appendLine("  netmask: '255.192.0.0'")
                appendLine("  cache-size: 10000")
            }
            appendLine("misc:")
            appendLine("  log-level: 'warn'")
        }
    }

    private fun String.yamlEscape(): String {
        return replace("'", "''")
    }
}
