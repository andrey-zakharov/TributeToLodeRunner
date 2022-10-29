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
fun sprite(texture: Texture2d,
                 name: String? = null,
                 spriteSize: Vec2i? = null,
                 regionSize: Vec2i? = null,
                 mirrorTexCoordsY: Boolean = false): Sprite {
    return Sprite(spriteSize, texture, regionSize, name, mirrorTexCoordsY)
}

open class Sprite(
    private val spriteSize: Vec2i?, // in pixels size of sprite view
    var texture: Texture2d?,
    private val regionSize: Vec2i?, // how much get from texture by pixels
    name: String? = null,
    private val mirrorTexCoordsY: Boolean = false
) : Group("sprite $name group") {

    val textureOffset = MutableVec2i(Vec2i.ZERO)
    var grayScaled: Boolean = false    // workaround GL_LUMINANCE obsolesce

    val spriteShader by lazy { SpriteShader() }

    val _spriteSize = MutableVec2i(spriteSize?.x ?: 0, spriteSize?.y ?: 0)
    val _regionSize = MutableVec2i(regionSize?.x ?: 0, regionSize?.y ?: 0)
    val onResize = mutableListOf<Sprite.(x: Int, y: Int) -> Unit>()

    init {
        buildMesh()
        onUpdate += {
            if ( _spriteSize == Vec2i.ZERO || _regionSize == Vec2i.ZERO ) {
                if ( texture?.loadingState == Texture.LoadingState.LOADED ) {
                    if ( _spriteSize == Vec2i.ZERO ) {
                        _spriteSize.x = texture!!.loadedTexture!!.width
                        _spriteSize.y = texture!!.loadedTexture!!.height
                        onResize.forEach { it(this, _spriteSize.x, _spriteSize.y) }
                    }
                    if ( _regionSize == Vec2i.ZERO ) {
                        _regionSize.x = texture!!.loadedTexture!!.width
                        _regionSize.y = texture!!.loadedTexture!!.height
                    }

                    transform.scale(_spriteSize.x.toDouble(), _spriteSize.y.toDouble(), 1.0)
                }
            }
            //transform.pus

        }
    }

    private fun buildMesh() {
        removeAllChildren()
        setIdentity()
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
                shader = spriteShader
            }

            onUpdate += {
                spriteShader.texture = texture
                spriteShader.textureOffset = this@Sprite.textureOffset
                spriteShader.tileSize = _regionSize
                spriteShader.grayScaled = if ( grayScaled ) 1 else 0
            }
        }
    }


    class SpriteShader : KslShader(Model(), pipelineConfig) {
        // or region from tex
        var texture by texture2d(UNIFORM_TEXTURE)
        var textureOffset by uniform2i(UNIFORM_OFFSET)
        var tileSize by uniform2i(UNIFORM_TILESIZE)
        var grayScaled by uniform1i(UNIFORM_GRAY)

        companion object {
            private const val UNIFORM_TEXTURE = "tex"
            private const val UNIFORM_OFFSET = "texOffset"
            private const val UNIFORM_TILESIZE = "tileSize"
            private const val UNIFORM_GRAY = "gray"

            val SPRITE_MESH_ATTRIBS = listOf(Attribute.POSITIONS, Attribute.TEXTURE_COORDS, Attribute.COLORS)
            private val pipelineConfig = PipelineConfig().apply {
                blendMode = BlendMode.BLEND_PREMULTIPLIED_ALPHA
                cullMethod = CullMethod.NO_CULLING
                depthTest = DepthCompareOp.DISABLED
            }
        }

        class Model : KslProgram("sprite") {
            init {
                val texCoords = interStageFloat2()
                val color = interStageFloat4()

                vertexStage {
                    main {
                        val mvp = mat4Var(mvpMatrix().matrix)
                        texCoords.input set vertexAttribFloat2(Attribute.TEXTURE_COORDS.name)
                        color.input set vertexAttribFloat4(Attribute.COLORS.name)
                        outPosition set mvp * float4Value(vertexAttribFloat3(Attribute.POSITIONS.name), 1f)
                    }
                }
                fragmentStage {
                    main {
                        val atlasTex = texture2d(UNIFORM_TEXTURE)
                        val textureOffset = uniformInt2(UNIFORM_OFFSET)
                        val tileSize = uniformInt2(UNIFORM_TILESIZE)
                        val gray = uniformInt1(UNIFORM_GRAY)
                        val atlasTexSize = textureSize2d(atlasTex).toFloat2()
                        val texel = texelFetch(atlasTex,
                            trunc(texCoords.output * tileSize.toFloat2() + textureOffset.toFloat2()).toInt2()
                        )
                        `if`(gray gt 0.const) {
                            colorOutput(texel.float3("xxx"))
                        }.`else` {
                            colorOutput(texel)
                        }
                    }
                }
            }
        }
    }
}


