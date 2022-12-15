package me.az.view

import ImageAtlas
import de.fabmax.kool.math.*
import de.fabmax.kool.modules.ksl.BasicVertexConfig
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.TexCoordAttributeBlock
import de.fabmax.kool.modules.ksl.blocks.mvpMatrix
import de.fabmax.kool.modules.ksl.blocks.texCoordAttributeBlock
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.modules.ui2.MutableStateValue
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.*
import de.fabmax.kool.scene.animation.Animator
import de.fabmax.kool.scene.animation.InterpolatedFloat
import de.fabmax.kool.scene.animation.InverseSquareAnimator
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Float32Buffer
import me.az.utils.format
import me.az.view.SpriteInstance.Companion.BGCOLOR
import me.az.view.SpriteInstance.Companion.INSTANCE_ATTRIBS
import me.az.view.SpriteInstance.Companion.MODMAT
import me.az.view.SpriteInstance.Companion.TILE_INDEX

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
    return (0..3).joinToString("\n") { r ->
        (0..3).joinToString(" ") { c ->
            "%.3f".format(this[r, c])
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
                else -> it.dump(level + 1)
            }
        }
}

class Vec2fOnBuf(private val buf: Float32Buffer, val pos: Int, init: Vec2f) : MutableVec2f(init.x, init.y) {
    override var x: Float
        get() = buf[pos]
        set(value) { buf[pos] = value }
    override var y: Float
        get() = buf[pos+1]
        set(value) { buf[pos+1] = value;}
}

data class SpriteInstance(
    val atlasId: MutableStateValue<Int> = mutableStateOf(0),
    val initialModelMat: Mat4f,
    val tileIndex: MutableStateValue<Int> = mutableStateOf(0),
    val grayed: MutableStateValue<Boolean> = mutableStateOf(false),
    val bgAlpha: MutableStateValue<Float> = mutableStateOf(0f),
) {
    val modelMat = Mat4f().set(initialModelMat)
    // workaround
    val _atlasFloat get() = atlasId.value.toFloat()

    companion object {
        internal const val TILE_INDEX = "instance_tileIndex"
        internal const val MODMAT = "instance_mvp"
        internal const val BGCOLOR = "instance_bg"
        internal val shiftMat = GlslType.MAT_4F.byteSize / GlslType.FLOAT.byteSize

        internal val INSTANCE_ATTRIBS = listOf(
            Attribute(MODMAT, GlslType.MAT_4F),
            Attribute(TILE_INDEX, GlslType.VEC_2F), // atlas index, tile index
            Attribute(BGCOLOR, GlslType.FLOAT),
        )
    }

    fun unbind() { bufPos = null; _parent = null }
    fun addSpriteInstance(instances: MeshInstanceList) {
        bufPos = instances.dataF.position
        _parent = instances
        with(instances.dataF) {
            // pos 0
            put(modelMat.matrix) // 0
            put(_atlasFloat) // 16
            put(tileIndex.value.toFloat()) // 17
            // bg alpha
            put(bgAlpha.value) // 18
        }
    }
    //fun writeModelMat(modelMat : Mat4d) = writeModelMat(Mat4f().set(modelMat))
    // mutable vec is not mutable state
/*    fun writeModelMat(modelMat : Mat4f) {
        var hasChange = false
        val start = 0
        _parent?.run {
            modelMat.matrix.forEachIndexed { i, v ->
                hasChange = hasChange || (dataF[bufPos!! + start + i] != v)
                dataF.set(bufPos!! + start + i, v)
            }
            hasChanged = hasChange
        }
    }*/
    init {
        /*atlasId.onChange { v -> _parent?.run {
            dataF.set(shiftMat + bufPos!!, v.toFloat()); hasChanged = true
        } }
        tileIndex.onChange { v -> _parent?.run {
            dataF.set(shiftMat + bufPos!! + 1, v.toFloat()); hasChanged = true
        } }*/
    }
    private var _parent: MeshInstanceList? = null
    private var bufPos: Int? = null
    override fun toString() = "SpriteInstance bufpos=${bufPos} mvp = $modelMat tile=${atlasId.value}-${tileIndex.value}, bg=${bgAlpha.value}"
}

class SpriteConfig(init: SpriteConfig.() -> Unit = {}) {
    private val _atlases = mutableListOf<ImageAtlas>()
    val atlases: List<ImageAtlas> = _atlases
    val atlasIdByName = mutableMapOf<String, Int>() // name-> id in list above

    operator fun plusAssign(x: String) = plusAssign(ImageAtlas(x))
    operator fun plusAssign(tilesAtlas: ImageAtlas) {
        _atlases.add(tilesAtlas)
        atlasIdByName[_atlases.last().name] = _atlases.size - 1
    }

    init {
        this.apply(init)
    }
}

