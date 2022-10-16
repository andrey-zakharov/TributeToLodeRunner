package me.az.ilode

import SoundPlayer
import de.fabmax.kool.math.MutableVec2i
import de.fabmax.kool.math.Vec2i
import me.az.utils.StackedState
import me.az.utils.StackedStateMachine
import me.az.utils.buildStateMachine


//stack
fun<E> MutableList<E>.pop() = removeLastOrNull()
fun<E> MutableList<E>.push(e: E) = add(e)
fun<E> MutableList<E>.current() = lastOrNull()

const val TILE_WIDTH    = 20
const val TILE_HEIGHT   = 22
const val W4 = TILE_WIDTH / 4 //10, 7, 5,
const val H4 = TILE_HEIGHT / 4 //11, 8, 5,
const val W2 = TILE_WIDTH / 2 //20, 15, 10,
const val H2 = TILE_HEIGHT / 2 //20, 15, 10,

// remnants
enum class Action {
    ACT_NONE,
    ACT_UP,
    ACT_DOWN,
    ACT_LEFT,
    ACT_RIGHT,
    ACT_DIG,
}

// all repos
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
    // guard
    Reborn("reborn"),
    ShakeRight("shakeRight"),
    ShakeLeft("shakeLeft"),
}

// controller = Input, AI
interface Controllable {
    val inputVec: MutableVec2i // (-1, 0) etc
    var digLeft: Boolean
    var digRight: Boolean
}

// diverge by bool features
sealed class Actor(val game: Game) : Controllable {

    open val fsm: StackedStateMachine<Actor> by lazy {
        val stopState = ActorState.StopState(this)
        buildStateMachine(stopState.name) {
            this += stopState
            this += ActorState.RunLeft(this@Actor)
            this += ActorState.RunRight(this@Actor)
            this += ActorState.RunUp(this@Actor)
            this += ActorState.RunDown(this@Actor)
            this += ActorState.BarLeft(this@Actor)
            this += ActorState.BarRight(this@Actor)
            // need something better
            this += ActorState.FallState(this@Actor)
        }
    }

    // conf
    var xMove = 4
    var yMove = 4

    var alive: Boolean = true
    var block = MutableVec2i(Vec2i.ZERO)
    var offset = MutableVec2i(Vec2i.ZERO)
    val level get() = game.level!!

    val absolutePosX get() = block.x * TILE_WIDTH + offset.x
    val absolutePosY get() = block.y * TILE_HEIGHT + offset.y
    val x get() = block.x
    val y get() = block.y
    val ox get() = offset.x
    val oy get() = offset.y
    val isLadder get() = level.isLadder(x, y)
    val isBar get() = level.isBar(x, y)
    var action: ActorSequence = ActorSequence.RunRight // sequence name
        set(v) {
            if ( field != v) {
                field = v
                frameIndex = 0
                sequenceSize = 0
            }
        }
    var frameIndex: Int = 0 // position in sequence
    var sequenceSize = 0 // filled in view

    init {
        game.onLevelStart += {
            alive = true
            stop()
            frameIndex = 0
            action = ActorSequence.RunRight
            offset.x = 0
            offset.y = 0
        }
    }

    // controls
    override val inputVec = MutableVec2i()

    var nextMove = Action.ACT_NONE //left or right
    // BAD BAD BAD but this is plan C, A & B failed

    // runner
    override var digLeft: Boolean = false
    override var digRight: Boolean = false

    lateinit var sounds: SoundPlayer

    open fun update() {
       fsm.update(this)
        //check collision
        checkGold()
    }
    open val shouldNotFall: Boolean get() =
        level.isLadder(x, y) || (oy >= 0 && level.isLadder(x, y + 1)) ||
                (oy == 0 && (level.isBar(x, y) || level.isFloor(x, y + 1, useGuard = true))) ||
                (oy < 0 && level.hasGuard(x, y + 1) && oy < game.getGuard(x, y+1).offset.y)

