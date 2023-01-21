package me.az.ilode

import org.mifek.wfc.adapters.GifSequenceWriter
import org.mifek.wfc.core.Cartesian2DWfcAlgorithm
import org.mifek.wfc.core.WfcAlgorithm
import org.mifek.wfc.datastructures.IntArray2D
import org.mifek.wfc.models.OverlappingCartesian2DModel
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.stream.FileImageOutputStream
import kotlin.math.floor

internal val Float.int: Int get() = this.toInt()

/**
 * To buffered image
 *
 * @return
 */
fun IntArray2D.toBufferedImage(scale: Float, palette: (value: Int) -> Int = { x -> x } ): BufferedImage {
    val ret = BufferedImage((width * scale).int, (height * scale).int, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until ret.height) {
        for (x in 0 until ret.width) {

            ret.setRGB(x, y, palette(this[floor(x/scale).int + width * floor(y/scale).int]))
        }
    }
    return ret
}

val tileColors = arrayOf(
    Color.GRAY,
    Color.YELLOW,
    Color.BLUE, // solid
    Color.GREEN, // ladder
    Color.CYAN, // rope
    Color.MAGENTA, // trap
    Color.GREEN.brighter(), // hladd
    Color.YELLOW.brighter(), // gold
    Color.RED, // guard
    Color.WHITE, // player
)

val unresolved = Color(0, 0, 0, 0)
fun OverlappingCartesian2DModel.constructRgbArray(algorithm: Cartesian2DWfcAlgorithm, highlight: Int?) =
    IntArray2D(outputWidth, outputHeight) { waveIndex ->
        val pair = shiftOutputWave(waveIndex)
        val index = pair.first
        val shift = pair.second

        val sum = algorithm.waves[index].filter { it }.count()
        when (sum) {
            0 -> unresolved.rgb // deep black
            1 -> {
                val tile = this.storage.patternsArray[patterns.indices.filter { algorithm.waves[index, it] }[0]][shift]
                tileColors[tile].rgb
            }
            else -> {
                val ret = de.fabmax.kool.util.MutableColor(0f, 0f, 0f, 1f)
                val colors = patterns.indices.filter { algorithm.waves[index, it] }
                    .map { tileColors[storage.patternsArray[it][shift]] }
                val totalColors = colors.size
                colors.forEach { v ->
                    ret.mix(
                        other = de.fabmax.kool.util.Color(v.red / 255f, v.green / 255f, v.blue / 255f),
                        weight = 1f / totalColors,
                        result = ret
                    )
                }
                Color(ret.r, ret.g, ret.b, ret.a).rgb
            }
        }.run {

            if ( waveIndex == highlight ) {
                Color(this).brighter().brighter().rgb
            } else {
                this
            }
        }
    }

var writer: GifSequenceWriter? = null
var lastAssign: Int? = null
var lastBan: Int? = null
var afterWarmup = false

actual fun debugAlgoStart(
    levelId: Int,
    model: OverlappingCartesian2DModel,
    algo: Cartesian2DWfcAlgorithm
) {

//    File("debug/${levelId + 1}/").createNewFile()
    val outfile = File("/opt/debug_out/${levelId + 1}_test.gif")
    outfile.delete()
    writer = GifSequenceWriter(
        FileImageOutputStream(outfile),
        BufferedImage.TYPE_INT_ARGB,
        1,
        loopContinuously = false
    )

    algo.afterWarmup += {
        println("after warmup")
        model.dis(algo)
        afterWarmup = true
    }

    algo.afterObserve += {(a, value, variable)->
        if (a.waves[value].count { it } == 0) {

        } else {
            lastAssign = value
        }
    }
    algo.afterFinished += {
        println("finished")
        writer?.close()
    }
    algo.afterBan += { (a: WfcAlgorithm, variable: Int, value: Int) ->
        if ( lastBan != variable ) {
            val data = model.constructRgbArray(algo, lastAssign)
            writer?.writeToSequence(
                data.toBufferedImage(3f)
            )
            lastBan = variable
        }

////        println(wcf.topology.deserializeCoordinates(variable).toString() + " banned $value = " + a.waves[variable].joinToString("") { if (it) "1" else "0" })
    }


//    algo.afterPropagationStep += {
//        if ( writePropagationSteps )
//            debugAlgo(wcf, algo)
//    }
//    algo.afterPropagation += {
////        if ( !writePropagationSteps ) {
////            debugAlgo(wcf, algo)
////        }
//    }
}