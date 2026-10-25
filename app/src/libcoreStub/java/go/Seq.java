package go;

/**
 * Compile-only stand-in for gomobile's {@code go.Seq}.
 * Used only when {@code app/libs/libcore.aar} is absent. Not product code.
 */
public final class Seq {
    private Seq() {}

    public static void setContext(Object context) {}
}
