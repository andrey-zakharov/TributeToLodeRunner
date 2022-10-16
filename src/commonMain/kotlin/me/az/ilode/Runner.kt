package me.az.ilode

import de.fabmax.kool.math.Vec2i
import kotlin.math.abs


const val START_HEALTH = 5
const val MAX_HEALTH = 100
val Controllable.anyKeyPressed get() = digLeft || digRight || inputVec.x != 0 || inputVec.y != 0

class Runner(game: Game) : Actor(game), Controllable {
    var health = game.state.runnerLifes
        set(value) {
            field = value
            game.state.runnerLifes = value
        }
    var score = game.state.curScore
        private set(value) {
            field = value
            game.state.curScore = value
        }
    val success: Boolean get() = y == 0 && oy == 0 && game.level?.isDone == true

    init {
        game.onLevelStart += {
            block.x = it.runnerPos.x
            block.y = it.runnerPos.y
        }
    }

    fun startNewGame() {
        health = START_HEALTH
        score = 0
    }

    override val fsm by lazy {
        super.fsm.apply {
            // it would need to separate Controllable for Runner's and Guard's
            this += DigRight(this@Runner)
            this += DigLeft(this@Runner)

        }
    }

    override val onFallStop = { sounds.playSound("down") }

    fun dead() {
        if (!game.state.immortal) alive = false // for game fsm
    } // game will handle this
    // should prevent others transitions for a while dig stops
    sealed class DigState(actor: Runner, animName: ActorSequence) : ActorState(actor, animName, animName.id) {
        companion object {
            const val digRunnerAnimLength = 1
        }
        init {
            onEnter {
                with(actor) {
                    offset.x = 0
                    offset.y = 0
                    sounds.playSound("dig")

                    val (digTileX, bitmap) = if (digLeft) {
                        Pair(x - 1, "digHoleLeft")
                    } else {
                        Pair(x + 1, "digHoleRight")
                    }

                    level.act[digTileX][y + 1] = TileLogicType.EMPTY
                    level.anims.add(Anim(Vec2i(digTileX, y), bitmap))
                    level.anims.add(Anim(Vec2i(digTileX, y + 1), "${bitmap}Base"))
                }
            }

            onUpdate {
                // if state expired and not loop
                if ( frameIndex >= digRunnerAnimLength ) {
                    //hack
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
        return level.isBlock(nx, y + 1) && level.isEmpty(nx, y)
    }

    fun addScore(points: Int) {
        score += points
    }

    override fun takeGold(): Boolean {
        sounds.playSound("getGold")
        level.gold --
        addScore(SCORE_GOLD)

        if ( level.isDone ) {
//                    playSound("goldFinish${(level.levelId - 1) % 6 + 1}")
            sounds.playSound("goldFinish")
            level.showHiddenLadders()
        }
        return true
    }

    override fun update() {
        super.update()
        if (checkCollision())
            dead()
    }

    private fun checkCollision(): Boolean {
        level.getAround(block).chunked(2).filter {
            level.isValid(it.first(), it.last())
        }.firstOrNull {
            level.hasGuard(it.first(), it.last())
        }?.run {
            val (x, y) = this
            val g = game.getGuard(x, y)
            if ( !g.isReborn() &&
                abs( absolutePosX - g.absolutePosX ) <= 3 * W4 &&
                abs( absolutePosY - g.absolutePosY ) <= 3 * H4
            ) {
                //dead
                return true
            }
        }

        return level.isBarrier(x, y)
    }
}
