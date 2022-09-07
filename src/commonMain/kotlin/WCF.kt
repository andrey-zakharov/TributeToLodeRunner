import de.fabmax.kool.math.Vec2i
import me.az.ilode.Tile
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

// reversed = (i + total/2) % total
val neighbours5x52D: List<Int> by lazy {
    val res = mutableListOf<Int>()

    (-2 until 2).forEach { y ->
        (-2 until 2).filterNot { x -> x == 0 && y == 0 }
            .forEach { x ->
                res += x
                res += y
            }
    }

    res.toList()
}

val neighboursMoor2D = listOf(
    -1, -1, 0, -1, 1, -1,
    -1,  0,        1,  0,
    -1,  1, 0,  1, 1,  1
)

val neighboursStar2d = listOf(0, 2, 2, 0, 0, -2, -2, 0, 0 , 1, 1, 0, 0, -1, -1, 0)

// up, right, down, left
val neighboursNeumann2d = listOf(0, 1, 1, 0, 0, -1, -1, 0)

val Neighbours = neighboursMoor2D
val dimSize = ceil(Neighbours.size / 2f).toInt()

interface BinaryConstrains<V> {
    operator fun get(a: Tile, b: Tile): V
}

fun Vec2i.neighbours(width: Int, height: Int): Sequence<Pair<Int, Vec2i>> =
    Neighbours.asSequence().chunked(2).mapIndexed { index, (dx, dy) ->
        Pair(index, Vec2i(this.x + dx, this.y + dy))
    }
    .filter { (_, it) ->
        it.x in 0 until width && it.y in 0 until height
    }

class Probabilities(initial: List<String>/*, n: Int = 1*/): BinaryConstrains<Int> {
    val totalArcs: Int
    val sum: MutableMap<Tile, MutableMap<Tile, Int>> = mutableMapOf()

    init {
        require( initial.isNotEmpty() )
        require( initial.first().isNotEmpty() )
//        require( initial.size >= n )
//        require( initial.first().length >= n )

        val height = initial.size
        val width = initial.first().length

        totalArcs = width * (height - 1) + height * (width - 1)

        (0 until height).forEach { y ->
            ( 0 until width).forEach { x ->
                val a = Tile.byChar[initial[y][x]]!!

                val nei = Vec2i(x, y).neighbours(width, height)
                for ( (dir, n) in nei ) {
                    val b = Tile.byChar[initial[n.y][n.x]]!!
                    incConstrain(a, b)
                }
            }
        }

    }

    override operator fun get(a: Tile, b: Tile): Int {
        // symmetric here
        return (if ( a < b ) sum[a]?.get(b) else sum[b]?.get(a)) ?: 0
    }

    private fun incConstrain(a: Tile, b: Tile) {
        var norA = a
        var norB = b
        if ( a > b ) norA = b.also { norB = a }
        sum[norA]!![norB] = sum.getOrPut(norA) { mutableMapOf() }.getOrPut(norB) { 0 } + 1
    }

    override fun toString() = sum.map { "${it.key} = ${it.value}" }.joinToString("\n")
}

// collecting probabilities
fun calcRestrictions(initial: List<String>) = Probabilities(initial)

//class IntField2d(val width: Int, val height: Int, init: (Int) -> Int = { 0 }) {
//    val _data = Array(width * height, init)
//    public operator fun get(x: Int, y: Int) =
//}
typealias VariableValue = Pair<Vec2i, Tile>

class Ac3(val width: Int, val height: Int, val constrains: BinaryConstrains<Int>, seed: Int = 0) {
    private val initDomain get() = Tile.values()
    private val random = Random(seed)
    // get, set, by cell index

    // current domains for variables
    val variables = Array(height) { Array(width) { mutableSetOf(*initDomain) } }

    // deleted domain values
//    val deltas = Array(height) { Array(width) { mutableMapOf<Tile, List< VariableValue >>() } }
    private val waitingList = mutableSetOf<Pair<Vec2i, Tile>>() //  delta:

    val allCells = (0 until height).asSequence().map { y ->
        (0 until width).asSequence().map { x -> Vec2i(x, y) }
    }.flatMap { it }

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

