import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.MutableStateValue
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.Group
import de.fabmax.kool.scene.mesh
import me.az.ilode.*
import me.az.shaders.TileMapShader
import me.az.shaders.TileMapShaderConf
import me.az.view.ActorView

class LevelView(
    game: Game, level: GameLevel,
    conf: ViewSpec,
    tilesAtlas: ImageAtlas,
    holesAtlas: ImageAtlas,
    runnerAtlas: ImageAtlas,
    runnerAnims: AnimationFrames,
    guardAtlas: ImageAtlas,
    guardAnims: AnimationFrames

) : Group() {
    private val tileMapShader = TileMapShader(TileMapShaderConf(tilesAtlas.tileCoords.size))
    val runnerView by lazy { ActorView( game.runner, runnerAtlas, runnerAnims, conf.tileSize) }
    val widthInPx = conf.tileSize.x * level.width
    val heightInPx = conf.tileSize.y * level.height

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
            }

            onUpdate +=  {
                tileMapShader.time =  it.time.toFloat()

                if ( level.dirty ) {
                    with(tileMapShader) {
                        field = Texture2d(simpleValueTextureProps, level.updateTileMap())
                        fieldSize = Vec2f(level.width.toFloat(), level.height.toFloat())
                        tiles = tilesAtlas.tex.value
                        secondaryTiles = holesAtlas.tex.value
                        tilesAtlas.tileCoords.forEachIndexed { index, vec2i ->
                            this.tileFrames[index] = MutableVec2f(vec2i.x.toFloat(), vec2i.y.toFloat())
                        }
                    }
                    level.dirty = false
                }
            }
        }

        +runnerView
        game.guards.forEach {
            +ActorView(it, guardAtlas, guardAnims, conf.tileSize)
        }
    }

}
