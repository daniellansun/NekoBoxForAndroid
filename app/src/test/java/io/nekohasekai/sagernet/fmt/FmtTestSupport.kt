package io.nekohasekai.sagernet.fmt

import java.util.Base64

fun b64Std(text: String): String =
    Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8)).trim()

fun b64Url(text: String): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))

fun <T : AbstractBean> T.withDefaults(): T = apply { initializeDefaultValues() }
