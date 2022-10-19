package me.az.utils
actual fun String.format(vararg args: Any?) = String.format(this, *args)
