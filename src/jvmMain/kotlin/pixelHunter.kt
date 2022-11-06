package me.az
// sudo aptitude install coreutils
import java.io.File
import java.io.InputStream
import java.io.PrintWriter
import java.lang.Exception
import java.nio.charset.StandardCharsets
import kotlin.math.*
import kotlin.system.exitProcess

const val headerHeight = 1
const val footerHeight = 2
const val columnsPad = 3

const val lblColor = 14
const val valColor = 2
var verbose = false
// kotlinc pixelHunter.kts -include-runtime -d pixelHunter.jar

class AppContext {
    val calcColCount get() = (termWidth + columnsPad) / (rowWidth + columnsPad)

    var termWidth = 0 // cache
    var offset = 0 // live var
    var bitsPerPixel = 4
    var rowWidth = 32
    var limitRows = Int.MAX_VALUE // but how many for export? to take snapshots....
    // ruler
    var skipEmptyRows = false
    var export = false
    var exit = false
}

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

@ExperimentalUnsignedTypes
fun List<UByte>.pixelStream(
    bitsPerPixel: Int = 2
) = sequence {

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
) = sequence {
    val windowBuff = ByteArray(bytesWindow)
    val mask = (2f.pow( bitsPerPixel ) - 1).toInt()
    if ( verbose ) println("mask = ${mask.toString(2).padStart(8, '0')} ")
    var currentByte = Int.MAX_VALUE
    var currentBit = 0
    var red = Int.MAX_VALUE
    var longRes = 0
    var resPos = 0

    while( red > 0 ) {

        if ( currentByte >= bytesWindow ) {
            //read  next chunk
            red = read(windowBuff)
            if ( red <= 0 ) break
            if ( verbose ) println("$bytesWindow WINDOW READ= ${windowBuff.joinToString(", ") { it.toUByte().toString(2).padStart(8, '0') } }")
            currentByte = 0
        }

        val curByte = windowBuff[currentByte].toInt()
        val bitsTake = min(8 - currentBit, bitsPerPixel - resPos )
        val rightShift = 8 - (currentBit + bitsTake)
        // normalize
        val normalizedPart = (curByte ushr rightShift) and (2f.pow( bitsTake ) - 1).toInt()
        val leftShift = bitsPerPixel - resPos - bitsTake
        val resPart = normalizedPart shl leftShift
        longRes = longRes or resPart
        resPos += bitsTake
        //
        if ( verbose ) {
            println(
                "bytePos=$currentByte bitPos=$currentBit code=${windowBuff[currentByte].toUByte().toString(16)} " +
                " ${windowBuff[currentByte].toUByte().toString(2).padStart(8, '0')}" +
                "\tres = $longRes"
            )
        }

        if ( resPos >= bitsPerPixel ) {
            yield(longRes)
            longRes = 0
            resPos = 0
        }
//
        currentBit += bitsTake
        if ( currentBit >= 8 ) {
            currentByte ++
            currentBit = 0
        }
    }
}

sealed class SpritePrinter {
    abstract var printedRows: Int
    abstract var redPixels: Int
    abstract fun visitRow(y: Int, row: List<Int>)

    class TermPrinter(
        private val term: Term,
        private val skipEmptyRows: Boolean = false,
        private val col: Int = 0) : SpritePrinter() {

        override var printedRows = 0
        override var redPixels = 0

        override fun visitRow(y: Int, row: List<Int>) {
            val rowStr = row.map {
                if ( it == 0 ) "\u001b[0m "
                else "\u001b[48;5;${it % 255}m "
            }.joinToString("")

            redPixels += row.size

            if ( rowStr.trim().isEmpty() && skipEmptyRows ) {
                return
            }

            if ( col > 0 ) term.move(col, printedRows + headerHeight)
            println("$rowStr\u001b[0m")
            printedRows ++
        }
    }

    class ImageMagickExporter(private val out: PrintWriter, width: Int, height: Int,
                              private val palette: Map<Int, String>): SpritePrinter() {
        override var printedRows: Int = 0
        override var redPixels: Int = 0

        init {
            out.println("# ImageMagick pixel enumeration: $width,$height,255,rgb")
        }

        override fun visitRow(y: Int, row: List<Int>) {
            row.forEachIndexed { x, p ->
                out.println("$x,$y: (${palette[p]})")
            }
            redPixels + row.size
            printedRows ++
        }
    }
}

// returns pixels red
fun InputStream.printSprite(
    printer: SpritePrinter,
    bitsPerPixel: Int = 2,
    width: Int = 12,
    height: Int = 11,
): Int {
    val stream = pixelsStream(
        bytesWindow = 8.lcm( bitsPerPixel * width ) / 8,
        bitsPerPixel = bitsPerPixel
    )

    // BYTE ORDER????

    stream.takeWhile { printer.printedRows < height }.chunked(width).forEachIndexed { y, row ->
        printer.visitRow(y, row)
    }
    return printer.redPixels
}
val palettes = mapOf(
    BitsPerPixel.ONE.bits to mapOf(
        0 to "0, 0, 0",
        1 to "255, 255, 255"
    ),
    BitsPerPixel.FOUR.bits to mapOf(
        0 to "0, 0, 0",
        1 to "0, 170, 170",
        2 to "170, 0, 170",
        3 to "170, 170, 170",
    )
)

