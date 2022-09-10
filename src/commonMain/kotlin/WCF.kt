import de.fabmax.kool.math.Vec2i
import me.az.ilode.Tile
import me.az.utils.choice
import kotlin.math.ceil
import kotlin.math.floor
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

val neighboursStar2d = listOf(0, 2, 2, 0, 0, -2, -2, 0, 0 , 1, 1, 0, 0, -1, -1, 0)

// up, right, down, left
val neighboursNeumann2d = listOf(0, 1, 1, 0, 0, -1, -1, 0)

val Neighbours = neighboursNeumann2d
val dimSize = ceil(Neighbours.size / 2f).toInt()

interface BinaryConstrains<V, R> {
    operator fun get(a: V, b: V, dir: Int): R
}

fun Vec2i.neighbours(width: Int, height: Int): Sequence<Pair<Int, Vec2i>> =
    Neighbours.asSequence().chunked(2).mapIndexed { index, (dx, dy) ->
        Pair(index, Vec2i(this.x + dx, this.y + dy))
    }
    .filter { (_, it) ->
        it.x in 0 until width && it.y in 0 until height
    }

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

//        totalArcs = width * (height - 1) + height * (width - 1)
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

    override fun toString() = sum.map { "${it.key} = ${it.value}" }.joinToString("\n")
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
// collecting probabilities
fun calcRestrictions(initial: List<String>) = Probabilities<Tile>(initial).apply {
    val height = initial.size
    val width = initial.first().length
    val N = 4

//    val subTiles = Array((1+width - N) * (1+height - N)) { Array(N*N) { } }
    val subTiles = mutableListOf<Array<Tile>>()

    (0 until height - N).forEach { startY ->
        (0 until width - N).forEach { startX ->


        }

    }

    load(initial)
}

open class FiniteField2d(val width: Int, val height: Int) {
    val Vec2i.idx get() = x + y * width
    val Int.cell get() = Vec2i(this % width, floor(this.toFloat() / width).toInt())
    val allCells = (0 until height).asSequence().map { y ->
        (0 until width).asSequence().map { x -> Vec2i(x, y) }
    }.flatMap { it }

}

typealias VariableValue = Pair<Vec2i, Tile>