    private val bans = mutableMapOf<Vec2i, MutableSet<Tile>>() // unary constraints
    private val decisions = mutableListOf<Pair<Vec2i, Tile>>()

    private var _batchUpdate = false
    fun batchUpdate(block: Ac3.() -> Unit) {
        _batchUpdate = true
        block.invoke(this)
        _batchUpdate = false
    }

    fun dis() {
        println(variables.joinToString("\n") { it.joinToString { it.map { it.char }.joinToString("") } })
    }

    val Vec2i.idx get() = x + y * width
    val Int.cell get() = Vec2i(this % width, floor(this.toFloat() / width).toInt())

    // or getLinkedNodes
    val Vec2i.neighbours get() = this.neighbours(width, height)
    // A table M of Booleans keeps track of which values of the initial
    // domain are in the current domain or not (M(i, a)=true ⇔ a∈Di).
    val Vec2i.domain get() = variables[y][x]
    fun M(i: Vec2i, a: Tile) = i.domain.contains(a)

    //search of the smallest value greater (or equal) than b that belongs to Dj
    fun Vec2i.nextInDomain(a: Tile) = domain.dropWhile { it < a }.first()
    val Vec2i.isResolved get() = domain.size == 1
    val Vec2i.isCondradiction get() = domain.size == 0

    // attempt #3

    //Sjb={(i, a)/(j, b) is the smallest value in Dj supporting (i, a) on Rij}
    //while in AC-4 it contains all values supported by (j, b).
    val counters = Array(height) { Array(width) { mutableListOf<VariableValue>() } }
    val Vec2i.minSupport get() = counters[y][x]

    fun isAllowed(a: Tile, b: Tile) = constrains[a, b] > 0

    fun nextSupport(x: Vec2i, y: Vec2i, a: Tile, b: Tile): Tile? {
        var emptySupport = false
        var nb: Tile? = null

        if ( b <= y.domain.last() ) {
            nb = y.nextInDomain(b)

            // search of the smallest support for (i, a) in Dj

            while( ! isAllowed(a, nb!!) && !emptySupport ) {
                if ( nb < y.domain.last() ) nb = y.nextInDomain(nb)
                else emptySupport = true
            }
        }
        return nb
    }

    fun solveStart() {
        // as we have simple tiles map, we could numerate arcs (edges, or sides)
        // almost the same way as cell
        // total = width * (height-1) + height * (width - 1)
        waitingList.clear()
//        deltas.forEach { row -> row.forEach { d -> d.clear() } }

        allArcs.forEach { (i, j) ->
            val toRemove = mutableSetOf<Tile>()
            for ( a in i.domain ) {
                val vv = VariableValue(i, a)
                var b = nextSupport(i, j, a, Tile.values().first())
                if ( b == null ) { // empty support
                    //remove a from domain
                    // concurrent mod
                    // i.domain.remove(a)
                    toRemove += a
                    waitingList += vv
                } else {
                    j.minSupport.add(vv)
                }
            }

            if ( toRemove.isNotEmpty() ) {
                i.domain.removeAll(toRemove)
            }
        }
    }


    // attempt #2
    // remove inconsistent arcs
    fun revise(i: Vec2i, j: Vec2i): Boolean {
        // save delta?
        return i.domain.removeAll { xtile ->
            j.domain.none { constrains[xtile, it] > 0 }
        }
    }

    fun propogate3(queue: MutableSet<Pair<Vec2i, Vec2i>>): Boolean {
        while( queue.isNotEmpty() ) {
            // pick Xj from Q
            val i = queue.first()
            queue.remove(i)

            for ( (dir, n) in i ) {
                if ( revise(n, i) ) {
                    if (n.isCondradiction) return false
                    queue.add(n)
                }
            }
        }
        return true
    }


