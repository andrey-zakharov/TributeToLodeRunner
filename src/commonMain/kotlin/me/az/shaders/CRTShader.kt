package me.az.shaders

import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.FullscreenShaderUtil
import de.fabmax.kool.pipeline.FullscreenShaderUtil.fullscreenQuadVertexStage

class CRTShader() : KslShader(Model(), FullscreenShaderUtil.fullscreenShaderPipelineCfg) {
    var intensity by uniform1f("intensity")
    // input ? cfg?
    var videoSize by uniform2f("video_size")
    var outputSize by uniform2f("output_size")
    var frameCount by uniform1f("frame_count")
    var frameDirection by uniform1f("frame_direction")

    var tex by texture2d("tex")

    class Model : KslProgram("NTSC emulate") {
        val uv = interStageFloat2("uv")


        init {
            dumpCode = true
            fullscreenQuadVertexStage(uv)
            fragmentStage {
                main {
                    // port of https://www.shadertoy.com/view/3t2XRV
                    // #define TAU  6.28318530717958647693
                    val TAU = 6.28318530717958647693f.const

                    val YIQ2RGB = mat3Var(mat3Value(
                        float3Value(1.000f.const, 1.000f.const, 1.000f.const),
                        float3Value(0.956f.const, (-0.272f).const, (-1.106f).const),
                        float3Value(0.621f.const, (-0.647f).const, 1.703f.const)
                    ), "YIQ2RGB")

                    val tex = texture2d("tex")
                    val size = int2Var(textureSize2d(tex), "size")
                    val fragCoord = int2Var((uv.output * size.toFloat2()).toInt2(), "fragCoord")
                    //	Sample composite signal and decode to YIQ
                    val YIQ = float3Var(0f.const3, "YIQ")
                    val n = int1Var((-2).const, "n")
                    `for` (n, n lt 2.const, 1.const) {
                        val pos = float2Var(uv.output + float2Value(n.toFloat1() / size.x.toFloat1(), 0f.const), "pos")
                        val phase = float1Var((fragCoord.x + n).toFloat1() * TAU / 4.0f.const, "phase")
                        YIQ += sampleTexture(tex, pos).rgb * float3Value(1.0f.const, cos(phase), sin(phase))
                    }

                    YIQ /= 4.0f.const3

                    //  Convert YIQ signal to RGB
//                    colorOutput(YIQ2RGB * YIQ)
//                    float3Array(3, listOf(float3Value()))
//                    colorOutput(float3Value(0.5f, 0.5f, 0f))
                    colorOutput(sampleTexture(tex, uv.output))

                }
            }
        }
    }

}