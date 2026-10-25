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

    /**
     * Unit tests put a response body here. Empty keeps the historical stub,
     * which returns a blank body and writes nothing.
     */
    public static volatile String httpBody = "";

    /** Unit tests put a response header value here. Empty matches the old stub. */
    public static volatile String httpHeader = "";

    public static HTTPClient newHttpClient() {
        return new EmptyHttpClient();
    }

    public static BoxInstance newSingBoxInstance(String config, LocalDNSTransport localTransport)
            throws Exception {
        return new BoxInstance();
    }

    /**
     * Unit tests set this to exercise the service catch around urlTest.
     * Null keeps the historical stub, which returns 0. Not in the APK.
     */
    public static volatile RuntimeException urlTestFailure;

    public static int urlTest(BoxInstance instance, String link, int timeout) throws Exception {
        RuntimeException failure = urlTestFailure;
        if (failure != null) {
            urlTestFailure = null;
            throw failure;
        }
        return 0;
    }

    /**
     * Null keeps the historical stub, which returns an unsuccessful empty result.
     * Not in the APK.
     */
    public static volatile StunResult stunResult;

    public static StunResult stunTest(String server) {
        StunResult override = stunResult;
        if (override != null) {
            return override;
        }
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
        @Override public StringBox getHeader(String key) {
            StringBox box = new StringBox();
            box.value = httpHeader;
            return box;
        }
        @Override public byte[] getContent() {
            return httpBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        @Override public StringBox getContentString() {
            StringBox box = new StringBox();
            box.value = httpBody;
            return box;
        }
        @Override public void writeTo(String path) throws Exception {
            if (httpBody.isEmpty()) return;
            java.nio.file.Files.write(
                    java.nio.file.Path.of(path),
                    httpBody.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        }
    }
}
