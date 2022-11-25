package me.az.view

import ImageAtlas
import de.fabmax.kool.math.Mat4d
import de.fabmax.kool.math.MutableVec2i
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ksl.BasicVertexConfig
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.ColorBlockConfig
import de.fabmax.kool.modules.ksl.blocks.TexCoordAttributeBlock
import de.fabmax.kool.modules.ksl.blocks.mvpMatrix
import de.fabmax.kool.modules.ksl.blocks.texCoordAttributeBlock
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.modules.ksl.model.KslScope
import de.fabmax.kool.modules.ui2.MutableStateValue
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.shading.unlitShader
import de.fabmax.kool.scene.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Float32Buffer
import me.az.utils.format
import me.az.view.SpriteParams.Companion.INSTANCE_ATTRIBS
import me.az.view.SpriteParams.Companion.POSITIONS
import me.az.view.SpriteParams.Companion.TILE_INDEX
import simpleTextureProps

// represent both simple texture2d or frame in atlas
fun sprite2d(texture: Texture2d,
             name: String? = null,
             spriteSize: Vec2i? = null,
             regionSize: Vec2i? = null,
             mirrorTexCoordsY: Boolean = false): Sprite {
    return Sprite(spriteSize, texture, regionSize, name, mirrorTexCoordsY)
}

open class Sprite(
    spriteSize: Vec2i?, // in pixels size of sprite view
    var texture: Texture2d?,
    regionSize: Vec2i?, // how much get from texture by pixels
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
                    if ( _regionSize == Vec2i.ZERO ) {
                        _regionSize.x = texture!!.loadedTexture!!.width
                        _regionSize.y = texture!!.loadedTexture!!.height
                    }
                    if ( _spriteSize == Vec2i.ZERO ) {
                        _spriteSize.x = texture!!.loadedTexture!!.width
                        _spriteSize.y = texture!!.loadedTexture!!.height
                        onResize.forEach { it(this, _spriteSize.x, _spriteSize.y) }
                    }
                }
            }

