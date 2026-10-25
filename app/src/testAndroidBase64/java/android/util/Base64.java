package android.util;

import java.nio.charset.StandardCharsets;

/**
 * Test-only stand-in for {@code android.util.Base64}.
 *
 * <p>JVM unit tests load a stub android.jar whose {@code Base64} methods return
 * null. Share-link parsers call those methods, so this class is placed on the
 * unit-test classpath and bootstrap loader. It is not packaged into the APK.
 */
public final class Base64 {
    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;
    public static final int NO_CLOSE = 16;

    private Base64() {}

    public static byte[] encode(byte[] input, int flags) {
        return encode(input, 0, input.length, flags);
    }

    public static byte[] encode(byte[] input, int offset, int len, int flags) {
        java.util.Base64.Encoder encoder = (flags & URL_SAFE) != 0
                ? java.util.Base64.getUrlEncoder()
                : java.util.Base64.getEncoder();
        if ((flags & NO_PADDING) != 0) {
            encoder = encoder.withoutPadding();
        }
        byte[] raw = encoder.encode(copy(input, offset, len));
        if ((flags & NO_WRAP) != 0 || raw.length <= 76) {
            return raw;
        }
        byte[] sep = (flags & CRLF) != 0 ? new byte[]{'\r', '\n'} : new byte[]{'\n'};
        return wrap(raw, sep);
    }

    public static String encodeToString(byte[] input, int flags) {
        return encodeToString(input, 0, input.length, flags);
    }

    public static String encodeToString(byte[] input, int offset, int len, int flags) {
        return new String(encode(input, offset, len, flags), StandardCharsets.US_ASCII);
    }

    public static byte[] decode(String str, int flags) {
        return decode(str.getBytes(StandardCharsets.UTF_8), flags);
    }

    public static byte[] decode(byte[] input, int flags) {
        return decode(input, 0, input.length, flags);
    }

    public static byte[] decode(byte[] input, int offset, int len, int flags) {
        String cleaned = stripWhitespace(new String(input, offset, len, StandardCharsets.ISO_8859_1));
        int pad = cleaned.length() % 4;
        if (pad != 0) {
            StringBuilder padded = new StringBuilder(cleaned);
            for (int i = pad; i < 4; i++) {
                padded.append('=');
            }
            cleaned = padded.toString();
        }
        java.util.Base64.Decoder decoder = (flags & URL_SAFE) != 0
                ? java.util.Base64.getUrlDecoder()
                : java.util.Base64.getDecoder();
        return decoder.decode(cleaned);
    }

    private static byte[] copy(byte[] input, int offset, int len) {
        byte[] slice = new byte[len];
        System.arraycopy(input, offset, slice, 0, len);
        return slice;
    }

    private static String stripWhitespace(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static byte[] wrap(byte[] raw, byte[] sep) {
        int lines = (raw.length + 75) / 76;
        byte[] out = new byte[raw.length + (lines - 1) * sep.length];
        int src = 0;
        int dst = 0;
        for (int line = 0; line < lines; line++) {
            int n = Math.min(76, raw.length - src);
            System.arraycopy(raw, src, out, dst, n);
            src += n;
            dst += n;
            if (line + 1 < lines) {
                System.arraycopy(sep, 0, out, dst, sep.length);
                dst += sep.length;
            }
        }
        return out;
    }
}
