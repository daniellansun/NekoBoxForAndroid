package libcore;

/** Compile-only stand-in for gomobile {@code libcore.ExchangeContext}. */
public final class ExchangeContext {
    public void onCancel(Func callback) {}

    public void success(String result) {}

    public void rawSuccess(byte[] result) {}

    public void errorCode(int code) {}

    public void errnoCode(int code) {}
}
