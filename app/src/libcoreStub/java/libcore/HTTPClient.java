package libcore;

/** Compile-only stand-in for gomobile {@code libcore.HTTPClient}. */
public interface HTTPClient {
    void restrictedTLS();

    void modernTLS();

    void pinnedTLS12();

    void pinnedSHA256(String sumHex);

    void trySocks5(int port, String username, String password);

    void tryH3Direct();

    void keepAlive();

    HTTPRequest newRequest();

    void close();
}
