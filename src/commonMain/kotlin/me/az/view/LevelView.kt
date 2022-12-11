package me.az.view

import AnimationFrames
import ViewSpec
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.modules.audio.WavFile
import de.fabmax.kool.scene.Group
import de.fabmax.kool.util.Time
import de.fabmax.kool.util.logE
import me.az.ilode.*
import me.az.utils.*


class AnimateSprite(
    val sprite: SpriteInstance,
    private val sequence: List<Int>,
    private val loop: Boolean = false,
) : Act<LevelView>() {
    private var counter = 0
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
//
//fun playAnim(x: Int, y: Int, animName: String, withFinish: () -> Iterable<Act<GameLevel>>?) {
//    acting.add(Anim(Vec2i(x, y), name = animName, onFinish = withFinish))
//}

class LevelView(
    private val spriteSystem: SpriteSystem,
    private val game: Game,
    private val level: GameLevel,

    val conf: ViewSpec,
    private val tilesAnims: AnimationFrames,
    private val runnerAnims: AnimationFrames,
    private val guardAnims: AnimationFrames,
    soundsBank: Map<Sound, WavFile>

) : Group() {

    val runnerView by lazy {
        ActorView( game.runner, spriteSystem, runnerAnims,
            conf.tileSize, soundsBank = soundsBank)
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
            +ActorView(it, spriteSystem, guardAnims,
                conf.tileSize, "guard", soundsBank)
        }
    }

    val tmpPos = Mat4f()
    private fun createSprite(x: Int, y: Int): SpriteInstance {
        tmpPos.set(modelMat)
            .translate(x.toFloat(), y.toFloat(), 0f)
            .scale(1f, -1f, 1f) // one for coords space
        return spriteSystem.sprite(tilesAnims.atlasId, 0, tmpPos)
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

    private fun SpriteInstance.replaceAnim(name: String, loop: Boolean = false) {
        val list = tilesAnims.sequence.getOrElse(name) {
            logE { "$name sequence not found" }
            return
        }
        val replace = acting.filterIsInstance<AnimateSprite>().filter { it.sprite == this }
        replace.forEach { acting.remove(it) } // stop?
        acting.add( AnimateSprite(this, list, loop) )
        atlasId.set( tilesAnims.atlasId )
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
        logd { "LevelVIew:: onTileUpdate $x, $y, $v" }
        with(handlers[x][y]) {
            when(v) {
                TileLogicType.DIGGINGHOLE -> startDigAnims(x, y)
                TileLogicType.HOLE -> startFillHole(x, y)
                else -> replaceAnim(v.name.lowercase())
            }
        }
    }

    fun fullRefresh() {
//        val tmpPos = MutableVec3f()
//        updateModelMat()
        level.all {
            with(handlers[it.x][it.y]) {
                val list = tilesAnims.sequence[it.tile.name.lowercase()] ?: throw IllegalArgumentException("${it.tile} sequence not found")
                atlasId.value = tilesAnims.atlasId
                tileIndex.value = list.first()
//                tmpPos.set(it.x.toFloat(), it.y.toFloat(), 0f)
//                toGlobalCoords(tmpPos)
//                spriteSystem.toLocalCoords(tmpPos)
//                if ( tmpPos.x != pos.x || tmpPos.y != pos.y ) {
//                    pos.x = tmpPos.x
//                    pos.y = tmpPos.y
//                    onPosUpdated()
//                }
//                println("${it.tile.name.lowercase()} $tileIndex")
            }
        }
    }

    private val acting = ActingList(this)

    init {
        // lode runners code has x started from leftmost to right, y from top to bottom
        translate(-level.width / 2f, level.height.toFloat() - 1, 0f) // 2 rows but actually should be layered by tileset dependent layout
        scale(1f, -1f, 1f) // one for tiling mirroring
        updateModelMat()
        //    addDebugAxis()

        game.onPlayGame += { g, _ ->
            // straight and forward updating views.
            // writing to buffer by callbacks does not work /from first attempt/.
            spriteSystem.dirty = true
            acting.update(Time.deltaT)

            // WHO PLAYS ANIMS
        }
        level.onTileUpdate(::onTileUpdate)
        fullRefresh()

        +runnerView
        game.onLevelStart += onLevelStart
        onLevelStart(game.level!!)

/* ??       +colorMesh {
            generate { rect {
                size.set(100f, 100f)
            } }
            geometry[0].color.set(Color.DARK_BLUE)
            geometry[1].color.set(Color.GREEN)
            geometry[2].color.set(Color.LIGHT_YELLOW)
            shader = unlitShader { useStaticColor(Color.GREEN) }
        }*/
        //logd { "init level px=${widthInPx}x${heightInPx}" }
    }

    override fun dispose(ctx: KoolContext) {
        super.dispose(ctx)
        level.unsubTileUpdate(::onTileUpdate)
        spriteSystem.sprites.removeAll( handlers.flatten() )
        game.onLevelStart -= onLevelStart
    }

}


