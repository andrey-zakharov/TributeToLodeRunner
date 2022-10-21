import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.TextureData2d
import de.fabmax.kool.scene.Group
import de.fabmax.kool.scene.colorMesh
import de.fabmax.kool.scene.mesh
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.createUint8Buffer
import me.az.ilode.*
import me.az.shaders.TileMapShader
import me.az.shaders.TileMapShaderConf
import me.az.view.ActorView
import kotlin.experimental.and

class LevelView(
    game: Game, level: GameLevel, conf: LevelSpec,
    tilesAtlas: ImageAtlas,
    holesAtlas: ImageAtlas,
    runnerAtlas: ImageAtlas,
    runnerAnims: AnimationFrames,
    guardAtlas: ImageAtlas,
    guardAnims: AnimationFrames

) : Group() {
    private val tileMapShader = TileMapShader(TileMapShaderConf(tilesAtlas.tileCoords.size))
//    val runnerView = me.az.view.ActorView( game.runner!!, runnerAtlas, runnerAnims, conf.tileSize)
    val runnerView = ActorView( game.runner, runnerAtlas, runnerAnims, conf.tileSize)
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

                tileSize = Vec2i(tilesAtlas.spec.tileWidth, tilesAtlas.spec.tileHeight)
                this.tiles = tilesAtlas.tex
                this.secondaryTiles = holesAtlas.tex
                tilesAtlas.tileCoords.forEachIndexed { index, vec2i ->
                    this.tileFrames[index] = MutableVec2f(vec2i.x.toFloat(), vec2i.y.toFloat())
                }
//                field = Texture2d(simpleValueTextureProps, level.updateTileMap())
            }

            onUpdate +=  {
                tileMapShader.time =  it.time.toFloat()
                if ( level.dirty ) {
                    tileMapShader.field = Texture2d(simpleValueTextureProps, level.updateTileMap())
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
