package io.nekohasekai.sagernet.ui.profile

import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.fmt.v2ray.realityFingerprintUnsupported

private const val REALITY_FINGERPRINT_HINT_KEY = "realityFingerprintHint"

/**
 * Shows the REALITY fingerprint hint under uTLS while a public key is set
 * and the fingerprint is android or 360. Saving is unaffected.
 */
internal fun PreferenceFragmentCompat.bindRealityFingerprintHint() {
    val fingerprint = findPreference<ListPreference>("utlsFingerprint") ?: return
    val realityPubKey = findPreference<EditTextPreference>("realityPubKey") ?: return
    val hint = findPreference<Preference>(REALITY_FINGERPRINT_HINT_KEY) ?: return

    fun apply(fingerprintValue: String?, publicKey: String?) {
        hint.isVisible = realityFingerprintUnsupported(publicKey, fingerprintValue)
    }

    apply(fingerprint.value, realityPubKey.text)
    fingerprint.setOnPreferenceChangeListener { _, newValue ->
        apply(newValue as? String, realityPubKey.text)
        true
    }
    realityPubKey.setOnPreferenceChangeListener { _, newValue ->
        apply(fingerprint.value, newValue as? String)
        true
    }
}
