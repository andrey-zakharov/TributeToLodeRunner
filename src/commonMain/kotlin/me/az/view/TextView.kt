package me.az.view

import ImageAtlas
import ImageAtlasSpec
import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.modules.ui2.MutableStateValue
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.TextureData2d
import de.fabmax.kool.scene.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.createUint8Buffer
import me.az.shaders.TileMapShader
import me.az.shaders.TileMapShaderConf
import me.az.utils.nearestTwo
import simpleValueTextureProps
import kotlin.experimental.and


fun textView(text: String, fontAtlas: ImageAtlas,
             imageAtlasSpec: MutableStateValue<ImageAtlasSpec>,
             capacity: Int = text.length, init: TextView.() -> Unit = {}) = TextView(
    mutableStateOf(text), fontAtlas, imageAtlasSpec, capacity, init
)

class TextView(val text: MutableStateValue<String>,
               private val fontAtlas: ImageAtlas,
               spec: MutableStateValue<ImageAtlasSpec>,
               capacity: Int = 1000,
               init: TextView.() -> Unit = {}
): Group() {

    private var dirty = true
    private val buf = createUint8Buffer(capacity)

    // totally custom
    companion object {
        const val fallbackChar = 0
        val fontMap = mutableMapOf<Char, Int>().apply {
            for (c in '0'..'9') {
                this[c] = c - '0'
            }
            for (c in 'a'..'z') {
                this[c] = c - 'a' + 10
            }
            this['.'] = 36
            this['<'] = 37
            this['>'] = 38
            this['-'] = 39
            this[' '] = 43
            this[':'] = 44
            this['_'] = 45
        }
    }

    private val tileMapShader = TileMapShader(
        TileMapShaderConf(fontAtlas.tileCoords.size)
    )

    private fun drawFromBuffer(string: String): TextureData2d {
        buf.clear()
        for ( c in string.lowercase() ) {
            buf.put((fontMap[c] ?: fallbackChar).toByte() and 0x7f)
        }
        buf.flip()
        return TextureData2d(buf, buf.limit.nearestTwo, 1, TexFormat.R)
    }

    private var lastWidth = 0

    private val textWidth get() = text.value.length.toFloat() //* fontAtlas.spec.tileWidth.toFloat()
    private val textHeight get() = 1f //fontAtlas.spec.tileHeight.toFloat()

    private fun rebuildMeshes(spec: ImageAtlasSpec) {
        if ( text.value.length == lastWidth ) return
        this.removeAllChildren()
        val pads = 2f

        +group("scalethis") {
            // bg
            +colorMesh {
                generate { rect {} }
                shader = KslUnlitShader { color { constColor(Color(0f, 0f, 0f, 0.75f)) } }
            }

            +mesh(listOf(Attribute.POSITIONS, Attribute.TEXTURE_COORDS)) {
                generate {
                    rect {
                        //size.set(textWidth, textHeight)
                        origin.set(pads / spec.tileWidth / textWidth, pads / spec.tileHeight / textHeight, 0f)
//                    size.set(12f, 20f)
                    }
                }

                shader = tileMapShader.apply {
                    this.tileSize = Vec2i(spec.tileWidth, spec.tileHeight)
                    this.secondaryTiles = fontAtlas.tex.value
                    // TBD just by math in shader itself
                }
            }
            scale(textWidth, textHeight, 1f)
        }

        lastWidth = text.value.length
    }

    private fun updateShader() = with(tileMapShader) {
        field = Texture2d(simpleValueTextureProps, drawFromBuffer(text.value))
        fieldSize = Vec2f(text.value.length.toFloat(), 1f)
        tiles = fontAtlas.tex.value
//        tileSize = Vec2i(fontAtlas.getFrame(0).w, fontAtlas.getFrame(0).z)
        fontAtlas.tileCoords.forEachIndexed { index, vec2i ->
            this.tileFrames[index] = MutableVec2f(vec2i.x.toFloat(), vec2i.y.toFloat())
        }
    }

    init {

            //on change fontAtlas.tex
        text.onChange {
            dirty = true
        }
        fontAtlas.tex.onChange {
            dirty = true
        }

        onUpdate += {
            if ( dirty ) {

                with (children[0] as Group) {
                    transform.resetScale()
                    scale(textWidth, textHeight, 1f)
                }
//                rebuildMeshes() // if dims are diff
                updateShader() // only text
                dirty = false
            }
        }

        rebuildMeshes(spec.value)
        init()
    }

}