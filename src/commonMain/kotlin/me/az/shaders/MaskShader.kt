package me.az.shaders

import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.modules.ksl.blocks.convertColorSpace
import de.fabmax.kool.modules.ksl.blocks.fragmentColorBlock
import de.fabmax.kool.modules.ksl.blocks.mvpMatrix
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.BlendMode
import de.fabmax.kool.pipeline.FullscreenShaderUtil.fullscreenQuadVertexStage

open class MaskShader(cfg: Config, model: KslProgram = MaskModel(cfg)) : KslUnlitShader(cfg, model) {
    var visibleRadius by uniform1f("radius") // in pixels

    constructor(block: Config.() -> Unit) : this(Config().apply(block))

    class MaskModel(cfg: Config) : KslProgram("Mask Shader") {
        val uv = interStageFloat2("uv")
        init {
            vertexStage {
                main {
                    val mvp = mat4Var(mvpMatrix().matrix)
                    uv.input set vertexAttribFloat2(Attribute.TEXTURE_COORDS.name)
                    if (cfg.isInstanced) {
                        mvp *= instanceAttribMat4(Attribute.INSTANCE_MODEL_MAT.name)
                    }
                    outPosition set mvp * float4Value(vertexAttribFloat3(Attribute.POSITIONS.name), 1f)
                }
            }
            fragmentStage {
                main {
                    val darkRadius = uniformFloat1("radius")
                    val tex = texture2d(cfg.colorCfg.primaryTexture?.textureName!!)
                    val texSize = float2Var(textureSize2d(tex).toFloat2())

                    //colorOutput(float4Value(texSize.x, texSize.y, 0f.const, 1f.const))
                    val div = 2f.const
                    val centered = uv.output - 0.5f.const2

                    `if` ( length(floor(texSize * centered / div)) ge darkRadius / div) {
                        colorOutput(0f.const4)

                    }.`else` {
                        val colorBlock = fragmentColorBlock(cfg.colorCfg)
                        val baseColor = float4Port("baseColor", colorBlock.outColor)
                        val outRgb = float3Var(baseColor.rgb)
                        outRgb set convertColorSpace(outRgb, cfg.colorSpaceConversion)
                        if (cfg.pipelineCfg.blendMode == BlendMode.BLEND_PREMULTIPLIED_ALPHA) {
                            outRgb set outRgb * baseColor.a
                        }
                        colorOutput(outRgb, baseColor.a)
                    }
                }
            }
            cfg.modelCustomizer?.invoke(this)
        }
    }
}

