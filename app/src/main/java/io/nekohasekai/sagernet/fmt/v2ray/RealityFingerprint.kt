package io.nekohasekai.sagernet.fmt.v2ray

import java.util.Locale

/**
 * uTLS 1.8.4 HelloAndroid_11_OkHttp and Hello360_7_5 emit TLS 1.2 ClientHellos
 * only (no supported_versions or key_share). REALITY needs TLS 1.3 plus an
 * X25519 key share, so those fingerprints cannot connect.
 *
 * REALITY is enabled when [realityPubKey] is not blank. The fingerprint is
 * never rewritten; callers only use this to decide whether to show a hint.
 */
fun realityFingerprintUnsupported(realityPubKey: String?, utlsFingerprint: String?): Boolean {
    if (realityPubKey.isNullOrBlank()) return false
    return utlsFingerprint?.trim()?.lowercase(Locale.ROOT) in REALITY_UNSUPPORTED_UTLS
}

private val REALITY_UNSUPPORTED_UTLS = setOf("android", "360")
