package libcore;

/**
 * Compile-only stand-in for the gomobile {@code libcore.Libcore} entry points.
 *
 * <p>The real types ship in {@code app/libs/libcore.aar}, which is produced by
 * {@code libcore/build.sh} and is not part of this git tree. These classes exist
 * so JVM unit tests can compile when that AAR is absent. They are not packaged
 * as an {@code implementation} dependency, and the JaCoCo filter omits {@code libcore.**}.
 */
public final class Libcore {
    private Libcore() {}

    public static void initCore(
            String process,
            String cachePath,
            String internalAssets,
            String externalAssets,
            int maxLogSizeKb,
            boolean logEnable,
            NB4AInterface nb4a,
            BoxPlatformInterface platform,
            LocalDNSTransport dns
    ) {}

    public static String versionBox() {
        return "";
    }

    public static void nekoLogPrintln(String message) {}

    public static void nekoLogClear() {}

    public static void forceGc() {}

    public static void resetAllConnections(boolean system) {}

    public static void unxz(String archive, String path) throws Exception {}

    public static HTTPClient newHttpClient() {
        return new EmptyHttpClient();
    }

    public static BoxInstance newSingBoxInstance(String config, LocalDNSTransport localTransport)
            throws Exception {
        return new BoxInstance();
    }

    public static int urlTest(BoxInstance instance, String link, int timeout) throws Exception {
        return 0;
    }

    public static StunResult stunTest(String server) {
        return new StunResult();
    }

    private static final class EmptyHttpClient implements HTTPClient {
        @Override public void restrictedTLS() {}
        @Override public void modernTLS() {}
        @Override public void pinnedTLS12() {}
        @Override public void pinnedSHA256(String sumHex) {}
        @Override public void trySocks5(int port, String username, String password) {}
        @Override public void tryH3Direct() {}
        @Override public void keepAlive() {}
        @Override public HTTPRequest newRequest() { return new EmptyHttpRequest(); }
        @Override public void close() {}
    }

    private static final class EmptyHttpRequest implements HTTPRequest {
        @Override public void setURL(String link) {}
        @Override public void setMethod(String method) {}
        @Override public void setHeader(String key, String value) {}
        @Override public void setContent(byte[] content) {}
        @Override public void setContentString(String content) {}
        @Override public void setUserAgent(String userAgent) {}
        @Override public void allowInsecure() {}
        @Override public HTTPResponse execute() { return new EmptyHttpResponse(); }
    }

    private static final class EmptyHttpResponse implements HTTPResponse {
        @Override public StringBox getHeader(String key) { return new StringBox(); }
        @Override public byte[] getContent() { return new byte[0]; }
        @Override public StringBox getContentString() { return new StringBox(); }
        @Override public void writeTo(String path) {}
    }
}
