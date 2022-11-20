import de.fabmax.kool.modules.audio.AudioOutput
import de.fabmax.kool.modules.audio.synth.Melody
import de.fabmax.kool.modules.audio.synth.Oscillator
import de.fabmax.kool.modules.audio.synth.SampleNode
import de.fabmax.kool.modules.audio.synth.Wave
import de.fabmax.kool.scene.Scene
import me.az.ilode.Game
import me.az.ilode.START_HEALTH
import me.az.scenes.MainMenuScene
import me.az.utils.StackedState

class MainMenuState(private val app: App) : StackedState<AppState, App>(AppState.MAINMENU) {
    var exitGame = false
    var mainMenu: MainMenuScene? = null
    var mainUiMenu: Scene? = null
    val menuContext get() = mainMenu?.menuContext ?: throw IllegalStateException()

    init {
        onEnter {
            /*val out = AudioOutput().apply {
                mixer.addNode( object : SampleNode() {

                    // 2741 Гц (F7) = -13,3 дБ
                    // 2734 Гц (F7) = -12,0 дБ
                    // 2579 Гц (E7) = -15,1 дБ
                    // 2580 Гц (E7) = -14,9 дБ
                    // 2477 Гц (D♯7) = -13,4 дБ
                    // 2355 Гц (D7) = -16,2 дБ
                    // max note = 100

                    // note (nN) = 45ms + 1ms gap + 45ms
                    // n1 + long gap + n2 + long gap + n3 + shot gap + n4 + long gap + n5...

                    private val osc = Oscillator(Wave.SQUARE).apply { gain = 0.5f }
                    override fun generate(dt: Float): Float {
                        // 45 ms one burst, 2 bursts per note,
                        // 14 ms gap between bursts, variance 7ms each 2?
                        // 14 ms 14 ms 7ms, 14 ms 14 ms 7ms
                        // 1 ms gap in the middle of burst (inverting in the middle?)

                        val note = (t).toInt() % 100
                        return osc.next(dt, note(note, 3))
                    }
                })
            }
            */
            val game = Game(app.context)
            // preload
            mainMenu = MainMenuScene(app.context, game, app.ctx.assetMgr).also {
                app.ctx.scenes += it
//            mainUiMenu = it.setupUi()
            }
            mainUiMenu?.run { app.ctx.scenes += this }
//            app.ctx.scenes += touchControls(game.runner)

            game.reset()
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
            app.ctx.scenes.clear()
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