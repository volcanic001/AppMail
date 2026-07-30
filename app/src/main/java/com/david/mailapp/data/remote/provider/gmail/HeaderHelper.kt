package com.david.mailapp.data.remote.provider.gmail

/**
 * Case-insensitive lookup over a list of [Header]s.
 *
 * - Compares [Header.name] with `equals(ignoreCase = true)`.
 * - Returns the first match.
 * - Blank values are treated as absent (returns null).
 */
internal fun List<Header>.headerValue(name: String): String? {
    val value = firstOrNull { it.name.equals(name, ignoreCase = true) }?.value?.trim()
    return value?.takeIf { it.isNotEmpty() }
}
