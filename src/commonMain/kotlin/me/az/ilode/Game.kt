package me.az.ilode

import AnimationFrames
import GameSpeed
import LevelSet
import TileSet
import com.russhwolf.settings.*
import de.fabmax.kool.InputManager
import de.fabmax.kool.KeyCode
import de.fabmax.kool.UniversalKeyCode
import me.az.utils.enumDelegate
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
}

const val SCORE_COMPLETE = 1500
const val SCORE_GOLD     = 250
const val SCORE_FALL     = 75
const val SCORE_DIES     = 75

//system
class Game(val settings: GameSettings) {
    var stopGuards = false
    var immortal = false
    var speed = settings.speed
    lateinit var runner: Runner
    val guards = mutableListOf<Guard>()

    var nextGuard = 0
    var nextMoves = 0

    val gameOver get() = runner.health <= 0
    var gameState = GameState.GAME_START
    val isPlaying get() = gameState == GameState.GAME_RUNNING
    val isPaused get() = gameState == GameState.GAME_PAUSE

    lateinit var level: GameLevel

    fun levelStartup(level: GameLevel, guardAnims: AnimationFrames) {
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
    }

    fun reset() {
        nextGuard = 0
        nextMoves = 0
        runner.reset()
    }

    fun tick() {
        when(gameState) {
            GameState.GAME_START -> {
                if (runner.anyKeyPressed) {
                    gameState = GameState.GAME_RUNNING
                    if ( level.gold <= 0 ) level.showHiddenLadders()
                }
            }

            GameState.GAME_RUNNING -> {
                playGame()
            }

            GameState.GAME_RUNNER_DEAD -> {
//                stopSound
            }
            else -> {}
        }
    }

    val onPlayGame = mutableListOf<Game.(level: GameLevel) -> Unit>()

    fun playGame() {
        runner.update()
        guardsUpdate()
        level.update(runner, guards)
        onPlayGame.forEach { it.invoke(this, level) }
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
                   updateGuard(runner)
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
    RESPAWN(UniversalKeyCode('r'), onRelease = { runner.alive = false })

}