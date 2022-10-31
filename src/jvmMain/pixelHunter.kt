package me.az
// sudo aptitude install coreutils
import java.io.File
import java.io.InputStream
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
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
@kotlin.ExperimentalUnsignedTypes
fun List<UByte>.pixelStream(
    bitsPerPixel: Int = 2
) = sequence<Int> {

    val mask = (2f.pow( bitsPerPixel ) - 1).toUInt()
    var currentByte = 0
    var currentShift = 0
    var currentMask = (mask shl (8 - bitsPerPixel)).toUByte()

    while( currentByte < size ) {
        val f = this@pixelStream[currentByte] and currentMask
        val res = f.toInt() shr (8 - currentShift - bitsPerPixel)
        yield( res )

        currentMask = (currentMask.toInt() shr bitsPerPixel).toUByte()
        currentShift += bitsPerPixel
        if ( currentShift >= 8 ) { // byte border TBD for 3 bits per pixel!
            ++currentByte
            currentShift = 0
            currentMask = (mask shl (8 - bitsPerPixel)).toUByte()
        }
    }

}
// or use colors for 1
fun InputStream.pixelsStream(
    bytesWindow: Int = 32,
    bitsPerPixel: Int = 2
) = sequence<Int> {

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
        yield( res )
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
            it.printPixel(export)
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

fun Int.printPixel(export: Boolean = false): String =
    if ( export ) "${toString(10)}"
    else
        if ( this == 0 ) " "
        // \u001b[47m
//        else "\u001b[38;5;${this % 255}m${palette[1]}"
        else "\u001b[48;5;${this % 255}m "

fun InputStream.readAll(mark: ByteArray, bitsPerPixel: Int = 2, width: Int = 12, height: Int = 11) {
    var p = 0
    var curMarkI = 0
    val charBuff = ByteArray(1)

    while( available() > 0 ) {

        while ( curMarkI < mark.size && read(charBuff) > 0 && mark[curMarkI] == charBuff[0] ) {
            curMarkI++
        }
        if ( curMarkI == mark.size ) {// exact match
            val red = printSprite(bitsPerPixel, width, height)
            println("red = $red bytes")
            p++

        } else {
            // failed match
        }
        curMarkI = 0
    }

    println("total $p")
}

val cga = mapOf(
    0 to "0, 0, 0",
    1 to "0, 170, 170",
    2 to "170, 0, 170",
    3 to "170, 170, 170",
)

@kotlin.ExperimentalUnsignedTypes
fun File.parse(mark: ByteArray, bitsPerPixel: Int = 2, width: Int = 12, height: Int = 11, export: Boolean = false) {

    var p = 0
    val b = readBytes().asUByteArray()
    val m1 = 0x0b.toUByte()
    val m2 = 0x03.toUByte()




    for ( i in 0 until b.size - 1 ) {
        if (b[i] == m1 && b[i+1] == m2) {
            val px = b.slice( i + 2 until i + 2 + (width * height * bitsPerPixel / 8) )
                .pixelStream(bitsPerPixel)

            val channels = Array(cga.size) { mutableListOf<Pair<Int, Int>>() }

//            val resFile = File("${(i + 2).toString(16)}.png")
            val resFile = File("${(i + 2).toString(16)}.txt")
            println("\u001B[0m = Sprite #${p+1} (${(i + 2).toString(16)}) =")

            // could not print at once. some imagemagick errors. draw each channel sep
//            val closeFile = " -quality 100 ${resFile.name}"

//            val openFile = "convert -size ${width}x$height xc:transparent $closeFile"

            resFile.printWriter().use { out ->
                out.println("# ImageMagick pixel enumeration: $width,$height,255,rgb")
                px.chunked(width).mapIndexed { y, r ->
                    r.forEachIndexed { x, p ->
//                        if ( p == 0 ) return@forEachIndexed
//                    channels[p].add(Pair(x, y))
                       out.println("$x,$y: (${cga[p]})")
                    }
                }.toList()
            }

            // for t in *.txt; do convert -transparent black txt:$t -define png:color-type=3 -define png:bit-depth=4 -quality 100 `basename $t .txt`.png; done
            // convert -transparent black txt:ddc0.txt ddc0.png
            // montage cf98.png cfbb.png cfde.png ca0d.png d06a.png d08d.png ccff.png cd22.png cd45.png cd68.png cd8b.png cdae.png cdae.left.png cdae.right.png cdd1.png cdd1.left.png cdd1.rigth.png cdf4.png ce17.png ce3a.png cec6.png cee9.png cf0c.png dc85.png dca8.png  -transparent white -geometry +0 -quality 100 out.png
            // montage c8cf.png c93b.png c9c7.png c918.png c9ea.png c95e.png c981.png c9a4.png dd11.png dd34.png dd57.png dd7a.png dd9d.png ddc0.png dde3.png de06.png de29.png de4c.png dccb.png dcee.png dc3f.png dc62.png -transparent white -geometry +0 -quality 100 out.png
//            Runtime.getRuntime().exec(openFile)
//
//            val command = channels.mapIndexed { color, cells ->
//                "convert ${resFile.name} -fill \"#${cga[color]!!.toString(16).padStart(3, '0')}\" ${cells.map { (x, y) ->
//                    "-draw 'point $x,$y'"
//                }.joinToString(" ")} $closeFile"
//            }.map {
//                println(it)
//                Runtime.getRuntime().exec(it)
//            }

            p++

        }
    }
    println(p)
}
// stdout catch
fun execAndOut(command:String): String {
    val p = Runtime.getRuntime().exec(command)
    p.waitFor()
    return String(p.inputStream.readAllBytes(),  StandardCharsets.UTF_8)
}

sealed class Term {
    abstract fun init()
    abstract fun restore()
    abstract fun clear()
    abstract fun withColor(s: String, c: Int): String
    abstract fun withBackgroundColor(s: String, c: Int): String
    abstract val width: Int
    abstract val height: Int

    class Posix : Term() {
        companion object { const val TTY = "/usr/bin/stty -F /dev/tty" }
        override fun init() {
            execAndOut("tput init")
            execAndOut("/usr/bin/stty -F /dev/tty cbreak min 1")
            execAndOut("/usr/bin/stty -F /dev/tty -echo")
        }
        override fun restore() { execAndOut("$TTY echo") }
        override fun clear() { println("\u001b[H\u001b[2J") }
        override val width get() = execAndOut("$TTY size").trim().split(" ")[1].toInt()
        override val height get() = execAndOut("$TTY size").trim().split(" ")[0].toInt()
        override fun withColor(s: String, c: Int) = "\u001b[38;5;${c % 255}m$s\u001B[0m"
        override fun withBackgroundColor(s: String, c: Int) = "\u001b[48;5;${c % 255}m$s\u001B[0m"
    }
}

class AppContext {
    var offset = 0 // live var
    var bitsPerPixel = 4
    var rowWidth = 32
    var limitRows = Int.MAX_VALUE // but how many for export? to take snapshots....
    // ruler
    var skipEmptyRows = false
    var export = false
    var exit = false
}

const val ESC = 0x1b
const val CSI = 0x5b
const val CSI_CURSOR_UP = 'A'.code
const val CSI_CURSOR_DOWN = 'B'.code
const val CSI_CURSOR_FORWARD = 'C'.code
const val CSI_CURSOR_BACK = 'D'.code
const val KEY_PLUS = '+'.code
const val KEY_MINUS = '-'.code
const val KEY_SLASH = '/'.code
const val KEY_ASTERIX = '*'.code

enum class BitsPerPixel(val bits: Int) {
    ONE(1), TWO(2), FOUR(4), EIGHT(8)
    // full color? TBD
}

sealed class Command( val match: List<Int>, private val command: AppContext.() -> Unit ) {
    constructor(code: Int, command: AppContext.() -> Unit) : this(listOf(code), command)
    private var matched = 0
    fun accept(ctx: AppContext, char: Int): Boolean {
        if ( char == match[matched] ) {
            matched ++
        } else {
            matched == 0
            return false
        }

        if ( matched >= match.size ) {
            //match
            command.invoke(ctx)
            matched = 0
        }
        return true // part match and full match
    }

    object MoveLeft : Command(listOf(ESC, CSI, CSI_CURSOR_BACK), {this.offset -= 1 })
    object MoveRight : Command(listOf(ESC, CSI, CSI_CURSOR_FORWARD), {this.offset += 1 })
    object MoveUp : Command(listOf(ESC, CSI, CSI_CURSOR_UP), {this.offset -= this.rowWidth * bitsPerPixel / 8 })
    object MoveDown : Command(listOf(ESC, CSI, CSI_CURSOR_DOWN), {this.offset += this.rowWidth * bitsPerPixel / 8 })
    object BitsLess : Command(KEY_SLASH, {
        val v = BitsPerPixel.values().indexOfFirst { it.bits == bitsPerPixel }
        bitsPerPixel = BitsPerPixel.values()[ (v - 1).mod( BitsPerPixel.values().size  ) ].bits
    } )
    object BitsMore : Command(KEY_ASTERIX, {
        val v = BitsPerPixel.values().indexOfFirst { it.bits == bitsPerPixel }
        bitsPerPixel = BitsPerPixel.values()[ (v + 1).mod( BitsPerPixel.values().size  ) ].bits
    } )

    object WidthLess : Command(KEY_MINUS, {rowWidth -= 1})
    object WidthMore : Command(KEY_PLUS, {rowWidth += 1})
//    object PageUp : Command(listOf(ESC, CSI, CSI_CURSOR_DOWN), {this.offset += this.rowWidth * bitsPerPixel / 8 })
//    object PageDown : Command(listOf(ESC, CSI, CSI_CURSOR_DOWN), {this.offset += this.rowWidth * bitsPerPixel / 8 })
//    object PageUp : Command(listOf(ESC))
    object Exit : Command(listOf(ESC), command = { this.exit = true })
}

const val lblColor = 14
const val valColor = 2
fun Term.labeledValue(l: String, v: String) {
    print(withColor(" $l: ", lblColor))
    print(withColor(v, valColor))
}

fun redraw(term: Term, opts: AppContext, f: File) {

    //validate
    val fl = f.length().toInt() // hope files not too much
    if ( opts.offset >= fl ) opts.offset -= fl
    if ( opts.offset < 0 ) opts.offset += fl

    if ( opts.rowWidth < 0 ) opts.rowWidth += term.width
    if ( opts.rowWidth >= fl ) opts.rowWidth -= fl

    with(term) {
        clear()
        labeledValue("offset", opts.offset.toString().padStart(8, '0'))
        labeledValue("bits per pixel", opts.bitsPerPixel.toString())
        labeledValue("row width", opts.rowWidth.toString())
    }

    println()

    val maxRows = min( opts.limitRows, term.height - 2 )
//    println(maxRows)
    val reader = f.inputStream()
    reader.skip(opts.offset.toLong())
//println(f.readBytes().joinToString(", ") { it.toUByte().toString(2).padStart(8, '0') } )

    val redPixels = reader.printSprite(
        opts.bitsPerPixel, opts.rowWidth, maxRows, opts.skipEmptyRows, opts.export
    )
//    println("new offset = 0x${(opts.offset + (redPixels * opts.bitsPerPixel / 8)).toString(16)}")
    print( term.withColor( term.withBackgroundColor(" PGUP, PGDN ", 7), 0) )
    print( term.withColor("change offset by -1 page, +1 page", 7) )
}

@kotlin.ExperimentalUnsignedTypes
fun main(args: Array<String>) {
    println(args.joinToString(", "))
    if (args.isEmpty()) {
        println("Usage: pixelHunter <file> <sprite width> <bit per pixel> <offset> <limit rows> <skip empty rows> <export>")
        exitProcess(-1)
    }

    val f = File(args[0].toString())

    // batch mode
    if ( args.size > 1 && args[1] == "mark" ) {
        val mark = ByteArray(2) { listOf(0x0b, 0x03)[it].toByte() }
        //check
        f.parse(mark, 2, 12, 11)
        exitProcess(0)
//
//        val b = f.readBytes()
//        val c = b.filterIndexed { i, c -> c == 0x03.toByte() && b[i-1] == 0x0b.toByte() }.count()
//        println(c)
//
//        reader.readAll(mark)
//        exitProcess(0)
    }

    val opts = AppContext().apply {
        rowWidth = try { Integer.decode(args[1]) } catch (e: Exception) {32} // in "pixels"
        bitsPerPixel = if ( args.size > 2) Integer.decode(args[2]) else 4 //
        offset = if ( args.size > 3 ) Integer.decode(args[3]) else 0
        limitRows = if ( args.size > 4 ) Integer.decode(args[4]) else Int.MAX_VALUE
        skipEmptyRows = if ( args.size > 5 ) args[5].toBoolean() else false
        export = if ( args.size > 6 ) args[6].toBoolean() else false
    }

    val term = Term.Posix()
    term.init()
    Runtime.getRuntime().addShutdownHook(object : Thread() { override fun run() = term.restore() })

    val commands = listOf(
        Command.MoveLeft, Command.MoveRight, Command.MoveUp, Command.MoveDown,
        Command.BitsLess, Command.BitsMore,
        Command.WidthLess, Command.WidthMore
    )

    var redraw = true
    java.lang.System.console()?.reader()?.run {
        while( !opts.exit ) {
            if ( redraw ) { redraw( term, opts, f ); redraw = false }

            val r = read()
            println("code=${r.toString(16)} (${r.toChar()})")
            if ( commands.none { it.accept(opts, r) } ) {
                Command.Exit.accept(opts, r)
            } else {
                redraw = true
            }
        }
    }

    term.restore()
    exitProcess(0)
//    return 0
}