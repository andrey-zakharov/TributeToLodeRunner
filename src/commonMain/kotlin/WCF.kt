import de.fabmax.kool.math.Vec2i
import me.az.ilode.Tile
import me.az.utils.choice
import me.az.utils.plus
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.ExperimentalTime

// reversed = (i + total/2) % total
fun neighboursNxN2D(n: Int): List<Int> {
    val res = mutableListOf<Int>()

    (-n until n).forEach { y ->
        (-n until n).filterNot { x -> x == 0 && y == 0 }
            .forEach { x ->
                res += x
                res += y
            }
    }

    return res.toList()
}

val neighbours9x9 = neighboursNxN2D(4)
val neighbours7x7 = neighboursNxN2D(3)
val neighbours5x5 = neighboursNxN2D(2)

val neighboursMoor2D = listOf(
    -1, -1, 0, -1, 1, -1,
    -1,  0,        1,  0,
    -1,  1, 0,  1, 1,  1
)

// neumann manhattan dist <= 2
val neighboursStar2d = listOf(0, 2, 2, 0, 0, -2, -2, 0, 0 , 1, 1, 0, 0, -1, -1, 0)

// up, right, down, left
val neighboursNeumann2d = listOf(0, 1, 1, 0, 0, -1, -1, 0)

val Neighbours = neighbours7x7
val dimSize = ceil(Neighbours.size / 2f).toInt()
// in finite field
fun List<Int>.of(x: Vec2i, width: Int, height: Int) =
    asSequence().chunked(2).mapIndexed { index, (dx, dy) ->
        Pair(index, Vec2i(x.x + dx, x.y + dy))
    }.filter { (_, it) ->
        it.x in 0 until width && it.y in 0 until height
    }

interface BinaryConstrains<V, R> {
    operator fun get(a: V, b: V, dir: Int): R
}

fun Vec2i.neighbours(width: Int, height: Int): Sequence<Pair<Int, Vec2i>> =
    Neighbours.of(this, width, height)

class Probabilities<T: Comparable<T>>(initial: List<String>/*, n: Int = 1*/): BinaryConstrains<T, Int> {
    //    val totalArcs: Int
    val sum: MutableMap<T, MutableMap<Int, MutableMap<T, Int>>> = mutableMapOf()

    init {
        require( initial.isNotEmpty() )
        require( initial.first().isNotEmpty() )
//        require( initial.size >= n )
//        require( initial.first().length >= n )

        val height = initial.size
        val width = initial.first().length

//        totalArcs = me.az.view.getWidth * (me.az.view.getHeight - 1) + me.az.view.getHeight * (me.az.view.getWidth - 1)
    }

    override operator fun get(a: T, b: T, dir: Int): Int {
        // symmetric here
        return (if ( a < b ) sum[a]?.get(dir)?.get(b) else sum[b]?.get(dir)?.get(a)) ?: 0
    }

    fun incConstrain(a: T, b: T, dir: Int) {
        var norA = a
        var norB = b
        if ( a > b ) norA = b.also { norB = a }
        val r = sum.getOrPut(norA) { mutableMapOf() }.getOrPut(dir) { mutableMapOf() }.getOrPut(norB) { 0 } + 1
        sum[norA]!![dir]!![norB] = r
    }

    override fun toString() = plainPrint()

    private fun plainPrint() = normalizedPrint { sum }

    fun normalizedPrint(mutableMap: () -> Map<T, Map<Int, Map<T, *>>>) =
        mutableMap().map { "${it.key} = \n${it.value.map {
        "   dir #${it.key} = ${it.value}"
    }.joinToString("\n")}" }.joinToString("\n")
}

