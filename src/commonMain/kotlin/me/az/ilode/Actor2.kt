package me.az.ilode

import SoundPlayer
import de.fabmax.kool.math.MutableVec2i
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.pipeline.RenderPass
import me.az.utils.StackedState
import me.az.utils.buildStateMachine


//stack
fun<E> MutableList<E>.pop() = removeLastOrNull()
fun<E> MutableList<E>.push(e: E) = add(e)
fun<E> MutableList<E>.current() = lastOrNull()

enum class ActorSequence(val id: String) {
    RunRight("runRight"),
    RunLeft("runLeft"),
    RunUpDown("runUpDown"),
    BarRight("barRight"),
    BarLeft("barLeft"),
    FallRight("fallRight"),
    FallLeft("fallLeft"),
    // runner
    DigRight("digRight"),
    DigLeft("digLeft"),
}

// controller = Input, AI
interface Controllable {
    val inputVec: MutableVec2i // (-1, 0) etc
    var digLeft: Boolean
    var digRight: Boolean
}
sealed class Actor2(val game: Game) : Controllable {
    // conf
    var xMove = 4
    var yMove = 4

    var alive: Boolean = true
    var block = MutableVec2i(Vec2i.ZERO)
    var offset = MutableVec2i(Vec2i.ZERO)
    val level get() = game.level!!
    val x get() = block.x
    val y get() = block.y
    val ox get() = offset.x
    val oy get() = offset.y
    var action: ActorSequence = ActorSequence.RunLeft // sequence name
        set(v) {
            if ( field != v) {
                field = v
                frameIndex = 0
            }
        }
    var frameIndex: Int = 0 // position in sequence

    // controls
    override val inputVec = MutableVec2i()
    override var digLeft: Boolean = false
    override var digRight: Boolean = false

    lateinit var sounds: SoundPlayer
//    private val fsm = StackedStateMachine(this)
    val stopState = StopState(this)
    private val fsm = buildStateMachine(stopState.name) {
        this += stopState
        this += RunLeft(this@Actor2)
        this += RunRight(this@Actor2)
        this += RunUp(this@Actor2)
        this += RunDown(this@Actor2)
        this += BarLeft(this@Actor2)
        this += BarRight(this@Actor2)
        this += FallState(this@Actor2)
    }

    open fun update(ev: RenderPass.UpdateEvent) {
       fsm.update(this)
        //check collision

    }
}

// states and valid transitions
sealed class ActorState(val actor: Actor2, val animName: ActorSequence?, name: String = animName!!.id) : StackedState<Actor2>(name) {
    init {
        onEnter {
            animName?.run { actor.action = this }
        }
    }

    fun Actor2.centerX() {
        if ( ox > 0 ) {
            offset.x -= xMove
            if ( offset.x < 0) offset.x = 0 //move to center X
        } else if ( ox < 0 ) {
            offset.x += xMove
            if ( offset.x > 0) offset.x = 0 //move to center X
        }
    }

    fun Actor2.centerY() {
        if ( offset.y > 0 ) {
            offset.y -= yMove
            if ( offset.y < 0)  offset.y = 0 //move to center Y
        } else if (offset.y < 0) {
            offset.y += yMove
            if ( offset.y > 0 ) offset.y = 0 //move to center Y
        }
    }

    /*open fun checkActionValid(game: Game, action: Action): Pair<Boolean, Action> = game.runner?.run {
        //correct action
        var moveStep = Action.ACT_NONE

        val (x, y) = block
        val (oy, ox) = offset

        var stayCurrPos = when(action) {
            Action.ACT_UP -> level.isBarrier(x, y - 1)
            Action.ACT_DOWN -> !level.isPassableForDown(x, y + 1)
            Action.ACT_LEFT -> level.isBarrier(x - 1, y)
            Action.ACT_RIGHT -> level.isBarrier(x + 1, y)
            else -> false
        }
/*        if ( level.isLadder(x, y) ||
            (oy > 0 && oy < H4 && level.isLadder(x, y + 1)) ||
            (oy <= 0 && )
        ) {

        }
        if ( level.isLadder(x, y) && oy > 0 && level.isLadder(x, y + 1) || stayCurrPos)*/

        when(action) {
            Action.ACT_UP -> {
                stayCurrPos = level.isBarrier(x, y - 1)

                if (!level.isLadder(x, y) && oy > 0 && oy < H4 && level.isLadder(x, y + 1)) {
                    stayCurrPos = true
                    moveStep = Action.ACT_UP
                } else
                    if (!level.isLadder(x, y) &&
                                (oy <= 0 || !level.isLadder(x, y + 1)) ||
                                (oy <= 0 && stayCurrPos)
                    ) {
                        moveStep = Action.ACT_UP
                    }
            }
            Action.ACT_DOWN -> {
                stayCurrPos = !level.isPassableForDown(x, y + 1)

                if (!(oy >= 0 && stayCurrPos))
                    moveStep = Action.ACT_DOWN;
            }
            Action.ACT_LEFT -> {
                stayCurrPos = !level.isBarrier(x - 1, y)

                if (!(ox <= 0 && stayCurrPos))
                    moveStep = Action.ACT_LEFT
            }
            Action.ACT_RIGHT -> {
                stayCurrPos = !level.isBarrier(x + 1, y)

                if (!(ox >= 0 && stayCurrPos))
                    moveStep = Action.ACT_RIGHT;
            }
//            Action.ACT_DIG -> {
//                this as Runner2
//                if (ok2Dig(keyAction)) {
//                    runnerMoveStep(keyAction, stayCurrPos);
//                    digHole(keyAction);
//                } else {
//                    runnerMoveStep(ACT_STOP, stayCurrPos);
//                }
//                keyAction = ACT_STOP;
//            }
            else -> {
                Pair(false, Action.ACT_NONE)
            }
        }

