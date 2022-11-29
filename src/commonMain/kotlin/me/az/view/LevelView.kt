import de.fabmax.kool.modules.audio.WavFile
import de.fabmax.kool.scene.Group
import de.fabmax.kool.scene.Node
import me.az.ilode.*
import me.az.scenes.Sequences
import me.az.utils.logd
import me.az.view.ActorView
import me.az.view.SpriteInstance
import me.az.view.SpriteSystem

class LevelView(
    spriteSystem: SpriteSystem,
    game: Game,
    level: GameLevel,
    conf: ViewSpec,
    private val tilesAnims: AnimationFrames,
    private val runnerAnims: AnimationFrames,
    private val guardAnims: AnimationFrames,
    soundsBank: Map<Sound, WavFile>

) : Group() {

    val runnerView by lazy {
        ActorView( game.runner, spriteSystem, runnerAnims,
            conf.tileSize, soundsBank = soundsBank)
    }
    val widthInPx = conf.tileSize.x * level.width
    val heightInPx = conf.tileSize.y * level.height

    private val onLevelStart = { gameLevel: GameLevel ->

        var n = findNode("guard") as? ActorView
        while(n != null) {
            removeNode(n)
            spriteSystem.sprites.remove(n.instance)
            n = findNode("guard") as? ActorView
        }

        game.guards.forEach {
            +ActorView(it, spriteSystem, guardAnims,
                conf.tileSize, "guard", soundsBank)
        }
    }

    val handlers = Array(level.width) { x ->
        Array(level.height) { y ->
            spriteSystem.sprite(0,
                // TBD MVP of group
                x.toFloat() - level.width / 2f + 0.5f,
                level.height - y.toFloat() + 0.5f, 0)
        }
    }
    // do not process all level, only dynamic.
    // key - x
    // value - map with
    //      key - y,
    //      value - tileindex
    val sequenceCounters = mutableMapOf<Int, MutableMap<Int, Int>>()
    sealed class Action {
        private val updates = mutableListOf<() -> Unit>()
        fun onUpdate(body: () -> Unit) {
            updates += body
        }
        fun onStart() {}
        fun onExit() {}

        class IncrementFrame(sequence: List<Int>) : Action() {
            var counter = 0
            init {
                onUpdate {
                    //seq
                }
            }
        }
    }

    init {
        level.onTileUpdate { ev ->
            val (x, y, v) = ev

            with(handlers[x][y]) {
                val list = tilesAnims.sequence.getOrElse(v.name.lowercase()) {
                    println("$v sequence not found")
                    listOf()
                }
                atlasId.set( tilesAnims.atlasId )
                // add action
                val frame = if ( list.size > 1 ) {
                    sequenceCounters.getOrPut(x) { mutableMapOf() }.getOrPut(y) { 0 }
                } else { 0 }
                tileIndex.set(list[frame])
                //println("$x $y $v = ${tileIndex.value}")
            }
            //handlers[x][y].tileIndex.set(  )
        }
        level.all {
            with(handlers[it.x][it.y]) {
                val list = tilesAnims.sequence[it.tile.name.lowercase()] ?: throw IllegalArgumentException("${it.tile} sequence not found")

                atlasId.set(tilesAnims.atlasId)
                tileIndex.set(list.first())
//                println("${it.tile.name.lowercase()} $tileIndex")
            }
        }

        //scale(conf.tileSize.x.toFloat(), conf.tileSize.y.toFloat(), 1f)
        //translate(0.0, 5.0, 0.0)

        /*+mesh(listOf(Attribute.POSITIONS, Attribute.TEXTURE_COORDS)) {

            generate {
                rect {
                    size.set(level.width.toFloat(), level.height.toFloat())
                    origin.set(-width/2f, 0f, 0f)
                }
            }

            shader = tileMapShader.apply {
                tileSize = conf.tileSize
                tileSizeInTileMap = tilesAtlas.getTileSize()
            }

            onUpdate +=  {
                tileMapShader.time =  it.time.toFloat()

                if ( level.dirty ) {
                    with(tileMapShader) {
                        field?.dispose()
                        field = Texture2d(simpleValueTextureProps, level.updateTileMap())
                        fieldSize = Vec2f(level.width.toFloat(), level.height.toFloat())
                        tilesAtlas.tex.value?.run { tiles = this }
                        tileSizeInTileMap = tilesAtlas.getTileSize()
                        tileSize = conf.tileSize

                    }
                    level.dirty = tileMapShader.tileSizeInTileMap.x == 0
                }
            }
        }
*/
        +runnerView
        game.onLevelStart += onLevelStart
        onLevelStart(game.level!!)

        logd { "level px=${widthInPx}x${heightInPx}" }
    }

}


