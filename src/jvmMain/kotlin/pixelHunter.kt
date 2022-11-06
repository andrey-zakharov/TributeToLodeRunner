package me.az
// sudo aptitude install coreutils
// color rgb term required for searching
// export require imagemagick 'convert'
//
//  ██████╗ ██╗██╗  ██╗███████╗██╗     ██╗  ██╗██╗   ██╗███╗   ██╗████████╗███████╗██████╗
//  ██╔══██╗██║╚██╗██╔╝██╔════╝██║     ██║  ██║██║   ██║████╗  ██║╚══██╔══╝██╔════╝██╔══██╗
//  ██████╔╝██║ ╚███╔╝ █████╗  ██║     ███████║██║   ██║██╔██╗ ██║   ██║   █████╗  ██████╔╝
//  ██╔═══╝ ██║ ██╔██╗ ██╔══╝  ██║     ██╔══██║██║   ██║██║╚██╗██║   ██║   ██╔══╝  ██╔══██╗
//  ██║     ██║██╔╝ ██╗███████╗███████╗██║  ██║╚██████╔╝██║ ╚████║   ██║   ███████╗██║  ██║
//  ╚═╝     ╚═╝╚═╝  ╚═╝╚══════╝╚══════╝╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═══╝   ╚═╝   ╚══════╝╚═╝  ╚═╝
//
// compile: kotlinc pixelHunter.kts -include-runtime -d pixelHunter.jar
// run: java -cp pixelHunter.jar:kotlin-stdlib.jar me.az.PixelHunterKt $* (tool ./run.sh)
//
// Tool to search, analyse and export 1, 2, 4, etc.-bit sprites from old-old games for Apple ][, NEC series, IBM, etc..
// Better results could be archived, when converting floppy or hdd image to "raw" data binary image without any
// sectors marks, etc.
// Usage (subject of change): pixelHunter <file> <sprite width> <bit per pixel> <offset> <limit rows> <skip empty rows>
// keyboard
// 2022 by Andrey Zakharov /aazaharov81+pixelhunter@gmail.com/, CC BY-NC-SA 4.0
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.system.exitProcess

const val headerHeight = 1
const val footerHeight = 2
const val columnsPad = 3

const val lblColor = 14
const val valColor = 2
var verbose = false

class AppContext {
    val calcColCount get() = (termWidth + columnsPad) / (rowWidth + columnsPad)

