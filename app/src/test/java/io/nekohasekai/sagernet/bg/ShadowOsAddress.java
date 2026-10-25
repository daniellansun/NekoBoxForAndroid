package io.nekohasekai.sagernet.bg;

import android.system.Os;
import android.system.OsConstants;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Robolectric's {@code Os.inet_pton} returns null, so numeric routes and
 * {@code parseNumericAddress} never succeed on the JVM. Parsing stays in the test
 * shadow; production still calls the platform method.
 */
@Implements(Os.class)
public class ShadowOsAddress {
    @Implementation
    protected static long sysconf(int name) {
        return 4096L;
    }

    @Implementation
    protected static InetAddress inet_pton(int family, String address) {
        if (address == null || address.isEmpty()) return null;
        try {
            if (family == OsConstants.AF_INET) {
                byte[] v4 = parseV4(address);
                return v4 == null ? null : InetAddress.getByAddress(v4);
            }
            if (family == OsConstants.AF_INET6 && address.indexOf(':') >= 0) {
                InetAddress parsed = InetAddress.getByName(address);
                return parsed instanceof Inet6Address ? parsed : null;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static byte[] parseV4(String address) {
        String[] parts = address.split("\\.", -1);
        if (parts.length != 4) return null;
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            int value;
            try {
                value = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (value < 0 || value > 255) return null;
            if (!Integer.toString(value).equals(parts[i])) return null;
            out[i] = (byte) value;
        }
        return out;
    }
}
