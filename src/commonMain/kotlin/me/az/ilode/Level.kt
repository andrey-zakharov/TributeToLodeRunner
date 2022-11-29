package me.az.ilode

import AnimationFrames
import de.fabmax.kool.math.MutableVec2i
import de.fabmax.kool.math.Vec2i
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.az.utils.component1
import me.az.utils.component2
import me.az.utils.logd
import org.mifek.wfc.core.Cartesian2DWfcAlgorithm
import org.mifek.wfc.datastructures.IntArray2D
import org.mifek.wfc.models.OverlappingCartesian2DModel
import org.mifek.wfc.models.options.Cartesian2DModelOptions
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

// game rules: On square-tiled field (usually 26x20) placed several types of tiles, acts differently.
// allowing entrance for each base block
// enter from up, down, ...
const val ENTER_ALL = 0
const val ENTER_UP = 1
const val ENTER_RIGHT = 2
const val ENTER_DOWN = 4
const val ENTER_LEFT = 8
const val ENTER_NONE = ENTER_DOWN + ENTER_UP + ENTER_LEFT + ENTER_RIGHT
// bitset of states... accept state.
enum class TileLogicType(
    private val collision: Int = ENTER_ALL //full = ENTER_DOWN + ENTER_UP + ENTER_LEFT + ENTER_RIGHT
) {
    EMPTY, // any state, except running above
    HOLE, // special state
    BLOCK(ENTER_NONE),
    SOLID(ENTER_NONE),
    LADDER, // anims
    BAR, // special case about falling
    TRAP(ENTER_LEFT + ENTER_RIGHT + ENTER_DOWN),
    HLADDER,
    GOLD,
    GROUND;

    // is pass blocked
    // 0 and any > 0 - block?
    //
    fun block(dir: Int) = (collision or dir) xor collision == 0
    fun isBarrier() = this == BLOCK || this == SOLID || this == TRAP || this == GROUND
}

/// named by frame names
// prob states for cells,
// hladdr -> laddr on level complete
// digHole -> fillHole -> block
// is it just stack of states?
enum class Tile(val char: Char, val base: TileLogicType, val act: TileLogicType, val frameName: String? = null) {
    EMPTY(' ', TileLogicType.EMPTY, TileLogicType.EMPTY ),
    BRICK('#', TileLogicType.BLOCK, TileLogicType.BLOCK ), // Normal Brick
    SOLID('@', TileLogicType.SOLID, TileLogicType.SOLID ), // Solid Brick
    LADDER('H', TileLogicType.LADDER, TileLogicType.LADDER ), // Ladder
    ROPE('-', TileLogicType.BAR, TileLogicType.BAR ), // Line of rope
    TRAP('X', TileLogicType.TRAP, TileLogicType.TRAP, BRICK.name.lowercase() ), // False brick
    HLADDER('S', TileLogicType.HLADDER, TileLogicType.EMPTY, LADDER.name.lowercase() ), //Ladder appears at end of level
    GOLD('$', TileLogicType.GOLD, TileLogicType.EMPTY ),
    GUARD('0', TileLogicType.EMPTY, TileLogicType.EMPTY, "" ),
    PLAYER('&', TileLogicType.EMPTY, TileLogicType.EMPTY, "" );
    val frame: String get() = frameName ?: name.lowercase()
    companion object {
//        fun byChar(c: Char) = Tile.values().first { it.char == c }
        val byChar = Tile.values().associateBy { it.char }
    }
    fun exportForGenerator() = when(this) {
        PLAYER,
        GUARD,
        GOLD -> EMPTY
        else -> this
    }.ordinal
}

const val primaryTileSet = 0

data class LevelCellUpdate (
    val x: Int, val y: Int, val tile: TileLogicType
) {
    //constructor(atlasId: Byte, frameNum: Int) : this(atlasId, frameNum.toByte())
    //constructor(t: Tile) : this(primaryTileSet, t.frame )
//    val pack: Byte get() {
//        var b: UInt = 0u
//        //b = b.packHole(hole)
//        b = b.packFrameNum(frameNum)
//        return b.toByte()
//    }
//
//    companion object {
//        fun unpack(b: Byte) = with(b.toUInt()) {
//            ViewCell(
//                false,
//                unpackFrameNum()
//            )
//        }
//
//        private fun UInt.getBit(position: Int): Boolean = (this shr position) and 1u > 0u
//        private fun UInt.withBit(position: Int, bit: Boolean): UInt {
//            return if ( bit ) {
//                this or (1u shl position)
//            } else {
//                this and (1u shl position).inv()
//            }
//        }
//        private fun UInt.packHole(v: Boolean) = withBit(7, v)
//        private fun UInt.unpackHole() = getBit(7)
//        private fun UInt.packFrameNum(f: Int) = (this and 0xff80u) or ( f.toUInt() and 0x007fu )
//        private fun UInt.unpackFrameNum() = (this and 0xff7fu).toInt()
//    }
}

