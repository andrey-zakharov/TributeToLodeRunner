
import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.InputStack
import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.modules.ui2.UiScene
import de.fabmax.kool.scene.Scene
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.az.app.controls.InputSpec
import me.az.app.controls.toInputSpec
import me.az.ilode.Game
import me.az.ilode.GameState
import me.az.scenes.GameLevelScene
import me.az.scenes.GameUI
import me.az.utils.runDelayed
import me.az.view.GameControls

import zakharov.kit.fsm.StackedState

class RunGameState(private val app: App) : StackedState<AppState, App>(AppState.RUNGAME) {

    var gameScene: GameLevelScene? = null
    var infoScene: Scene? = null
    var debugScene: Scene? = null
    private val keyListeners = mutableListOf<InputStack.SimpleKeyListener>()
    var exit = false
    val game = Game(app.context)

    enum class LocalActions(
        override val keyCode: InputSpec,
        override val onPress: RunGameState.(KeyEvent) -> Unit = {},
        override val onRelease: RunGameState.(KeyEvent) -> Unit = {}
    ) : KeyAction<RunGameState> {
        DEBUGTOGGLE('l'.toInputSpec(KeyboardInput.KEY_MOD_CTRL), onRelease = {
            when (debugScene) {
                null -> {}
                else -> {
                    if ( app.ctx.scenes.contains(debugScene) ) {
                        app.ctx.scenes -= debugScene!!
                    } else {
                        app.ctx.scenes += debugScene!!
                    }
                }
            }
        }),
        EXIT(KeyboardInput.KEY_ESC.toInputSpec(), onRelease = {
            if ( gameScene?.pauseMenu?.isShown == true ) {
                gameScene?.pauseMenu?.hideMenu()
                game.resumeGame()
            } else {
                game.pauseGame()
                gameScene?.pauseMenu?.showMenu()
            }
        })
    }

    init {
        onEnter {
            exit = false

            gameScene = GameLevelScene(
                game, app.ctx,
                gameContext = app.context,
                name = "level"
            ).apply {
                addNode( GameControls(game, KeyboardInput) )
                addNode( GameUI(uiSpriteSystem, game, gameContext) )
            }

            app.ctx.scenes += gameScene!!
            app.ctx.scenes += gameScene!!.pauseMenu.ui

            debugScene = UiScene {
                gameScene?.setupUi(this)!!
            }
            keyListeners.addAll( KeyboardInput.registerActions(this, LocalActions.entries.asIterable()) )

            game.onStateChanged += {
                when (this.name) {
                    GameState.GAME_OVER -> exit = true
                    GameState.GAME_OVER_ANIMATION -> app.ctx.runDelayed((app.ctx.fps * 7).toInt()) {
                        exit = true
                    }
                    else -> { }
                }
            }

            // controller
//            app.ctx.scenes += touchControls(game.runner)
        }

        onExit {

            with(app.ctx) {
                keyListeners.forEach { KeyboardInput.removeKeyListener(it) }
                gameScene?.run {
                    game.finish()
                    scenes -= this;
                    dispose(this@with)
                }
                gameScene = null
                infoScene?.run { scenes -= this; dispose(this@with) }
                infoScene = null
                debugScene?.run { app.ctx.scenes -= this; dispose(this@with) }
                debugScene = null
                //app.ctx.scenes -= app.touchControls
                // app.ctx.scenes.clear()
            }
        }

        onUpdate {
            if ( exit ) AppState.MAINMENU
            else null
        }
    }
}
