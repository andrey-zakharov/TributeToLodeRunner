package me.az.ilode

import SoundPlayer
import de.fabmax.kool.math.MutableVec2i
import de.fabmax.kool.math.Vec2i
import me.az.utils.*

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

    open val fsm by lazy {
        val stopState = ActorState.StopState(this)
        buildStateMachine<String, Actor>(stopState.name) {
            this += stopState
            this += ActorState.MoveLeft.RunLeft(this@Actor)
            this += ActorState.MoveLeft.BarLeft(this@Actor)
//            this += ActorState.MoveLeftState(this@Actor)
            this += ActorState.RunRight(this@Actor)
            this += ActorState.RunUp(this@Actor)
            this += ActorState.RunDown(this@Actor)
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

    open fun update() {
        fsm.update(this)
        //check collision
        checkGold()
    }
    open val canStay get() =
        level.isLadder(x, y) ||
                (oy == 0 && level.isBar(x, y)) ||
                (oy >= 0 && level.isFloor(x, y + 1)) ||
                (level.hasGuard(x, y + 1) && oy >= game.getGuard(x, y + 1).offset.y)
    // strict check is where needed

    open val onFallStop: (() -> Unit)? = null
    open val canMoveUp get() =
        (oy <= 0 && level.isLadder(x, y) && !level.isBarrier(x, y - 1)) || // in ladder
        (oy > 0 && level.isLadder(x, y + 1)) // at ladder

    open val canMoveDown get() =
        level.isLadder(x, y) &&
                oy < 0 || level.isPassableForDown(x, y + 1)


    open val canMoveRight get() = ox < 0 || (ox >= 0 && !level.isBarrier(x + 1, y))
    open val canMoveLeft get() = ox > 0 || (ox <= 0 && !level.isBarrier(x - 1, y))

    abstract fun takeGold(): Boolean

    fun stop() = fsm.reset()

    // collect gold
    private fun checkGold() {
        if ( level.isGold(x, y) && ox > -xMove && ox < xMove && oy > -yMove && oy < yMove ) {
            if ( takeGold() ) {
                level.takeGold(x, y)
            }
        }
    }


//    fun<E> StackedState<E>.canGoLeft =

    // states and valid transitions
    // what if states = suspend functions
    sealed class ActorState(
        val actor: Actor,
        val animName: ActorSequence?,
        name: String = animName!!.id,
    ) : StackedState<String, Actor>(name) {
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

        // controllable fsm parallel to action fsm
        sealed class ControllableState(
            actor: Actor,
            animName: ActorSequence?,
            name: String = animName!!.id,
        ) : ActorState(actor, animName, name) {
            var contMode = true
            init {

                // movedown behaviour
                edge(FallState.name) {
                    validWhen { !canStay }
                    validWhen {
//                            println("${inputVec.y} ${inputVec.y > 0} ${!level.isBarrier(x, y + 1)}")
                        inputVec.y > 0 && level.isPassableForDown(x, y + 1) &&
                                !(level.isLadder(x, y) || level.isLadder(x, y + 1))
                    }
                    action {
                        actor.action = ActorSequence.FallLeft

                        when(this@ControllableState) {
                            is MoveRight -> {
                                actor.action = ActorSequence.FallRight
                                actor.nextMove = Action.ACT_RIGHT
                            }
                            is MoveLeft -> {
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
                        inputVec.y < 0 && canMoveUp
                    }
                }

                edge(RunDown.name) {
                    validWhen {
                        inputVec.y > 0 && canMoveDown
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


                edge(StopState.name, weight = Int.MAX_VALUE) {
                    validWhen { !contMode && !anyKeyPressed && this@ControllableState !is StopState }
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
            init {
                onUpdate {
                    // special cases - cannot move - change anim
                    if ( inputVec.x < 0 ) {
                        actor.action = ActorSequence.RunLeft
                    }

                    if ( inputVec.x > 0 ) {
                        actor.action = ActorSequence.RunRight
                    }
                }
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

            init {
                onEnter { with(actor) {
                    if (this is Runner)
                        // actor - is sound source.
                        // enable (play) / disable.
                        game.playSound(Sound.FALL, x, y)
                }}
                onExit { with(actor) {
                    if (this is Runner) game.stopSound(Sound.FALL)
                }}

                // this if final stop after fall state.
                // when none of checks from `update` did not passed
                edge(StopState.name) {
                    action {
                        actor.onFallStop?.invoke()
                    }
                    validWhen { canStay }
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
                    if ( level.isFloor(x, y + 1) || isBar) {
                        offset.y = 0
                        when (actor.nextMove) {
                            Action.ACT_LEFT -> if ( isBar ) ActorSequence.BarLeft.id else ActorSequence.RunLeft.id
                            Action.ACT_RIGHT -> if ( isBar ) ActorSequence.BarRight.id else ActorSequence.RunRight.id
                            else -> {
                                if ( isBar ) action = ActorSequence.BarLeft
                                StopState.name
                            }
                        }
                    } else null
                })
            }
        }

        sealed class MovementState(actor: Actor, animName: ActorSequence?, name: String = animName!!.id) : ControllableState(actor, animName, name) {
            init {
                // pause runner when all edges fails but still some keys

                edge(StopState.name, weight = Int.MAX_VALUE) {

                    validWhen { inputVec.y > 0 }
                    validWhen { inputVec.y < 0  }
                    validWhen { digLeft }
                    validWhen { digRight }
                }

                onUpdate {
                    frameIndex ++
                    null
                }
            }
        }

        class MoveLeftState(actor: Actor) : CompoundState<String, Actor>(name) {
            companion object {
                const val name = "moveleft"
            }

            override val internalFsm by lazy {
                buildStateMachine(ActorSequence.RunLeft.id) {
                    this += MoveLeft.RunLeft(actor)
                    this += MoveLeft.BarLeft(actor)
                }
            }

            init {
                onEnter {

                    println("enter")
                }
            }


        }

        // bar and simple walk
        sealed class MoveLeft(actor: Actor, animName: ActorSequence ) : MovementState(actor, animName) {

            class BarLeft(actor: Actor) : MoveLeft(actor, ActorSequence.BarLeft) {
                init {
                    edge(ActorSequence.RunLeft.id) {
                        validWhen { !level.isBar(x, y) }
                    }

                    onEnter {
                        actor.offset.y = 0
                        Unit
                    }
                }
            }

            class RunLeft(actor: Actor) : MoveLeft(actor, ActorSequence.RunLeft) {
                init {
                    edge(ActorSequence.BarLeft.id) {
                        validWhen { level.isBar(x, y ) }
                    }
                }
            }

            init {
                // so we have entered state - set animation, but stop before update
                edge(StopState.name) {
                    validWhen { !canMoveLeft }
                }
                onUpdate {
                    offset.x -= xMove
                    centerY()
                    if ( ox > 0 ) return@onUpdate null
                    if ( !canMoveLeft ) {
                        offset.x = 0
                        return@onUpdate StopState.name
                    }
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
            init {
                onUpdate {
                    offset.x += xMove
                    centerY()
                    if ( !canMoveRight ) {
                        offset.x = 0
                        return@onUpdate StopState.name
                    }
                    if ( ox > W2 ) { //move to x+1 position
                        block.x++
                        offset.x -= TILE_WIDTH
                        if ( this is Guard && inHole ) inHole = false
                    }
                    null
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

            init {
                edge(StopState.name) {
                    validWhen { !canMoveDown }
                }
                BehaviorMoveDown {// onCenterTile
                    if ( !canMoveDown ) {
                        offset.y = 0 // prevent redraw lag
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
            !canMoveUp
        }) {
            companion object {
                const val name = "runUp"
            }

            init {

                edge(StopState.name) {
                    validWhen { this@RunUp.onCenter?.let { it(this) } == true }
                }
            }
        }

    }
}