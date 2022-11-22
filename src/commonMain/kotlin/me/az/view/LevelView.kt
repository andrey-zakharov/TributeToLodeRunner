import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.math.Vec4i
import de.fabmax.kool.modules.audio.WavFile
import de.fabmax.kool.modules.ui2.MutableStateValue
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.Texture3d
import de.fabmax.kool.scene.Group
import de.fabmax.kool.scene.mesh
import me.az.ilode.*
import me.az.shaders.TileMapShader
import me.az.shaders.TileMapShaderConf
import me.az.view.ActorView

class LevelView(
    game: Game,
    level: GameLevel,
    conf: ViewSpec,
    tilesAtlas: ImageAtlas,
    runnerAtlas: ImageAtlas,
    runnerAnims: AnimationFrames,
    guardAtlas: ImageAtlas,
    guardAnims: AnimationFrames,
    soundsBank: Map<Sound, WavFile>

) : Group() {
    private val tileMapShader = TileMapShader(TileMapShaderConf())
    val runnerView by lazy { ActorView( game.runner, runnerAtlas, runnerAnims, conf.tileSize, soundsBank = soundsBank) }
    val widthInPx = conf.tileSize.x * level.width
    val heightInPx = conf.tileSize.y * level.height

    private val onLevelStart = { gameLevel: GameLevel ->

        var n = findNode("guard")
        while(n != null) {
            removeNode(n)
            n = findNode("guard")
        }

        game.guards.forEach {
            +ActorView(it, guardAtlas, guardAnims, conf.tileSize, "guard", soundsBank)
        }
    }

    init {

        scale(conf.tileSize.x.toFloat(), conf.tileSize.y.toFloat(), 1f)
        translate(0.0, 1.0, 0.0)

        +mesh(listOf(Attribute.POSITIONS, Attribute.TEXTURE_COORDS)) {

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

        +runnerView
        game.onLevelStart += onLevelStart
        onLevelStart(game.level!!)

        println("level px=${widthInPx}x${heightInPx}")
    }

}


