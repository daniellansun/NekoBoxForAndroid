package moe.matsuri.nb4a.utils

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaUtilWebViewTest {
    @Test
    fun webviewLockPathsMatchProcessAndHuawei() {
        val main = JavaUtil.webviewLockPaths("/data", "io.app", null, false)
        assertEquals(
            setOf(
                "/data/app_webview/webview_data.lock",
                "/data/app_webview_io.app/webview_data.lock",
            ),
            main,
        )
        val huawei = JavaUtil.webviewLockPaths("/data", "io.app", null, true)
        assertEquals(4, huawei.size)
        assertTrue(huawei.contains("/data/app_hws_webview/webview_data.lock"))
        assertTrue(huawei.contains("/data/app_hws_webview_io.app/webview_data.lock"))

        val other = JavaUtil.webviewLockPaths("/data", "io.app:bg", "io.app:bg", false)
        assertEquals(setOf("/data/app_webview_io.app:bg/webview_data.lock"), other)
        val otherHuawei = JavaUtil.webviewLockPaths("/data", "io.app:bg", "pkg", true)
        assertEquals(
            setOf(
                "/data/app_webview_pkg/webview_data.lock",
                "/data/app_hws_webview_pkg/webview_data.lock",
            ),
            otherHuawei,
        )
    }

    @Test
    fun lockFileIsRecreatedWhenTheLockCannotBeTaken() {
        JavaUtil.handleWebviewDir(null)
        val file = File.createTempFile("webview", ".lock")
        file.writeText("x")
        JavaUtil.tryLockOrRecreateFile(file)
        assertTrue(file.exists())

        val held = File.createTempFile("webview-held", ".lock")
        RandomAccessFile(held, "rw").use { raf ->
            val lock = raf.channel.lock()
            try {
                JavaUtil.tryLockOrRecreateFile(held)
            } finally {
                lock.release()
            }
        }

        val dir = File.createTempFile("webview-dir", "").apply {
            delete()
            mkdirs()
        }
        JavaUtil.tryLockOrRecreateFile(dir)
        JavaUtil.createFile(file, false)
        val missing = File(file.parentFile, "missing-webview.lock")
        if (missing.exists()) missing.delete()
        JavaUtil.createFile(missing, true)
        assertTrue(missing.exists())
        JavaUtil.createFile(missing, true)
        JavaUtil.createFile(File(missing, "nested"), true)
        assertFalse(File(missing, "nested").exists())
        missing.delete()
        held.delete()
        file.delete()
    }
}