//            println(this.dump())
        }
    }

    private fun buildMesh() {
        removeAllChildren()
        setIdentity()
        +group("scaler") {
            +mesh(SpriteShader.SPRITE_MESH_ATTRIBS, "${this@Sprite.name} mesh") {
                generate {
                    rect {
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
                    spriteShader.grayScaled = if (grayScaled) 1 else 0
                }
            }

            onResize += {w, h ->
                this@group.transform.resetScale()
                this@group.scale(w.toDouble(), h.toDouble(), 1.0)
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
                blendMode = BlendMode.BLEND_ADDITIVE
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
fun Mat4d.dis(): String {
    return (0..3).joinToString(" |\t") { r ->
        (0..3).joinToString(", ") { c ->
            if ( c == 0 ) "0" else "%.3f".format(this[r, c])
        }
    }
}

fun Node.dump(nestLevel: Int = 0): String {
    val tab = nestLevel * 2
    val c = if ( tab > 0 ) " ".repeat(tab) + "\u2514\u2500" else ""
    return "${c}name=$name modelMat=${modelMat.dis()}"
}
fun Group.dump(level: Int = 0): String {
    return "${(this as Node).dump(level)} transform=${transform.dis()} children=${children.size}\n" +
        children.joinToString("\n") {
            when (it) {
                is Group -> it.dump(level + 1)
                is Node -> it.dump(level + 1)
                else -> "$it"
            }
        }
}

data class SpriteParams(
    val atlasId: Int = 0,
    val pos: Vec2f, // local translation
    val tileIndex: MutableStateValue<Int> = mutableStateOf(0),
    val grayed: Boolean = false,
) {
    // workaround
    val _atlasFloat = atlasId.toFloat()

    companion object {
        internal const val TILE_INDEX = "instance_tileIndex"
        internal const val POSITIONS = "instance_positions"

        internal val INSTANCE_ATTRIBS = listOf(
            Attribute(POSITIONS, GlslType.VEC_2F),
            Attribute(TILE_INDEX, GlslType.VEC_2F) // atlas index, tile index
        )
    }
    fun addSpriteInstance(buf: Float32Buffer) {
        // pos
        buf.put(pos.x)
        buf.put(pos.y)
        // tileindex
        buf.put(_atlasFloat)
        buf.put(tileIndex.value.toFloat())
    }
}

class SpriteConfig(init: SpriteConfig.() -> Unit = {}) {
    val atlases = mutableListOf<ImageAtlas>()

    operator fun plusAssign(x: String) { atlases.add(ImageAtlas(x)) }
    init {
        this.apply(init)
    }
}

class SpriteSystem(
    cfg: SpriteConfig,
    name: String? = null,
): Group(name) {

    val onResize = mutableListOf<SpriteSystem.(x: Int, y: Int) -> Unit>()
    // also global, as attribute?
    var mirrorTexCoordsY: Boolean = false
    var dirty = false

    val sprites = mutableListOf<SpriteParams>()
    val spriteSize: MutableVec2i = MutableVec2i(0, 0) // in pixels size of sprite view
    val regionSize: MutableVec2i = MutableVec2i(0, 0) // how much get from texture by pixels
//    operator fun ImageAtlas.plusAssign(x: SpriteParams) {
//        sprites.add ( SpriteParams(
//            //this.
//        ))
//    }
    fun sprite(atlasId: Int, pos: Vec2f, tileIndex: Int): Int = sprite(atlasId, pos, mutableStateOf(tileIndex))
    fun sprite(atlasId: Int, pos: Vec2f, tileIndex: MutableStateValue<Int>): Int {
        if ( !sprites.add( SpriteParams(atlasId, pos, tileIndex ) ) ) {
            throw RuntimeException("add")
        }

        tileIndex.onChange {
            dirty = true
        }

        dirty = true
        return sprites.size - 1
    }

    val spriteShader = SpriteBatchShader(SpriteShaderConfig().apply {
        vertexCfg.isInstanced = true
        atlasArraySize = cfg.atlases.size
    })

    // use lazy as multi thread cache locker. is it so in kotlin?
    val mesh = mesh(listOf(Attribute.POSITIONS, Attribute.TEXTURE_COORDS, Attribute.COLORS), name) {
            generate {
                rect {
                    // if center posed mode
                    origin.set(-width / 2f, -height / 2f, 0f)
                    if (mirrorTexCoordsY) {
                        mirrorTexCoordsY()
                    }
                }
            }
            shader =
                spriteShader
//                    unlitShader {useColorMap(testTex)}

            instances = MeshInstanceList(INSTANCE_ATTRIBS, sprites.size)

//            onUpdate += ::updateSizes
            onUpdate += {

                if ( dirty ) {
                    cfg.atlases.forEachIndexed { index, imageAtlas ->
                        spriteShader.textures.set(index, imageAtlas.tex.value)
                    }
//                    spriteShader.test = testTex
//                    println(spriteShader.texture?.loadingState == Texture.LoadingState.LOADED)
println("cleaning")
                    instances?.run {
                        clear()
                        addInstances(sprites.size) { buf ->
                            for ( i in sprites.indices ) {
                                sprites[i].addSpriteInstance(buf)
                            }
                        }
                    }

                    //setIdentity().scale(100.0, 100.0, 1.0)
//                    transform.scale(spriteSize.x.toDouble(), spriteSize.y.toDouble(), 1.0)
                    dirty = false
                }

//                spriteShader.grayScaled = if ( grayScaled ) 1 else 0
            }
        }

    init {
        +mesh
    }

    class SpriteShaderConfig {
        val vertexCfg = BasicVertexConfig()
        val pipelineCfg = KslShader.PipelineConfig().apply {
            blendMode = BlendMode.BLEND_PREMULTIPLIED_ALPHA
            cullMethod = CullMethod.NO_CULLING
            depthTest = DepthCompareOp.DISABLED
        }
        var atlasArraySize = 0
        fun pipeline(block: KslShader.PipelineConfig.() -> Unit) {
            pipelineCfg.apply(block)
        }
    }

    class SpriteBatchShader(cfg: SpriteShaderConfig) : KslShader(Model(cfg), cfg.pipelineCfg) {
        // or region from tex
        val textures by texture3dArray(UNIFORM_TEXTURE, cfg.atlasArraySize)
//        var test by texture2d(UNIFORM_TEXTURE2)

        companion object {

            private const val UNIFORM_TEXTURE = "tex"
            private const val UNIFORM_TEXTURE2 = "tex2"
            private const val UNIFORM_OFFSET = "texOffset"
            private const val UNIFORM_TILESIZE = "tileSize"
            private const val UNIFORM_GRAY = "gray"
        }

        private class Model(cfg: SpriteShaderConfig) : KslProgram("sprite") {

            val tileIndex = interStageFloat2("tileindex")

            fun KslScopeBuilder.fetchSprite(const: Int, uv: KslExprFloat2, atlasTexs: KslUniformArray<KslTypeColorSampler3d>): KslTexelFetch<KslTypeColorSampler3d> {
                val atlasTexSize = textureSize3d(atlasTexs[const]).toFloat3()
                val xyInTile = (uv * atlasTexSize.xy).toInt2()
                return texelFetch(atlasTexs[const], int3Value(xyInTile.x, xyInTile.y, tileIndex.output.y.toInt1()))
            }

            init {
                dumpCode = true
                val texCoordBlock: TexCoordAttributeBlock
                val color = interStageFloat4()

                vertexStage {
                    main {
                        val mvp = mat4Var(mvpMatrix().matrix)

                        color.input set vertexAttribFloat4(Attribute.COLORS.name)

                        texCoordBlock = texCoordAttributeBlock()

                        if (cfg.vertexCfg.isInstanced) {
                            tileIndex.input set instanceAttribFloat2(TILE_INDEX)
                            val pos = instanceAttribFloat2(POSITIONS)

                            mvp *= mat4Var(mat4Value(
                                float4Value(1f.const, 0f.const, 0f.const, 0f.const),
                                float4Value(0f.const, 1f.const, 0f.const, 0f.const),
                                float4Value(0f.const, 0f.const, 1f.const, 0f.const),
                                float4Value(pos.x, pos.y, 0f.const, 1f.const)
                            ))
                        }
                        outPosition set mvp * float4Value(vertexAttribFloat3(Attribute.POSITIONS.name), 1f)
                    }
                }
                fragmentStage {
                    main {
                        val atlasId = tileIndex.output.x.toInt1()
                        val atlasTexs = textureArray3d(UNIFORM_TEXTURE, cfg.atlasArraySize)
                        val uv = texCoordBlock.getAttributeCoords(Attribute.TEXTURE_COORDS)
                        // error: sampler arrays indexed with non-constant expressions are forbidden in GLSL ES 3.00 and later
                        for (c in 0 until cfg.atlasArraySize) {
                            `if` ( atlasId eq c.const ) {
                                colorOutput(fetchSprite(c, uv, atlasTexs))
                            }
                        }

//                        colorOutput(float4Value(uv.x, 0.5f.const, 1f.const, 1f.const))
//                        colorOutput(texelFetch(atlasTex, int3Value((uv.x * atlasTexSize.x).toInt1(), (uv.y * atlasTexSize.y).toInt1(), 0.const)))
//                        colorOutput(sampleTexture(atlasTex[0], float3Value(uv.x, uv.y, 0f.const)))
//                        colorOutput(float3Value(uv.x, uv.y, 1f.const))

                    }
                }
            }
        }
    }


//    private var needResize = spriteSize == Vec2i.ZERO || regionSize == Vec2i.ZERO
//    private fun updateSizes(ev: RenderPass.UpdateEvent) {
//        // workaround impossibility to unsubscribe while onUpdate'ing
//        if ( !needResize ) return
//        if ( spriteSize == Vec2i.ZERO || regionSize == Vec2i.ZERO ) {
//            if ( texture?.loadingState == Texture.LoadingState.LOADED ) {
//                if ( regionSize == Vec2i.ZERO ) {
//                    regionSize.x = texture!!.loadedTexture!!.width
//                    regionSize.y = texture!!.loadedTexture!!.height
//                    dirty = true
//                }
//                if ( spriteSize == Vec2i.ZERO ) {
//                    spriteSize.x = texture!!.loadedTexture!!.width
//                    spriteSize.y = texture!!.loadedTexture!!.height
//                    dirty = true
//                }
//            }
//        } else {
//            needResize = false
//            // mesh.onUpdate -= ::updateSizes
//        }
//    }

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