    open val onFallStop: (() -> Unit)? = null
    abstract fun takeGold(): Boolean

    fun stop() = fsm.reset()

    fun playSound(sound: String, force: Boolean = false) =
        if ( force )
            sounds.playSound(sound, false, false)
        else
            sounds.playSound(sound, false, true)
    // collect gold
    private fun checkGold() {
        if ( level.isGold(x, y) && ox > -W4 && ox < W4 && oy > -H4 && oy < H4 ) {
            if ( takeGold() ) {
                level.takeGold(x, y)
            }
        }
    }


    // states and valid transitions
    // what if states = suspend functions
    sealed class ActorState(
        val actor: Actor,
        val animName: ActorSequence?,
        name: String = animName!!.id,
    ) : StackedState<Actor>(name) {
        init {
            onEnter { animName?.run { actor.action = this } }
        }

        fun Actor.centerX() {
            if ( ox > 0 ) {
                offset.x -= xMove
                if ( offset.x < 0) offset.x = 0 //move to center X
            } else if ( ox < 0 ) {
                offset.x += xMove
                if ( offset.x > 0) offset.x = 0 //move to center X
            }
        }

        fun Actor.centerY() {
            if ( offset.y > 0 ) {
                offset.y -= yMove
                if ( offset.y < 0)  offset.y = 0 //move to center Y
            } else if (offset.y < 0) {
                offset.y += yMove
                if ( offset.y > 0 ) offset.y = 0 //move to center Y
            }
        }

        // there should be way to avoid inheritance of ControllableState and MoveDown state
        fun ActorState.BehaviorMoveDown(onCenter: (Actor.() -> String?)? = null) = apply {
            onUpdate {

                offset.y += yMove
                centerX()
                if (oy in 0 until yMove) { // bypass center on this tick
                    onCenter?.invoke(this)?.run {
                        // return new state
                        return@onUpdate this
                    }
                }
                // move to common checks? and callback tileChanged?
                if ( oy > H2 ) { //move to y+1 position
                    block.y++
                    offset.y -= TILE_HEIGHT
                }

                null
            }
        }

        sealed class ControllableState(
            actor: Actor,
            animName: ActorSequence?,
            name: String = animName!!.id,
        ) : ActorState(actor, animName, name) {
            var contMode = true
            init {

                edge(FallState.name) {
                    validWhen {!shouldNotFall}

                    action {
                        actor.action = ActorSequence.FallLeft

                        when(this@ControllableState) {
                            is RunRight, is BarRight -> {
                                actor.action = ActorSequence.FallRight
                                actor.nextMove = Action.ACT_RIGHT
                            }
                            is RunLeft, is BarLeft -> {
                                actor.nextMove = Action.ACT_LEFT
                            }
                            else -> {

                            }
                        }
                    }
                }

                edge(RunUp.name) {
                    validWhen {
                        // input in other coords then level ones
                        inputVec.y < 0 && (
                                (oy <= 0 && level.isLadder(x, y)) || // in ladder
                                        (oy > 0 &&  level.isLadder(x, y + 1)) // at ladder
//                    (oy >= 0 && level.isLadder(x, y)) ||
//                    (oy < 0 && )
                                )
                    }
                }

                edge(RunDown.name) {
                    validWhen {
                        inputVec.y > 0 && ((oy >= 0 && level.isPassableForDown(x, y + 1)) || oy < 0)
                    }
                }

                edge(ActorSequence.BarLeft.id) {
                    validWhen {
                        inputVec.x < 0 && ((ox > 0 && level.isBar(x, y)) || (ox <= 0 && level.isBar(x - 1, y)))
                    }
                }

                edge(ActorSequence.BarRight.id) {
                    validWhen {
                        inputVec.x > 0 && ((ox < 0 && level.isBar(x, y)) || (ox >= 0 && level.isBar(x + 1, y)))
                    }
                }

                edge(ActorSequence.RunLeft.id) {
                    validWhen {
                        inputVec.x < 0 && // got input
                                ( ox > 0 || ( ox <= 0 && !level.isBarrier(x - 1, y) ) ) && // no obstacle ahead left
//                    (level.isFloor(x, y + 1) || level.isLadder(x, y)) )
                                !level.isBar(if ( ox > 0 ) x else x - 1, y)
//                )
                    }
                }

                edge(ActorSequence.RunRight.id) {
                    validWhen {
                        inputVec.x > 0 && (
                                (ox < 0) || (ox >= 0 && !(level.isBarrier(x + 1, y)))
                                ) && !level.isBar(if ( ox >= 0 ) x + 1 else x, y)
                    }
                }

                edge(ActorSequence.DigRight.id) {
                    validWhen {
                        this is Runner && digRight && ok2Dig(x + 1)
                    }
                }

                edge(ActorSequence.DigLeft.id) {
                    validWhen {
                        this is Runner && digLeft && ok2Dig(x - 1)
                    }
                }


                edge(StopState.name) {
                    validWhen { !contMode && !anyKeyPressed }
                }

                onEnter {
                    actor.nextMove = Action.ACT_NONE
                }
            }
        }

        class StopState(actor: Actor) : ControllableState(actor, null, name) {
            companion object {
                const val name = "stop"
            }
        }

        // dynamic animation name
        // level.base used for guards
        // level.act used for runner
        // stacked
        class FallState(
            actor: Actor
        ) : ActorState(actor, null, name) {
            companion object {
                const val name = "fall"
            }
            private val Actor.invariant get() = oy < 0 || (oy >= 0 && !level.isFloor(x, y + 1))

            init {
                onEnter { with(actor) {
                    if (this is Runner)
                        playSound("fall")
                }}
                onExit { with(actor) {
                    sounds.stopSound("fall")
                }}

                edge(StopState.name) {
                    action {
                        actor.onFallStop?.invoke()
                    }
                    validWhen { shouldNotFall }
                }

                onUpdate {
                    //collect input while falling
                    if ( (inputVec.x < 0 || digLeft) ) {
                        if (action != ActorSequence.FallLeft) action = ActorSequence.FallLeft
                        actor.nextMove = if ( digLeft ) Action.ACT_DIG else Action.ACT_LEFT
                    } else if ( (inputVec.x > 0 || digRight) ) {
                        if ( action != ActorSequence.FallRight ) action = ActorSequence.FallRight
                        actor.nextMove = if ( digRight ) Action.ACT_DIG else Action.ACT_RIGHT
                    } else if ( inputVec.y > 0 ) {
                        actor.nextMove = Action.ACT_DOWN
                    } else if ( anyKeyPressed ) { // all other
                        actor.nextMove = Action.ACT_NONE // stop
                    }
                    null
                }

                // onTileChange
                BehaviorMoveDown(onCenter = {
                    val isBar = level.isBar(x, y)
                    if ( !invariant || isBar) {
                        offset.y = 0
                        when (actor.nextMove) {
                            Action.ACT_LEFT -> if ( isBar ) ActorSequence.BarLeft.id else ActorSequence.RunLeft.id
                            Action.ACT_RIGHT -> if ( isBar ) ActorSequence.BarRight.id else ActorSequence.RunRight.id
                            else -> StopState.name
                        }
                    } else null
                })
            }
        }

        sealed class MovementState(actor: Actor, animName: ActorSequence?, name: String = animName!!.id) : ControllableState(actor, animName, name) {

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
                    null
                }
            }
        }
        // bar and simple walk
        sealed class MoveLeft(actor: Actor, animName: ActorSequence ) : MovementState(actor, animName) {
            private val Actor.invariant get() = ox > 0 || (ox <= 0 && !level.isBarrier(x - 1, y))
            init {
                edge(StopState.name) {
                    action { actor.offset.x = 0 }
                    validWhen { !invariant }
                }

                onUpdate {
                    offset.x -= xMove
                    centerY()
                    if ( !invariant ) offset.x = 0 // stop until next tick
                    if (ox < -W2) { //move to x-1 position
                        block.x--
                        offset.x += TILE_WIDTH
                        if ( this is Guard && inHole ) inHole = false
                    }


                    null // ignored
                }
            }
        }

        // bar and walk
        sealed class MoveRight(actor: Actor, animName: ActorSequence) : MovementState(actor, animName) {
            val Actor.invariant get() = ox < 0 || (ox >= 0 && !level.isBarrier(x + 1, y))
            init {
                edge(StopState.name) {
                    action { actor.offset.x = 0 }
                    validWhen { !invariant }
                }

                onUpdate {
                    offset.x += xMove
                    centerY()
                    if ( !invariant ) offset.x = 0
                    if ( ox > W2 ) { //move to x+1 position
                        block.x++
                        offset.x -= TILE_WIDTH
                        if ( this is Guard && inHole ) inHole = false
                    }


                    null
                }
            }
        }

        class RunLeft(actor: Actor) : MoveLeft(actor, ActorSequence.RunLeft) {
            init {
                edge(ActorSequence.BarLeft.id) {
                    validWhen { ox <= 0 && level.isBar(x - 1, y ) }
                }
            }
        }

        class RunRight(actor: Actor) : MoveRight(actor, ActorSequence.RunRight) {
            init {
                edge(ActorSequence.BarRight.id) {
                    validWhen { ox >= 0 && level.isBar(x + 1, y) }
                }
            }
        }

        class BarLeft(actor: Actor) : MoveLeft(actor, ActorSequence.BarLeft) {
            init {
                edge(ActorSequence.RunLeft.id) {
                    validWhen { ox <= 0 && !level.isBarrier(x - 1, y) && !level.isBar(x - 1, y) }
                }
                onEnter {
                    actor.offset.y = 0
                    Unit
                }
            }

        }

        class BarRight(actor: Actor) : MoveRight(actor, ActorSequence.BarRight) {
            init {
                edge(ActorSequence.RunRight.id) {
                    validWhen { ox >= 0 && !level.isBarrier(x + 1, y) && !level.isBar(x + 1, y) }
                }
                onEnter {
                    actor.offset.y = 0
                    Unit
                }
            }
        }

        class RunDown(actor: Actor): MovementState(actor, ActorSequence.RunUpDown, name) {
            companion object {
                const val name = "runDown"
            }

            private val Actor.stopWhen get() = oy >= 0 && !level.isPassableForDown(x, y + 1)

            init {
                edge(StopState.name) {
                    action { actor.offset.y = 0 }
                    validWhen { stopWhen }
                }
                BehaviorMoveDown {
                    if ( stopWhen ) {
                        offset.y = 0
                    }
                    null
                }
            }
        }

        // oncenter return to break or not - true- break, false - not
        sealed class MoveUp(actor: Actor, name: String,
                            protected val onCenter: (Actor.() -> Boolean)? = null)
            : MovementState(actor, ActorSequence.RunUpDown, name) {
            init {
                onUpdate {
                    val pos = oy > 0
                    offset.y -= yMove
                    centerX()
                    if ( pos && oy <= 0 && onCenter?.invoke(this) == true) {
                        offset.y = 0
                        null // next tick
                    }
                    if ( oy < -H2 ) { //move to y-1 position
                        block.y--
                        offset.y += TILE_HEIGHT
                    }

                    null
                }
            }
        }
        class RunUp(actor: Actor): MoveUp(actor, name, onCenter = {
            level.isBarrier(x, y - 1) || (
                !level.isLadder(x, y) && !(this is Guard && this.inHole && level.isHole(x, y))
            )
        }) {
            companion object {
                const val name = "runUp"
            }

            init {

                edge(StopState.name) {
                    action { actor.offset.y = 0 }
                    validWhen { this@RunUp.onCenter?.let { it(this) } == true }
                }
            }
        }

    }
}