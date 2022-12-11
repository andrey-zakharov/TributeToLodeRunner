package me.az.ilode

import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.math.clamp
import de.fabmax.kool.math.min
import de.fabmax.kool.math.randomI
import me.az.utils.format
import kotlin.math.abs
import kotlin.random.Random

class GuardAiController(val guard: Controllable, val runner: Runner) {

}
class Guard(game: Game, private val random: Random = Random.Default) : Actor(game) {
    var hasGold = 0
    var inHole = false

    private fun randomRangeGenerator(min: Int, max:Int, random: Random = this.random): Sequence<Int> {
        val current = mutableListOf<Int>()
        var i = 0
        return generateSequence {
            if ( current.size == 0 || i % current.size == 0 ) {
                // restart random sequence
                current.clear()
                current.addAll((min until max).shuffled(random))
            }
            current[i++ % current.size]
        }
    }

    companion object {
        // old AI?
        val guardsMoves = arrayOf(
            arrayOf(0, 0, 0),
            arrayOf(0, 1, 1),
            arrayOf(1, 1, 1),
            arrayOf(1, 2, 1),
            arrayOf(1, 2, 2),
            arrayOf(2, 2, 2),
            arrayOf(2, 2, 3))
        var nextGuard = 0
    }

    init {
        game.onLevelStart += {
            action = ActorSequence.RunLeft
        }
    }

    override fun takeGold(): Boolean =
        if (hasGold == 0) {
            hasGold = randomI(0, 26) + 12
            true
        } else false

    override val canStay: Boolean
        get() = super.canStay || inHole // when running up from hole

    override val availableSpaceRight: Int get() {
        if ( level.hasGuard(x + 1, y) ) return (game.getGuard(x + 1, y).ox - ox).clamp(0, xMove)
        // guard down right not moving enough
        if ( level.hasGuard(x + 1, y + 1)) {
            val g = game.getGuard(x + 1, y + 1)
            if ( g.oy < 0 ) return ( g.ox - ox ).clamp(0, xMove)
        }
        if ( level.hasGuard(x + 1, y - 1)) {
            val g = game.getGuard(x + 1, y - 1)
            if ( g.oy > 0 ) return ( ox - g.ox ).clamp(0, xMove)
        }

        return super.availableSpaceRight
    }

    override val availableSpaceLeft: Int get() {
        if ( level.hasGuard(x - 1, y) ) return (ox - game.getGuard(x - 1, y).ox).clamp(0, xMove)

        // guard down right not moving enough
        if ( level.hasGuard(x - 1, y + 1)) {
            val g = game.getGuard(x - 1, y + 1)
            if ( g.oy < 0 ) return ( g.ox - ox ).clamp(0, xMove)
        }
        if ( level.hasGuard(x - 1, y - 1)) {
            val g = game.getGuard(x - 1, y - 1)
            if ( g.oy > 0 ) return ( ox - g.ox ).clamp(0, xMove)
        }

        return super.availableSpaceLeft
    }

    override val canMoveUp get() = (
        super.canMoveUp && (
            !level.hasGuard(x, y - 1) ||
            (level.hasGuard(x, y - 1) && oy < game.getGuard(x, y - 1).oy)
        ) ) || (this.inHole && level.isHole(x, y)) // hole hack

    // TBD collisions and groups
    override val canMoveDown get() = super.canMoveDown && (
            !level.hasGuard(x, y + 1) ||
            (level.hasGuard(x, y + 1) && oy > game.getGuard(x, y + 1).oy)
    )

    override val availableSpaceDown: Int
        get() = if (level.hasGuard(x, y + 1)) min(game.getGuard(x, y + 1).oy - oy, yMove)
            else super.availableSpaceDown

    override val availableSpaceUp: Int
        get() = if (level.hasGuard(x, y - 1)) min(oy - game.getGuard(x, y - 1).oy, yMove)
            else super.availableSpaceUp

