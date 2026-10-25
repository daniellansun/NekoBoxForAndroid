package libcore;

/** Compile-only stand-in for gomobile {@code libcore.NB4AInterface}. */
public interface NB4AInterface {
    boolean useOfficialAssets();

    void selector_OnProxySelected(String selectorTag, String tag);
}
