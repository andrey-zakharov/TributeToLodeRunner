import de.fabmax.kool.math.MutableVec2i
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.mvpMatrix
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.Group
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.mesh
// represent both simple texture2d or frame in atlas
fun Scene.sprite(texture: Texture2d,
                 name: String? = null,
                 spriteSize: Vec2i? = null,
                 regionSize: Vec2i? = null,
                 mirrorTexCoordsY: Boolean = false): Sprite {
    return Sprite(spriteSize, texture, regionSize, name, mirrorTexCoordsY)
}

open class Sprite(
    private val spriteSize: Vec2i?, // in pixels
    private val texture: Texture2d,
    private val regionSize: Vec2i?, // how much get from texture by pixels
    name: String? = null,
    private val mirrorTexCoordsY: Boolean = false) : Group("sprite $name group") {

    val textureOffset = MutableVec2i(Vec2i.ZERO)

    val spriteShader by lazy { SpriteShader() }

    val _spriteSize = MutableVec2i(spriteSize?.x ?: 0, spriteSize?.y ?: 0)
    val _regionSize = MutableVec2i(regionSize?.x ?: 0, regionSize?.y ?: 0)

    init {
        buildMesh()
        onUpdate += {
            if ( _spriteSize == Vec2i.ZERO || _regionSize == Vec2i.ZERO ) {
                if ( texture.loadingState == Texture.LoadingState.LOADED ) {
                    if ( _spriteSize == Vec2i.ZERO ) {
                        _spriteSize.x = texture.loadedTexture!!.width
                        _spriteSize.y = texture.loadedTexture!!.height
                    }
                    if ( _regionSize == Vec2i.ZERO ) {
                        _regionSize.x = texture.loadedTexture!!.width
                        _regionSize.y = texture.loadedTexture!!.height
                    }
                }
            }
            transform.resetScale()
            transform.scale(_spriteSize.x.toDouble(), _spriteSize.y.toDouble(), 1.0)
        }
    }

    private fun buildMesh() {
        removeAllChildren()

        // texture should be loaded
//        require(texture.loadingState == Texture.LoadingState.LOADED) { "texture in sprite $name (${texture.name}) not loaded" }
//
//        val fullTexSize = Vec2i( texture.loadedTexture!!.width, texture.loadedTexture!!.height )

        +mesh(SpriteShader.SPRITE_MESH_ATTRIBS, name) {
            generate {
                rect {
                    width = 1f//(spriteSize ?: fullTexSize).x.toFloat()
                    height = 1f//(spriteSize ?: fullTexSize).y.toFloat()
                    origin.set(-width / 2f, -height / 2f, 0f)

                    if (mirrorTexCoordsY) {
                        mirrorTexCoordsY()
                    }
                }
                shader = spriteShader.also {
                    it.texture = texture
                }
            }

            onUpdate += {
                spriteShader.textureOffset = this@Sprite.textureOffset
                spriteShader.tileSize = _regionSize
            }
        }
        setIdentity()

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
                        colorOutput(texelFetch(atlasTex,
                            (texCoords.output * tileSize.toFloat2() + textureOffset.toFloat2()).toInt2()))
                    }
                }
            }
        }


    }
}