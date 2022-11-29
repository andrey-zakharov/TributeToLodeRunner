import de.fabmax.kool.modules.audio.AudioOutput
import de.fabmax.kool.modules.audio.synth.Melody
import de.fabmax.kool.modules.audio.synth.Oscillator
import de.fabmax.kool.modules.audio.synth.SampleNode
import de.fabmax.kool.modules.audio.synth.Wave
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.modules.ksl.blocks.ColorBlockConfig
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.colorMesh
import de.fabmax.kool.scene.scene
import de.fabmax.kool.util.Color
import me.az.ilode.Game
import me.az.ilode.START_HEALTH
import me.az.scenes.MainMenuScene
import me.az.utils.StackedState

class MainMenuState(private val app: App) : StackedState<AppState, App>(AppState.MAINMENU) {
    var exitGame = false
    var mainMenu: MainMenuScene? = null
    var mainUiMenu: Scene? = null

    init {
        onEnter {

            val game = Game(app.context)
            // preload
            mainMenu = MainMenuScene(app.context, game, app.ctx.assetMgr).also {
                app.ctx.scenes += it
            }
//            mainUiMenu?.run { app.ctx.scenes += this }
//            app.ctx.scenes += touchControls(game.runner)
        }

        onExit {
            mainUiMenu?.run {
                app.ctx.scenes -= this
                app.ctx.runDelayed(1) { dispose(app.ctx) }
            }
            mainUiMenu = null

            mainMenu?.run {
                app.ctx.scenes -= this
                app.ctx.runDelayed(1) { dispose(app.ctx) }
            }
            mainMenu = null
            //app.ctx.scenes.clear()
        }

        onUpdate {
            mainMenu?.run {
                if ( startnewGame || continueGame ) {
                    if ( startnewGame  || context.runnerLifes.value <= 0 ) {
                        with( context ) {
                            currentLevel.set(menuContext.level.value)
                            levelSet.set(menuContext.levelSet.value)
                            runnerLifes.set(START_HEALTH)
                            score.set(0)
                        }
                        gameSettings.sometimePlayInGodMode = false
                    }
                    startnewGame = false
                    continueGame = false
                    return@onUpdate AppState.RUNGAME
                }
            }

            if ( exitGame ) {
                exitGame = false
                return@onUpdate AppState.EXIT
            }
            null
        }
    }
}