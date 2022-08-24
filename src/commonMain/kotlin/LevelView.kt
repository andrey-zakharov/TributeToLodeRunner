import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.Group
import de.fabmax.kool.scene.mesh
import me.az.ilode.*

class LevelView(
    game: Game, level: GameLevel, conf: LevelSceneSpec,
    tilesAtlas: ImageAtlas,
    runnerAtlas: ImageAtlas,
    runnerAnims: AnimationFrames,
    guardAtlas: ImageAtlas,
    guardAnims: AnimationFrames

) : Group() {
    val tileMapShader = TileMapShader(TileMapShaderConf(tilesAtlas.tileCoords.size))

    init {

        scale(conf.tileSize.x.toFloat(), conf.tileSize.y.toFloat(), 1f)
        translate(0.0, 1.0, 0.0)

        +mesh(listOf(Attribute.POSITIONS, Attribute.TEXTURE_COORDS)) {

            generate {
                rect {
//                    size.set(conf.gameWidth.toFloat(), conf.gameHeight.toFloat() - conf.tileSize.y * 2)
//                    origin.set(-size.x/2f, -size.y/2f, 0f)
                    size.set(level.width.toFloat(), level.height.toFloat())
                    origin.set(-level.width/2f, -level.height/2f, 0f)

                }
            }

            shader = tileMapShader.apply {

                tileSize = Vec2i(tilesAtlas.spec.tileWidth, tilesAtlas.spec.tileHeight)
                this.tiles = tilesAtlas.tex
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

        +ActorView( game.runner, runnerAtlas, runnerAnims, conf.tileSize)
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
                actor.block.x - actor.level.width/2.0 + 0.5, actor.level.height/2.0 - actor.block.y - 0.5,
//                (actor.block.x  ) + actor.offset.x,
//                (actor.level.height/2.0 - actor.block.y ) - actor.offset.y,
//                ((actor.block.x + 0.5) * TILE_WIDTH + actor.offset.x/* - actor.level.width / 2 */),
//                (actor.level.height - ((actor.block.y + 0.5) * TILE_HEIGHT - actor.offset.y) /*+  / 2*/),
//                (actor.block.x * me.az.ilode.TILE_WIDTH + actor.offset.x).toDouble(),
//                (actor.block.y * me.az.ilode.TILE_WIDTH + actor.offset.y).toDouble(),
                0.0)
            scale(1f/tileSize.x, 1f/tileSize.y, 1f )
            translate(actor.offset.x.toDouble(), -actor.offset.y.toDouble(), 0.0)

        }
    }
}
