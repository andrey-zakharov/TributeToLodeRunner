package me.az.ilode

import AnimationFrames
import calcOverlapping
import de.fabmax.kool.math.MutableVec2i
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.TextureData2d
import de.fabmax.kool.util.createUint8Buffer
import org.mifek.wfc.core.Cartesian2DWfcAlgorithm
import org.mifek.wfc.datastructures.IntArray2D
import org.mifek.wfc.models.OverlappingCartesian2DModel
import org.mifek.wfc.models.options.Cartesian2DModelOptions
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

enum class TileLogicType() {
    EMPTY,
    BLOCK,
    SOLID,
    LADDR,
    BAR,
    TRAP,
    HLADR,
    GOLD,
}

/// named by frame names
enum class Tile(val char: Char, val base: TileLogicType, val act: TileLogicType, val frameName: String? = null) {
    EMPTY(' ', TileLogicType.EMPTY, TileLogicType.EMPTY ),
    BRICK('#', TileLogicType.BLOCK, TileLogicType.BLOCK ), // Normal Brick
    SOLID('@', TileLogicType.SOLID, TileLogicType.SOLID ), // Solid Brick
    LADDER('H', TileLogicType.LADDR, TileLogicType.LADDR ), // Ladder
    ROPE('-', TileLogicType.BAR, TileLogicType.BAR ), // Line of rope
    TRAP('X', TileLogicType.TRAP, TileLogicType.EMPTY, BRICK.name.lowercase() ), // False brick
    HLADDER('S', TileLogicType.HLADR, TileLogicType.EMPTY, LADDER.name.lowercase() ), //Ladder appears at end of level
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

data class ViewCell (
    var hole: Boolean, // maybe several atlases, TBD
    var frameNum: Int
) {
    val pack: Byte get() {
        var b: UInt = 0u
        b = b.packHole(hole)
        b = b.packFrameNum(frameNum)
        return b.toByte()
    }

    companion object {
        fun unpack(b: Byte) = with(b.toUInt()) {
            ViewCell(
                unpackHole(),
                unpackFrameNum()
            )
        }

        private fun UInt.getBit(position: Int): Boolean = (this shr position) and 1u > 0u
        private fun UInt.withBit(position: Int, bit: Boolean): UInt {
            return if ( bit ) {
                this or (1u shl position)
            } else {
                this and (1u shl position).inv()
            }
        }
        private fun UInt.packHole(v: Boolean) = withBit(7, v)
        private fun UInt.unpackHole() = getBit(7)
        private fun UInt.packFrameNum(f: Int) = (this and 0xff80u) or ( f.toUInt() and 0x007fu )
        private fun UInt.unpackFrameNum() = (this and 0xff7fu).toInt()
    }
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
    println(map)
}
expect fun debugAlgoStart(levelId: Int, model: OverlappingCartesian2DModel, algo: Cartesian2DWfcAlgorithm)

@OptIn(ExperimentalTime::class)
fun generateGameLevel(
    levelId: Int,
    exampleMap: List<String>,
    tilesAtlasIndex: Map<String, Int>,
    holesIndex: MutableMap<String, Int>,
    holesAnims: AnimationFrames
): GameLevel {



//    val contrains = calcRestrictions(exampleMap)
    val contrains = calcOverlapping(exampleMap)
    //println(contrains.normalizedPrint { contrains.normalized() })
    val exampleWidth = exampleMap.first().length
    val exampleHeight = exampleMap.size
    val mapHeight = 2 * exampleHeight
    val mapWidth = 2 * exampleWidth
    val patternSize = 3 // n , m

    val exampleMapShiftX = (mapWidth - exampleWidth) / 2
    val exampleMapShiftY = mapHeight - exampleHeight
    println("input = $exampleWidth x $exampleHeight, output = $mapWidth x $mapHeight")
    println(exampleMap.joinToString("\n"))
    println(exampleMap.joinToString("\n") { it.map { Tile.values()[Tile.byChar[it]!!.exportForGenerator()].char }.joinToString("")})
    println("raw patterns count: ${(exampleWidth - patternSize + 1) * (exampleHeight - patternSize + 1)}")

    val initials = IntArray2D(exampleWidth, exampleHeight) { idx ->
        val x = idx % exampleWidth
        val y = idx / exampleWidth
        Tile.byChar[exampleMap[y][x]]!!.exportForGenerator()
    }

    val wcf = OverlappingCartesian2DModel(initials, overlap = patternSize - 1,
        outputWidth = mapWidth, outputHeight = mapHeight,
        options = Cartesian2DModelOptions(
            allowRotations = false,
            allowHorizontalFlips = true,
            allowVerticalFlips = false,
            grounded = true,
            roofed = false,
            leftSided = false,
            rightSided = false,
            periodicInput = true,
            periodicOutput = false,
        )
    )

    // set in map
    exampleMap.forEachIndexed { y, row ->
        row.forEachIndexed { x, c ->
//            println("$x $y -> ${(mapWidth - exampleWidth) / 2 + x}, ${y + mapHeight - exampleHeight}")
            wcf.setPixel(
                exampleMapShiftX + x, exampleMapShiftY + y,
                Tile.byChar[exampleMap[y][x]]!!.exportForGenerator()
            )
            //(wcf.width - exampleWidth) / 2 +
            //(wcf.height - exampleHeight) / 2 +
        }
    }
    println(formatPatterns(wcf.patterns.toList().toTypedArray(), patternSize))

    val algo = wcf.build()

//    debugAlgoStart(levelId, wcf, algo)

//    val wcf = LevelGenerator(mapWidth, mapHeight, contrains, initials, contrains.patterns.keys.toTypedArray())

    // fill example
//    println(wcf.dis("after load example"))

    algo.afterFail += {
        println("failed")
        //debugAlgoEnd(wcf, algo)
    }

    val dur = measureTime {
        algo.run(seed = 2)
    }
    println("wcf run in $dur")
    wcf.dis(algo)

    val out = wcf.constructNullableOutput(algo)

    // metric
    println((0 until mapWidth).map { i -> if ( i % 10 == 0 ) i / 10 else " "}.joinToString(""))
    println((0 until mapWidth).map { i -> (i % 10) }.joinToString(""))

    println( out.joinToString("\n") { row ->
        row.joinToString("") { when(it) {
            null -> "."
            Int.MIN_VALUE -> "!"
            else -> "$it"
        } }
    } )
    // print original (example) level to map without filtering

    exampleMap.forEachIndexed { y, row ->
        row.forEachIndexed { x, c ->
            out[y + exampleMapShiftY][exampleMapShiftX + x] =
                Tile.byChar[exampleMap[y][x]]!!.ordinal
        }
    }

    return loadGameLevel(levelId, out.mapIndexed { y, row ->
        row.joinToString("") { domain ->
            val tileIndex = when(domain) {
                null -> 0 // empty
                Int.MIN_VALUE -> 0
                else -> domain
            }
            Tile.values()[tileIndex].char.toString()
        }
    }, tilesAtlasIndex, holesIndex, holesAnims).apply {

    }

}

fun loadGameLevel(
    levelId: Int,
    map: List<String>,
    tilesAtlasIndex: Map<String, Int>,
    holesIndex: MutableMap<String, Int>,
    holesAnims: AnimationFrames
): GameLevel {
    return GameLevel(levelId, map.first().length, map.size + 1, tilesAtlasIndex, holesIndex, holesAnims).apply {

        val maxGuards = 5
        // for load

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
                this[x, y] = ViewCell(false, tilesAtlasIndex[
                        if ( tile.base == TileLogicType.HLADR || tile.frame.isEmpty() )
                            Tile.EMPTY.frame
                        else tile.frame
                ]!!
                )
            }
        }
        for (x in 0 until this.width ) {
            this.act[x][this.height-1] = TileLogicType.SOLID
            this.base[x][this.height-1] = TileLogicType.SOLID
            this[x, this.height-1] = ViewCell(false, tilesAtlasIndex["ground"]!!)
        }
    }
}

