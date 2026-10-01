package dev.gabrie.brainwave.util

import java.util.Locale

/**
 * Holds a value derived from the device's default locale, rebuilding it if that
 * locale changes.
 *
 * Collators, date formatters and month-name tables are expensive enough that
 * you want them cached, but caching them in a plain `val` on an object silently
 * freezes the locale that happened to be active at class-load time — so after
 * the user switches their phone to another language, weekday names and
 * title sorting stay stale until the process is killed.
 */
class LocaleAware<T : Any>(private val factory: (Locale) -> T) {

    @Volatile private var locale: Locale? = null
    @Volatile private var cached: T? = null

    fun get(): T {
        val current = Locale.getDefault()
        val existing = cached
        if (existing != null && locale == current) return existing

        return synchronized(this) {
            val rechecked = cached
            if (rechecked != null && locale == current) {
                rechecked
            } else {
                factory(current).also {
                    cached = it
                    locale = current
                }
            }
        }
    }
}
