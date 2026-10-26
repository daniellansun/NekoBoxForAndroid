package io.nekohasekai.sagernet.ktx

/**
 * True for http(s) URLs the app already fetches. Other schemes (file, javascript,
 * intent, content) are rejected. The check is scheme-only so unusual but valid
 * http(s) URLs keep working.
 */
fun isHttpOrHttpsUrl(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    val trimmed = value.trim()
    val scheme = trimmed.substringBefore(':', missingDelimiterValue = "").lowercase()
    if (scheme != "http" && scheme != "https") return false
    return trimmed.length > scheme.length + 3 &&
        trimmed.startsWith("$scheme://", ignoreCase = true)
}

/** Release notes opened from the GitHub API. Host is pinned so a swapped html_url cannot leave GitHub. */
fun isGitHubHttpsUrl(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    val trimmed = value.trim()
    if (!trimmed.startsWith("https://", ignoreCase = true)) return false
    val hostPort = trimmed.substring("https://".length)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
    val host = hostPort.substringAfter('@').substringBefore(':')
    return host.equals("github.com", ignoreCase = true) ||
        host.equals("www.github.com", ignoreCase = true)
}

private val URL_USERINFO = Regex("""(?i)([a-z][a-z0-9+.-]*://)[^/\s@]+@""")

/** Drops `user:password@` from URLs before they are written to logs or crash reports. */
fun redactUrlUserInfo(value: String): String = URL_USERINFO.replace(value, "$1")