    fun updateGuard(runner: Runner) {
        if ( fsm.currentState is ActorState.ControllableState ) {
            val nextAct = bestMove(runner)
//            println("best act = $nextAct")
            val neiGuard = when (nextAct) {
                Action.ACT_UP -> Vec2i(x, y - 1)
                Action.ACT_DOWN -> Vec2i(x, y + 1)
                Action.ACT_RIGHT -> Vec2i(x + 1, y)
                Action.ACT_LEFT -> Vec2i(x - 1, y)
                else -> null
            }

            if (neiGuard != null && !level.hasGuard(neiGuard)) {
//                    move(nextAct)
                inputVec.x = when (nextAct) {
                    Action.ACT_RIGHT -> 1
                    Action.ACT_LEFT -> -1
                    else -> 0
                }
                inputVec.y = when (nextAct) {
                    Action.ACT_DOWN -> 1
                    Action.ACT_UP -> -1
                    else -> 0
                }
            } else {
                inputVec.x = 0
                inputVec.y = 0
            }
//            }
            //}
        }

        val oldx = x
        val oldy = y
        //update fsm
        super.update()

        // try to drop gold every step
        if ( fsm.currentState is ActorState.MovementState ) dropGold()
        if ( level.isBlock(x, y) ) fsm.setState(ActorSequence.Reborn.id)

        if ( oldx != x || oldy != y ) {
//            println("${fsm.currentStateName} $frameIndex $oldx -> ${block.x} $oldy -> ${block.y}")
            level.guard[oldx][oldy] = false // field.removeGuard
            level.guard[x][y] = true // field.addGuard

        }

        // leave hole
        if ( fsm.currentState is ActorState.ControllableState ) {
            if (inHole && !level.isHole(x, y) && !level.isHole(x, y + 1)) inHole = false
        }
    }

    override val fsm by lazy {
        super.fsm.apply {
            this += InHole(this@Guard)
            this += Shake(this@Guard, ActorSequence.ShakeLeft)
            this += Shake(this@Guard, ActorSequence.ShakeRight)

            state(ActorSequence.Reborn.id) {
                onEnter {

                    val gen = randomRangeGenerator(0, level.width).iterator()
                    for ( bornY in 1 until level.height ) {
                        val bornX = gen.next()
                        if ( level.isEmpty(bornX, bornY) ) {
                            block.x = bornX
                            block.y = bornY

                            action = ActorSequence.Reborn
                            // play this state until anim ends
                            game.runner.addScore(SCORE_DIES)
                            break
                        }
                    }
                }
                edge(ActorState.StopState.name) {
                    validWhen { sequenceSize != 0 && frameIndex >= sequenceSize }
                }
                onUpdate {
                    frameIndex++
                    null
                }
                onExit {
                    game.playSound(Sound.BORN, x, y)
                }
            }

            getState(ActorState.FallState.name).apply {
                edge(InHole.name) {
                    validWhen { oy < 0 && level.isHole(x, y) }
                }
            }

//            debugOn()
        }

    }

    class InHole(actor: Guard) : ActorState(actor, null, name) {
        companion object { const val name = "inhole"}
        init {
            onEnter { with(actor) {
                if (hasGold > 0) {
//                    println("has gold: empty: ${level.isEmpty(x, y - 1)}")
                    if (level.isEmpty(x, y - 1)) {
                        level.dropGold(x, y - 1)
                        hasGold = 0
                    } else {
                        // disappered from level
                        level.gold --
                    }
                }
            }}

            BehaviorMoveDown { // on center
                offset.y = 0
                game.playSound(Sound.TRAP, x, y)
                game.runner.addScore(SCORE_FALL)
                when(action) {
                    ActorSequence.FallRight -> ActorSequence.ShakeRight
                    else -> ActorSequence.ShakeLeft
                }.id
            }
        }
    }

    class Shake(actor: Guard, animName: ActorSequence) : ActorState(actor, animName, animName.id) {
        init {
            onEnter { actor.inHole = true }
            onUpdate {
                if ( frameIndex < sequenceSize ) {

                } else {
                    return@onUpdate RunUp.name
                }

                frameIndex++
                null
            }
        }
    }

