package me.az.ilode

import de.fabmax.kool.math.Vec2i


const val START_HEALTH = 5
const val MAX_HEALTH = 100

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