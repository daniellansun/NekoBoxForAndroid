package libcore;

/** Compile-only stand-in for gomobile {@code libcore.ExchangeContext}. */
public final class ExchangeContext {
    /** Test-only. Null on every production call path; the gomobile class has no such field. */
    public static volatile RuntimeException successFailure;
    public static volatile RuntimeException errnoFailure;

    public void onCancel(Func callback) {}

    public void success(String result) {
        RuntimeException failure = successFailure;
        if (failure != null) {
            throw failure;
        }
    }

    public void rawSuccess(byte[] result) {}

    public void errorCode(int code) {}

    public void errnoCode(int code) {
        RuntimeException failure = errnoFailure;
        if (failure != null) {
            errnoFailure = null;
            throw failure;
        }
    }
}
