package me.az.ilode

import AnimationFrames
import GameSpeed
import LevelSet
import TileSet
import com.russhwolf.settings.*
import de.fabmax.kool.InputManager
import de.fabmax.kool.KeyCode
import de.fabmax.kool.KoolContext
import de.fabmax.kool.UniversalKeyCode
import de.fabmax.kool.scene.animation.InterpolatedFloat
import de.fabmax.kool.scene.animation.LinearAnimator
import me.az.utils.enumDelegate
import org.mifek.wfc.utils.EventHandler
import kotlin.math.sqrt
import kotlin.random.Random


enum class GameState {
    GAME_START,
    GAME_RUNNING ,
    GAME_FINISH , GAME_FINISH_SCORE_COUNT,
    GAME_WAITING , GAME_PAUSE,
    GAME_NEW_LEVEL , GAME_RUNNER_DEAD,
    GAME_OVER_ANIMATION , GAME_OVER,
    GAME_NEXT_LEVEL , GAME_PREV_LEVEL,
    GAME_LOADING , GAME_WIN
}

class GameSettings(val settings: Settings) {
    var spriteMode: TileSet by enumDelegate(settings, defaultValue = TileSet.SPRITES_APPLE2)
    var version: LevelSet by enumDelegate(settings, defaultValue = LevelSet.CLASSIC)
    val startLevel: Int by settings.int(defaultValue = 0)
    var speed: GameSpeed by enumDelegate(settings, defaultValue = GameSpeed.SPEED_SLOW)
    var introDuration by settings.float(defaultValue = 60f)
}
const val SCORE_COUNTER = 15 // how much scores bumbs in finish anim
const val SCORE_COMPLETE = 1500
const val SCORE_GOLD     = 250
const val SCORE_FALL     = 75
const val SCORE_DIES     = 75

//system
class Game(val settings: GameSettings) {
    var stopGuards = false
    var immortal = false
    var speed = settings.speed
    var runner: Runner? = null
    val guards = mutableListOf<Guard>()

    var nextGuard = 0
    var nextMoves = 0

    val gameOver get() = runner?.health!! <= 0
    private var gameState = GameState.GAME_START
        set(value) {
            field = value
            onStatusChanged.forEach { it.invoke(field) }
        }
    val isPlaying get() = gameState == GameState.GAME_RUNNING
    val isPaused get() = gameState == GameState.GAME_PAUSE

    val onStatusChanged = mutableListOf<(GameState) -> Unit>()
    val onLevelStart = mutableListOf<(GameLevel) -> Unit>()

    lateinit var level: GameLevel

    fun levelStartup(level: GameLevel, guardAnims: AnimationFrames) {
        gameState = GameState.GAME_START
        this.level = level
        // playStartAnim
        runner = Runner(level)
        guards.clear()
        level.guardsPos.forEach {
            val g = Guard(level, anims = guardAnims)
            g.startLevel(level, it)
            guards.add(g)
        }
        level.status = GameLevel.Status.LEVEL_PLAYING
        onLevelStart.forEach { it.invoke(level) }
    }

    fun reset() {
        nextGuard = 0
        nextMoves = 0
        runner?.reset()
        gameState = GameState.GAME_START
    }

    fun tick( ctx: KoolContext ) {
        when(gameState) {
            GameState.GAME_START -> {
                if ( runner?.anyKeyPressed == true) {
                    gameState = GameState.GAME_RUNNING
                    if ( level.isDone ) level.showHiddenLadders()
                }
            }

            GameState.GAME_RUNNING -> {
                playGame()
                if ( level.isDone ) {
                    level.showHiddenLadders()
                }

                if ( level.isDone && runner?.block?.y == 0 && runner?.offset?.y == 0) {
                    // enter state
                    gameState = GameState.GAME_FINISH
                }
            }
            GameState.GAME_FINISH -> {
                // stop all sprites anims
                // play mode == PLAY
                runner?.addScore(SCORE_COMPLETE)
                gameState = GameState.GAME_FINISH_SCORE_COUNT


            }

            GameState.GAME_RUNNER_DEAD -> {
//                stopSound
            }
            else -> {
                println(gameState)
            }
        }
    }

    val onPlayGame = mutableListOf<Game.(level: GameLevel) -> Unit>()

    fun playGame(): Boolean {
        runner?.update()
        guardsUpdate()
        level.update(runner!!, guards)
        onPlayGame.forEach { it.invoke(this, level) }
        return level.isDone
    }

    fun guardsUpdate() {
        if ( guards.isEmpty() ) return

        // some old AI?
        if ( nextMoves == 2 ) {
            nextMoves = 0
        } else {
            nextMoves ++
        }

        var movesCount = if ( level.status == GameLevel.Status.LEVEL_STARTUP ) {
            guards.size
        } else {
            Guard.guardsMoves[guards.size][nextMoves]
        }

        while ( movesCount > 0 ) {
           if ( nextGuard == guards.size - 1 ) {
               nextGuard = 0
           } else {
               nextGuard ++
           }

           with(guards[nextGuard]) {
               if ( !alive ) {
                   // respawn
                   for ( ty in 0 until level.height ) {
                       block.x = Random.nextInt(level.width)
                       block.y = ty
                       if ( level.base[block.y][ty] == TileLogicType.EMPTY ) {
                           action = "reborn"
                           alive = true
                           inHole = false
                           break
                       }
                   }
               }

               if ( !stopGuards ) {
                   updateGuard(runner!!)
               }
           }


            movesCount--
        }
    }
}

class GameControls(val game: Game, val inputManager: InputManager) {

}

enum class GameAction(
    val keyCode: KeyCode, // or no
    val onPress: Game.(InputManager.KeyEvent) -> Unit = {},
    val onRelease: Game.(InputManager.KeyEvent) -> Unit
) {
    BACK(InputManager.KEY_BACKSPACE, onRelease = {
        // stopAudio
        // destroy chars
        // destroy stage
        // exit cycle
    }),
    RESPAWN(UniversalKeyCode('r'), onRelease = { runner?.alive = false })

}