    fun searchSolution(assignments: Map<Vec2i, Tile>): Response {
        if ( assignments.size == width * height ) return Response.Complete(assignments)

        val cell = findCandidateToCollapse(assignments)
        // wave collapse - just chose first random branch on minimal entropic cell
        // if ( collapseToRandom(cell) ) {
        // ac-3 depth first search

        cell.domain.forEach {
            val result = searchSolution(assignments + (cell to it))
            if ( result is Response.Complete ) return result
            if ( result is Response.Stuck )
        }
        //if ( collapseToRandom(cell) ) {

            // else revert



    }
    // attempt #1
    fun collapseTile(x: Int, y: Int, t: Tile) = collapseTile(Vec2i(x, y), t)
    fun collapseTile(cell: Vec2i, t: Tile): Boolean {
        with(cell.domain) {
            if (!any { it == t }) {
                throw IllegalStateException()
            }

            removeAll { it != t }

            waitingList -= cell
            // propagate
            for ( (dir, n) in cell.neighbours(width, height) ) {
                if ( !n.isResolved && n !in waitingList ) {
                    waitingList += n
                }
            }

            if ( isEmpty() ) {
                // error collapsing
                return false
            }
            // collapsed
            return true
        }
    }

//    fun run() {
//        do {
//            if ( !propagate() ) {
//                // reset
//                // restart
//                // backstep
//                //create rule
//                //bans.put(a, except)
//                dis()
//                //break
//            }
//
//            when(val candidate = findCandidateToCollapse()) {
//                is Response.Success -> {
//                    collapseToRandom(candidate.payload)
//                    propagating += candidate.payload
//                }
//                is Response.Complete -> {
//                    println("complete? ")
//                    dis()
//                }
//                is Response.Stuck -> {
//                    val lastD = decisions.removeLast()
//                    println("stuck ${candidate.at}")
//                    bans[lastD.first] = lastD.second
//                    getDomain(lastD.first).addAll(initDomain)
//                    getDomain(candidate.at).addAll(initDomain)
//                    //propagating += lastD.first
//                    decisions.removeLast()
//                }
//            }
//
//        } while( propagating.isNotEmpty() )
//
//    }

    sealed class Response {
        class Success() : Response()
        class Stuck(val at: Vec2i): Response()
        class Complete(val result: Map<Vec2i, Tile>) : Response()
    }

    //GetMinEntropy
    // МНВ
    private fun findCandidateToCollapse(assignments: Map<Vec2i, Tile>): Vec2i {
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
////
//    private fun propagate(): Boolean {
//        // process unary constrains
//
//        // pop next
//        var processed = 0
//        while( propagating.isNotEmpty() ) {
//            val cell = propagating.first()
//            propagating.remove(cell)
//
//            bans[cell]?.forEach { getDomain(cell).remove(this) }
//
//            for ((dir, n) in cell.neighbours(width, height)) {
//                if (arcReduce(cell, n, dir)) {
////                    println("reduced to ${getDomain(cell)}")
//                    if (getDomain(cell).isEmpty()) {
//                        println("stuck")
//                        return false // failure
//                    } else {
//                        propagating += cell
//                    }
//                }
//            }
//            processed++
//        }
//
//        println("propagate: $processed")
//        return true
//    }

    //make decision
    private fun collapseToRandom(cell: Vec2i): Boolean {
        val a = cell.domain
        bans[cell]?.forEach { a.remove(it) }

        // count overall dist
        val occ = mutableMapOf<Tile, Int>()

        for ( (dir, n) in cell.neighbours(width, height) ) {
            val b = n.domain
            bans[n]?.forEach { b.remove(it) }
            // reduce
            a.forEach { atile ->
                b.forEach {
                    val sum = occ.getOrPut(atile) { 0 } + constrains[atile, it]
                    occ[atile] = sum
                }
            }
        }

        val total = occ.values.sum()
        print("collapsing random for: $cell $a sum=$occ total=$total ")
        var r = random.nextInt(total)
        var choice = occ.keys.last()

        while ( r > 0 ) {
            choice = occ.keys.first()
            val v = occ[choice]!!
            r -= v
            occ.remove(choice)
        }
        println("collapsed to $choice")

        if ( choice == Tile.PLAYER || choice == Tile.GUARD ) {
            choice = Tile.EMPTY
        }

        decisions.add(decisions.size, Pair(cell, choice))

        return collapseTile(cell.x, cell.y, choice)
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
// return index in choices
fun Random.choice(choices: List<Int>): Int {
    val total = choices.sum()
//    print("collapsing random for: $choices total=$total ")
    var r = nextInt(total)
    var choice = 0//choices.last()

    while ( r > 0 ) {
        r -= choices[choice]
        if ( r <= 0 ) return choice
        choice++
    }
    return choice
}