    private fun dropGold(): Boolean {
        when {
            hasGold > 1 -> hasGold -- // // count > 1,  don't drop it only decrease count
            hasGold == 1 -> { //drop gold
                // not at hidden ladders
                if ( level.isEmpty(x, y) && level.base[x][y] == TileLogicType.EMPTY && level.isFloor(x, y + 1, useGuard = false) ) {
                    level.dropGold( x, y )
                    hasGold = -1
                    return true
                }
            }
            hasGold < 0 -> hasGold++
        }
        return false
    }

    private fun bestMove(runner: Actor): Action {
        if ( inHole ) { // for controllable state
            if ( level.isHole(x, y) ) {
                return Action.ACT_UP
            }
        }

        val maxTileY = level.height - 1
        var x = block.x
        val y = block.y

        if ( y == runner.block.y || runner.block.y == maxTileY && y == maxTileY - 1 ) {
            while ( x != runner.block.x ) {
                if ( level.isLadder(x, y, false) || level.isFloor(x, y+1, true) ||
                    level.isBar(x, y) || level.isBar(x, y+1) || level.isGold(x, y+1)) {

                    if ( x < runner.block.x ) {
                        x++
                    } else if ( x > runner.block.x ) {
                        x--
                    }
                } else {
                    break
                }
            }

            if ( x == runner.block.x ) {
                val nextMove = if ( block.x < runner.block.x ) {
                    Action.ACT_RIGHT
                } else if ( block.x > runner.block.x ) {
                    Action.ACT_LEFT
                } else {
                    if ( offset.x < runner.offset.x ) {
                        Action.ACT_RIGHT
                    } else {
                        Action.ACT_LEFT
                    }
                }
                return nextMove
            }
        }

        return scanFloor(runner)
    }

    class GuardAiContext {
        var bestRating = 255
        var bestPath = Action.ACT_NONE
        var leftEnd: Int = -1
        var rightEnd: Int = -1
    }

    private fun scanFloor(runner: Actor): Action {
        var x = block.x
        val y = block.y
        val maxTileX = level.width - 1
        val maxTileY = level.height - 1
        val guardAi = GuardAiContext()

        // calculate left limit
        guardAi.leftEnd = block.x
        while ( guardAi.leftEnd > 0 ) {
            val curTile = Vec2i( guardAi.leftEnd - 1, y )
            if ( level.getAct(curTile) == TileLogicType.BLOCK || level.getAct(curTile) == TileLogicType.SOLID ) {
                break
            }
            val downTile = Vec2i(guardAi.leftEnd - 1, y + 1 )
            if ( level.isLadder(curTile, false) || level.isBar(curTile) ||
                y >= maxTileY || y < maxTileY && level.isFloor(downTile, true, false) ) {
                guardAi.leftEnd --
            } else {
                guardAi.leftEnd --
                break
            }
        }

        // calculate right limit
        guardAi.rightEnd = block.x
        while ( guardAi.rightEnd < maxTileX ) {
            val curTile = Vec2i(guardAi.rightEnd + 1, y )
            val curAct = level.getAct(curTile)
            if ( curAct == TileLogicType.BLOCK || curAct == TileLogicType.SOLID ) {
                break
            }

            val downTile = Vec2i(guardAi.rightEnd + 1, y + 1)

            if ( level.isLadder(curTile, false) || level.isBar(curTile) || y >= maxTileY ||
                    y < maxTileY && level.isFloor(downTile, true, false) ) {
                guardAi.rightEnd ++
            } else {
                guardAi.rightEnd ++
                break
            }
        }

        // scan from current position
        if ( y < maxTileY ) {
            val downTileBase = level.base[x][y + 1]
            if ( downTileBase != TileLogicType.BLOCK && downTileBase != TileLogicType.SOLID ) {
                scanDown(x, Action.ACT_DOWN, guardAi, runner)
            }
        }

        if ( level.base[x][y] == TileLogicType.LADDER ) {
            scanUp(x, Action.ACT_UP, guardAi, runner)
        }

        // scan left and right
        var curPath = Action.ACT_LEFT
        x = guardAi.leftEnd
        while (true) {
            if ( x == block.x ) {
                if ( curPath == Action.ACT_LEFT && guardAi.rightEnd != block.x ) {
                    curPath = Action.ACT_RIGHT
                    x = guardAi.rightEnd
                } else {
                    break
                }
            }

            if ( y < maxTileY ) {
                val downTile = level.base[x][y + 1]
                if ( downTile != TileLogicType.BLOCK && downTile != TileLogicType.SOLID ) {
                    scanDown(x, curPath, guardAi, runner)
                }
            }

            if ( level.base[x][y] == TileLogicType.LADDER ) {
                scanUp(x, curPath, guardAi, runner)
            }

            if ( curPath == Action.ACT_LEFT ) {
                x ++
            } else {
                x --
            }
        }

        return guardAi.bestPath
    }

