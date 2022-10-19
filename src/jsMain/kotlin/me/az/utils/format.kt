package me.az.utils
actual fun String.format(vararg args: Any?): String = this + " format " + args.joinToString(", ")