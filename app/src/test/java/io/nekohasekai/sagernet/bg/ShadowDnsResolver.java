package io.nekohasekai.sagernet.bg;

import android.net.DnsResolver;
import android.net.Network;
import android.os.CancellationSignal;
import android.system.ErrnoException;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Invokes {@link DnsResolver.Callback} on the calling thread so JVM tests can
 * cover {@code LocalResolverImpl} without a real network stack.
 */
@Implements(DnsResolver.class)
public class ShadowDnsResolver {

    public static int mode;

    public static void reset() {
        mode = 0;
    }

    @Implementation
    protected static DnsResolver getInstance() {
        return ReflectionHelpers.callConstructor(DnsResolver.class);
    }

    @Implementation
    protected void query(
            Network network,
            String domain,
            int flags,
            Executor executor,
            CancellationSignal signal,
            DnsResolver.Callback<? super List<InetAddress>> callback
    ) {
        answer(callback, addresses());
    }

    @Implementation
    protected void query(
            Network network,
            String domain,
            int nsType,
            int flags,
            Executor executor,
            CancellationSignal signal,
            DnsResolver.Callback<? super List<InetAddress>> callback
    ) {
        answer(callback, addresses());
    }

    @Implementation
    protected void rawQuery(
            Network network,
            byte[] message,
            int flags,
            Executor executor,
            CancellationSignal signal,
            DnsResolver.Callback<? super byte[]> callback
    ) {
        answer(callback, new byte[]{0, 1});
    }

    private static List<InetAddress> addresses() {
        try {
            return Collections.singletonList(InetAddress.getByName("127.0.0.1"));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static void answer(DnsResolver.Callback callback, Object value) {
        int step = mode++;
        if (step % 4 == 0) {
            callback.onAnswer(value, 0);
        } else if (step % 4 == 1) {
            callback.onAnswer(value, 3);
        } else if (step % 4 == 2) {
            callback.onError(dnsError(new ErrnoException("recv", 11)));
        } else {
            callback.onError(dnsError(new IOException("dns")));
        }
    }

    private static DnsResolver.DnsException dnsError(Throwable cause) {
        try {
            Constructor<DnsResolver.DnsException> ctor = DnsResolver.DnsException.class
                    .getDeclaredConstructor(int.class, Throwable.class);
            ctor.setAccessible(true);
            return ctor.newInstance(DnsResolver.ERROR_SYSTEM, cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
