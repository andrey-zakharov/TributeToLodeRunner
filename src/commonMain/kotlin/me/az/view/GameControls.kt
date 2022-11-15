package me.az.view

import KeyAction
import de.fabmax.kool.InputManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.scene.Node
import me.az.ilode.Game
import me.az.ilode.InputSpec
import me.az.ilode.toInputSpec
import registerActions
import unregisterActions

class GameControls(val game: Game, val inputManager: InputManager): Node() {

    val subs = inputManager.registerActions(game, GameKeyAction.values().asIterable())
    override fun dispose(ctx: KoolContext) {
        super.dispose(ctx)
        inputManager.unregisterActions(subs)
    }
}

enum class GameKeyAction(
    override val keyCode: InputSpec, // or no
    override val onPress: Game.(InputManager.KeyEvent) -> Unit = {},
    override val onRelease: Game.(InputManager.KeyEvent) -> Unit = {}
) : KeyAction<Game> {
    BACK(InputManager.KEY_BACKSPACE.toInputSpec(), onRelease = {
        // stopAudio
        // destroy chars
        // destroy stage
        // exit cycle
    }),
    RESPAWN('a'.toInputSpec(InputManager.KEY_MOD_CTRL), onRelease = {
        abortGame()
    }),
    GAMEOVER('f'.toInputSpec(InputManager.KEY_MOD_CTRL), onRelease = {
        overGame()
    }),
    FINISH('s'.toInputSpec(InputManager.KEY_MOD_CTRL), onRelease = {
        finishGame()
    }),
    ANIMS('n'.toInputSpec(InputManager.KEY_MOD_CTRL), onRelease = {
        stopAnims.set( !stopAnims.value )
    }),

    PREV(','.toInputSpec(InputManager.KEY_MOD_CTRL), onRelease = {
        prevLevel()
    }),
    NEXT('.'.toInputSpec(InputManager.KEY_MOD_CTRL), onRelease = {
        println("pressed")
        nextLevel()
    }),
}
