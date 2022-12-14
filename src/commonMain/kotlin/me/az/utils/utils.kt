package me.az.utils

import com.russhwolf.settings.Settings
import com.russhwolf.settings.contains
import de.fabmax.kool.math.Mat4d
import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.scene.Group
import de.fabmax.kool.scene.lineMesh
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Time
import kotlin.jvm.JvmField
import kotlin.math.floor
import kotlin.properties.ReadWriteProperty
import kotlin.random.Random
import kotlin.reflect.KProperty

// actually platform.env["debug"]
expect class Env { // system
    companion object {
        fun getProperty(key: String): String?
    }
}

@JvmField
val canDebug: Boolean = Env.getProperty("debug").toBoolean()

inline fun debugOnly( block: () -> Unit ) { if ( canDebug ) block() }
inline fun logd( block: () -> String ) { if ( canDebug ) println("+${Time.frameCount} ${block()}") }

internal val Int.b get() = toByte()
internal val Float.floor: Float get() = floor(this)

expect fun String.format(vararg args: Any?): String


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

internal fun Group.addDebugAxis(scale: Float = 1f) {
    +lineMesh("x") { addLine(Vec3f.ZERO, Vec3f(scale, 0f, 0f), Color.RED) }
    +lineMesh("y") { addLine(Vec3f.ZERO, Vec3f(0f, scale, 0f), Color.GREEN) }
    +lineMesh("z") { addLine(Vec3f.ZERO, Vec3f(0f, 0f, scale), Color.BLUE) }
}

internal val Int.nearestTwo: Int get() {
    var x = this
    x -= 1
    x = x or (x shr 1)
    x = x or (x shr 2)
    x = x or (x shr 4)
    x = x or (x shr 8)
    x = x or (x shr 16)
    return x + 1
}

internal operator fun Vec2i.plus(o: Vec2i) = Vec2i(this.x + o.x, this.y + o.y)

internal fun Float.lerp(a: Float, b: Float): Float {
    return a + this * (b - a)
}

// rude stub
fun Mat4f.mul(m: Mat4d) = mul(Mat4f().set(m))
