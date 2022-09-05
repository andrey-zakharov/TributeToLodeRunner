import de.fabmax.kool.math.Vec2i
import me.az.ilode.Tile
import kotlin.math.ceil
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

val neighboursMoor2D = listOf(-1, -1, 0, -1, 1, -1,
    -1, 0, 1, 0,
    -1, 1, 0, 1, 1, 1
)

val neighboursStar2d = listOf(0, 2, 2, 0, 0, -2, -2, 0, 0 , 1, 1, 0, 0, -1, -1, 0)

// up, right, down, left
val neighboursNeumann2d = listOf(0, 1, 1, 0, 0, -1, -1, 0)

val neighbours = neighbours5x52D
val dimSize = ceil(neighbours.size / 2f).toInt()

interface BinaryConstrains<V> {
    operator fun get(a: Tile, b: Tile, dir: Int): V
}

fun Vec2i.neighbours(maxx: Int, maxy: Int) =
    neighbours.chunked(2).mapIndexed { index, (dx, dy) ->
        Pair(index, Vec2i(this.x + dx, this.y + dy))
    }
    .filter {(_, it) ->
        it.x in 0 until maxx && it.y in 0 until maxy
    }

class Probabilities(val total: Int, val sum: MutableMap<Tile, Array<MutableMap<Tile, Int>>>): BinaryConstrains<Int> {
    override operator fun get(a: Tile, b: Tile, dir: Int): Int {
        // symmetric here
        return (if ( a < b ) sum[a]?.get(dir)?.get(b) else sum[b]?.get(dir)?.get(a)) ?: 0
    }

    override fun toString() = sum.map { "${it.key} = ${it.value.joinToString { it.toString() }}" }.joinToString("\n")
}


// collecting probabilities
fun calcRestrictions(initial: List<String>, n: Int = 1): Probabilities {
    require( initial.isNotEmpty() )
    require( initial.first().isNotEmpty() )
    require( initial.size >= n )
    require( initial.first().length >= n )

    val height = initial.size
    val width = initial.first().length

    val totalArcs = width * (height - 1) + height * (width - 1)
    val sum = mutableMapOf<Tile, Array<MutableMap<Tile, Int>>>()

    // TBD symmetric or not
    for ( y in 0 until height) {
        for ( x in 0 until width) {
            val a = Tile.byChar[initial[y][x]]!!

            val nei = Vec2i(x, y).neighbours(width, height)
            for ( (dir, n) in nei ) {
                val b = Tile.byChar[initial[n.y][n.x]]!!

                val astats = sum.getOrPut(a) {// by dirs
                    Array(dimSize) { mutableMapOf() } }
                sum[a]!![dir][b] = astats[dir].getOrPut(b) { 0 } + 1
            }
        }
    }

    return Probabilities(totalArcs, sum)
}

class Ac3(val width: Int, val height: Int, val constrains: BinaryConstrains<Int>, seed: Int = 0) {
    private val initDomain get() = Tile.values()
    private val random = Random(seed)
    // get, set, by cell index
    val variables = Array(height) { Array(width) { mutableSetOf(*initDomain) } }
    private val propagating = mutableSetOf<Vec2i>()
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

    val Vec2i.domain get() = variables[y][x]
    val Vec2i.isResolved get() = domain.size == 1

    fun getDomain(cell: Vec2i) = variables[cell.y][cell.x]
    fun getPossible(cell: Vec2i) = getDomain(cell).filter { bans[cell]?.contains(it) != true }.toMutableSet()

    // attempt #2
    fun revise(i: Vec2i, j: Vec2i): Boolean {
        return i.domain.removeAll { xtile ->
            j.domain.none { constrains[xtile, it, dir] > 0 || constrains[it, xtile, dir] > 0 }
        }
    }

    // attempt #1
    fun collapseTile(x: Int, y: Int, t: Tile) = collapseTile(Vec2i(x, y), t)
    fun collapseTile(cell: Vec2i, t: Tile): Boolean {
        with(cell.domain) {
            if (!any { it == t }) {
                throw IllegalStateException()
            }

            removeAll { it != t }

            propagating -= cell
            // propagate
            for ( (dir, n) in cell.neighbours(width, height) ) {
                if ( !n.isResolved && n !in propagating ) {
                    propagating += n
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

    fun run() {
        do {
            if ( !propagate() ) {
                // reset
                // restart
                // backstep
                //create rule
                //bans.put(a, except)
                dis()
                //break
            }

            when(val candidate = findCandidateToCollapse()) {
                is Response.Success -> {
                    collapseToRandom(candidate.payload)
                    propagating += candidate.payload
                }
                is Response.Complete -> {
                    println("complete? ")
                    dis()
                }
                is Response.Stuck -> {
                    val lastD = decisions.removeLast()
                    println("stuck ${candidate.at}")
                    bans[lastD.first] = lastD.second
                    getDomain(lastD.first).addAll(initDomain)
                    getDomain(candidate.at).addAll(initDomain)
                    //propagating += lastD.first
                    decisions.removeLast()
                }
            }

        } while( propagating.isNotEmpty() )

    }

    sealed class Response {
        class Success() : Response()
        class Stuck(val at: Vec2i): Response()
        object Complete : Response()
    }

    //GetMinEntropy
    // МНВ
    private fun findCandidateToCollapse(assignments: Set<Vec2i>): Vec2i {
        var min = initDomain.size
        var minPos = Vec2i(Vec2i.ZERO)
        var found = 0
        var resolved = 0

        for (y in 0 until height) {
            for ( x in 0 until width ) {
                val cell = Vec2i(x, y)
                if ( assignments.contains(cell) ) continue
                val d = getDomain(cell)
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

    private fun solve(): Response {
        return solve(setOf())
    }

    private fun solve(assignments: Set<Vec2i>): Response {
        if ( assignments.size == width * height ) return Response.Complete

        val cell = findCandidateToCollapse(assignments)
        if ( collapseToRandom(cell) ) {
            val result = solve(assignments + cell)
            if ( result == Response.Complete ) return result

            // else
            assignments
        }

    }

    private fun propagate(): Boolean {
        // process unary constrains

        // pop next
        var processed = 0
        while( propagating.isNotEmpty() ) {
            val cell = propagating.first()
            propagating.remove(cell)

            bans[cell]?.forEach { getDomain(cell).remove(this) }

            for ((dir, n) in cell.neighbours(width, height)) {
                if (arcReduce(cell, n, dir)) {
//                    println("reduced to ${getDomain(cell)}")
                    if (getDomain(cell).isEmpty()) {
                        println("stuck")
                        return false // failure
                    } else {
                        propagating += cell
                    }
                }
            }
            processed++
        }

        println("propagate: $processed")
        return true
    }

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
                    val sum = occ.getOrPut(atile) { 0 } + constrains[atile, it, dir]
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

    private fun arcReduce(x: Vec2i, y: Vec2i, dir: Int): Boolean {
        var changed = false
        val yd = getDomain(y)

        changed = getDomain(x).removeAll { xtile ->
            yd.none { constrains[xtile, it, dir] > 0 || constrains[it, xtile, dir] > 0 }
        }

        return changed
    }
}