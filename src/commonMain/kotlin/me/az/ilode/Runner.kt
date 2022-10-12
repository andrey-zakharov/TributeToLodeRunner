package me.az.ilode

import de.fabmax.kool.math.MutableVec2i
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.pipeline.RenderPass
import me.az.utils.StackedStateMachine
import me.az.utils.buildStateMachine


const val START_HEALTH = 5
const val MAX_HEALTH = 100
val Controllable.anyKeyPressed get() = digLeft || digRight || inputVec.x != 0 || inputVec.y != 0

class Runner2(game: Game) : Actor2(game, false), Controllable {
    var health = START_HEALTH
    var score = 0
    val success: Boolean get() = y == 0 && oy == 0 && game.level?.isDone == true
    fun startLevel(level: GameLevel) {
        block.x = level.runnerPos.x
        block.y = level.runnerPos.y
        offset.x = 0
        offset.y = 0
    }

    override val fsm by lazy {
        val stopState = StopState(this@Runner2)
        buildStateMachine(stopState.name) {
            this += stopState
            this += RunLeft(this@Runner2)
            this += RunRight(this@Runner2)
            this += RunUp(this@Runner2)
            this += RunDown(this@Runner2)
            this += BarLeft(this@Runner2)
            this += BarRight(this@Runner2)
            this += DigRight(this@Runner2)
            this += DigLeft(this@Runner2)
            // need something better
            this += FallState(this@Runner2, false)
        }
    }

    //Page 276 misc.c (book)
    fun ok2Dig(nx: Int): Boolean {
        return level.isBlock(nx, y + 1) && level.isEmpty(nx, y)
    }

    fun addScore(points: Int) {
        score += points
    }

    override fun takeGold(): Boolean {
        sounds.playSound("getGold")
        level.gold --
        addScore(SCORE_GOLD)

        if ( level.gold == 0 ) {
//                    playSound("goldFinish${(level.levelId - 1) % 6 + 1}")
            sounds.playSound("goldFinish")
            level.showHiddenLadders()
        }
        return true
    }
}
class Runner(level: GameLevel) : Actor(level, CharType.RUNNER) {
    val stance = Array(4) { false } // up, right, down, left

    var health = START_HEALTH
    var score = 0
    var digLeft: Boolean = false
    var digRight: Boolean = false
    val dig: Boolean get() = digLeft || digRight
    val success: Boolean get() = block.y == 0 && offset.y == 0 && level.gold == 0

    val anyKeyPressed get() = dig || stance.any { it }

    val canDig: Boolean get() {
        val x = block.x
        val y = block.y
        if ( (digLeft || (action == "runLeft" && !digRight)) && x > 1 ) {

            if ( y < level.height && x > 0 ) {
                val lTile = Vec2i(x-1, y+1)
                val dTile = Vec2i(x-1, y)

                if ( level.getAct(lTile) == TileLogicType.BLOCK && level.getAct(dTile) == TileLogicType.EMPTY) {
                    if ( level.getBase(dTile) != TileLogicType.GOLD && !level.hasGuard(dTile) ) {
                        return true
                    }
                }
            }
        } else if ((digRight || action == "runRight") && x < level.width - 2 ) {
            if ( y < level.height && x < level.width ) {
                val rTile = Vec2i(x+1, y+1)
                val dTile = Vec2i(x+1, y)
                if ( level.getAct(rTile) == TileLogicType.BLOCK && level.getAct(dTile) == TileLogicType.EMPTY) {
                    if ( level.getBase(dTile) != TileLogicType.GOLD && !level.hasGuard(dTile)) {
                        return true
                    }
                }
            }
        }
        return false
    }


    init {
        block = level.runnerPos
    }
    fun reset() {
        digLeft = false
        digRight = false
        stance.fill(false)
    }

    fun addScore(points: Int) {
        score += points
    }

    fun update() {
        if ( success ) return

        // Update runner position
        move( when {
            ( state == State.STATE_FALL) || level.status == GameLevel.Status.LEVEL_STARTUP -> Action.ACT_NONE
            dig -> Action.ACT_DIG
            stance[0] -> Action.ACT_UP
            stance[1] -> Action.ACT_RIGHT
            stance[2] -> Action.ACT_DOWN
            stance[3] -> Action.ACT_LEFT
            else -> Action.ACT_NONE
        } )

        // Falling sound
        if ( state == State.STATE_FALL && level.status == GameLevel.Status.LEVEL_PLAYING ) {
            playSound("fall")
        } else {
            sounds.stopSound("fall")
        }
        updateFrame()

    }




}