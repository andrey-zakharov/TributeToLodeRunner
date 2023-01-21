package me.az.view

import AnimationFrames
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.math.randomI
import de.fabmax.kool.modules.audio.WavFile
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.scene.Group
import de.fabmax.kool.util.Time
import de.fabmax.kool.util.logE
import me.az.ilode.*
import me.az.utils.Act
import me.az.utils.ActingList
import me.az.utils.ActionStatus

class AnimateSprite(
    val sprite: SpriteInstance,
    private val sequence: List<Int>,
    private val loop: Boolean = false,
    initialCounter: Int = 0 // for delays
) : Act<LevelView>() {
    private var counter = initialCounter
    init {

        onUpdate {
            // sometimes we play anim backwards
            if ( counter >= sequence.size ) {
                if ( !loop ) return@onUpdate ActionStatus.DONE
                counter = 0
            }
            // on tick
            sprite.tileIndex.set(sequence[counter.mod(sequence.size)])
            counter++
            ActionStatus.CONTINUE
        }
    }
}

class LevelView(
    private val spriteSystem: SpriteSystem,
    private val game: Game,
    private val level: GameLevel,

    private val tilesAnims: AnimationFrames,
    private val runnerAnims: AnimationFrames,
    private val guardAnims: AnimationFrames,
    soundsBank: Map<Sound, WavFile>

) : Group() {

    val runnerView by lazy {
        ActorView( game.runner, spriteSystem, runnerAnims, soundsBank = soundsBank)
    }

    private val onLevelStart = { gameLevel: GameLevel ->

        var n = findNode("guard") as? ActorView
        while(n != null) {
            removeNode(n)
            spriteSystem.sprites.remove(n.instance)
            n.instance.unbind()
            n = findNode("guard") as? ActorView
        }

        game.guards.forEach {
            +ActorView(it, spriteSystem, guardAnims,"guard", soundsBank)
        }
    }

    private val tmpPos = Mat4f()
    private val atlasView = mutableStateOf(tilesAnims.atlasId)
    private fun createSprite(x: Int, y: Int): SpriteInstance {
        tmpPos.set(modelMat)
            .translate(x.toFloat(), y.toFloat(), 0f)
            .scale(1f, -1f, 1f) // one for coords space
        val s = SpriteInstance(atlasView, tmpPos, bgAlpha = mutableStateOf(1f) )
        spriteSystem.sprites.add( s )//spriteSystem.sprite(, 0, tmpPos)
        return s
    }

    // specific of this set of tile instances that do not need pos, its const celled
    private val handlers by lazy {

        updateModelMat()
//        println(modelMat.dis())
        val r = Array(level.width) { x ->
            Array(level.height) { y ->
                createSprite(x, y)
            }
        }
        spriteSystem.refresh()
        r
    }

    private fun SpriteInstance.replaceAnim(name: String, delay: Int = 0) {
        val list = tilesAnims.sequence.getOrElse(name) {
            logE { "$name sequence not found" }
            return
        }
        val replace = acting.filterIsInstance<AnimateSprite>().filter { it.sprite == this }
        replace.forEach { acting.remove(it) } // stop?

        atlasId.set( tilesAnims.atlasId )
        tileIndex.set(list.first())
        if (list.size > 1) {
            acting.add(AnimateSprite(this, list, loop = true, initialCounter = delay))
        }
    }

    private fun startDigAnims(x: Int, y: Int ) {
        val sequence = if (game.runner.x == x + 1) {
             "digHoleLeft"
        } else {
             "digHoleRight"
        }

        acting += AnimateSprite(handlers[x][y], tilesAnims.sequence["${sequence}Base"]!!, loop = false)
        acting += AnimateSprite(handlers[x][y - 1], tilesAnims.sequence[sequence]!!, loop = false)
    }
    private fun startFillHole(x: Int, y: Int) {
        acting += AnimateSprite(handlers[x][y], tilesAnims.sequence["fillHole"]!!, loop = false)
    }

    private fun onTileUpdate(ev: LevelCellUpdate) {
        val (x, y, v) = ev
//        logd { "LevelVIew:: onTileUpdate $x, $y, $v" }
        with(handlers[x][y]) {
            when(v) {
                TileLogicType.DIGGINGHOLE -> startDigAnims(x, y)
                TileLogicType.HOLE -> startFillHole(x, y)
                TileLogicType.GOLD -> replaceAnim(v.name.lowercase(), randomI(0, 60))
                else -> replaceAnim(v.name.lowercase())
            }
        }
        spriteSystem.dirty = true
    }

    fun fullRefresh() {
        acting.clear()

        level.all {
            onTileUpdate(it)
        }
    }

    private val acting = ActingList(this)

    init {
        // lode runners code has x started from leftmost to right, y from top to bottom
        translate(-level.width / 2f, level.height.toFloat() - 1, 0f) // 2 rows but actually should be layered by tileset dependent layout
        scale(1f, -1f, 1f) // one for tiling mirroring
        updateModelMat()
        //    addDebugAxis()

        game.onPlayGame += ::onPlayGame
        level.onTileUpdate(::onTileUpdate)
        fullRefresh()

        +runnerView
        game.onLevelStart += onLevelStart
        onLevelStart(game.level!!)
    }

    private fun onPlayGame(g: Game, t: Any?) {
        // straight and forward updating views.
        // writing to buffer by callbacks does not work /from first attempt/.
        acting.update(Time.deltaT)
        spriteSystem.dirty = true
        // WHO PLAYS ANIMS
    }

    override fun dispose(ctx: KoolContext) {
        super.dispose(ctx)
        level.unsubTileUpdate(::onTileUpdate)
        spriteSystem.sprites.removeAll( handlers.flatten() )
        game.onLevelStart -= onLevelStart
        game.onPlayGame -= ::onPlayGame
    }

}


