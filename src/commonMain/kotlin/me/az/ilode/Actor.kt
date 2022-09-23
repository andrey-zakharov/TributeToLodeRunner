package me.az.ilode

import SoundPlayer
import de.fabmax.kool.math.MutableVec2i
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.math.randomI

const val MOVE_X = 4
const val MOVE_Y = 4
const val TILE_WIDTH    = 20
const val TILE_HEIGHT   = 22
const val W4 = TILE_WIDTH / 4 //10, 7, 5,
const val H4 = TILE_HEIGHT / 4 //11, 8, 5,

enum class CharType {
    RUNNER, GUARD
}
enum class State {
    STATE_STOP,
    STATE_MOVE,
    STATE_FALL
}
enum class Action {
    ACT_NONE,
    ACT_UP,
    ACT_DOWN,
    ACT_LEFT,
    ACT_RIGHT,
    ACT_DIG,
}

open class Actor(val level: GameLevel,
                 val charType: CharType,
                 ) {
    var alive: Boolean = true
    var state: State = State.STATE_STOP
    var action: String = "runLeft" // sequence name
        set(v) {
            field = v
            onActionUpdate.forEach { it.invoke(this) }
            frameIndex = 0
        }

    var frameIndex: Int = 0 // position in sequence
    val onActionUpdate = mutableListOf<Actor.() -> Unit>()

    var block = MutableVec2i(Vec2i.ZERO)
    var offset = MutableVec2i(Vec2i.ZERO)

    lateinit var sounds: SoundPlayer

    fun move(act: Action) {
        val hladr = level.gold == 0
        val curTile = block
        val curBase = level.getBase(curTile)

        // collect gold
        if ( curBase == TileLogicType.GOLD && (
                (offset.x == 0 && offset.y >= 0 && offset.y < H4) ||
                (offset.y == 0 && offset.x >= 0 && offset.x < W4) ||
                (block.y < level.height && level.base[block.x][block.y+1] == TileLogicType.LADDR && offset.y < H4) // gold above laddr
            )
        ) {
            var takeGold = false
            if ( charType == CharType.RUNNER) {
                this as Runner
                playSound("getGold")
                level.gold --
                addScore(SCORE_GOLD)
                takeGold = true
                if ( level.gold == 0 ) {
//                    playSound("goldFinish${(level.levelId - 1) % 6 + 1}")
                    playSound("goldFinish")
                    level.showHiddenLadders()
                }
            } else if ((this as Guard).hasGold == 0) {
                hasGold = randomI(0, 26) + 12
                takeGold = true
            }

            if ( takeGold ) {
                level.base[block.x][block.y] = TileLogicType.EMPTY
                level[block] = Tile.EMPTY
            }
        }

        // update char position
        val upTile =    Vec2i( block.x, block.y - 1)
        val downTile =  Vec2i( block.x, block.y + 1)
        val downTile2 = Vec2i( block.x, block.y + 2)
        val leftTile =  Vec2i( block.x - 1, block.y)
        val rightTile = Vec2i( block.x + 1, block.y)

        if ( state != State.STATE_FALL || level.isFloor(downTile) ) {
            state = State.STATE_STOP
        }

        if ( state == State.STATE_FALL) {
            if ( curBase == TileLogicType.BAR && offset.y == 0) {
                state = State.STATE_MOVE
                action = action.replace("fall", "bar")
            }

        } else if ( act == Action.ACT_DIG && charType == CharType.RUNNER) {
            this as Runner
            if ( canDig ) {
                playSound("dig")
                val (digTileX, bitmap) = if ( digLeft || (action == "runLeft" && !digRight)) {
                    action = "digLeft"
                    Pair(block.x - 1, "digHoleLeft")
                } else {
                    action = "digRight"
                    Pair(block.x + 1, "digHoleRight")
                }
                level.act[digTileX][block.y + 1] = TileLogicType.EMPTY
                level.anims[Vec2i(digTileX, block.y)] = Pair(bitmap, 0)
                level.anims[Vec2i(digTileX, block.y + 1)] = Pair("${bitmap}Base", 0)
                state = State.STATE_MOVE
                offset.x = 0
            }

        } else if ( act == Action.ACT_UP && !(level.isBarrier(upTile) && offset.y == 0)) {
            if ( level.isLadder(curTile, hladr) || (level.isLadder(downTile, hladr) && offset.y > 0)) {
                if ( action != "runUpDown" ) {
                    action = "runUpDown"
                }

                state = State.STATE_MOVE
                offset.x = 0
                offset.y -= MOVE_Y
                if ( offset.y < 0) {
                    if ( level.isLadder(curTile, hladr) && !level.isBarrier(upTile) ) {
                        block.y --
                        offset.y += TILE_HEIGHT
                        if ( charType == CharType.GUARD) {
                            this as Guard
                            tryDropGold()
                        }
                    } else {
                        offset.y = 0
                    }
                }
            }

        } else if ( act == Action.ACT_DOWN && !level.isBarrier(downTile)) {
            if ( level.isLadder(curTile, hladr) || level.isLadder(downTile, hladr) ) {
                if (action != "runUpDown") {
                    action = "runUpDown"
                }

                state = State.STATE_MOVE
                offset.x = 0
                offset.y += MOVE_Y
                if ( offset.y >= TILE_HEIGHT ) {
                    block.y ++
                    offset.y -= TILE_HEIGHT
                    if ( offset.y < MOVE_Y ) {
                        offset.y = 0
                    }

                    if ( !level.isFloor(downTile) && !level.isFloor(downTile2) ) {
                        state = State.STATE_FALL
                        action = "fallLeft"
                    }

                    if ( charType == CharType.GUARD) {
                        this as Guard
                        tryDropGold()
                    }
                }
            } else if ( curBase == TileLogicType.BAR || curBase == TileLogicType.EMPTY) {
                state = State.STATE_FALL
                action = "fallLeft"
            }

        } else if ( act == Action.ACT_LEFT) {
            if ( curBase == TileLogicType.BAR && action != "barLeft" ) {
                action = "barLeft"
            } else if ( curBase != TileLogicType.BAR) {
                if (!level.isFloor(downTile) && !level.isLadder(curTile, hladr)) {
                    action = "fallLeft"
                } else if ( action != "runLeft" ) {
                    action = "runLeft"
                }
            }

            if (action == "fallLeft") {
                state = State.STATE_FALL
            } else if (!((block.x == 0 || level.isBarrier(leftTile)) && offset.x == 0)) {
                state = State.STATE_MOVE
                offset.x -= MOVE_X
                if ( offset.x < 0 ) {
                    block.x --
                    offset.x += TILE_WIDTH
                    if ( charType == CharType.GUARD) {
                        this as Guard
                        tryDropGold()
                    }
                }
                offset.y = 0
            }
        } else if ( act == Action.ACT_RIGHT) {
            if ( curBase == TileLogicType.BAR && action != "barRight" ) {
                action = "barRight"
            } else if (curBase != TileLogicType.BAR) {
                if ( !level.isFloor(downTile) && !level.isLadder(curTile, hladr) ) {
                    action = "fallRight"
                } else if (action != "runRight" ) {
                    action = "runRight"
                }
            }

            if ( action == "fallRight" ) {
                state = State.STATE_FALL
            } else if ( !((block.x == level.width-1 && offset.x == 0) || level.isBarrier(rightTile))) {
                state = State.STATE_MOVE
                offset.x += MOVE_X
                if ( offset.x >= TILE_WIDTH / 2 ) {
                    block.x ++
                    offset.x -= TILE_WIDTH
                    if ( charType == CharType.GUARD) {
                        this as Guard
                        tryDropGold()
                    }
                }
                offset.y = 0
            } else if ( offset.x < 0 ) {
                state = State.STATE_MOVE
                offset.x += MOVE_X
                offset.y = 0
            }
        } else if (! level.isFloor(downTile) && !level.isFloor(downTile2) ) {
            if ( !level.isBar(curTile) && !level.isLadder(curTile, hladr) ) {
                state = State.STATE_FALL
                action = "fallLeft"
            }
        }

        // update fall
        if ( state == State.STATE_FALL) {
            offset.x = 0
            offset.y += MOVE_Y
            if ( offset.y >= TILE_HEIGHT ) {
                block.y ++
                offset.y -= TILE_HEIGHT
                if ( offset.y < MOVE_Y ) {
                    offset.y = 0
                }

                if ( charType == CharType.GUARD) {
                    this as Guard
                    tryDropGold()
                }
            }
        }
    }

    fun updateFrame() {
        if ( state == State.STATE_STOP ) return

        frameIndex++
    }

    fun playSound(sound: String) = sounds.playSound(sound)
//    fun stopSound(sound: String) = onEvent.forEach { it.invoke(this, StopSound(sound)) }
}

