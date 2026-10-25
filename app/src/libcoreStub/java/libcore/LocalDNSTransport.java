package libcore;

/** Compile-only stand-in for gomobile {@code libcore.LocalDNSTransport}. */
public interface LocalDNSTransport {
    boolean raw();

    long networkHandle();

    void lookup(ExchangeContext ctx, String network, String domain) throws Exception;

    void exchange(ExchangeContext ctx, byte[] message) throws Exception;
}