@ExperimentalUnsignedTypes
fun File.dumpAppleImages(mark: ByteArray, bitsPerPixel: Int = 2, width: Int = 12, height: Int = 11, export: Boolean = false) {

    // apple dump
    var p = 0
    val b = readBytes().asUByteArray()
    val m1 = 0x0b.toUByte()
    val m2 = 0x03.toUByte()

    for ( i in 0 until b.size - 1 ) {
        if (b[i] == m1 && b[i+1] == m2) {
            val px = b.slice( i + 2 until i + 2 + (width * height * bitsPerPixel / 8) )
                .pixelStream(bitsPerPixel)

//            val channels = Array(cga.size) { mutableListOf<Pair<Int, Int>>() }

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
                       out.println("$x,$y: (${palettes[bitsPerPixel]!![p]})")
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

fun File.export(offset: Int, count: Int, bitsPerPixel: Int = 2, width: Int = 12, height: Int = 11) {
    val bytesPerRow = 8.lcm( bitsPerPixel * width ) / 8

    for( i in 0 until count) {
        val reader = inputStream()
        reader.skip((offset + i * bytesPerRow * height).toLong())

        val t = "${offset.toString(16)}.${(i+1).toString(10).padStart(3, '0')}"
        val f = File("$t.txt")

        f.printWriter().use {
            val exporter = SpritePrinter.ImageMagickExporter(it, width, height,palettes[bitsPerPixel]!!)
            reader.printSprite(
                exporter, bitsPerPixel, width, height
            )
        }
        execAndOut("convert -transparent black txt:$t.txt -define png:color-type=3 -define png:bit-depth=$bitsPerPixel -quality 100 $t.png")
    }
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
    abstract fun move(x: Int, y: Int): Unit
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
        override fun clear() { print("\u001b[H\u001b[2J") }
        override val width get() = execAndOut("$TTY size").trim().split(" ")[1].toInt()
        override val height get() = execAndOut("$TTY size").trim().split(" ")[0].toInt()
        override fun withColor(s: String, c: Int) = "\u001b[38;5;${c % 255}m$s\u001B[0m"
        override fun withBackgroundColor(s: String, c: Int) = "\u001b[48;5;${c % 255}m$s\u001B[0m"
        override fun move(x: Int, y: Int) = print("\u001B[${y+1};${x+1}H")
    }
}

enum class BitsPerPixel(val bits: Int) {
    ONE(1), TWO(2), THREE(3),  FOUR(4), SIX(6), EIGHT(8),
    NINE(9),
    TWELVE(12),
    FIFTEEN(15),
    SIXTEEN(16),
    EIGHTEEN(18),
    RGB24(24),
    RGB30(30)
    // full color? TBD
}

enum class MatchResult { False, Maybe, True }

sealed class Command( val match: List<Int>, private val command: AppContext.() -> Unit ) {
    constructor(code: Int, command: AppContext.() -> Unit) : this(listOf(code), command)
    private var matched = 0
    fun accept(ctx: AppContext, char: Int): MatchResult {
        if ( char == match[matched] ) {
            matched ++
        } else {
            matched == 0
            return MatchResult.False
        }

        if ( matched >= match.size ) {
            //match
            command.invoke(ctx)
            matched = 0
            return MatchResult.True
        }
        return MatchResult.Maybe
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
    class PageUp(private val term: Term) : Command(listOf(ESC) + "[5~".map { it.code }, {
        val rowsPass = min( limitRows, term.height - 2 )
        this.offset -= calcColCount * rowsPass * this.rowWidth * bitsPerPixel / 8
    })
    class PageDown(private val term: Term) : Command(listOf(ESC) + "[6~".map { it.code }, {
        val rowsPass = min( limitRows, term.height - 2 )
        this.offset += calcColCount * rowsPass * this.rowWidth * bitsPerPixel / 8
    })
    object ScrollStart : Command(listOf(ESC) + "[H~".map { it.code }, { offset = 0 })
    class ScrollEnd(term: Term) : Command(listOf(ESC) + "[F~".map { it.code }, {
        offset = -(term.height -2) * this.rowWidth * bitsPerPixel / 8
    })

    object Exit : Command(listOf(ESC), command = { this.exit = true })
}

fun Term.labeledValue(l: String, v: String) {
    print(withColor(" $l: ", lblColor))
    print(withColor(v, valColor))
}

fun redraw(term: Term, opts: AppContext, f: File) {

    val fl = f.length().toInt() // hope files not too much
    val h = term.height
    opts.termWidth = term.width
    val w = opts.termWidth

    if ( opts.offset >= fl ) opts.offset -= fl
    if ( opts.offset < 0 ) opts.offset += fl

    if ( opts.rowWidth < 1 ) opts.rowWidth = 1
    if ( opts.rowWidth > fl ) opts.rowWidth = fl

    with(term) {
        clear()
        labeledValue("offset", opts.offset.toString().padStart(8, '0'))
        val p = (opts.offset * 100 / fl).toInt()
        print("($p%)")
        labeledValue("bits per pixel", opts.bitsPerPixel.toString())
        labeledValue("row width", opts.rowWidth.toString())
    }

    println()

    val maxRows = min( opts.limitRows, h - headerHeight - footerHeight )
    val reader = f.inputStream()
    reader.skip(opts.offset.toLong())
    val cols = opts.calcColCount

//println(f.readBytes().joinToString(", ") { it.toUByte().toString(2).padStart(8, '0') } )
    var redPixels = 0
    for ( c in 0 until cols ) {
        redPixels += reader.printSprite(
            SpritePrinter.TermPrinter(term, opts.skipEmptyRows, c * (opts.rowWidth + columnsPad)),
            opts.bitsPerPixel, opts.rowWidth, maxRows
        )
    }

    val labeledHelp = fun(keys: String, lbl: String) {
        print( term.withColor( term.withBackgroundColor("$keys", 7), 0) )
        print( term.withColor(" $lbl ", 7) )
    }
    term.move(0, h - footerHeight)
    labeledHelp("ARROWS", "move offset by +-1 col or row")
    labeledHelp("/ *", "change bits per pixel")
    labeledHelp("- +", "change row width")
    labeledHelp("PGUP PGDN", "move offset by -1 page, +1 page")
    labeledHelp("HOME END", "move offset to start or end")
    labeledHelp("ESC", "exit")
    term.move(0, 0)
}

@ExperimentalUnsignedTypes
fun main(args: Array<String>) {
//    println(args.joinToString(", "))

    if (args.isEmpty()) {
        println("Usage: pixelHunter <file> <sprite width> <bit per pixel> <offset> <limit rows> <skip empty rows> <export>")
        exitProcess(-1)
    }

    if ( args[0] == "test") {
        tests()
        exitProcess(0)
    }

    val f = File(args[0])

    // batch mode
    if ( args.size > 1 ) {
        when(args[1]) {
            "mark" -> {
                val mark = ByteArray(2) { listOf(0x0b, 0x03)[it].toByte() }
                //check
                f.dumpAppleImages(mark, 2, 12, 11)
                exitProcess(0)
            }
            "export" -> {
                try {
                    f.export(
                        Integer.decode(args[2]),
                        Integer.decode(args[3]),
                        Integer.decode(args[4]),
                        Integer.decode(args[5]),
                        Integer.decode(args[6]),
                    )
                    exitProcess(0)
                } catch (e: Throwable) {
                    println("Error: $e")
                    println("Usage: pixelHunter export <offset> <count of sprites> <bitsPerPixel> <sprite width> <sprite height>")
                    e.printStackTrace()
                    exitProcess(-1)
                }
            }
        }
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
        Command.WidthLess, Command.WidthMore,
        Command.PageUp(term), Command.PageDown(term),
        Command.ScrollStart, Command.ScrollEnd(term)
    )

    var redraw = true
    System.console()?.reader()?.run {
        while( !opts.exit ) {
            if ( redraw ) { redraw( term, opts, f ); redraw = false }

            val r = read()
//            println("code=${r.toString(16)} (${r.toChar()})")

            val results = commands.map { it.accept(opts, r) }
            if ( results.all { MatchResult.False == it } ) {
                Command.Exit.accept(opts, r)
            } else {
                if ( results.any { it == MatchResult.True } )
                    redraw = true
            }
        }
    }

    term.restore()
    println()
    exitProcess(0)
//    return 0
}

@OptIn(ExperimentalUnsignedTypes::class)
fun tests() {

    fun UByteArray.inputStream() = String(toByteArray(), Charsets.ISO_8859_1).byteInputStream(Charsets.ISO_8859_1)
//    fun print(vararg a: UByte) = ubyteArrayOf(*a).inputStream().
    val term = Term.Posix()

    ubyteArrayOf(0x5du, 0x00u).inputStream().printSprite(SpritePrinter.TermPrinter(term), 1, 3, 3)
    println()
    ubyteArrayOf(0x1bu, 0x6fu, 0xbcu, 0xf0u).inputStream().printSprite(SpritePrinter.TermPrinter(term), 2, 4, 4)
    println()
    ubyteArrayOf(0x01u, 0x23u, 0x45u, 0x67u, 0x89u, 0xabu, 0xcdu, 0xefu).inputStream().printSprite(SpritePrinter.TermPrinter(term),
        4, 4, 4
    )
    println()

    val d = ubyteArrayOf(
        0x24u, 0x92u, 0x49u,
        0x60u, 0x00u, 0x03u,
        0x64u, 0x12u, 0x4bu,
        0xe3u, 0x8eu, 0x38u,
        0x1cu, 0x71u, 0xc7u)

    d.inputStream().printSprite(SpritePrinter.TermPrinter(term), 3, 8, 5)
    println()
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
