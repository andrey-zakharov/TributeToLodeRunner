import de.fabmax.kool.math.MutableVec2i
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.mvpMatrix
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.Group
import de.fabmax.kool.scene.mesh
// represent both simple texture2d or frame in atlas
fun createSprite() {

}
open class Sprite(val spriteSize: Vec2i, // in pixels
                  val texture: Texture2d,
                  val regionSize: Vec2i, // how much get from texture by pixels
                  name: String? = null,
                  mirrorTexCoordsY: Boolean = false) :
    Group("sprite $name group") {

    val textureOffset = MutableVec2i(Vec2i.ZERO)

    val spriteShader by lazy { SpriteShader() }
    init {
        +mesh(SpriteShader.SPRITE_MESH_ATTRIBS, name) {
            generate {
                rect {
                    width = spriteSize.x.toFloat()
                    height = spriteSize.y.toFloat()
                    origin.set(-width / 2f, -height / 2f, 0f)
                    if (mirrorTexCoordsY) {
                        mirrorTexCoordsY()
                    }
                }
                shader = spriteShader.also {
                    it.texture = texture
                    it.tileSize = regionSize
                }
            }

            onUpdate += {
                spriteShader.textureOffset = this@Sprite.textureOffset
            }
        }
    }

    class SpriteShader() : KslShader(Model(), pipelineConfig) {
        // or region from tex
        var texture by texture2d("tex")
        var textureOffset by uniform2i("texOffset")
        var tileSize by uniform2i("tileSize")

        companion object {
            val SPRITE_MESH_ATTRIBS = listOf(Attribute.POSITIONS, Attribute.TEXTURE_COORDS, Attribute.COLORS)
            private val pipelineConfig = PipelineConfig().apply {
                blendMode = BlendMode.BLEND_PREMULTIPLIED_ALPHA
                cullMethod = CullMethod.NO_CULLING
                depthTest = DepthCompareOp.LESS_EQUAL
            }
        }

        class Model() : KslProgram("sprite") {
            init {
                val texCoords = interStageFloat2()
                val color = interStageFloat4()

                vertexStage {
                    main {
                        texCoords.input set vertexAttribFloat2(Attribute.TEXTURE_COORDS.name)
                        color.input set vertexAttribFloat4(Attribute.COLORS.name)
                        outPosition set mvpMatrix().matrix * float4Value(vertexAttribFloat3(Attribute.POSITIONS.name), 1f)
                    }
                }
                fragmentStage {
                    main {
                        val atlasTex = texture2d("tex")
                        val textureOffset = uniformInt2("texOffset")
                        val tileSize = uniformInt2("tileSize")
                        val atlasTexSize = textureSize2d(atlasTex).toFloat2()
                        colorOutput(sampleTexture(atlasTex,
                            (texCoords.output * tileSize.toFloat2() + textureOffset.toFloat2()) / atlasTexSize))
                    }
                }
            }
        }


    }


}