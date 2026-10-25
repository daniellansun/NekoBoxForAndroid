package libcore;

/** Compile-only stand-in for gomobile {@code libcore.BoxInstance}. */
public final class BoxInstance {
    public void start() throws Exception {}

    public void close() throws Exception {}

    public void sleep() {}

    public void wake() {}

    public void setAsMain() {}

    public void setV2rayStats(String outbounds) {}

    /**
     * Tests raise this when they need a non-zero delta. The gomobile method
     * in {@code libcore.aar} does not use this field.
     */
    public static volatile long stats;

    public long queryStats(String tag, String direct) {
        return stats;
    }

    public boolean selectOutbound(String tag) {
        return false;
    }
}
