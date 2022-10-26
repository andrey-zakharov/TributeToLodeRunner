package me.az

import java.io.File
import java.io.InputStream
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
    o.forEach { k, d ->
        res.put( k, res.getOrDefault(k, 0) + d )
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
fun pixelsStream(
    inputStream: InputStream
    bitsPerPixel: Int = 2,
    limitRows: Int = Int.MAX_VALUE,
    skipEmptyRows: Boolean = false,
    export: Boolean = false
) = sequence<Int> {
    val mask = (2f.pow( bitsPerPixel ) - 1).toUInt()
    var currentByte = Int.MAX_VALUE
    var currentShift = 0
    var currentMask = (mask shl (8 - bitsPerPixel)).toUByte()
    var red = Int.MAX_VALUE

    while( red > 0 ) {
        if ( currentByte >= bytesWindow ) {
            //read  next chunk
            red = reader.read(windowBuff)
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
fun main(args: Array<String>) {
//    println(4.lcm(3))
//    println(18.lcm(8))
    println(args.joinToString(", "))
    if (args.isEmpty()) {
        println("enter file")
        exitProcess(-1)
    }

    val rowWidth = if ( args.size > 1) Integer.decode(args[1]) else 32 // in "pixels"
    val bitsPerPixel = if ( args.size > 2) Integer.decode(args[2]) else 4 //
    val offset = if ( args.size > 3 ) Integer.decode(args[3]) else 0
    val limitRows = if ( args.size > 4 ) Integer.decode(args[4]) else Int.MAX_VALUE
    val skipEmptyRows = if ( args.size > 5 ) args[5].toBoolean() else false
    val export = if ( args.size > 6 ) args[6].toBoolean() else false

val f = File(args[0].toString())
val reader = f.inputStream()
reader.skip(offset.toLong())

//println(f.readBytes().joinToString(", ") { it.toUByte().toString(2).padStart(8, '0') } )

val bytesWindow = 8.lcm( bitsPerPixel * rowWidth ) / 8
println("window size in bytes = $bytesWindow")
val windowBuff = ByteArray(bytesWindow)

    val pixelsStream = sequence<UInt> {
        val mask = (2f.pow( bitsPerPixel ) - 1).toUInt()
        var currentByte = Int.MAX_VALUE
        var currentShift = 0
        var currentMask = (mask shl (8 - bitsPerPixel)).toUByte()
        var red = Int.MAX_VALUE

        while( red > 0 ) {
            if ( currentByte >= bytesWindow ) {
                //read  next chunk
                red = reader.read(windowBuff)
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

        // BYTE ORDER????
        var printedRows = 0
        var redPixels = 0

        pixelsStream.takeWhile { printedRows < limitRows }.chunked(rowWidth).forEach { row ->
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

    println("new offset = 0x${(offset + (redPixels * bitsPerPixel / 8)).toString(16)}")

    exitProcess(0)
//    return 0
}