        Pair(stayCurrPos, moveStep)
    } ?: Pair(false, Action.ACT_NONE)

     */
}
sealed class ControllableState(actor: Actor2, animName: ActorSequence?, name: String = animName!!.id) : ActorState(actor, animName, name) {
    var contMode = true
    init {
        edge(RunUp.name) {
            validWhen {
                inputVec.y < 0 &&
                        level.isLadder(x, y)
//                        (oy > 0 && oy < H4 && level.isLadder(x, y)) ||
//                        (oy <= 0 && level.isLadder(x, y - 1))
            }
        }

        edge(RunDown.name) {
            validWhen {
                inputVec.y > 0 && ((oy >= 0 && level.isPassableForDown(x, y + 1)) || oy < 0)
            }
        }

        edge(ActorSequence.RunLeft.id) {
            validWhen {
                inputVec.x < 0 && (
                    ( ox > 0 || !(level.isBarrier(x - 1, y) || level.isBar(x - 1, y)) )
                )
            }
        }

        edge(ActorSequence.RunRight.id) {
            validWhen {
                inputVec.x > 0 && (
                    (ox < 0) || (ox >= 0 && !(level.isBarrier(x + 1, y) || level.isBar(x + 1, y)))
                )
            }
        }

        edge(ActorSequence.BarLeft.id) {
            validWhen {
                inputVec.x < 0 && (level.isBar(x, y) || (ox <= 0 && level.isBar(x - 1, y)))
            }
        }

        edge(ActorSequence.BarRight.id) {
            validWhen {
                inputVec.x > 0 && (level.isBar(x, y) || (ox >= 0 && level.isBar(x + 1, y)))
            }
        }
        edge(FallState.name) {
            action {

                actor.action = when(this@ControllableState) {
                    is RunLeft, is BarLeft -> ActorSequence.FallLeft
                    /*is RunRight, is BarRight, */
                    else -> ActorSequence.FallRight
                }
            }

            validWhen {
                val onLadder = level.isLadder(x, y) || (oy >= 0 && level.isLadder(x, y + 1))
                val guardBelow = level.hasGuard(x, y + 1)
                val onFoot = onLadder || oy == 0 && (level.isBar(x, y) || level.isFloor(x, y + 1, useGuard = true))
                val aboveGround = oy < 0 && !onLadder && (!guardBelow || (guardBelow && oy < game.getGuard(x, y+1).offset.y))
                !onFoot || aboveGround
            }
        }
        edge(StopState.name) {
            validWhen { !contMode && !anyKeyPressed }
        }
    }
}
class StopState(actor: Actor2) : ControllableState(actor, null, name) {
    companion object {
        const val name = "stop"
    }
}

sealed class MovementState(actor: Actor2, animName: ActorSequence?, name: String = animName!!.id) : ControllableState(actor, animName, name) {

    init {
        // pause runner when all edges fails but still some keys
        edge(StopState.name) {
            validWhen {
                inputVec.x < 0 && (this@MovementState !is RunLeft && this@MovementState !is BarLeft)
            }
            validWhen {
                inputVec.x > 0 && (this@MovementState !is RunRight && this@MovementState !is BarRight)
            }
            validWhen { inputVec.y > 0 && this@MovementState !is RunDown }
            validWhen { inputVec.y < 0 && this@MovementState !is RunUp }
            validWhen { digLeft }
            validWhen { digRight }

        }

        onUpdate {
            frameIndex ++
            false
        }
    }
}
// bar and simple walk
sealed class MoveLeft(actor: Actor2, animName: ActorSequence, val invariant: Actor2.() -> Boolean ) : MovementState(actor, animName) {
    init {
        edge(StopState.name) {
            action { actor.offset.x = 0 }
            validWhen { !invariant() }
        }

        onUpdate {
            offset.x -= xMove
            centerY()
            if ( !invariant() ) offset.x = 0 // stop until next tick
            if (ox < -W2) { //move to x-1 position
                block.x--
                offset.x += TILE_WIDTH
            }

            false
        }
    }
}

// bar and walk
sealed class MoveRight(actor: Actor2, animName: ActorSequence, val invariant: Actor2.() -> Boolean ) : MovementState(actor, animName) {
    init {
        edge(StopState.name) {
            action { actor.offset.x = 0 }
            validWhen { !invariant() }
        }

        onUpdate {
            offset.x += xMove
            centerY()
            if ( !invariant() ) offset.x = 0
            if ( ox > W2 ) { //move to x+1 position
                block.x++
                offset.x -= TILE_WIDTH
            }

            false
        }
    }
}

