package me.az.app.controls

import de.fabmax.kool.KeyCode
import de.fabmax.kool.LocalKeyCode

typealias KeyMod = Int
data class InputSpec(val code: KeyCode, val modificatorBitMask: Int)

fun KeyCode.toInputSpec(vararg mod: KeyMod) = InputSpec(this, mod.sum())
fun Char.toInputSpec(vararg mod: KeyMod) = LocalKeyCode(this).toInputSpec(*mod)