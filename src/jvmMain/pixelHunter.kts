import java.io.File
import kotlin.math.*
import kotlin.reflect.typeOf
import kotlin.system.exitProcess

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

val colors = arrayOf(
    31, 32, 33, 34, 35, 36, 37,
    91, 92, 93, 94, 95, 96, 97,
)
val palette = arrayOf(
    ' ', '▮', '▯', '▼', '▽', '◆', '◇', '◈',
    '▮', '▮', '▮', '▮', '▮', '▮', '▮', '▮',
)
// or use colors for 1

//fun main(vararg args: Any?): Int {
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

val f = File(args[0].toString())
//val reader = f.bufferedReader()
val reader = f.inputStream()
reader.skip(offset.toLong())

//println(f.readBytes().joinToString(", ") { it.toUByte().toString(2).padStart(8, '0') } )

val bytesWindow = 8.lcm( bitsPerPixel * rowWidth ) / 8
println("window size in bytes = $bytesWindow")
val windowBuff = ByteArray(bytesWindow)

    val pixelsStream = sequence<UInt> {
        val mask = (2f.pow( bitsPerPixel ) - 1).toUInt()
//        println("mask = ${mask.toString(2)}")
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
//            f.toInt() shr 1
//            println(f.toInt().toString(2))
//            println((f.toInt() shr (8 - currentShift - bitsPerPixel)).toString(2))
            val res = (windowBuff[currentByte].toUByte() and currentMask).toInt() shr (8 - currentShift - bitsPerPixel)
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


        val bytesToFetchF = rowWidth * bitsPerPixel / 8f //<bytes bits number
        val bytesToFetch = ceil(bytesToFetchF).toInt()
        if ( bytesToFetch.toFloat() != bytesToFetchF ) {
            println("WARNING bytes per row=${bytesToFetchF}")
        }
//        val windowBuff = CharArray(bytesToFetch)

        var readedRows = 0
//        var

        pixelsStream.takeWhile { readedRows < limitRows }.chunked(rowWidth).forEach { row ->
            val rowStr = row.map {
                if ( it == 0u ) " "
                else {
//                    println("got pixel  ${it.toString(2)}")
                    "\u001b[38;5;${it % 255u}m${palette[1]}"
                }
            }.joinToString("")

            if ( rowStr.trim().isEmpty() && skipEmptyRows ) {
                return@forEach
            }

            readedRows ++
            println("$rowStr\u001b[0m")
        }
//
//        while( readedRows < limitRows ) {
////            reader.read(windowBuff, 0, bytesToFetch)
//            // it could read 1 byte ahead, need to store this byte
//
//            for ( i in 0 until rowWidth ) {
//                // take i bits
////                val shift = i * bitsPerPixel
//                // BYTE ORDER??
////                val bytePos = shift / 8
////                val remainShift = shift - bytePos * 8
//                val pixel = pixelsStream.
//                if ( pixel == 0 ) {
//                    print(" ")
//                    continue
//                }
//
////            print("\u001b[${colors[pixel % colors.size]}m")
//                print("\u001b[38;5;${pixel % 255}m")
//                print(palette[1])//pixel % palette.size])
//                printed = true
//            }
//            if ( skipEmptyRows && !printed  ) {
//                print("\r")
//            } else {
//                readedRows ++
//                println("\u001b[0m")
//            }
//
//            // remainder
//
////        }
//    }

    exitProcess(0)
//    return 0
//}