    var termWidth = 0 // cache
    var offset = 0 // live var
    var bitsPerPixel = BitsPerPixel.FOUR
    var rowWidth = 32
    var limitRows = Int.MAX_VALUE // but how many for export? to take snapshots....
    // ruler
    var skipEmptyRows = false
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

// parse bytes to pixels bypass "bits" phase.
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

// or we could just make bits stream, and manipulate it by sequence operators
fun InputStream.bitsStream() = sequence {
    val windowBuff = ByteArray(1)
    var currentByte = Int.MAX_VALUE
    var currentBit = 0

    while (true) {
        if (currentByte >= windowBuff.size) {
            //read  next chunk
            if (read(windowBuff) <= 0) break
//            if ( verbose )
//                println("$bytesWindow WINDOW READ= ${windowBuff.joinToString(", ") { it.toUByte().toString(2).padStart(8, '0') } }")
            currentByte = 0
        }

        val bit = (windowBuff[currentByte].toInt() ushr (8 - currentBit - 1)) and 0x01
        yield(bit == 1)

        currentBit ++

        if (currentBit >= 8) {
            currentByte++
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
        private val pixelTermExport: (v: Int) -> String = BitsPerPixel.ONE::exportTerm,
        private val col: Int = 0
    ) : SpritePrinter() {

        override var printedRows = 0
        override var redPixels = 0

        override fun visitRow(y: Int, row: List<Int>) {
            val rowStr = row.joinToString("", transform = pixelTermExport)

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
                              private val palette: (Int) -> String): SpritePrinter() {
        override var printedRows: Int = 0
        override var redPixels: Int = 0

        init {
            out.println("# ImageMagick pixel enumeration: $width,$height,255,rgb")
        }

        override fun visitRow(y: Int, row: List<Int>) {
            row.forEachIndexed { x, p ->
                out.println("$x,$y: (${palette(p)})")
            }
            redPixels + row.size
            printedRows ++
        }
    }
}

val List<Boolean>.int get() = foldIndexed(0) { i, acc, v -> acc or (v.int shl (size - i - 1)) }
val Boolean.int get() = if ( this ) 1 else 0

// returns pixels red
fun InputStream.printSprite(
    printer: SpritePrinter,
    bitsPerPixel: BitsPerPixel = BitsPerPixel.TWO,
    width: Int = 12,
    height: Int = 11,
): Int {

    if ( bitsPerPixel == BitsPerPixel.RGB1 ) {
        sequence {
            val bitsPerChannel = 1
            bitsStream().takeWhile { printer.printedRows < height }.chunked(8).chunked(3).forEach {
//                println(it.joinToString { it.joinToString("") { it.int.toString(2).padStart(2, '0') } })
                for( px in 0 until 8) {
                    val r = it[0][px].int shl (bitsPerChannel * 2)
                    val g = it[1][px].int shl (bitsPerChannel)
                    val b = it[2][px].int
//                    println("px=$px r=$r b=$b g=$g val=${r or g or b}")
                    yield( r or g or b)
                }
            }
        }.chunked(width).forEachIndexed { y, row ->
            printer.visitRow(y, row)
        }
        return 0
    } else {

        val stream = pixelsStream(
            bytesWindow = 8.lcm(bitsPerPixel.bits * width) / 8,
            bitsPerPixel = bitsPerPixel.bits
        )

        // BYTE ORDER????

        stream.takeWhile { printer.printedRows < height }.chunked(width).forEachIndexed { y, row ->
            printer.visitRow(y, row)
        }
        return printer.redPixels
    }
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

// export to png
fun InputStream.exportSprite(baseName: String, bitsPerPixel: BitsPerPixel = BitsPerPixel.TWO, width: Int = 12, height: Int = 11) {
    File("$baseName.txt").printWriter().use {
        val exporter = SpritePrinter.ImageMagickExporter(it, width, height, bitsPerPixel::exportRgb)
        printSprite(exporter, bitsPerPixel, width, height)
    }
    execAndOut("convert -transparent black txt:$baseName.txt -define png:color-type=3 -define png:bit-depth=${bitsPerPixel.bits} -quality 100 $baseName.png")
    if ( !verbose ) execAndOut("rm $baseName.txt")
}

fun File.dumpByMark(mark: ByteArray, bitsPerPixel: BitsPerPixel, width: Int = 12, height: Int = 11, dryRun: Boolean = false) {
    val b = readBytes()
    var foundSprites = 0

    for ( i in 0 until b.size - mark.size ) {
        val marked = mark.foldIndexed(true) { index, res, byte -> res && byte == b[i + index] }
        if ( marked ) {
            val data = b.sliceArray( i + mark.size until i + mark.size + (width * height * bitsPerPixel.bits / 8) )
            val t = "${(i + mark.size).toString(16)}.${(foundSprites+1).toString(10).padStart(3, '0')}"

            if ( !dryRun ) {
                ByteArrayInputStream(data).exportSprite(t, bitsPerPixel, width, height)
            } else {
                println("\u001B[0m = Sprite #${foundSprites+1} (${(i + mark.size).toString(16)}) =")
                ByteArrayInputStream(data).printSprite(
                    SpritePrinter.TermPrinter(Term.Posix(), pixelTermExport = bitsPerPixel::exportTerm), bitsPerPixel, width, height
                )
            }
            foundSprites ++
        }
    }

    println("Exported $foundSprites sprites")
}

/// NEC
// rgb1 mark = 0x0a 0x03 width=24 height = 10

/// dumpAppleImages mark: ByteArray = 0x0b 0x03, bitsPerPixel = 2, width = 12, height = 11
// for t in *.txt; do convert -transparent black txt:$t -define png:color-type=3 -define png:bit-depth=4 -quality 100 `basename $t .txt`.png; done
// convert -transparent black txt:ddc0.txt ddc0.png
// montage cf98.png cfbb.png cfde.png ca0d.png d06a.png d08d.png ccff.png cd22.png cd45.png cd68.png cd8b.png cdae.png cdae.left.png cdae.right.png cdd1.png cdd1.left.png cdd1.rigth.png cdf4.png ce17.png ce3a.png cec6.png cee9.png cf0c.png dc85.png dca8.png  -transparent white -geometry +0 -quality 100 out.png
// montage c8cf.png c93b.png c9c7.png c918.png c9ea.png c95e.png c981.png c9a4.png dd11.png dd34.png dd57.png dd7a.png dd9d.png ddc0.png dde3.png de06.png de29.png de4c.png dccb.png dcee.png dc3f.png dc62.png -transparent white -geometry +0 -quality 100 out.png

fun File.export(offset: Int, count: Int, bitsPerPixel: BitsPerPixel = BitsPerPixel.TWO, width: Int = 12, height: Int = 11) {
    val bytesPerRow = 8.lcm( bitsPerPixel.bits * width ) / 8

    for( i in 0 until count) {
        val reader = inputStream()
        reader.skip((offset + i * bytesPerRow * height).toLong())

        val t = "${offset.toString(16)}.${(i+1).toString(10).padStart(3, '0')}"
        reader.exportSprite(t, bitsPerPixel, width, height)
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
    RGB1(3) {
        private fun unpack(v: Int) = listOf (
            if ((v ushr 2) == 1) 255 else 0,
            if ((v ushr 1) and 0x01 == 1) 255 else 0,
            if ((v and 0x01) == 1) 255 else 0
        )
        //channel ordered messed, not r, g, b. looks like b, r, g
        private fun toRgb(v: Int) = with(unpack(v)) { listOf( get(1), get(2), get(0) ) }
        override fun exportRgb(v: Int) =  toRgb(v).joinToString(", ")
        override fun exportTerm(v: Int) = with(toRgb(v)) { "\u001b[48;2;${get(0)};${get(1)};${get(2)} m " }
    }, // 1bit per pixel over 3 bytes
    RGB24(24),
    RGB30(30)
    // full color? TBD
    ;
    open fun exportTerm(v: Int) = if ( v == 0 ) "\u001b[0m " else "\u001b[48;5;${v % 255}m "
    open fun exportRgb(v: Int) = palettes[FOUR.bits]!![v % FOUR.bits]!!
    companion object {
        fun decodeArg(a: String): BitsPerPixel {
            val bits = a.toIntOrNull()
            return if ( bits != null ) {
                BitsPerPixel.values().first { it.bits == bits }
            } else {
                BitsPerPixel.valueOf(a.uppercase())
            }
        }
    }
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
    object MoveUp : Command(listOf(ESC, CSI, CSI_CURSOR_UP), {this.offset -= this.rowWidth * bitsPerPixel.bits / 8 })
    object MoveDown : Command(listOf(ESC, CSI, CSI_CURSOR_DOWN), {this.offset += this.rowWidth * bitsPerPixel.bits / 8 })
    object BitsLess : Command(KEY_SLASH, {
        val v = bitsPerPixel.ordinal
        bitsPerPixel = BitsPerPixel.values()[ (v - 1).mod( BitsPerPixel.values().size  ) ]
    } )
    object BitsMore : Command(KEY_ASTERIX, {
        val v = bitsPerPixel.ordinal
        bitsPerPixel = BitsPerPixel.values()[ (v + 1).mod( BitsPerPixel.values().size  ) ]
    } )

    object WidthLess : Command(KEY_MINUS, {rowWidth -= 1})
    object WidthMore : Command(KEY_PLUS, {rowWidth += 1})
    class PageUp(private val term: Term) : Command(listOf(ESC) + "[5~".map { it.code }, {
        val rowsPass = min( limitRows, term.height - 2 )
        this.offset -= calcColCount * rowsPass * this.rowWidth * bitsPerPixel.bits / 8
    })
    class PageDown(private val term: Term) : Command(listOf(ESC) + "[6~".map { it.code }, {
        val rowsPass = min( limitRows, term.height - 2 )
        this.offset += calcColCount * rowsPass * this.rowWidth * bitsPerPixel.bits / 8
    })
    object ScrollStart : Command(listOf(ESC) + "[H~".map { it.code }, { offset = 0 })
    class ScrollEnd(term: Term) : Command(listOf(ESC) + "[F~".map { it.code }, {
        offset = -(term.height -2) * this.rowWidth * bitsPerPixel.bits / 8
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
            SpritePrinter.TermPrinter(term, opts.skipEmptyRows, opts.bitsPerPixel::exportTerm, col = c * (opts.rowWidth + columnsPad)),
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
        println("Usage: pixelHunter <file> <sprite width> <bit per pixel> <offset> <limit rows> <skip empty rows>")
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
                try {
                    val mark = ByteArray(ceil(args[2].length / 2f).toInt()) { i->
                        args[2].substring(i*2, i*2+2).toInt(16).toByte()
                    }
//                val mark = ByteArray(2) { listOf(0x0b, 0x03)[it].toByte() }
                    //check
//                f.dumpAppleImages(mark, 2, 12, 11)
                    f.dumpByMark(mark, BitsPerPixel.RGB1, 24, 10)
                    exitProcess(0)
                } catch (e: Throwable) {
                    println("Error: $e")
                    println("Usage: pixelHunter mark <mark as hex string e.g. 0b03> <offset> <bitsPerPixel> <sprite width> <sprite height>")
                    e.printStackTrace()
                    exitProcess(-1)
                }

            }

            "export" -> {
                try {

                    f.export(
                        Integer.decode(args[2]),
                        Integer.decode(args[3]),
                        BitsPerPixel.decodeArg(args[4]),
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
        bitsPerPixel = if ( args.size > 2) BitsPerPixel.decodeArg(args[2]) else BitsPerPixel.FOUR //
        offset = if ( args.size > 3 ) Integer.decode(args[3]) else 0
        limitRows = if ( args.size > 4 ) Integer.decode(args[4]) else Int.MAX_VALUE
        skipEmptyRows = if ( args.size > 5 ) args[5].toBoolean() else false
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

    ubyteArrayOf(0x5du, 0x00u).inputStream().printSprite(SpritePrinter.TermPrinter(term), BitsPerPixel.ONE, 3, 3)
    println()
    ubyteArrayOf(0x1bu, 0x6fu, 0xbcu, 0xf0u).inputStream().printSprite(SpritePrinter.TermPrinter(term), BitsPerPixel.TWO, 4, 4)
    println()
    ubyteArrayOf(0x01u, 0x23u, 0x45u, 0x67u, 0x89u, 0xabu, 0xcdu, 0xefu).inputStream().printSprite(SpritePrinter.TermPrinter(term),
        BitsPerPixel.FOUR, 4, 4
    )
    println()

    val d = ubyteArrayOf(
        0x24u, 0x92u, 0x49u,
        0x60u, 0x00u, 0x03u,
        0x64u, 0x12u, 0x4bu,
        0xe3u, 0x8eu, 0x38u,
        0x1cu, 0x71u, 0xc7u)

    d.inputStream().printSprite(SpritePrinter.TermPrinter(term), BitsPerPixel.THREE, 8, 5)
    println()
    expect { listOf(true, false, true).int.toString(2) == "101" }
    expect { listOf(true, true, true, false, false, true, true, false).int.toString(2) == "11100110" }
}

internal fun expect(v: () -> Boolean) = if ( !v() ) throw AssertionError("expect") else Unit
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
