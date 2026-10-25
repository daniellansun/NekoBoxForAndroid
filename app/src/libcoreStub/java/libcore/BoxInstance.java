package libcore;

/** Compile-only stand-in for gomobile {@code libcore.BoxInstance}. */
public final class BoxInstance {
    public void start() throws Exception {}

    public void close() throws Exception {}

    public void sleep() {}

    public void wake() {}

    public void setAsMain() {}

    public void setV2rayStats(String outbounds) {}

    public long queryStats(String tag, String direct) {
        return 0L;
    }

    public boolean selectOutbound(String tag) {
        return false;
    }
}
