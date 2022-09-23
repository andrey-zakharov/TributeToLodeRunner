import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.TextureData2d
import de.fabmax.kool.scene.Group
import de.fabmax.kool.scene.mesh
import de.fabmax.kool.util.createUint8Buffer
import me.az.ilode.*
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
    val runnerView = ActorView( game.runner!!, runnerAtlas, runnerAnims, conf.tileSize)

    init {

        scale(conf.tileSize.x.toFloat(), conf.tileSize.y.toFloat(), 1f)
        translate(0.0, 1.0, 0.0)

        +mesh(listOf(Attribute.POSITIONS, Attribute.TEXTURE_COORDS)) {

            generate {
                rect {
//                    size.set(conf.gameWidth.toFloat(), conf.gameHeight.toFloat() - conf.tileSize.y * 2)
//                    origin.set(-size.x/2f, -size.y/2f, 0f)
                    size.set(level.width.toFloat(), level.height.toFloat())
                    origin.set(-level.width/2f, 0f, 0f)

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

            onUpdate += {
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


class ActorView(val actor: Actor,
                val atlas: ImageAtlas,
                val animations: AnimationFrames,
                val tileSize: Vec2i
) : Sprite(tileSize, atlas.tex, tileSize) {


    init {
        onUpdate += {

            val sequence = animations.sequence[actor.action]!!
            actor.frameIndex %= sequence.size
            textureOffset.set(atlas.getTexOffset(sequence[actor.frameIndex]))

            setIdentity()
            translate(
                actor.block.x - actor.level.width/2.0 + 0.5, actor.level.height - actor.block.y - 0.5,
//                (actor.block.x  ) + actor.offset.x,
//                (actor.level.height/2.0 - actor.block.y ) - actor.offset.y,
//                ((actor.block.x + 0.5) * TILE_WIDTH + actor.offset.x/* - actor.level.width / 2 */),
//                (actor.level.height - ((actor.block.y + 0.5) * TILE_HEIGHT - actor.offset.y) /*+  / 2*/),
//                (actor.block.x * me.az.ilode.TILE_WIDTH + actor.offset.x).toDouble(),
//                (actor.block.y * me.az.ilode.TILE_WIDTH + actor.offset.y).toDouble(),
                0.0)
//            scale(1f, 1f, 1f )
            translate(actor.offset.x.toDouble()/tileSize.x, -actor.offset.y.toDouble()/tileSize.y, 0.0)

        }
    }
}

class StringDrawer(val atlas: ImageAtlas, val map: Map<Char, Int>, val fallbackChar: Int = 0) {
    val buf = createUint8Buffer(1000)

    fun draw(string: String): TextureData2d {
        buf.clear()
        for ( c in string ) {
            buf.put((map[c] ?: fallbackChar).toByte() and 0x7f)
        }
        buf.flip()
        return TextureData2d(buf, buf.limit, 1, TexFormat.R)
    }
}

class StatusView(val game: Game, val builder: StringDrawer) : Group() {

    var currentText: String = ""

    private val tileMapShader = TileMapShader(
        TileMapShaderConf(builder.atlas.tileCoords.size)
    )

    init {

        val tileSize = Vec2i(builder.atlas.spec.tileWidth, builder.atlas.spec.tileHeight)

        +mesh(listOf(Attribute.POSITIONS, Attribute.TEXTURE_COORDS)) {
            generate {
                rect {
                    size.set(28f * tileSize.x, tileSize.y.toFloat())
//                    size.set(12f, 20f)
                    origin.set(-width/2, 0f, 0f)
                }
            }

            shader = tileMapShader.apply {
                this.tileSize = tileSize
                this.tiles = builder.atlas.tex
                this.secondaryTiles = builder.atlas.tex
                // TBD just by math in shader itself
                builder.atlas.tileCoords.forEachIndexed { index, vec2i ->
                    this.tileFrames[index] = MutableVec2f(vec2i.x.toFloat(), vec2i.y.toFloat())
                }
            }

//            shader = unlitShader {
//
//            }

            onUpdate += {
                if ( game.runner != null ) {
                    val scores = game.runner!!.score.toString().padStart(7, '0')
                    val lives = game.runner!!.health.toString().padStart(3, '0')
                    val level = (game.level.levelId + 1).toString().padStart(3, '0')
                    val text = "score$scores men$lives level$level"
                    if (text != currentText) {
                        tileMapShader.field = Texture2d(simpleValueTextureProps, builder.draw(text))
                        currentText = text
                    }
                }

            }
        }

    }

}