    private fun scanDown(x: Int, curPath: Action, guardAi: GuardAiContext, runner: Actor) {
        val maxTileX = level.width - 1
        val maxTileY = level.height - 1
        var y = block.y

        while ( y < maxTileY && level.base[x][y + 1] != TileLogicType.BLOCK && level.base[x][y + 1] != TileLogicType.SOLID ) {
            if ( level.base[x][y] != TileLogicType.EMPTY && level.base[x][y] != TileLogicType.HLADDER ) {
                if ( x > 0 ) {
                    val downTile = Vec2i( x - 1, y + 1 )
                    if ( level.isFloor(downTile, true, false) || level.base[x - 1][y] == TileLogicType.BAR ) {
                        if ( y >= runner.block.y ) {
                            break
                        }
                    }
                }

                if ( x < maxTileX ) {
                    val downTile = Vec2i(x + 1, y + 1)
                    if ( level.isFloor(downTile) || level.base[ x + 1 ][y] == TileLogicType.BAR ) {
                        if ( y >= runner.block.y ) {
                            break
                        }
                    }
                }
            }

            y++
        }

        val curRating = if ( y == runner.block.y ) {
            abs(runner.block.x - x)
        } else if ( y > runner.block.y ) {
            y - runner.block.y + 200
        } else {
            runner.block.y - y + 100
        }

        if ( curRating < guardAi.bestRating ) {
            guardAi.bestRating = curRating
            guardAi.bestPath = curPath
        }
    }

    private fun scanUp(x: Int, curPath: Action, guardAi: GuardAiContext, runner: Actor) {
        var y = block.y
        while ( y > 0 && level.base[x][y] == TileLogicType.LADDER ) {
            y--
            if ( x > 0 ) {
                val downTile = Vec2i(x - 1, y + 1)
                if ( level.isFloor(downTile, useBase = true, useGuard = false) || level.base[x-1][y] == TileLogicType.BAR ) {
                    if ( y <= runner.block.y ) {
                        break
                    }
                }
            }

            if ( x < level.width - 1 ) {
                val downTile = Vec2i(x + 1, y + 1)
                if ( level.isFloor(downTile, useBase = true, useGuard = false) || level.base[x+1][y] == TileLogicType.BAR ) {
                    if ( y <= runner.block.y ) {
                        break
                    }
                }
            }
        }

        val curRating = if ( y == runner.block.y ) {
            abs( block.x - x )
        } else if ( y > runner.block.y ) {
            y - runner.block.y + 200
        } else {
            runner.block.y - y + 100
        }
        if ( curRating < guardAi.bestRating ) {
            guardAi.bestRating = curRating
            guardAi.bestPath = curPath
        }
    }

    val isReborn get() = action == ActorSequence.Reborn

    override fun toString() = "%s guard %d+%d x %d+%d state=%s gold=%d inhole=%s".format(
        super.toString(), x, ox, y, oy, fsm.currentStateName, hasGold, inHole
    )
}
