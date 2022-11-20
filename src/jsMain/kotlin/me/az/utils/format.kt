package me.az.utils
actual fun String.format(vararg args: Any?): String = this + " format " + args.joinToString(", ")

actual class Env {
    actual companion object {
        actual fun getProperty(key: String): String? {
            // system.property
            return ""
        }
    }
}