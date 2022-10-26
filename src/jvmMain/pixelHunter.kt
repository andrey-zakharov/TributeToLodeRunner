package me.az

import java.io.File
import java.io.InputStream
import java.lang.Exception
import java.nio.ByteBuffer
import kotlin.math.*
import kotlin.reflect.typeOf
import kotlin.system.exitProcess

// kotlinc pixelHunter.kts -include-runtime -d pixelHunter.jar

// factor and its degree
fun Int.factors(k: Int = 2): Map<Int, Int> {
    if ( this == 1 ) return mapOf()
    if ( this <= 3 ) return mapOf(this to 1)
    val res = mutableMapOf<Int, Int>()
    var r: Int = this
    while( r % k == 0 ) {
        res.put( k, res.getOrDefault( k, 0 ) + 1 )
        r /= k
    }
    val o = r.factors( if ( k < 3 ) k + 1 else k + 2)
    o.forEach { ok, d ->
        res.put( ok, res.getOrDefault(ok, 0) + d )
    }
    return res
}

// less common multiplier
fun Int.lcm(o: Int): Int {
    val f1 = this.factors().toMutableMap()
    o.factors().entries.forEach { (k, v) -> f1.put(k, max( f1.getOrDefault(k, 0), v ) ) }
    return f1.factor
}

val Map<Int, Int>.factor get() = entries.fold(1) { r, e -> r * e.key.toFloat().pow( e.value ).toInt() }

val palette = arrayOf(
    ' ', '▮', '▯', '▼', '▽', '◆', '◇', '◈',
    '▮', '▮', '▮', '▮', '▮', '▮', '▮', '▮',
)
// or use colors for 1
fun InputStream.pixelsStream(
    bytesWindow: Int = 32,
    bitsPerPixel: Int = 2
) = sequence<UInt> {

    val windowBuff = ByteArray(bytesWindow)
    val mask = (2f.pow( bitsPerPixel ) - 1).toUInt()
    var currentByte = Int.MAX_VALUE
    var currentShift = 0
    var currentMask = (mask shl (8 - bitsPerPixel)).toUByte()
    var red = Int.MAX_VALUE

    while( red > 0 ) {
        if ( currentByte >= bytesWindow ) {
            //read  next chunk
            red = read(windowBuff)
//                println("WINDOW READ= ${windowBuff.joinToString(", ") { it.toUByte().toString(2).padStart(8, '0') } }")
            currentByte = 0
        }

        val f = windowBuff[currentByte].toUByte() and currentMask
        val res = f.toInt() shr (8 - currentShift - bitsPerPixel)
//
//            println("currentByte=$currentByte code=${windowBuff[currentByte].toUByte().toString(16)} " +
//                    "\ncode=${windowBuff[currentByte].toUByte().toString(2).padStart(8, '0')}" +
//                    "\nmask = ${currentMask.toString(2).padStart(8, '0')} " +
//                    "\tres = $res")
//
        yield( res.toUInt() )
//
        currentMask = (currentMask.toInt() shr bitsPerPixel).toUByte()
        currentShift += bitsPerPixel
        if ( currentShift >= 8 ) { // byte border TBD for 3 bits per pixel!
            ++currentByte
            currentShift = 0
            currentMask = (mask shl (8 - bitsPerPixel)).toUByte()
        }
    }
}

fun InputStream.readAll(mark: ByteArray, bitsPerPixel: Int = 2, width: Int = 12, height: Int = 11) {
    var p =0
    val markBuf = ByteArray(mark.size)
    while( available() > 0 ) {
        read(markBuf)
        if (markBuf[0] == mark[0] && markBuf[1] == mark[1]) {
            p ++
            // mark[0] was rows, mark[1] bytes per row (= 3 * 8 / 2  = 12)
//            println(pos.toString(16).padStart(8, '0'))
//            printSprite(bitsPerPixel, width, height)
//            println(read().toString(16))
//            println(read().toString(16))
//            println(read().toString(16))
//            println(read().toString(16))
            println()
        }
    }
    println("total $p")
}

// returns pixels red
fun InputStream.printSprite(
    bitsPerPixel: Int = 2,
    width: Int = 12,
    height: Int = 11,
    skipEmptyRows: Boolean = false,
    export: Boolean = false,
): Int {
    val bytesWindow = 8.lcm( bitsPerPixel * width ) / 8
//    println("window size in bytes = $bytesWindow")

    val stream = pixelsStream(
        bytesWindow = bytesWindow,
        bitsPerPixel = bitsPerPixel
    )

    // BYTE ORDER????
    var printedRows = 0
    var redPixels = 0

    stream.takeWhile { printedRows < height }.chunked(width).forEach { row ->
        val rowStr = row.map {
            if ( export ) "${it.toString(10)}"
            else
                if ( it == 0u ) " "
                else "\u001b[38;5;${it % 255u}m${palette[1]}"
        }.joinToString("")

        redPixels += row.size

        if ( rowStr.trim().isEmpty() && skipEmptyRows ) {
            return@forEach
        }

        printedRows ++
        println("$rowStr\u001b[0m")
    }
    return redPixels
}

fun main(args: Array<String>) {
//    println(4.lcm(3))
//    println(18.lcm(8))
    println(args.joinToString(", "))
    if (args.isEmpty()) {
        println("enter file")
        exitProcess(-1)
    }

    val f = File(args[0].toString())
    val reader = f.inputStream()
    if ( args.size > 1 && args[1] == "mark" ) {
        val mark = ByteArray(2) { listOf(0x0b, 0x03)[it].toByte() }
        reader.readAll(mark)
        exitProcess(0)
    }

    val rowWidth = try { Integer.decode(args[1]) } catch (e: Exception) {32} // in "pixels"
    val bitsPerPixel = if ( args.size > 2) Integer.decode(args[2]) else 4 //
    val offset = if ( args.size > 3 ) Integer.decode(args[3]) else 0
    val limitRows = if ( args.size > 4 ) Integer.decode(args[4]) else Int.MAX_VALUE
    val skipEmptyRows = if ( args.size > 5 ) args[5].toBoolean() else false
    val export = if ( args.size > 6 ) args[6].toBoolean() else false

    reader.skip(offset.toLong())
//println(f.readBytes().joinToString(", ") { it.toUByte().toString(2).padStart(8, '0') } )

    val redPixels = reader.printSprite(
        bitsPerPixel, rowWidth, limitRows, skipEmptyRows, export
    )
    println("new offset = 0x${(offset + (redPixels * bitsPerPixel / 8)).toString(16)}")

    exitProcess(0)
//    return 0
}