fun Probabilities<Tile>.load(initial: List<String>) {
    val height = initial.size
    val width = initial.first().length
    // import TileString ->... tbd
    (0 until height).forEach { y ->
        ( 0 until width).forEach { x ->
            val a = Tile.byChar[initial[y][x]]!!

            val nei = Vec2i(x, y).neighbours(width, height)
            for ( (dir, n) in nei ) {
                val b = Tile.byChar[initial[n.y][n.x]]!!
                incConstrain(a, b, dir)
            }
        }
    }
}
fun Probabilities<Tile>.loadOverlapped(initial: List<String>, n: Int) {
    val height = initial.size
    val width = initial.first().length
//    val subTiles = Array((1+me.az.view.getWidth - N) * (1+me.az.view.getHeight - N)) { Array(N*N) { } }
    val subTiles = mutableListOf<Array<Tile>>()

    (0 until height - n).forEach { startY ->
        (0 until width - n).forEach { startX ->
            subTiles += Array(n * n) {idx ->
                val nx = idx % n
                val ny = idx / n

                Tile.byChar[initial[startY + ny][startX + nx]]!!
            }
        }
    }

    val sep = "+" + "-".repeat(n + 2) + "+\n"
    println(
        subTiles.joinToString("\n") { tile ->
            sep +
            tile.asIterable().chunked(n).map { r ->
                "| " + r.map { it.char }.joinToString("") + " |"
            }.joinToString("\n") + "\n" + sep }
    )

    subTiles.forEach {
        load(
            it.asIterable().chunked(n).map { list -> list.map { it.char }.joinToString("") }
        )
    }
}
fun Probabilities<Tile>.normalized() = sum.map { (x, dirs) ->
        x to dirs.map { (dir, sums) ->
            val total = sums.values.sum().toFloat()
            dir to sums.map { it.key to it.value / total }.toMap()
        }.toMap()
    }.toMap()

// collecting probabilities
fun calcRestrictions(initial: List<String>) = Probabilities<Tile>(initial).apply {
    load(initial)
}

fun calcOverlapping(initial: List<String>) = PatternRestrictions<PatternHash>(5).apply {
    //loadOverlapped(initial, 5)
    load(initial)
}

typealias PatternHash = Int
// Int - index of pattern, so its domain value in some variable
class PatternRestrictions<T>(val n: Int, val m: Int = n): BinaryConstrains<T, Int> {

    val patterns = mutableMapOf<PatternHash, Array<Tile>>() // by hash
    private val weights = mutableMapOf<PatternHash, Float>() // by pattern hash

    lateinit var patternIdOfInitial: (x: Int, y:Int) -> PatternHash
    // not array because do not size atm, make it lateinit ?
    // up, down, left , right
    // by hash
    private val sockets = mutableMapOf<Int, Array<MutableSet<Int>>>()//(//Array(neighboursNeumann2d.size / 2) {// how tiles connects to each other

    val uniquePatternsCount get() = patterns.size
    // "propagator"
    fun getSupported(patternId: Int, dir: Int) = sockets[ patternId ]?.get(dir) ?: setOf()

    fun load(initial: List<String>) {

        require( initial.isNotEmpty() )
        require( initial.first().isNotEmpty() )
        val height = initial.size
        val width = initial.first().length
        val patternsHash = IntArray((width-n) * (height-m)) // circular?
        var patternIndex = 0
        // at least
        (0 until height - m).forEach { startY ->
            (0 until width - n).forEach { startX ->
                val newPattern = Array(n * m) {idx ->
                    val nx = idx % n
                    val ny = idx / n

                    Tile.byChar[initial[startY + ny][startX + nx]]!!
                }

//                println("${newPattern.contentHashCode()}\t" + newPattern.joinToString("") { it.char.toString() })
                // hash tile by string?
                val hash = newPattern.contentHashCode()
                patternsHash[patternIndex++] = hash
                if ( !patterns.containsKey(hash) ) {
                    patterns[hash] = newPattern
                }
                val up = weights.getOrPut(hash) { 0f } + 1f
                weights[hash] = up
            }
        }

        val totalDirs = ((2*n - 1) * (2*m - 1) - 1)
        // find sockets for tiles
        // not rotating, not mirroring
        patternsHash.forEachIndexed { index, hash ->
            val x = index % width
            val y = index / width

            ( -n + 1 until n - 1).forEach { dx ->
                ( -m + 1 until m - 1).forEach dy@{ dy ->
                    if ( dx == 0 && dy == 0) return@dy

                    val dirIdx = (dy + m - 1) * n + (dx + n - 1)
                    val connectedPatternIdx = (y + dy) * width + (x + dx)

                    if ( connectedPatternIdx < 0 || connectedPatternIdx >= patternsHash.size) return@dy

                    val connectedHash = patternsHash[connectedPatternIdx]
                    sockets.getOrPut(hash) {
                        Array(totalDirs) { mutableSetOf() }
                    }[dirIdx].add(connectedHash)
                }
            }
        }

        patternIdOfInitial = { x, y ->
            patternsHash[min(x, width-n-1) + min(y, height-m-1) * (width-n)]
        }
    }

