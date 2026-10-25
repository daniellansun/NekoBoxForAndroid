package libcore;

/** Compile-only stand-in for gomobile {@code libcore.HTTPRequest}. */
public interface HTTPRequest {
    void setURL(String link) throws Exception;

    void setMethod(String method);

    void setHeader(String key, String value);

    void setContent(byte[] content);

    void setContentString(String content);

    void setUserAgent(String userAgent);

    void allowInsecure();

    HTTPResponse execute() throws Exception;
}
