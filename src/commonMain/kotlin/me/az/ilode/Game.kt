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


class GameSettings(val settings: Settings) {
    var spriteMode: TileSet by enumDelegate(settings, defaultValue = TileSet.SPRITES_APPLE2)
    var version: LevelSet by enumDelegate(settings, defaultValue = LevelSet.CLASSIC)
    val startLevel: Int by settings.int(defaultValue = 0)
    var speed: GameSpeed by enumDelegate(settings, defaultValue = GameSpeed.SPEED_NORMAL)
}

const val SCORE_COMPLETE = 1500
const val SCORE_GOLD     = 250
const val SCORE_FALL     = 75
const val SCORE_DIES     = 75

const val TILES_X = 28
const val TILES_Y = 16
//system
class Game(val settings: GameSettings) {
    var stopGuards = false
    var immortal = false
    var speed = settings.speed
    lateinit var runner: Runner
    val guards = mutableListOf<Guard>()

    var nextGuard = 0
    var nextMoves = 0

    val gameOver get() = runner.health > 0

    lateinit var level: GameLevel

    fun levelStartup(level: GameLevel, guardAnims: AnimationFrames) {
        this.level = level
        // playStartAnim
        runner = Runner(level)
        guards.clear()
        level.guards.forEach {
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
        runner.update()
        guardsUpdate()
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

               }

               if ( !stopGuards ) {
                   updateGuard(runner)
               }
           }


            movesCount--
        }

        //guards.forEach { it.updateGuard(runner) }
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