class GameLevel(
    val levelId: Int,
    val width: Int,
    val height: Int,
    private val primaryTileSet: Map<String, Int>, // index
    private val holesTileSet: Map<String, Int>,
    val holesAnims: AnimationFrames
) {

    enum class Status {
        LEVEL_STARTUP,
        LEVEL_PAUSED,
        LEVEL_PLAYING
    }
    // store view info
    val buf = createUint8Buffer(width * height)
    var runnerPos = MutableVec2i()
    val guardsPos = mutableListOf<Vec2i>()
    var gold = 0
    val isDone get() = gold == 0
    var status = Status.LEVEL_STARTUP

    // store logic info
    val act = Array(width) { Array(height) { TileLogicType.EMPTY } }
    val base = Array(width) { Array(height) { TileLogicType.EMPTY } }
    val guard = Array(width) { Array(height) { false } }
    val anims = mutableMapOf<Vec2i, Pair<String, Int>>() // pos -> anim name, current frame index in anim
    var dirty = false

    fun update(runner: Runner, guards: List<Guard>) {
//        anims.takeIf { it.isNotEmpty() }?.run { println(this) }
        anims.forEach {
            val x = it.key.x
            val y = it.key.y
            var ( animName, frameIndex ) = it.value
            val animArray = holesAnims.sequence[animName]!!
            val tileIndex = animArray[frameIndex]
            val frame = frameIndex + 1
            anims[it.key] = Pair(animName, frame)

            if ( frame == animArray.size ) {
                if ( animName == "fillHole" ) {
                    this[it.key] = ViewCell(false, primaryTileSet[Tile.BRICK.frame]!!)
                    act[x][y] = TileLogicType.BLOCK
                    guard[x][y] = false
                    // check runner death
                    if ( runner.block.x == x || runner.block.y == y ) {
                        runner.alive = false
                    }

                    if ( guards.isNotEmpty() ) {
                        guards.firstOrNull { gp -> gp.block.x == x && gp.block.y == y }?.run {
                            alive = false
                            runner.addScore(SCORE_DIES)
                        }
                    }


                } else if ( animName == "digHoleLeftBase" || animName == "digHoleRightBase" ) {
                    anims[it.key] = Pair("fillHole", 0)
                    println("starting fill hole")
                } else {

                }
            // break dig
            } else if ( (animName == "digHoleLeftBase" || animName == "digHoleRightBase") && guard[x][y - 1] ) {
                // break dig
                act[x][y] = TileLogicType.BLOCK
                this[x, y] = ViewCell(false, primaryTileSet[Tile.BRICK.frame]!!)
                guard[x][y] = false
                //stopSound
                runner.sounds.stopSound("dig")
                anims[it.key] = Pair(animName, animArray.size)
            } else {
                this[x, y] = ViewCell(true, tileIndex)
            }
        }

        val iter = anims.iterator()
        while( iter.hasNext() ) {
            val entry = iter.next()
            val (animName, frameIndex) = entry.value
            if ( frameIndex < holesAnims.sequence[animName]!!.size ) continue

            if ( animName == "digHoleLeft" || animName == "digHoleRight" ) {
                this[entry.key] = ViewCell(false, primaryTileSet[Tile.EMPTY.frame]!!)
                act[entry.key.x][entry.key.y] = TileLogicType.EMPTY
            }

            iter.remove()
        }
    }


    // 0 - 6 bits
    // 7 bit - for hole as frame tile set

    operator fun get(x: Int, y: Int): ViewCell? = if ( isValid(x, y) ) ViewCell.unpack(buf[y * width + x]) else null
    operator fun set(x: Int, y: Int, v: ViewCell)  { if ( isValid(x, y) ) buf[y * width + x] = v.pack; dirty = true }

    operator fun get(at: Vec2i) = get(at.x, at.y)
    operator fun set(at: Vec2i, v: ViewCell) = set(at.x, at.y, v)
    operator fun set(at: Vec2i, t: Tile) {
        if (isValid(at)) {
            set(at.x, at.y, ViewCell(get(at.x, at.y)!!.hole, primaryTileSet[t.frame]!!))
        }
    }

    fun getAct(at: Vec2i) = act[at.x][at.y]
    fun getBase(at: Vec2i) = base[at.x][at.y]

    private fun isValid(x: Int, y: Int) = x >= 0 && x < width && y >= 0 && y < height
    private fun isValid(at: Vec2i) = isValid(at.x, at.y)

    fun updateTileMap(): TextureData2d {
//        println("updating field($width x $height)")
        return TextureData2d(buf, width, height, TexFormat.R)
    }

    fun showHiddenLadders() {
        for ( y in 0 until height ) {
            for ( x in 0 until width) {
                if ( base[x][y] == TileLogicType.HLADR ) {
                    base[x][y] = TileLogicType.LADDR
                    act[x][y] = TileLogicType.LADDR
                    this[x, y] = ViewCell(false, primaryTileSet[Tile.LADDER.frame]!!)
                }
            }
        }

    }

    fun isBarrier(x: Int, y: Int) = !isValid(x, y) ||
        act[x][y] == TileLogicType.BLOCK || act[x][y] == TileLogicType.SOLID ||
        act[x][y] == TileLogicType.TRAP
    fun isBarrier(at: Vec2i) = isBarrier(at.x, at.y)

    fun isLadder(x: Int, y: Int, hidden: Boolean) = isValid(x, y) && (
        act[x][y] == TileLogicType.LADDR || (base[x][y] == TileLogicType.HLADR && hidden)
    )
    fun isLadder(at: Vec2i, hidden: Boolean) = isLadder(at.x, at.y, hidden)

    fun isFloor(x: Int, y: Int, useBase: Boolean = false, useGuard: Boolean = true): Boolean {
        val check = if ( useBase ) { base } else { act }
        return !isValid(x, y) || check[x][y] == TileLogicType.BLOCK || check[x][y] == TileLogicType.SOLID ||
                check[x][y] == TileLogicType.LADDR || (useGuard && guard[x][y])
    }
    fun isFloor(at: Vec2i, useBase: Boolean = false, useGuard: Boolean = true) = isFloor(at.x, at.y, useBase, useGuard)

    fun isBar(x: Int, y: Int) = isValid(x, y) && base[x][y] == TileLogicType.BAR
    fun isBar(at: Vec2i) = isBar(at.x, at.y)

    fun isGold(x: Int, y: Int) = isValid(x, y) && base[x][y] == TileLogicType.GOLD

    fun isEmpty(x: Int, y: Int) = isValid(x, y) && base[x][y] == TileLogicType.EMPTY
    fun isEmpty(at: Vec2i) = isEmpty(at.x, at.y)

    fun hasGuard(x: Int, y: Int) = isValid(x, y) && guard[x][y]
    fun hasGuard(at: Vec2i) = hasGuard(at.x, at.y)

}