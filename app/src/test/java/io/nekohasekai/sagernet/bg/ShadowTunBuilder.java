package io.nekohasekai.sagernet.bg;

import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

import java.io.File;
import java.io.IOException;

/**
 * {@code VpnService.Builder.addAddress} calls the hidden
 * {@code InetAddress.parseNumericAddress}, which the desktop JVM does not have.
 * These stand-ins let {@code establishTun} run its own lines. Production still
 * uses the platform builder.
 */
@Implements(android.net.VpnService.Builder.class)
public class ShadowTunBuilder {
    public static volatile boolean establishReturnsNull;

    @RealObject
    android.net.VpnService.Builder real;

    @Implementation
    protected android.net.VpnService.Builder addAddress(String address, int prefixLength) {
        return real;
    }

    @Implementation
    protected android.net.VpnService.Builder addRoute(String address, int prefixLength) {
        return real;
    }

    @Implementation
    protected android.net.VpnService.Builder addDnsServer(String address) {
        return real;
    }

    @Implementation
    protected android.net.VpnService.Builder addDisallowedApplication(String packageName)
            throws PackageManager.NameNotFoundException {
        return requireInstalled(packageName);
    }

    @Implementation
    protected android.net.VpnService.Builder addAllowedApplication(String packageName)
            throws PackageManager.NameNotFoundException {
        return requireInstalled(packageName);
    }

    private android.net.VpnService.Builder requireInstalled(String packageName)
            throws PackageManager.NameNotFoundException {
        RuntimeEnvironment.getApplication().getPackageManager().getPackageInfo(packageName, 0);
        return real;
    }

    @Implementation
    protected ParcelFileDescriptor establish() {
        if (establishReturnsNull) return null;
        try {
            File file = File.createTempFile("tun", ".fd");
            file.deleteOnExit();
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
        } catch (IOException e) {
            return null;
        }
    }
}
