package io.nekohasekai.sagernet.utils

import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.ktx.redactUrlUserInfo

/**
 * Formats a throwable the same way [CrashHandler.formatThrowable] always has,
 * and stops if a cause chain loops back on itself.
 */
fun formatThrowableReport(throwable: Throwable): String = formatThrowableReport(throwable, HashSet())

private fun formatThrowableReport(throwable: Throwable, seen: MutableSet<Throwable>): String {
    if (!seen.add(throwable)) {
        return throwable.javaClass.name + ": cyclic cause"
    }
    var format = throwable.javaClass.name
    val message = throwable.message
    if (!message.isNullOrBlank()) {
        format += ": $message"
    }
    format += "\n"
    format += throwable.stackTrace.joinToString("\n") {
        "    at ${it.className}.${it.methodName}(${it.fileName}:${if (it.isNativeMethod) "native" else it.lineNumber})"
    }
    val cause = throwable.cause
    if (cause != null) {
        format += "\n\nCaused by: " + formatThrowableReport(cause, seen)
    }
    return format
}

fun isSensitiveSettingKey(key: String): Boolean {
    val normalized = key.lowercase()
    return normalized.contains("password") ||
        normalized.contains("secret") ||
        normalized.contains("privatekey") ||
        normalized.contains("private_key") ||
        normalized.contains("token") ||
        normalized == Key.GLOBAL_CUSTOM_CONFIG.lowercase() ||
        normalized == Key.SERVER_CONFIG.lowercase() ||
        normalized == Key.SERVER_CUSTOM.lowercase() ||
        normalized == Key.SERVER_CUSTOM_OUTBOUND.lowercase()
}

/** One settings line for a crash report. Stored values are not modified. */
fun settingReportLine(key: String, value: String): String {
    val shown = if (isSensitiveSettingKey(key)) "[redacted]" else redactUrlUserInfo(value)
    return "$key: $shown"
}
