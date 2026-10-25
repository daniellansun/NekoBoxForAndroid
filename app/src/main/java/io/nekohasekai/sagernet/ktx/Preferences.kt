package io.nekohasekai.sagernet.ktx

import androidx.preference.PreferenceDataStore
import kotlin.reflect.KProperty

fun PreferenceDataStore.string(
    name: String,
    defaultValue: () -> String = { "" },
) = PreferenceProxy(name, defaultValue, ::getString, ::putString)

fun PreferenceDataStore.boolean(
    name: String,
    defaultValue: () -> Boolean = { false },
) = PreferenceProxy(name, defaultValue, ::getBoolean, ::putBoolean)

fun PreferenceDataStore.int(
    name: String,
    defaultValue: () -> Int = { 0 },
) = PreferenceProxy(name, defaultValue, ::getInt, ::putInt)

fun PreferenceDataStore.stringSet(
    name: String,
    defaultValue: () -> Set<String> = { setOf() },
) = PreferenceProxy(name, defaultValue, ::getStringSet, ::putStringSet)

fun PreferenceDataStore.stringToInt(
    name: String,
    defaultValue: () -> Int = { 0 },
) = PreferenceProxy(name, defaultValue, { key, default ->
    getString(key, "$default")?.toIntOrNull() ?: default
}, { key, value -> putString(key, "$value") })

fun PreferenceDataStore.stringToIntIfExists(
    name: String,
    defaultValue: () -> Int = { 0 },
) = PreferenceProxy(name, defaultValue, { key, default ->
    getString(key, "$default")?.toIntOrNull() ?: default
}, { key, value -> putString(key, value.takeIf { it > 0 }?.toString() ?: "") })

fun PreferenceDataStore.long(
    name: String,
    defaultValue: () -> Long = { 0L },
) = PreferenceProxy(name, defaultValue, ::getLong, ::putLong)

fun PreferenceDataStore.stringToLong(
    name: String,
    defaultValue: () -> Long = { 0L },
) = PreferenceProxy(name, defaultValue, { key, default ->
    getString(key, "$default")?.toLongOrNull() ?: default
}, { key, value -> putString(key, "$value") })

class PreferenceProxy<T>(
    val name: String,
    val defaultValue: () -> T,
    val getter: (String, T) -> T?,
    val setter: (String, value: T) -> Unit,
) {

    operator fun setValue(thisObj: Any?, property: KProperty<*>, value: T) = setter(name, value)
    operator fun getValue(thisObj: Any?, property: KProperty<*>) = getter(name, defaultValue())!!

}

/**
 * Same codecs as the [PreferenceDataStore] extensions, but the store is resolved on each
 * get and set. Callers that own a Room-backed store use these so class load does not open it.
 */
fun stringPref(
    store: () -> PreferenceDataStore,
    name: String,
    defaultValue: () -> String = { "" },
) = PreferenceProxy(
    name,
    defaultValue,
    { key, def -> store().getString(key, def) },
    { key, value -> store().putString(key, value) },
)

fun booleanPref(
    store: () -> PreferenceDataStore,
    name: String,
    defaultValue: () -> Boolean = { false },
) = PreferenceProxy(
    name,
    defaultValue,
    { key, def -> store().getBoolean(key, def) },
    { key, value -> store().putBoolean(key, value) },
)

fun intPref(
    store: () -> PreferenceDataStore,
    name: String,
    defaultValue: () -> Int = { 0 },
) = PreferenceProxy(
    name,
    defaultValue,
    { key, def -> store().getInt(key, def) },
    { key, value -> store().putInt(key, value) },
)

fun stringToIntPref(
    store: () -> PreferenceDataStore,
    name: String,
    defaultValue: () -> Int = { 0 },
) = PreferenceProxy(
    name,
    defaultValue,
    { key, default -> store().getString(key, "$default")?.toIntOrNull() ?: default },
    { key, value -> store().putString(key, "$value") },
)

fun stringToIntIfExistsPref(
    store: () -> PreferenceDataStore,
    name: String,
    defaultValue: () -> Int = { 0 },
) = PreferenceProxy(
    name,
    defaultValue,
    { key, default -> store().getString(key, "$default")?.toIntOrNull() ?: default },
    { key, value -> store().putString(key, value.takeIf { it > 0 }?.toString() ?: "") },
)

fun longPref(
    store: () -> PreferenceDataStore,
    name: String,
    defaultValue: () -> Long = { 0L },
) = PreferenceProxy(
    name,
    defaultValue,
    { key, def -> store().getLong(key, def) },
    { key, value -> store().putLong(key, value) },
)