fun formatPatterns(patterns: Array<IntArray>, patternSize: Int): String {
    return patterns.mapIndexed { index, it ->
        "$index:\nintArrayOf(\n" +
                "\t${it.asIterable().chunked(patternSize).joinToString(",\n\t") { it.joinToString(", ") } }\n)"
    }.joinToString("\n\n")
}

fun OverlappingCartesian2DModel.dis(algo: Cartesian2DWfcAlgorithm) {
    val map = constructNullableOutput(algo).joinToString("\n") { row->
        row.map {
            when (it) {
                null -> "." // not collapsed
                Int.MIN_VALUE -> "!" // conflict
                else -> Tile.values()[it].char.toString()
            }
        }.joinToString("")
    }
    logd { map }
}
expect fun debugAlgoStart(levelId: Int, model: OverlappingCartesian2DModel, algo: Cartesian2DWfcAlgorithm)

@OptIn(ExperimentalTime::class)
fun generateGameLevel(
    levelId: Int,
    // generator topology setup
    exampleMap: List<String>,
    exampleWidth: Int =  exampleMap.first().length,
    exampleHeight: Int = exampleMap.size,
    mapWidth: Int = exampleWidth,
    mapHeight: Int = exampleHeight, // default turn off
    exampleOriginX: Int = (mapWidth - exampleWidth) / 2,
    exampleOriginY: Int = mapHeight - exampleHeight,
    genOptions: Cartesian2DModelOptions = Cartesian2DModelOptions(
        allowRotations = false,
        allowHorizontalFlips = true,
        allowVerticalFlips = false,
        grounded = true,
        roofed = false,
        leftSided = false,
        rightSided = false,
        periodicInput = true,
        periodicOutput = false,
    ),

    // view stuff
    tilesAtlasIndex: Map<String, Int>,
    scope: CoroutineScope,
): GameLevel {
    val patternSize = 3 // n , m

    logd { "input = $exampleWidth x $exampleHeight, output = $mapWidth x $mapHeight example origin= $exampleOriginX x $exampleOriginY" }
    logd { exampleMap.joinToString("\n") }
    logd { "for generator" }
    logd { exampleMap.joinToString("\n") { it.map { ch -> Tile.values()[Tile.byChar[ch]!!.exportForGenerator()].char }.joinToString("")} }
    logd { "raw patterns count: ${(exampleWidth - patternSize + 1) * (exampleHeight - patternSize + 1)}" }

    val initials = IntArray2D(exampleWidth, exampleHeight) { idx ->
        val x = idx % exampleWidth
        val y = idx / exampleWidth
        Tile.byChar[exampleMap[y][x]]!!.exportForGenerator()
    }

    return loadGameLevel(levelId, Array(mapHeight) { y ->
        (0 until mapWidth).joinToString("") { x ->
            val exampleX = x - exampleOriginX
            val exampleY = y - exampleOriginY
            val tileIndex =
                if (exampleX in 0 until exampleWidth &&
                    exampleY in 0 until exampleHeight) exampleMap[exampleY][exampleX]
                else Tile.values()[0].char
            tileIndex.toString()
        }
    }.toList(), tilesAtlasIndex).apply {

        scope.launch {

            val wcf = OverlappingCartesian2DModel(initials, overlap = patternSize - 1,
                outputWidth = mapWidth, outputHeight = mapHeight,
                options = genOptions
            )

            // set in map
            exampleMap.forEachIndexed { y, row ->
                row.forEachIndexed { x, c ->
//            println("$x $y -> ${(mapWidth - exampleWidth) / 2 + x}, ${y + mapHeight - exampleHeight}")
                    wcf.setPixel(
                        exampleOriginX + x, exampleOriginY + y,
                        Tile.byChar[exampleMap[y][x]]!!.exportForGenerator()
                    )
                    //(wcf.me.az.view.getWidth - exampleWidth) / 2 +
                    //(wcf.me.az.view.getHeight - exampleHeight) / 2 +
                }
            }
//            println(formatPatterns(wcf.patterns.toList().toTypedArray(), patternSize))

            val (algo, buildTime) = measureTimedValue { wcf.build() }
            logd { "algorithm build in $buildTime" }

            algo.afterFail += {
                //log error
                println("failed")
            }

            val (res, dur) = measureTimedValue {
                algo.run(seed = 2)
            }
            logd { "wcf run in $dur with result = $res" }
            wcf.dis(algo)

            val out = wcf.constructNullableOutput(algo)

            // metric
            println((0 until mapWidth).map { i -> if ( i % 10 == 0 ) i / 10 else " "}.joinToString(""))
            println((0 until mapWidth).map { i -> (i % 10) }.joinToString(""))

            logd { out.joinToString("\n") { row ->
                row.joinToString("") { when(it) {
                    null -> "."
                    Int.MIN_VALUE -> "!"
                    else -> "$it"
                } }
            } }
            // print original (example) level to map without filtering

            exampleMap.forEachIndexed { y, row ->
                row.forEachIndexed { x, c ->
                    out[y + exampleOriginY][exampleOriginX + x] =
                        Tile.byChar[exampleMap[y][x]]!!.ordinal
                }
            }

            out.forEachIndexed { y, row ->
                row.forEachIndexed { x, cellIdx ->
                    if ( cellIdx == null || cellIdx == Int.MIN_VALUE ) return@forEachIndexed
                    /// skip initialized
                    if (x - exampleOriginX in 0 until exampleWidth &&
                        y - exampleOriginY in 0 until exampleHeight
                    ) return@forEachIndexed
                    val tile = Tile.values()[cellIdx]

                    act[x][y] = tile.act
                    base[x][y] = tile.base
                    redrawCell(x, y)
                    // view mode
//                    this@apply[x, y] = LevelCellUpdate(0, tilesAtlasIndex[
//                            if ( tile.base == TileLogicType.HLADR || tile.frame.isEmpty() )
//                                Tile.EMPTY.frame
//                            else tile.frame
//                    ]!!)
                }
            }
        }
    }


}

