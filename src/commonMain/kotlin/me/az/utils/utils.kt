package me.az.utils

import com.russhwolf.settings.Settings
import com.russhwolf.settings.contains
import org.mifek.wfc.datastructures.IntArray2D
import kotlin.properties.ReadWriteProperty
import kotlin.random.Random
import kotlin.reflect.KProperty

val Int.b get() = toByte()


internal inline fun <reified E: Enum<E>>enumDelegate(settings: Settings,
                                                     key: String? = null,
                                                     defaultValue: E ) =
    EnumDelegate(settings, key, defaultValue) { s -> enumValueOf(s) }

internal class EnumDelegate<E: Enum<E>>(
    private val settings: Settings,
    val key: String?,
    private val defaultValue: E,
    private val convert: (s: String) -> E
) : ReadWriteProperty<Any?, E> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): E {
        val lookupKey = key ?: property.name
        return if ( settings.contains(lookupKey) ) {
            convert(settings.getString(lookupKey))
        } else {
            defaultValue
        }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: E) {
        val lookupKey = key ?: property.name
        settings.putString(lookupKey, value.name)
    }

}


// return index in choices
fun Random.choice(choices: List<Int>): Int {
    val total = choices.sum()
//    print("collapsing random for: $choices total=$total ")
    var r = nextInt(total)
    var choice = 0//choices.last()

    while ( r > 0 ) {
        r -= choices[choice]
        if ( r <= 0 ) return choice
        choice++
    }
    return choice
}
