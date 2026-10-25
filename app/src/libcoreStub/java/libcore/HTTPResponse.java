package libcore;

/** Compile-only stand-in for gomobile {@code libcore.HTTPResponse}. */
public interface HTTPResponse {
    StringBox getHeader(String key);

    byte[] getContent() throws Exception;

    StringBox getContentString() throws Exception;

    void writeTo(String path) throws Exception;
}
