package me.az.utils
actual fun String.format(vararg args: Any?) = String.format(this, *args)

actual class Env {
    actual companion object {
        actual fun getProperty(key: String): String? {
            return System.getenv(key) ?: System.getProperty(key)
        }
    }
}
