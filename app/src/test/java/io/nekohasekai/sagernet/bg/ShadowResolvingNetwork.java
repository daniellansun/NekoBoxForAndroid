package io.nekohasekai.sagernet.bg;

import android.net.Network;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * {@code Network.getAllByName} calls the hidden {@code InetAddress.getAllByNameOnNet}.
 * The desktop JVM does not have that method, so the fake-DNS lookup cannot run.
 */
@Implements(Network.class)
public class ShadowResolvingNetwork {
    /**
     * When non-null, {@link #getAllByName} throws for this exact host.
     * Blank hosts still throw. Every other host still resolves to 127.0.0.1.
     */
    public static volatile String unknownHost;

    @Implementation
    protected static Network newInstance(int netId) {
        return ReflectionHelpers.callConstructor(
                Network.class,
                ReflectionHelpers.ClassParameter.from(int.class, netId)
        );
    }

    @Implementation
    protected InetAddress[] getAllByName(String host) throws UnknownHostException {
        if (host == null || host.trim().isEmpty()) {
            throw new UnknownHostException(String.valueOf(host));
        }
        String blocked = unknownHost;
        if (blocked != null && blocked.equals(host)) {
            throw new UnknownHostException(host);
        }
        return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
    }
}
