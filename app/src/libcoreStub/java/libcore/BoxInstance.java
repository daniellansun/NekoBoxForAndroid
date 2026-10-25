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
        // Non-zero so JVM tests can execute the traffic-delta branches.
        // The real gomobile method is still the one in libcore.aar.
        return 8L;
    }

    public boolean selectOutbound(String tag) {
        return false;
    }
}