class RunLeft(actor: Actor2) : MoveLeft(actor, ActorSequence.RunLeft,
    { ox > 0 || (ox <= 0 && !level.isBarrier(x - 1, y ))}
) {
    init {
        edge(ActorSequence.BarLeft.id) {
            validWhen { ox <= 0 && level.isBar(x - 1, y ) }
        }
    }
}

class RunRight(actor: Actor2) : MoveRight(actor, ActorSequence.RunRight,
    {ox < 0 || (ox >= 0 && !level.isBarrier(x + 1, y))}
) {
    init {
        edge(ActorSequence.BarRight.id) {
            validWhen { ox >= 0 && level.isBar(x + 1, y) }
        }
    }
}

class BarLeft(actor: Actor2) : MoveLeft(actor, ActorSequence.BarLeft,
    { ox > 0 || (ox <= 0 && !level.isBarrier(x - 1, y))}
) {
    init {
        edge(ActorSequence.RunLeft.id) {
            validWhen { ox <= 0 && !level.isBarrier(x - 1, y) && !level.isBar(x - 1, y) }
        }
        onEnter {
            actor.offset.y = 0
        }
    }

}

class BarRight(actor: Actor2) : MoveRight(actor, ActorSequence.BarRight,
    { ox < 0 || (ox >= 0 && !level.isBarrier(x + 1, y))}
) {
    init {
        edge(ActorSequence.RunRight.id) {
            validWhen { ox >= 0 && !level.isBarrier(x + 1, y) && !level.isBar(x + 1, y) }
        }
        onEnter {
            actor.offset.y = 0
        }
    }
}

class RunDown(actor: Actor2): MovementState(actor, ActorSequence.RunUpDown, name) {
    companion object {
        const val name = "runDown"
    }

    val Actor2.invariant get() = oy >= 0 && (!level.isPassableForDown(x, y + 1))

    init {
        edge(StopState.name) {
            validWhen { invariant }
        }
        onUpdate {
            offset.y += yMove
            centerX()
            if ( invariant ) offset.y = 0
            if ( oy > H2 ) { //move to y+1 position
                block.y++
                offset.y -= TILE_HEIGHT
            }

            false
        }
    }
}

class RunUp(actor: Actor2): MovementState(actor, ActorSequence.RunUpDown, name) {
    companion object {
        const val name = "runUp"
    }

    val Actor2.invariant get() = oy <= 0 && (level.isBarrier(x, y - 1) || !level.isLadder(x, y))

    init {

        edge(StopState.name) {
            action { actor.offset.y = 0 }
            validWhen {
                invariant
            }
        }
        onUpdate {
            offset.y -= yMove
            centerX()
            if ( invariant ) offset.y = 0
            if ( oy < -H2 ) { //move to y-1 position
                block.y--
                offset.y += TILE_HEIGHT
            }

            false
        }
    }
}

// dynamic animation name
// level.base used for guards
// level.act used for runner
// stacked
class FallState(actor: Actor2, private val useBase: Boolean = false) : ActorState(actor, null, name) {
    val Actor2.invariant get() =
        oy < 0 || (oy >= 0 && !level.isFloor(x, y + 1, useBase, true))
    companion object {
        const val name = "fall"
    }
    init {
        onEnter { with(actor) {
            sounds.playSound("fall")
        }}
        onExit { with(actor) {
            sounds.stopSound("fall")
            sounds.playSound("down")
        }}

        edge(StopState.name) {
            validWhen { !invariant }
        }

        onUpdate {

            if ( inputVec.x < 0 && action != ActorSequence.FallLeft) {
                action = ActorSequence.FallLeft
            }
            if ( inputVec.x > 0 && action != ActorSequence.FallRight) {
                action = ActorSequence.FallRight
            }
            val toBar = oy < 0 && level.isBar(x, y) // rework TBD
            offset.y += yMove
            centerX()
            if ( !invariant || (oy > 0 && toBar) ) { offset.y = 0; return@onUpdate true } // pop last
            if ( oy > H2 ) { //move to y+1 position
                block.y++
                offset.y -= TILE_HEIGHT
            }

            if ( level.isBar(x, y) && oy >= 0 ) {
                // transit to bar left, or bar right
            }

            if (level.hasGuard(x, y + 1)) { //over guard
                //don't collision
                with(game.getGuard(x, y + 1)) {
                    if(this@onUpdate.oy > this.offset.y) this@onUpdate.offset.y = this.offset.y
                }
            }


            false
        }
    }
}

// should prevent others transitions for a while dig stops
sealed class DigState(actor: Actor2, animName: ActorSequence?) : ActorState(actor, animName) {
    init {
        onEnter {
            actor.offset.x = 0
            actor.offset.y = 0
        }
    }
}

class DigLeft(actor: Actor2) : DigState(actor, ActorSequence.DigLeft) {

}

class DigRight(actor: Actor2): DigState(actor, ActorSequence.DigRight) {

}

private operator fun Vec2i.component1() = x
private operator fun Vec2i.component2() = y
