package me.az.ilode

import de.fabmax.kool.math.Vec2i
import kotlin.math.abs


const val START_HEALTH = 5
const val MAX_HEALTH = 100

const val SCORE_COUNTER = 15 // how much scores bumbs in finish anim
const val SCORE_COMPLETE = 1500
const val SCORE_COMPLETE_INC = SCORE_COMPLETE / SCORE_COUNTER
const val SCORE_GOLD     = 250
const val SCORE_FALL     = 75
const val SCORE_DIES     = 75

val Controllable.anyKeyPressed get() = digLeft || digRight || inputVec.x != 0 || inputVec.y != 0

class Runner(game: Game) : Actor(game), Controllable {
    var health = game.state.runnerLifes.value
        set(value) {
            field = value
            game.state.runnerLifes.set( value )
        }
    var score = game.state.score.value
        private set(value) {
            field = value
            game.state.score.set( value )
        }
    val success: Boolean get() = y == 0 && oy == 0 && game.level?.isDone == true

    init {
        game.onLevelStart += {
            block.x = it.runnerPos.x
            block.y = it.runnerPos.y
        }
    }

    override val fsm by lazy {
        super.fsm.apply {
            // it would need to separate Controllable for Runner's and Guard's
            this += DigRight(this@Runner)
            this += DigLeft(this@Runner)

//            debugOn()
        }
    }

    override val onFallStop = { game.playSound(Sound.DOWN, x, y) }

    fun dead() {
        if (!game.state.immortal.value) alive = false // for game fsm
    } // game will handle this
    // should prevent others transitions for a while dig stops
    sealed class DigState(actor: Runner, animName: ActorSequence) : ActorState(actor, animName, animName.id) {
        init {
            onEnter {
                with(actor) {
                    offset.x = 0
                    offset.y = 0
                    game.playSound(Sound.DIG, x, y)

                    val (digTileX, bitmap) = if (digLeft) {
                        Pair(x - 1, "digHoleLeft")
                    } else {
                        Pair(x + 1, "digHoleRight")
                    }
                    //hack

                    level.anims.add(Anim(Vec2i(digTileX, y), bitmap))
                    val holePos = Vec2i(digTileX, y + 1)
                    level.anims.add(Anim(holePos, "${bitmap}Base") {
                        level.act[digTileX][y + 1] = TileLogicType.EMPTY
//                        level.anims.add(Anim(holePos, "fillHole"))
                    })
                }
            }

            onUpdate {
                // if state expired and not loop
                if ( sequenceSize > 0 && frameIndex >= sequenceSize ) {
                    actor.action = when(this@DigState) {
                        is DigLeft -> ActorSequence.RunLeft
                        is DigRight -> ActorSequence.RunRight
                    }
                    return@onUpdate StopState.name
                }

                frameIndex ++
                null
            }

        }
    }

    class DigLeft(actor: Runner) : DigState(actor, ActorSequence.DigLeft)
    class DigRight(actor: Runner): DigState(actor, ActorSequence.DigRight)

    //Page 276 misc.c (book)
    fun ok2Dig(nx: Int): Boolean {
        return level.isBlock(nx, y + 1) && level.isEmpty(nx, y) &&
                !level.anims.any { it.pos.x == nx && it.pos.y == y + 1 }
    }

    fun addScore(points: Int) {
        score += points
    }

    override fun takeGold(): Boolean {
        game.playSound(Sound.GOLD, x, y)
        level.gold --
        addScore(SCORE_GOLD)

        if ( level.isDone ) {
//                    playSound("goldFinish${(level.levelId - 1) % 6 + 1}")
//            sounds.playSound("goldFinish")
            level.showHiddenLadders()
        }
        return true
    }

    override fun update() {
        super.update()
        if (checkCollision())
            dead()
    }

    private val Number.tabs get() = this.toString().padStart(3, ' ')

    private fun checkCollision(): Boolean {
        
        if ( level.hasGuard(x, y) ) return true

        level.getAround(block).chunked(2).filter {
            level.isValid(it.first(), it.last())
        }.firstOrNull {
            level.hasGuard(it.first(), it.last())
        }?.run {
            val (x, y) = this
            val g = game.getGuard(x, y)
            if ( !g.isReborn &&
                abs( absolutePosX - g.absolutePosX ) <= 3 * W4 &&
                abs( absolutePosY - g.absolutePosY ) <= 3 * H4
            ) {
                //dead
                return true
            }
        }

        return level.isBlock(x, y)
    }
//                                level?.run { runner.canMoveDown },
//                                runner.inputVec.x, runner.inputVec.y,
//                                runner.x, runner.ox, runner.y, runner.oy,
//                                runner.action.id,
//                                runner.frameIndex,
    override fun toString() = "pos: ${x.tabs} ${ox.tabs} x ${y.tabs} ${oy.tabs} = ${absolutePosX.tabs} x ${absolutePosY.tabs}\n" +
        "input: $inputVec\nanim: $action[ $frameIndex ]"
}