fun loadGameLevel(
    levelId: Int,
    map: List<String>,
    tilesAtlasIndex: Map<String, Int>,
) = GameLevel(levelId, map, tilesAtlasIndex)

data class Anim( val pos: Vec2i, val name: String, var currentFrame: Int = 0, val onFinish: () -> Unit = {})

class GameLevel(
    val levelId: Int,
    private val map: List<String>,
    private val primaryTileSet: Map<String, Int>, // index
    val maxGuards: Int = 5,
) {
    private val cbs = mutableListOf<(LevelCellUpdate) -> Unit>()
    fun onTileUpdate(cb: (update: LevelCellUpdate) -> Unit) {
        cbs += cb
    }
    // for load
    val width = map.first().length
    val height = map.size + 1 // ground

    enum class Status {
        LEVEL_STARTUP,
        LEVEL_PAUSED,
        LEVEL_PLAYING,
        LEVEL_DONE
    }
    // store view info
    // private val buf = createUint8Buffer(textureWidth * height)
    var runnerPos = MutableVec2i()
    val guardsPos = mutableListOf<Vec2i>()
    var gold = 0
    val isDone get() = gold == 0
    var status = Status.LEVEL_STARTUP

    // store logic info
    // prob overridable cell, so special getter -
    // get cell = if ( !act ) base
    val act = Array(width) { Array(height) { TileLogicType.EMPTY } }
    val base = Array(width) { Array(height) { TileLogicType.EMPTY } }
    val guard = Array(width) { Array(height) { false } }

    val anims = mutableListOf<Anim>() // pos -> anim name, current frame index in anim
    var dirty = false

    var holesAnims: AnimationFrames? = null

    init {
        logd {"creating level $levelId $width x $height from map:" }
        logd {map.joinToString("\n")}
        reset()
    }

    fun update(runner: Runner) {
//        anims.takeIf { it.isNotEmpty() }?.run { println(this) }
        val iter = anims.iterator()
        val toAdd = mutableListOf<Anim>()

        while( iter.hasNext() ) {
            val entry = iter.next()
            val (pos, animName, frame) = entry
            entry.currentFrame++
            val animArray = holesAnims?.sequence?.get(animName) ?: continue
            val (x, y) = pos

            if ( frame < animArray.size ) {
                val tileIndex = animArray[frame]
                if ( (animName == "digHoleLeftBase" || animName == "digHoleRightBase") && guard[x][y - 1] ) {
                    // break dig
                    act[x][y] = TileLogicType.BLOCK
                    redrawCell(x, y)
                    guard[x][y] = false //?
                    runner.stopSound(Sound.DIG)
//                    runner.stop() //?
                    iter.remove()
                    continue
                } else {
                    redrawCell(x, y)
                }

            } else {
                entry.onFinish()
                // on exit
                if ( animName == "fillHole" ) {
                    act[x][y] = TileLogicType.BLOCK
                }

                if ( animName == "digHoleLeftBase" || animName == "digHoleRightBase" ) {
                    toAdd.add(Anim(pos, "fillHole"))
                }

                // purge old anims
                if ( animName == "digHoleLeft" || animName == "digHoleRight" ) {
                    act[pos.x][pos.y] = TileLogicType.EMPTY
                }
                redrawCell(x, y)
                iter.remove()
            }
        }

        anims.addAll(toAdd)
    }

    // 0 - 6 bits
    // 7 bit - for hole as frame tile set


    private fun exportCellData(x: Int, y: Int, mode: ViewMode = ViewMode.PLAY) =
        LevelCellUpdate(x, y, if ( y == height - 1 ) {
            TileLogicType.GROUND
        } else when(mode) {
            // why gold is special? IDK, simplify
            ViewMode.PLAY -> if ( base[x][y] == TileLogicType.GOLD ) base[x][y] else act[x][y]
            ViewMode.EDIT -> base[x][y]
    })
//    operator fun get(x: Int, y: Int): ViewCell? = if ( isValid(x, y) ) ViewCell.unpack(buf[y * textureWidth + x]) else null
    fun redrawCell(x: Int, y: Int, mode: ViewMode = ViewMode.PLAY)  {
        if ( isValid(x, y) ) {
            val ev = exportCellData(x, y, mode)
            cbs.forEach { it(ev) }
        }
        //dirty = true
    }

    enum class ViewMode {
        PLAY, EDIT
    }

    fun all(mode: ViewMode = ViewMode.PLAY, each: (LevelCellUpdate) -> Unit) {
        for( x in 0 until width ) {
            for ( y in 0 until height ) {
                each(exportCellData(x, y, mode))
            }
        }
    }

//    operator fun get(at: Vec2i) = get(at.x, at.y)
    fun redrawCell(at: Vec2i) = redrawCell(at.x, at.y)

    fun getAround(at: Vec2i) = getAround(at.x, at.y)
    fun getAround(x: Int, y: Int) = NeighbourhoodTopology.NeighborhoodNeumann2d.neighbours(x, y)

    fun getAct(at: Vec2i) = act[at.x][at.y]
    fun getBase(at: Vec2i) = base[at.x][at.y]

    fun isValid(x: Int, y: Int) = x >= 0 && x < width && y >= 0 && y < height
    private fun isValid(at: Vec2i) = isValid(at.x, at.y)

    fun reset() {
        stopAllAnims()
        gold = 0
        guardsPos.clear()

        map.forEachIndexed { y, row ->
            row.forEachIndexed { x, cell ->
                var guard = false

                val tile = Tile.byChar[cell]!!

                when(tile) {
                    Tile.PLAYER -> {
                        runnerPos.x = x
                        runnerPos.y = y
                    }
                    Tile.GUARD -> {
                        if ( guardsPos.size < maxGuards ) {
                            guard = true
                            guardsPos.add(Vec2i(x, y))
                        }
                    }
                    Tile.GOLD -> {
                        gold ++
                    }
                    else -> Unit
                }

                this.act[x][y] = tile.act
                this.base[x][y] = tile.base
                this.guard[x][y] = guard

            }
        }

        fillGround(primaryTileSet)

        guard.forEach { row-> row.forEachIndexed { index, _ -> row[index] = false } }
        guardsPos.forEach {
            guard[it.x][it.y] = true
        }
        dirty = true
    }

    fun fillGround(tileSet: Map<String, Int>) {

        for (x in 0 until this.width ) {
            this.act[x][this.height-1] = TileLogicType.BLOCK
            this.base[x][this.height-1] = TileLogicType.BLOCK
            tileSet["ground"]?.run {
                this@GameLevel.redrawCell(x, this@GameLevel.height-1)//, TileLogicType.GROUND)
            }
        }
    }

    private fun stopAllAnims() {
        val i = anims.iterator()
        while( i.hasNext() ) {
            val a = i.next()
            // tbd make FSM
            if ( a.name == "fillHole" || a.name == "digHoleLeftBase" || a.name == "digHoleRightBase") {
                act[a.pos.x][a.pos.y] = TileLogicType.BLOCK
                this.redrawCell(a.pos)//, LevelCellUpdate(false, primaryTileSet[Tile.BRICK.frame]!!))
            }
            i.remove()
        }
    }

    fun showHiddenLadders() {
        for ( y in 0 until height ) {
            for ( x in 0 until width) {
                if ( base[x][y] == TileLogicType.HLADDER ) {
                    act[x][y] = TileLogicType.LADDER
                    redrawCell(x, y)
                }
            }
        }
        status = Status.LEVEL_DONE
    }

    // not safe
    fun dropGold(x: Int, y: Int) {
        base[x][y] = TileLogicType.GOLD
        redrawCell(x, y)
    }
    fun takeGold(x: Int, y: Int) {
        base[x][y] = TileLogicType.EMPTY
        redrawCell(x, y)
    }

    // for dig
    fun isHole(x: Int, y: Int) = isValid(x, y) && base[x][y] == TileLogicType.BLOCK && act[x][y] == TileLogicType.EMPTY
    fun isBlock(x: Int, y: Int) = isValid(x, y) && act[x][y] == TileLogicType.BLOCK

    fun isBarrier(x: Int, y: Int) = !isValid(x, y) || act[x][y].isBarrier()
    fun isBarrier(at: Vec2i) = isBarrier(at.x, at.y)

    //tbd to tiles itself?
    fun isPassableForUp(x: Int, y: Int,) = isValid(x, y) &&
            !act[x][y].block(ENTER_UP)
    fun isPassableForDown(x: Int, y: Int) = isValid(x, y) &&
            act[x][y] != TileLogicType.BLOCK &&
            act[x][y] != TileLogicType.SOLID

    fun isLadder(x: Int, y: Int, hidden: Boolean = this.isDone) = isValid(x, y) && (
        act[x][y] == TileLogicType.LADDER || (base[x][y] == TileLogicType.HLADDER && hidden)
    )
    fun isLadder(at: Vec2i, hidden: Boolean = this.isDone) = isLadder(at.x, at.y, hidden)

    fun isFloor(x: Int, y: Int, useBase: Boolean = false, useGuard: Boolean = true): Boolean {
        val check = if ( useBase ) { base } else { act }
        return !isValid(x, y) || check[x][y] == TileLogicType.BLOCK || check[x][y] == TileLogicType.SOLID ||
                check[x][y] == TileLogicType.LADDER || (useGuard && hasGuard(x, y))
    }
    fun isFloor(at: Vec2i, useBase: Boolean = false, useGuard: Boolean = true) = isFloor(at.x, at.y, useBase, useGuard)

    fun isBar(x: Int, y: Int) = isValid(x, y) && base[x][y] == TileLogicType.BAR
    fun isBar(at: Vec2i) = isBar(at.x, at.y)

    fun isGold(x: Int, y: Int) = isValid(x, y) && base[x][y] == TileLogicType.GOLD

    fun isEmpty(x: Int, y: Int) = isValid(x, y) &&
            act[x][y] == TileLogicType.EMPTY &&
            base[x][y] != TileLogicType.GOLD && !hasGuard(x, y)
    fun isEmpty(at: Vec2i) = isEmpty(at.x, at.y)

    fun hasGuard(x: Int, y: Int) = isValid(x, y) && guard[x][y]
    fun hasGuard(at: Vec2i) = hasGuard(at.x, at.y)

}

sealed class NeighbourhoodTopology {
    abstract fun neighbours(x: Int, y: Int): Sequence<Int>
    object NeighborhoodNeumann2d: NeighbourhoodTopology() {
        val indx = arrayOf( 0, -1, -1, 0, 1, 0, 0, 1 )
        override fun neighbours(x: Int, y: Int) =
            indx.asSequence().chunked(2).flatMap { (dx, dy) ->
                sequenceOf(x + dx, y + dy)
            }
    }
}