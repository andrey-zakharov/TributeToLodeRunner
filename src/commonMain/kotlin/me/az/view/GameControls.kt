package me.az.view

import KeyAction

import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.scene.Node
import me.az.app.controls.InputSpec
import me.az.app.controls.toInputSpec
import me.az.ilode.Game
import registerActions
import unregisterActions

class GameControls(val game: Game, val inputManager: KeyboardInput): Node() {

    val subs = inputManager.registerActions(game, GameKeyAction.values().asIterable())
    override fun dispose(ctx: KoolContext) {
        super.dispose(ctx)
        inputManager.unregisterActions(subs)
    }
}

enum class GameKeyAction(
    override val keyCode: InputSpec, // or no
    override val onPress: Game.(KeyEvent) -> Unit = {},
    override val onRelease: Game.(KeyEvent) -> Unit = {}
) : KeyAction<Game> {
    BACK(KeyboardInput.KEY_BACKSPACE.toInputSpec(), onRelease = {
        // stopAudio
        // destroy chars
        // destroy stage
        // exit cycle
    }),
    RESPAWN('a'.toInputSpec(KeyboardInput.KEY_MOD_CTRL), onRelease = {
        abortGame()
    }),
    GAMEOVER('f'.toInputSpec(KeyboardInput.KEY_MOD_CTRL), onRelease = {
        overGame()
    }),
    FINISH('s'.toInputSpec(KeyboardInput.KEY_MOD_CTRL), onRelease = {
        finishGame()
    }),
    ANIMS('n'.toInputSpec(KeyboardInput.KEY_MOD_CTRL), onRelease = {
        stopAnims.set( !stopAnims.value )
    }),

    PREV(','.toInputSpec(KeyboardInput.KEY_MOD_CTRL), onRelease = {
        prevLevel()
    }),
    NEXT('.'.toInputSpec(KeyboardInput.KEY_MOD_CTRL), onRelease = {
        nextLevel()
    }),
}
