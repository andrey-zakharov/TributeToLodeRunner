package me.az.view

import ImageAtlas
import ImageAtlasSpec
import backgroundImageFile
import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.Group
import de.fabmax.kool.scene.OrthographicCamera
import de.fabmax.kool.scene.colorMesh
import de.fabmax.kool.scene.mesh
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.createUint8Buffer
import me.az.ilode.Game
import me.az.scenes.height
import me.az.scenes.width
import me.az.shaders.TileMapShader
import me.az.shaders.TileMapShaderConf
import simpleTextureProps
import simpleValueTextureProps
import sprite
import kotlin.experimental.and
import kotlin.math.max
import kotlin.math.min

class StringDrawer(val atlas: ImageAtlas,
                   val spec: MutableStateValue<ImageAtlasSpec>,
                   private val map: Map<Char, Int>,
                   private val fallbackChar: Int = 0) {
    private val buf = createUint8Buffer(1000)

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

        val tileSize = Vec2i(builder.spec.value.tileWidth, builder.spec.value.tileHeight)
        val toolbarWidth = 28f * tileSize.x
        val toolbarHeight = tileSize.y.toFloat()

        +colorMesh {
            generate {
                rect {
                    size.set(toolbarWidth, toolbarHeight)
                    origin.set(-width / 2, 0f, 0f)
                }
            }
            shader = KslUnlitShader { color { constColor(Color(0f, 0f, 0f, 0.75f)) } }
        }

        +mesh(listOf(Attribute.POSITIONS, Attribute.TEXTURE_COORDS)) {
            generate {
                rect {
                    size.set(toolbarWidth, toolbarHeight)
//                    size.set(12f, 20f)
                    origin.set(-width / 2, 0f, 0f)
                }
            }

            shader = tileMapShader.apply {
                this.tileSize = tileSize
                this.tiles = builder.atlas.tex.value
                this.secondaryTiles = builder.atlas.tex.value
                // TBD just by math in shader itself
                builder.atlas.tileCoords.forEachIndexed { index, vec2i ->
                    this.tileFrames[index] = MutableVec2f(vec2i.x.toFloat(), vec2i.y.toFloat())
                }
            }

            onUpdate += {
                if (game.level != null) {
                    val scores = game.runner.score.toString().padStart(7, '0')
                    val lives = game.runner.health.toString().padStart(3, '0')
                    val level = (game.level!!.levelId + 1).toString().padStart(3, '0')
                    val text = "score$scores men$lives level$level"
                    if (text != currentText) {
                        tileMapShader.field = Texture2d(simpleValueTextureProps, builder.draw(text))
                        tileMapShader.fieldSize = Vec2f(text.length.toFloat(), 1f)
                        currentText = text
                    }
                }

            }
        }

    }

}