@OptIn(ExperimentalTime::class)
class LevelGenerator<T: Comparable<T>>(width: Int, height: Int,
                                       val constrains: Probabilities<T>,
                                       val initialAssignments: Map<Vec2i, T>,
                                       private val initDomain: Array<T>,
                                       seed: Int = 0) : FiniteField2d(width, height) {
    private val random = Random(seed)
    // get, set, by cell index

    // current domains for variables
    val variables = Array(height) { Array(width) { mutableSetOf(*initDomain) } }

    // deleted domain values
//    val deltas = Array(height) { Array(width) { mutableMapOf<Tile, List< VariableValue >>() } }
    private val waitingList = mutableSetOf<Vec2i>()

    ///tbd sequence
    val allArcs by lazy { mutableMapOf<Vec2i, Vec2i>().apply {
        for ( c in allCells ) {
            for ( (dir, n) in c.neighbours ) {
                val rev = c.idx > n.idx
                val first = if ( rev ) n else c
                val second = if ( rev ) c else n
                // unique sorted TBD something less general
                this[first] = second
            }
        }
    } }

    private val bans = mutableMapOf<Vec2i, MutableSet<T>>() // unary constraints
    private val decisions = mutableListOf<Pair<Vec2i, T>>()

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
    // domain are in the current domain or not (M(i, a)=true ⇔ a∈Di).
    val Vec2i.domain get() = variables[y][x]
    fun M(i: Vec2i, a: T) = i.domain.contains(a)

    //search of the smallest value greater (or equal) than b that belongs to Dj
    fun Vec2i.nextInDomain(a: T) = domain.dropWhile { it < a }.first()
    val Vec2i.isResolved get() = domain.size == 1
    val Vec2i.isCondradiction get() = domain.size == 0

    init {
        reset()
    }

    //attempt #4
    fun reset() {
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
//                    if ( filter(n, cell) ) {
                        updateNextVariable(n)
                        waitingList += n
//                    }
                }
            }
            // collapsed
            return true
        }
    }

    // narrowing domains step, reducing original solution
    // looking for min candidate
    fun propagate(): Boolean {
        while( waitingList.isNotEmpty() ) {
            val x = waitingList.first()
            waitingList.remove(x)

//            if ( y.isResolved ) continue
            if ( x.isCondradiction ) return false

            for ( (dir, n) in x.neighbours ) {
                if ( n.isResolved ) continue
                if ( filter(n, x, dir) ) {
                    updateNextVariable(n)
                    waitingList += n
                }
            }
        }
//        println(dis("propagated"))
        return true
    }

    private var currentSelectedNextProb = 0f
    private var currentSelectedNext: Vec2i? = null
    private var currentSelectedNextValue: T? = null

    fun updateNextVariable(x: Vec2i) {
        if ( x.domain.size > 1 ) {
            // specific - we have probabilities, so we could calc it
            val sum = x.sumDomain
            val minValue = sum.keys.maxBy { sum[it]!! }
            val minProb = sum[minValue]!! / sum.values.sum()
            if ( minProb > currentSelectedNextProb ) {
                currentSelectedNext = x
                currentSelectedNextProb = minProb
                currentSelectedNextValue = minValue
                println("updated min prob: $minProb $minValue at $x sum=$sum")
            }
        }
    }

    private fun filter(i: Vec2i, j: Vec2i, dir: Int): Boolean {
        // save delta?
        return i.domain.removeAll { xtile ->
            j.domain.none { constrains[xtile, it, dir] > 0 }
        }
    }

    private fun selectVariable(): Vec2i {

        var res = currentSelectedNext
        currentSelectedNext = null
        currentSelectedNextProb = 0f

        if ( res != null && !res.isResolved && !res.isCondradiction ) {

            return res
        }
        // select by scan
        // heuristics
        // by min constraints
        // allCells.filter { it.domain.size > 1 }.minBy { it.domain.size }
        // or by max prob
        allCells.filter { it.domain.size > 1 }.forEach {
            updateNextVariable(it)
        }

        res = Vec2i(currentSelectedNext!!)
        currentSelectedNext = null
        currentSelectedNextProb = 0f
        return res
    }

    private fun selectValue(variable: Vec2i): T {
//        return currentSelectedNextValue ?: TODO("update min tile")
        val sum = variable.sumDomain
        val sums = sum.entries.toTypedArray()
        val choiceIdx = random.choice(sums.map { it.value.toInt() })
        var choice = sums[choiceIdx].key

        if ( choice == Tile.PLAYER || choice == Tile.GUARD ) {
            choice = Tile.EMPTY as T
        }
        return choice
    }

    private fun searchForSolution(assignments: Map<Vec2i, T>): Response {
        println("waitingList = ${waitingList.size}")
        if ( assignments.size == width * height ) return Response.Complete(assignments)

        if ( propagate() ) {
            do {
                val y = selectVariable()
                val b = selectValue(y)

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

    // attempt #3

    //Sjb={(i, a)/(j, b) is the smallest value in Dj supporting (i, a) on Rij}
    //while in AC-4 it contains all values supported by (j, b).
    val counters = Array(height) { Array(width) { mutableMapOf<T, MutableList<VariableValue>>() } }
    val Vec2i.minSupport get() = counters[y][x]

//    fun isAllowed(a: Tile, b: Tile) = constrains[a, b, dir] > 0


//    fun solveStart() {
//        // as we have simple tiles map, we could numerate arcs (edges, or sides)
//        // almost the same way as cell
//        // total = width * (height-1) + height * (width - 1)
//        waitingList.clear()
////        deltas.forEach { row -> row.forEach { d -> d.clear() } }
//
//        allArcs.forEach { (i, j) ->
//            val toRemove = mutableSetOf<Tile>()
//            for ( a in i.domain ) {
//                val vv = VariableValue(i, a)
//                var b = nextSupport(i, j, a, Tile.values().first())
//                if ( b == null ) { // empty support
//                    //remove a from domain
//                    // concurrent mod
//                    // i.domain.remove(a)
//                    toRemove += a
//                    waitingList += vv
//                } else {
//                    j.minSupport.getOrPut(b) { mutableListOf() } += vv
//                }
//            }
//
//            if ( toRemove.isNotEmpty() ) {
//                i.domain.removeAll(toRemove)
//            }
//        }
//    }

    ////
//    private fun propagateWithSupport(): Boolean {
//        // process unary constrains
//
//        // pop next
//        var processed = 0
//        while( waitingList.isNotEmpty() ) {
//            val (j, b) = waitingList.first()
//            waitingList.remove(waitingList.first())
//            /// before its deletion (j, b) was the smallest begin support in Dj for (i, a) on Rij
//            for (vv in j.minSupport[b] ?: emptyList()) {
//                j.minSupport[b]?.remove(vv)
//                val (i, a) = vv
//                if (i.domain.contains(a)) { // if M(i, a) then
//                    var c = nextSupport(i, j, a, b)
//                    if (c == null) {
//                        i.domain.remove(a)
//                        waitingList += vv
//                    } else {
//                        j.minSupport.getOrPut(c) { mutableListOf() } += vv
//                    }
//                }
//            }
//        }
//    }

    // attempt #2
    // remove inconsistent arcs
//    fun revise(i: Vec2i, j: Vec2i): Boolean {
//        // save delta?
//        return i.domain.removeAll { xtile ->
//            j.domain.none { constrains[xtile, it] > 0 }
//        }
//    }


    sealed class Response {
        object Stuck : Response()
        class Complete<T>(val result: Map<Vec2i, T>) : Response()
    }

    //GetMinEntropy
    // МНВ
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
//
//    private fun solve(): Response {
//        return solve(setOf())
//    }
//
//    private fun solve(assignments: Set<Vec2i>): Response {
//        if ( assignments.size == width * height ) return Response.Complete
//
//        val cell = findCandidateToCollapse(assignments)
//        if ( collapseToRandom(cell) ) {
//            val result = solve(assignments + cell)
//            if ( result == Response.Complete ) return result
//
//            // else
//            assignments
//        }
//
//    }

    // returns probabilities
    val Vec2i.sumDomain: Map<T, Float> get() = buildMap {
        // count overall dist

        for ( (dir, n) in neighbours(width, height) ) {
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

    //make decision
    private fun collapseToRandom(cell: Vec2i): Boolean {
        val a = cell.domain
        bans[cell]?.forEach { a.remove(it) }

        // count overall dist
        val occ = mutableMapOf<T, Float>().apply { putAll(cell.sumDomain) }
        val total = occ.values.sum()
        print("collapsing random for: $cell $a sum=$occ total=$total ")
        var r = random.nextDouble(total.toDouble())
        var choice = occ.keys.last()

        while ( r > 0 ) {
            choice = occ.keys.first()
            val v = occ[choice]!!
            r -= v
            occ.remove(choice)
        }
        println("collapsed to $choice")

        if ( choice == Tile.PLAYER || choice == Tile.GUARD ) {
            choice = Tile.EMPTY as T
        }

        decisions.add(decisions.size, Pair(cell, choice))

        return setVariable(cell.x, cell.y, choice)
    }
//
//    private fun arcReduce(x: Vec2i, y: Vec2i, dir: Int): Boolean {
//        var changed = false
//        val yd = getDomain(y)
//
//        changed = getDomain(x).removeAll { xtile ->
//            yd.none { constrains[xtile, it, dir] > 0 || constrains[it, xtile, dir] > 0 }
//        }
//
//        return changed
//    }
}