class SpriteSystem(
    val cfg: SpriteConfig,
    name: String? = null,
): Group(name) {

    val instances = MeshInstanceList(INSTANCE_ATTRIBS, 0)
    val sprites = mutableListOf<SpriteInstance>()

    val onResize = mutableListOf<SpriteSystem.(x: Int, y: Int) -> Unit>()
    // also global, as attribute?
    var mirrorTexCoordsY: Boolean = false
    var dirty = false

    val tmp = Mat4f()
    fun refresh() { // full rebuild
//        println("full refresh system sprite")
        cfg.atlases.forEachIndexed { index, imageAtlas ->
            //if (imageAtlas.tex.value?.loadingState == Texture.LoadingState.LOADED )
            spriteShader.textures.set(index, imageAtlas.tex.value)
        }

        instances.run {
            clear()
            addInstances(sprites.size) { buf ->
                for ( i in sprites.indices ) {
                    sprites[i].addSpriteInstance(instances)
                }
            }
        }

        //setIdentity().scale(100.0, 100.0, 1.0)
//                    transform.scale(spriteSize.x.toDouble(), spriteSize.y.toDouble(), 1.0)
        dirty = false
    }

    // const
    fun sprite(atlasId: Int, tileIndex: Int, modelMat: Mat4f) =
        sprite(mutableStateOf(atlasId), mutableStateOf(tileIndex), modelMat)

    fun sprite(atlasId: Int,
               tileIndex: MutableStateValue<Int>,
               modelMat: Mat4f
    ) = sprite(mutableStateOf(atlasId), tileIndex, modelMat)

    fun sprite(atlasId: MutableStateValue<Int>,
               tileIndex: MutableStateValue<Int>,
               modelMat: Mat4f,
    ): SpriteInstance {

        //tmp.setIdentity().translate(pos.x, pos.y, 0f).scale(scale.value)
        val inst = SpriteInstance(atlasId, modelMat, tileIndex )

        if ( !sprites.add( inst ) ) {
            throw RuntimeException("add")
        }

        val spriteIndex =  sprites.size - 1
//        println("created $spriteIndex $inst")

        dirty = true
        return sprites[spriteIndex]
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
                    //origin.set(-width / 2f, -height / 2f, 0f)
                    if (mirrorTexCoordsY) {
                        mirrorTexCoordsY()
                    }
                }
            }
            shader = spriteShader
            instances = this@SpriteSystem.instances

            //addDebugAxis()
        }

    init {
        +mesh
        cfg.atlases.forEach { it.tex.onChange {
            //dirty = true
        } }
        onUpdate += {
            if ( dirty ) { // TBD rework to not all update
                refresh()
            }
        }
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
            private const val UNIFORM_OFFSET = "texOffset"
            private const val UNIFORM_TILESIZE = "tileSize"
            private const val UNIFORM_GRAY = "gray"
        }

        private class Model(cfg: SpriteShaderConfig) : KslProgram("sprite") {

            val tileIndex = interStageFloat2("tileindex")
            val alpha = interStageFloat1("bgcolor")

            fun KslScopeBuilder.fetchSprite(constAtlasId: Int,
                                            uv: KslExprFloat2,
                                            atlasTexs: KslUniformArray<KslTypeColorSampler3d>): KslTexelFetch<KslTypeColorSampler3d> {
                val atlasTexSize = textureSize3d(atlasTexs[constAtlasId]).toFloat3()
                val xyInTile = (uv * atlasTexSize.xy).toInt2()
                return texelFetch(atlasTexs[constAtlasId], int3Value(xyInTile.x, xyInTile.y, tileIndex.output.y.toInt1()))
            }

            init {
                dumpCode = false
                val texCoordBlock: TexCoordAttributeBlock
                val color = interStageFloat4()

                vertexStage {
                    main {
                        val mvp = mat4Var(mvpMatrix().matrix)

                        color.input set vertexAttribFloat4(Attribute.COLORS.name)

                        texCoordBlock = texCoordAttributeBlock()

                        if (cfg.vertexCfg.isInstanced) {
                            tileIndex.input set instanceAttribFloat2(TILE_INDEX)
                            alpha.input set instanceAttribFloat1(BGCOLOR)
                            val instanceModelMat = instanceAttribMat4(MODMAT)
                            mvp *= instanceModelMat
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
                                val texel = fetchSprite(c, uv, atlasTexs)
                                `if`(texel.a lt 1f.const) {
                                    `if`(alpha.output eq 0f.const) {
//                                        colorOutput(Color.MAGENTA.const)
                                        discard()
                                    }.`else` {
                                        // bg
                                        colorOutput(float3Value(0f.const, 0f.const, 0f.const), alpha.output)
                                    }
//                                    colorOutput(Color.GREEN.const)
                                }.`else` {
                                    colorOutput(texel)
                                }

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

}