open class Sprite3d(
    private val spriteSize: Vec2i?, // in pixels size of sprite view
    var texture: Texture3d?,
    private val regionSize: Vec2i?, // how much get from texture by pixels
    name: String? = null,
    private val mirrorTexCoordsY: Boolean = false
) : Group("sprite 3d $name group") {
    val spriteShader by lazy { SpriteAtlasShader() }
    var tileIndex = 0

    val _spriteSize = MutableVec2i(spriteSize?.x ?: 0, spriteSize?.y ?: 0)
    val _regionSize = MutableVec2i(regionSize?.x ?: 0, regionSize?.y ?: 0)
    val onResize = mutableListOf<Sprite3d.(x: Int, y: Int) -> Unit>()

    init {
        buildMesh()
        onUpdate += {
            if ( _spriteSize == Vec2i.ZERO || _regionSize == Vec2i.ZERO ) {
                if ( texture?.loadingState == Texture.LoadingState.LOADED ) {
                    if ( _spriteSize == Vec2i.ZERO ) {
                        _spriteSize.x = texture!!.loadedTexture!!.width
                        _spriteSize.y = texture!!.loadedTexture!!.height
                        onResize.forEach { it(this, _spriteSize.x, _spriteSize.y) }
                    }
                    if ( _regionSize == Vec2i.ZERO ) {
                        _regionSize.x = texture!!.loadedTexture!!.width
                        _regionSize.y = texture!!.loadedTexture!!.height
                    }

                    transform.scale(_spriteSize.x.toDouble(), _spriteSize.y.toDouble(), 1.0)
                }
            }
            //transform.pus

        }
    }

    private fun buildMesh() {
        removeAllChildren()
        setIdentity()
        +mesh(SpriteAtlasShader.SPRITE_MESH_ATTRIBS, name) {
            generate {
                rect {
                    width = 1f//(spriteSize ?: fullTexSize).x.toFloat()
                    height = 1f//(spriteSize ?: fullTexSize).y.toFloat()
                    origin.set(-width / 2f, -height / 2f, 0f)

                    if (mirrorTexCoordsY) {
                        mirrorTexCoordsY()
                    }
                }
                shader = spriteShader
            }

            onUpdate += {
                spriteShader.texture = this@Sprite3d.texture
                spriteShader.tileIndex = this@Sprite3d.tileIndex
//                spriteShader.grayScaled = if ( grayScaled ) 1 else 0
            }
        }
    }

    class SpriteAtlasShader : KslShader(Model(), pipelineConfig) {
        // or region from tex
        var texture by texture3d(UNIFORM_TEXTURE)
        var tileIndex by uniform1i(UNIFORM_OFFSET)

        companion object {
            private const val UNIFORM_TEXTURE = "tex"
            private const val UNIFORM_OFFSET = "texOffset"
            private const val UNIFORM_TILESIZE = "tileSize"
            private const val UNIFORM_GRAY = "gray"

            val SPRITE_MESH_ATTRIBS = listOf(Attribute.POSITIONS, Attribute.TEXTURE_COORDS, Attribute.COLORS)
            private val pipelineConfig = PipelineConfig().apply {
                blendMode = BlendMode.BLEND_PREMULTIPLIED_ALPHA
                cullMethod = CullMethod.NO_CULLING
                depthTest = DepthCompareOp.DISABLED
            }
        }

        class Model : KslProgram("sprite") {
            init {
                val texCoords = interStageFloat2()
                val color = interStageFloat4()

                vertexStage {
                    main {
                        val mvp = mat4Var(mvpMatrix().matrix)
                        texCoords.input set vertexAttribFloat2(Attribute.TEXTURE_COORDS.name)
                        color.input set vertexAttribFloat4(Attribute.COLORS.name)
                        outPosition set mvp * float4Value(vertexAttribFloat3(Attribute.POSITIONS.name), 1f)
                    }
                }
                fragmentStage {
                    main {
                        val atlasTex = texture3d(UNIFORM_TEXTURE)
                        val tileIndex = uniformInt1(UNIFORM_OFFSET)
                        val atlasTexSize = textureSize3d(atlasTex).toFloat3()
                        val xyInTile = (texCoords.output * atlasTexSize.xy).toInt2()
                        val texel = texelFetch(atlasTex, int3Value(xyInTile.x, xyInTile.y, tileIndex))
                        colorOutput(texel)
                    }
                }
            }
        }
    }
}