    val dirs get() = sequence {
        var dirId = 0
        (-n + 1 until n - 1).forEach { dx ->
            (-m + 1 until m - 1).forEach dy@{ dy ->
                if ( dx == 0 && dy == 0) return@dy
                yield(Pair(dirId++, Vec2i(dx, dy)))
            }
        }
    }

    fun neighbours(x: Vec2i) = dirs.mapIndexed { index, d -> index to x + d.second }

    override fun get(a: T, b: T, dir: Int): Int {
        return if (sockets[a as Int]?.get(dir)?.contains(b as Int) == true) 1 else 0
    }
}

open class FiniteField2d(val width: Int, val height: Int) {
    val Vec2i.idx get() = x + y * width
    val Int.cell get() = Vec2i(this % width, floor(this.toFloat() / width).toInt())
    val allCells = (0 until height).asSequence().map { y ->
        (0 until width).asSequence().map { x -> Vec2i(x, y) }
    }.flatMap { it }
}

//typealias VariableValue = Pair<Vec2i, Tile>
// pattern numbers
//LevelGenerator<Int>
@OptIn(ExperimentalTime::class)
class LevelGenerator<T: Comparable<T>>(
    width: Int, height: Int,
    val constrains: PatternRestrictions<T>,
    val initialAssignments: Map<Vec2i, T>,
    private val initDomain: Array<T>,
    seed: Int = 0) : FiniteField2d(width, height) {
    private val random = Random(seed)
    // get, set, by cell index

    // current domains for variables
    val variables = Array(height) { Array(width) { mutableSetOf(*initDomain) } }

    // deleted domain values
//    val deltas = Array(me.az.view.getHeight) { Array(me.az.view.getWidth) { mutableMapOf<Tile, List< VariableValue >>() } }
    private val waitingList = mutableSetOf<Vec2i>()

    private val bans = mutableMapOf<Vec2i, MutableSet<T>>() // unary constraints

    private var _batchUpdate = false
    fun batchUpdate(block: LevelGenerator<T>.() -> Unit) {
        _batchUpdate = true
        block.invoke(this)
        _batchUpdate = false
    }

    fun dis(label: String? = null): String {
        label?. run { println( "%%% $this %%%") }
        return variables.joinToString("\n") {  row ->
            row.joinToString(" ") { domain ->
                domain/*.chunked(3)*/.map { when(it) {
                    is Tile -> it.char
                    else -> it
                } }.joinToString("")
            }
        }
    }

    // or getLinkedNodes
    val Vec2i.neighbours get() = this.neighbours(width, height)
    // A table M of Booleans keeps track of which values of the initial
    // domain are in the current domain or not (M(i, a)=true ??? a???Di).
    private val Vec2i.domain get() = variables[y][x]
    fun M(i: Vec2i, a: T) = i.domain.contains(a)

    //search of the smallest value greater (or equal) than b that belongs to Dj
    fun Vec2i.nextInDomain(a: T) = domain.dropWhile { it < a }.first()
    val Vec2i.isResolved get() = domain.size == 1
    val Vec2i.isCondradiction get() = domain.size == 0

    private val selectStrategy = SelectNextVariable.MinDomainRandomStrategy(
        Tile.values().size, random, { x -> x.domain }
    ) { x -> x.sumDomain }
//    private val selectStrategy = SelectNextVariable.MaxStrategy { x -> x.sumDomain }
//    private val strategy = SelectNextVariable.MinStrategy<T>()

    init {
        reset()
    }

    //attempt #4
    fun reset() {
        selectStrategy.reset()
        waitingList.clear()
        allCells.forEach {
            it.domain.clear()
            it.domain.addAll(initDomain)
        }

        initialAssignments.forEach { (k, v) ->
            setVariable(k, v)
        }

        bans.forEach { (x, a) ->
            x.domain.removeAll(a)
        }
    }

    fun run() {
        val res = searchForSolution(initialAssignments)
        when( res ) {
            is Response.Complete<*> -> println("RESULT: ${res.result}")
            is Response.Stuck -> {
                println(dis("STUCK"))
            }
        }
    }
    fun setVariable(x: Int, y: Int, t: T) = setVariable(Vec2i(x, y), t)
    fun setVariable(cell: Vec2i, t: T): Boolean {
        // by good way - track all supports for this domain
        // and update "friends"
        with(cell.domain) {
            if (none { it == t }) {
                throw IllegalStateException()
            }

            /// let's make small propagate here
//            waitingList += cell
            waitingList -= cell

            if ( removeAll { it != t } ) { // on domain change
                if ( isEmpty() ) {
                    // solve stuck
                    return false
                }

                // on variable change
                for ( (dir, n) in cell.neighbours ) {
                    if ( n.isResolved ) continue
                    onPropagate(n)
                    waitingList += n
                }
            }
            // collapsed
            return true
        }
    }

    // narrowing domains step, reducing original solution
    // looking for min candidate
    private fun propagate(): Boolean {
        while( waitingList.isNotEmpty() ) {
            val x = waitingList.first()
            waitingList.remove(x)

//            if ( y.isResolved ) continue
            if ( x.isCondradiction ) return false

            for ( (dir, n) in constrains.neighbours(x) ) {

                if ( filter(n, x, dir) ) {
                    onPropagate(n)
                    waitingList += n
                }
            }
        }
//        println(dis("propagated"))
        return true
    }

    private sealed class SelectNextVariable<VariableType, ValueType> {

        var currentSelectedNext: VariableType? = null
        var currentSelectedNextValue: ValueType? = null

        val cached get() = currentSelectedNext != null
        open val selectedVariableValue get() = Pair(currentSelectedNext!!, currentSelectedNextValue!!)

        abstract fun updateCandidate(x: VariableType)
        open fun reset() {
            currentSelectedNext = null
        }

         class MinStrategy<T>(private val summator: (x: Vec2i) -> Map<T, Float>): SelectNextVariable<Vec2i, T>() {
            private var currentSelectedNextProb = 1f
            override fun reset() {
                super.reset()
                currentSelectedNextProb = 1f
            }
            override fun updateCandidate(x: Vec2i) {
                val sum = summator(x)
                val minValue = sum.keys.minBy { sum[it]!! }
                val minProb = sum[minValue]!! / sum.values.sum()
                if ( minProb < currentSelectedNextProb ) {
                    currentSelectedNext = x
                    currentSelectedNextProb = minProb
                    currentSelectedNextValue = minValue
                    println("updated min prob: $minProb $minValue at $x sum=$sum")
                }
            }
        }

        class MaxStrategy<T>(private val summator: (x: Vec2i) -> Map<T, Float>): SelectNextVariable<Vec2i, T>() {
            private var currentSelectedNextProb = 0f
            override fun reset() {
                super.reset()
                currentSelectedNextProb = 0f
            }
            override fun updateCandidate(x: Vec2i) {
                val sum = summator(x)
                val minValue = sum.keys.maxBy { sum[it]!! }
                val minProb = sum[minValue]!! / sum.values.sum()
                if ( minProb > currentSelectedNextProb ) {
                    currentSelectedNext = x
                    currentSelectedNextProb = minProb
                    currentSelectedNextValue = minValue
                    println("updated max prob: $minProb $minValue at $x sum=$sum")
                }
            }
        }

        class MinDomainRandomStrategy<T: Comparable<T>>(
            private val minDomainSize: Int,
            private val random: Random,
            private val domain: (x: Vec2i) -> Set<T>,
            private val summator: (x: Vec2i) -> Map<T, Float>,
        ): SelectNextVariable<Vec2i, T>() {
            var currDomainSize = minDomainSize
            override fun reset() {
                super.reset()
                currDomainSize = minDomainSize
            }

            override val selectedVariableValue: Pair<Vec2i, T>
                get() {
                    if ( currentSelectedNext == null  ) {
                        TODO("global select")
                    } else {
                        //choose random value
                        val sum = summator(currentSelectedNext!!)
                        val sums = sum.entries.toTypedArray()
//                        if ( sums.any { it.key == Tile.LADDER }) {
//                            println(sums.joinToString(", "))
//                        }
                        val choiceIdx = random.choice(sums.map { it.value.toInt() })
                        var choice = sums[choiceIdx].key

                        if (choice == Tile.PLAYER || choice == Tile.GUARD) {
                            choice = Tile.EMPTY as T
                        }
                        return Pair(currentSelectedNext!!, choice)
                    }
                }

            override fun updateCandidate(x: Vec2i) {
                with(domain(x).size) {
                    if (this < currDomainSize) {
                        currentSelectedNext = x
                        currDomainSize = this
                    }
                }
            }
        }
    }


    private fun onPropagate(x: Vec2i) {
        if ( x.domain.size > 1 ) {
            // specific - we have probabilities, so we could calc it
//            val minValue = sum.keys.maxBy { sum[it]!! }
            selectStrategy.updateCandidate(x)
        }
    }

    private fun filter(i: Vec2i, j: Vec2i, dir: Int): Boolean {
        // save delta?
        return i.domain.removeAll { xtile ->
            j.domain.none { constrains[xtile, it, dir] > 0 }
        }
    }

    private fun selectVariableValue(): Pair<Vec2i, T> {

        if ( selectStrategy.cached ) {
            val (x, b) = selectStrategy.selectedVariableValue

            if (!x.isResolved && !x.isCondradiction) {
                val v = Pair(x, b)
                selectStrategy.reset()
                return v
            }
        }

        // ???????? ?????? ??????????:
        // - ???????????? ????????????
        // - ???????????? ????????
        // - ???????????????? ?? ??????????
        // - ?????????????????? ??????????????
         //   -- ?? ?????? ?????? ????????????
        // select by scan
        // heuristics
        // by min constraints
        // allCells.filter { it.domain.size > 1 }.minBy { it.domain.size }
        // or by max prob
        allCells.filter { it.domain.size > 1 }.forEach {
            onPropagate(it)
        }

        val res = selectStrategy.selectedVariableValue
        selectStrategy.reset()
        return res
    }

    private fun searchForSolution(assignments: Map<Vec2i, T>): Response {
//        println("waitingList = ${waitingList.size}")
        if ( assignments.size == width * height ) return Response.Complete(assignments)

        if ( propagate() ) {
            do {
                val (y, b) = selectVariableValue()
                //val b = selectValue(y)

                val oldDomain = y.domain.toSet()
                setVariable(y, b)
                val res = searchForSolution( assignments + (y to b) )
                when ( res ) {
                    is Response.Complete<*> -> return res
                    is Response.Stuck -> {
                        println("revert")
                        // this is so silly method to revert
                        // but it should not be needed at best case
                        // TBD dump ban and restart
                        y.domain.clear()
                        y.domain.addAll(oldDomain)
                        y.domain.remove(b)
                    }
                }
            } while( !y.isCondradiction && propagate() )
        }
        // reset state

        return Response.Stuck
    }

    //Sjb={(i, a)/(j, b) is the smallest value in Dj supporting (i, a) on Rij}
    //while in AC-4 it contains all values supported by (j, b).
//    val counters = Array(me.az.view.getHeight) { Array(me.az.view.getWidth) { mutableMapOf<T, MutableList<Pair<Vec2i, T>>>() } }
//    val Vec2i.minSupport get() = counters[y][x]

    sealed class Response {
        object Stuck : Response()
        class Complete<T>(val result: Map<Vec2i, T>) : Response()
    }

    //GetMinEntropy
    // ??????
    private fun findCandidateToCollapse(assignments: Map<Vec2i, T>): Vec2i {
        var min = initDomain.size
        var minPos = Vec2i(Vec2i.ZERO)
        var found = 0
        var resolved = 0

        for (y in 0 until height) {
            for ( x in 0 until width ) {
                val cell = Vec2i(x, y)
                if ( assignments.contains(cell) ) continue
                val d = cell.domain
//                if ( d.size == 0 ) return Response.Stuck(Vec2i(x, y))
                if ( d.size == 1 ) { resolved++ ; continue }

                if ( d.size < min ) {
                    min = d.size
                    minPos = cell
                    found ++
                }
            }
        }
        println("candidate: found=$found")

//        return if ( found > 0 ) Response.Success(minPos)
//        else Response.Complete
        return minPos
    }

    // returns probabilities
    val Vec2i.sumDomain: Map<T, Float> get() = buildMap {
        // count overall dist

        for ( (dir, n) in neighbours ) {
            // reduce
            domain.forEach { a ->
                n.domain.forEach { b ->
                    this[a] = getOrPut(a) { 0f } + constrains[a, b, dir]
                }
            }
        }

        // normalize
//        val total = occ.values.sum()
//        occ.forEach {(t, v) ->
//            occ[t] = v / total
//        }


    }

}
