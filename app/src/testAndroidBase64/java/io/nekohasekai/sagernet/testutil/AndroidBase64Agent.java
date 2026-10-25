package io.nekohasekai.sagernet.testutil;

import java.lang.instrument.Instrumentation;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

/**
 * Installs the test-only {@code android.util.Base64} on the bootstrap loader
 * so it wins over the stub class inside android.jar.
 */
public final class AndroidBase64Agent {
    private AndroidBase64Agent() {}

    public static void premain(String args, Instrumentation inst) throws Exception {
        String path = AndroidBase64Agent.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
        inst.appendToBootstrapClassLoaderSearch(new JarFile(decoded));
    }
}
