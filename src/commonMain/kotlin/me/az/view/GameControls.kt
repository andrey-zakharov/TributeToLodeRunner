package me.az.view

import de.fabmax.kool.InputManager
import de.fabmax.kool.scene.Node
import me.az.ilode.Game
import me.az.ilode.InputSpec
import me.az.ilode.toInputSpec

class GameControls(val game: Game, val inputManager: InputManager): Node() {

    init {
        GameAction.values().forEach {  action ->
            inputManager.registerKeyListener(
                keyCode = action.keyCode.code,
                name = action.name,
                filter = {ev -> ev.isPressed &&
                        ev.modifiers and action.keyCode.modificatorBitMask == ev.modifiers
                }
            ) { ev ->
                if ( ev.isReleased ) action.onRelease.invoke(game, ev) else
                    if (ev.isPressed) action.onPress.invoke(game, ev)
            }
        }
    }
}

enum class GameAction(
    val keyCode: InputSpec, // or no
    val onPress: Game.(InputManager.KeyEvent) -> Unit = {},
    val onRelease: Game.(InputManager.KeyEvent) -> Unit
) {
    BACK(InputManager.KEY_BACKSPACE.toInputSpec(), onRelease = {
        // stopAudio
        // destroy chars
        // destroy stage
        // exit cycle
    }),
    RESPAWN('a'.toInputSpec(InputManager.KEY_MOD_CTRL), onRelease = {
        abortGame()
    })



}