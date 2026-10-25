package io.nekohasekai.sagernet.bg;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.jakewharton.processphoenix.ProcessPhoenix;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Backup import calls {@link ProcessPhoenix#triggerRebirth}, which starts an
 * activity that kills the process. This shadow keeps that call on the JVM.
 */
@Implements(value = ProcessPhoenix.class, isInAndroidSdk = false)
public class ShadowProcessPhoenix {
    public static volatile int rebirths;

    @Implementation
    public static void triggerRebirth(Context context) {
        rebirths++;
    }

    @Implementation
    public static void triggerRebirth(Context context, Intent... nextIntents) {
        rebirths++;
    }

    @Implementation
    public void onCreate(Bundle savedInstanceState) {
        rebirths++;
    }

    public static void reset() {
        rebirths = 0;
    }
}
