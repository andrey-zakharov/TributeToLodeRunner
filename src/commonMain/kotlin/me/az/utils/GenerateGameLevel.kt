package me.az.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.az.ilode.GameLevel
import me.az.ilode.Tile
import me.az.ilode.loadGameLevel
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/*
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
    tilesAtlasIndex: Map<String, List<Int>>,
    scope: CoroutineScope,
): GameLevel {
    val patternSize = 3 // n , m

    logd { "input = $exampleWidth x $exampleHeight, output = $mapWidth x $mapHeight example origin= $exampleOriginX x $exampleOriginY" }
    logd { exampleMap.joinToString("\n") }
    logd { "for generator" }
    logd {
        exampleMap.joinToString("\n") {
            it.map { ch -> Tile.values()[Tile.byChar[ch]!!.exportForGenerator()].char }.joinToString("")
        }
    }
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
                    exampleY in 0 until exampleHeight
                ) exampleMap[exampleY][exampleX]
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

            logd {
                out.joinToString("\n") { row ->
                    row.joinToString("") {
                        when (it) {
                            null -> "."
                            Int.MIN_VALUE -> "!"
                            else -> "$it"
                        }
                    }
                }
            }
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


}*/