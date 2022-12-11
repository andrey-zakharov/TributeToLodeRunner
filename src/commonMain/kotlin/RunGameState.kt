import de.fabmax.kool.InputManager
import de.fabmax.kool.modules.ui2.UiScene
import de.fabmax.kool.scene.Scene
import me.az.ilode.Game
import me.az.ilode.GameState
import me.az.app.controls.InputSpec
import me.az.app.controls.toInputSpec
import me.az.scenes.GameLevelScene
import me.az.scenes.GameUI
import me.az.utils.StackedState
import me.az.view.GameControls

class RunGameState(private val app: App) : StackedState<AppState, App>(AppState.RUNGAME) {

    var gameScene: GameLevelScene? = null
    var infoScene: Scene? = null
    var debugScene: Scene? = null
    private val keyListeners = mutableListOf<InputManager.KeyEventListener>()
    var exit = false

    enum class LocalActions(
        override val keyCode: InputSpec,
        override val onPress: RunGameState.(InputManager.KeyEvent) -> Unit = {},
        override val onRelease: RunGameState.(InputManager.KeyEvent) -> Unit = {}
    ) : KeyAction<RunGameState> {
        DEBUGTOGGLE('l'.toInputSpec(InputManager.KEY_MOD_CTRL), onRelease = {
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
        EXIT(InputManager.KEY_ESC.toInputSpec(), onRelease = {
            exit = true
        })
    }

    init {
        onEnter {
            exit = false
            val game = Game(app.context)

            gameScene = GameLevelScene(
                game, app.ctx,
                appContext = app.context,
                name = "level"
            ).apply {
                +GameControls(game, app.ctx.inputMgr)
                +GameUI(uiSpriteSystem, game, appContext)
            }

            app.ctx.scenes += gameScene!!
            debugScene = UiScene {
                gameScene?.setupUi(this)!!
            }

            keyListeners.addAll( app.ctx.inputMgr.registerActions(this, LocalActions.values().asIterable()) )

            game.onStateChanged += {
                when (this.name) {
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
                keyListeners.forEach { inputMgr.removeKeyListener(it) }
                gameScene?.run {
                    game.finish()
                    scenes -= this;
                    runDelayed(1) { dispose(this@with) }
                }
                gameScene = null
                infoScene?.run { scenes -= this; runDelayed(1) { dispose(this@with) } }
                infoScene = null
                debugScene?.run { app.ctx.scenes -= this; runDelayed(1) { dispose(this@with) } }
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