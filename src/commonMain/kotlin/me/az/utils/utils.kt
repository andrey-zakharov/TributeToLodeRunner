package me.az.utils

import com.russhwolf.settings.Settings
import com.russhwolf.settings.contains
import de.fabmax.kool.math.Vec2i
import org.mifek.wfc.datastructures.IntArray2D
import kotlin.math.floor
import kotlin.properties.ReadWriteProperty
import kotlin.random.Random
import kotlin.reflect.KProperty

internal val Int.b get() = toByte()
internal val Float.floor: Float get() = floor(this)


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
internal fun Random.choice(choices: List<Int>): Int {
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

internal operator fun Vec2i.component1() = x
internal operator fun Vec2i.component2() = y