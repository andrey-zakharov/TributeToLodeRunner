package me.az.ilode

import AnimationFrames
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.math.randomI
import kotlin.math.abs

class Guard(level: GameLevel, val anims: AnimationFrames) : Actor(level, CharType.GUARD) {
    var hasGold = 0
    var inHole = false

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

    fun startLevel(level: GameLevel, guardPos: Vec2i) {
        block.set(guardPos)
        offset.x = 0
        offset.y = 0
        action = "runLeft"
        frameIndex = 0
    }

    fun updateGuard(runner: Runner2) {
        val x = block.x
        val y = block.y
        val curTile = Vec2i(block)
        val hladr = level.gold == 0
        val upTile =    Vec2i( block.x, block.y - 1)
        val downTile =  Vec2i( block.x, block.y + 1)
        val leftTile =  Vec2i( block.x - 1, block.y)
        val rightTile = Vec2i( block.x + 1, block.y)

        // inevitable
        if (level[curTile]?.hole == true || inHole) {
            // release gold
            if ( level[curTile]?.hole == true && hasGold > 0 ) {
                level.base[upTile.x][upTile.y] = TileLogicType.GOLD
                level[upTile] = Tile.GOLD
                hasGold = -10
            }

            // process guard in hole
            if ( offset.y == 0 && !inHole ) {
                inHole = true
                action = action.replace("fall", "shake")
                runner.addScore(SCORE_FALL)
                playSound("trap")
            } else if (action.startsWith("shake")) {
                if ( frameIndex == anims.sequence[action]!!.size - 1 ) {
                    action = "runUpDown"
                }
            }

            if ( action == "runUpDown" ) {
                if ( level[curTile]?.hole == true ) {
                    offset.x = 0
                    offset.y -= MOVE_Y
                    if ( offset.y <= 0 ) {
                        block.y --
                        offset.y += TILE_HEIGHT
                    }
                } else {
                    offset.x = 0
                    offset.y -= MOVE_Y
                    if ( offset.y <= 0 ) {
                        offset.y = 0
                        if ( !level.isBarrier(leftTile) && (x > runner.block.x || level.isBarrier(rightTile)) ) {
                            action = "runLeft"
                        } else if ( !level.isBarrier(rightTile) ) {
                            action = "runRight"
                        }
                    }
                }
            }

            if ( action == "runLeft" ) {
                offset.x -= MOVE_X
                if ( offset.x < 0 ) {
                    block.x --
                    offset.x += TILE_WIDTH
                    inHole = false
                }
            } else if (action == "runRight") {
                offset.x += MOVE_X
                if ( offset.x >= TILE_WIDTH / 2 ) {
                    block.x ++
                    offset.x -= TILE_WIDTH
                    inHole = false
                }
            }
            state = State.STATE_MOVE

        } else if ( action == "reborn") {
            if ( anims.sequence[action]!!.size - 1 == frameIndex ) {
                playSound("reborn")
                action = "runLeft"
                frameIndex = 1
            }
        // AI actions
        } else {
            if ( state == State.STATE_FALL || level.status == GameLevel.Status.LEVEL_STARTUP ) {
                move(Action.ACT_NONE)
            } else {
                val nextAct = bestMove(runner)

                val neiGuard = when(nextAct) {
                    Action.ACT_UP -> upTile
                    Action.ACT_RIGHT -> rightTile
                    Action.ACT_DOWN -> downTile
                    Action.ACT_LEFT -> leftTile
                    else -> null
                }

                if ( neiGuard != null && !level.hasGuard(neiGuard) ) {
                    move(nextAct)
                } else {
                    move(Action.ACT_NONE)
                }
            }
        }

        if ( block.x != x || block.y != y ) {
            level.guard[x][y] = false // field.removeGuard
            level.guard[block.x][block.y] = true // field.addGuard
        }

        updateFrame()
    }

    fun tryDropGold() {
        if ( hasGold > 1 ) {
            hasGold --
        } else if ( hasGold == 1 ) {
            if ( level.isEmpty(block) && level.isFloor(block.x, block.y + 1, true, false)) {
                level.base[block.x][block.y] = TileLogicType.GOLD
                level[block] = Tile.GOLD
                hasGold = -1
            }
        } else if ( hasGold < 0 ) {
            hasGold ++
        }
    }

    private fun bestMove(runner: Actor2): Action {
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

    private fun scanFloor(runner: Actor2): Action {
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
        val downTileBase = level.base[x][y + 1]
        if ( y < maxTileY && downTileBase != TileLogicType.BLOCK && downTileBase != TileLogicType.SOLID ) {
            scanDown(x, Action.ACT_DOWN, guardAi, runner)
        }

        if ( level.base[x][y] == TileLogicType.LADDR ) {
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

            if ( level.base[x][y] == TileLogicType.LADDR ) {
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

    private fun scanDown(x: Int, curPath: Action, guardAi: GuardAiContext, runner: Actor2) {
        val maxTileX = level.width - 1
        val maxTileY = level.height - 1
        var y = block.y

        while ( y < maxTileY && level.base[x][y + 1] != TileLogicType.BLOCK && level.base[x][y + 1] != TileLogicType.SOLID ) {
            if ( level.base[x][y] != TileLogicType.EMPTY && level.base[x][y] != TileLogicType.HLADR ) {
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

    private fun scanUp(x: Int, curPath: Action, guardAi: GuardAiContext, runner: Actor2) {
        var y = block.y
        while ( y > 0 && level.base[x][y] == TileLogicType.LADDR ) {
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
}


class Guard2(game: Game) : Actor2(game) {
    var hasGold = 0
    override fun takeGold(): Boolean =
        if (hasGold == 0) {
            hasGold = randomI(0, 26) + 12
            true
        } else false

}