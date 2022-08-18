import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.Mesh

class TileMapShaderConf(val totalTiles: Int) {

}
class TileMapShader(conf: TileMapShaderConf) : KslShader(Program(conf), PipelineConfig().apply {
    blendMode = BlendMode.DISABLED
    cullMethod = CullMethod.NO_CULLING
    depthTest = DepthCompareOp.DISABLED
}) {

    var tiles by texture2d("tiles") // tile set
    var tileSize by uniform2i("tileSize")
    val tileFrames by uniform2fv("frames", conf.totalTiles)
    var field by texture2d("tileMap") // field
    var time by uniform1f("time")

    var fitMatrix by uniformMat4f("fitMatrix")


    class Program(conf: TileMapShaderConf) : KslProgram("tilemap") {
        val uv = interStageFloat2("uv")

        init {
//            dumpCode = true
            vertexStage {
                val uScaleMat = uniformMat4("fitMatrix")
                main {
                    uv.input set vertexAttribFloat2(Attribute.TEXTURE_COORDS.name)
                    outPosition set uScaleMat * float4Value(vertexAttribFloat3(Attribute.POSITIONS.name), 1f)
                }
            }
            fragmentStage {
                val field = texture2d("tileMap")
                val tileSet = texture2d("tiles")
                val time = uniformFloat1("time")
                val tileSize = uniformInt2("tileSize")
                val tileFrames = uniformFloat2Array("frames", conf.totalTiles)


                main {
                    val tileSetSize = float2Var(textureSize2d(tileSet).toFloat2())
                    // in tiles
                    val fieldSize = float2Var(textureSize2d(field).toFloat2())

                    val inField = fieldSize * uv.output
                    val tilePos = floor(inField)
                    val t = sampleTexture(field, tilePos / fieldSize)
                    val tileIndex = intVar((t.r * 255f.const).toInt1())
                    
//                    colorOutput(sampleTexture(field, uv.output) * conf.totalTiles.const.toFloat1())
//                    colorOutput(float4Value(tileFrames[tileIndex].x / tileSetSize.x, tileFrames[tileIndex].y / tileSetSize.y, 0f.const, 1f.const))
//                    colorOutput(float4Value(tileFrames[tileIndex].x / tileSetSize.x, tileIndex.toFloat1() / conf.totalTiles.const.toFloat1(), 0f.const, 1f.const))
                    colorOutput(sampleTexture(tileSet,((inField - tilePos) * tileSize.toFloat2() + tileFrames[tileIndex]) / tileSetSize))
                }
            }
        }
    }
}

fun Mesh.generateFullscreenQuad(mirrorTexCoordsY: Boolean = true) {
    isFrustumChecked = false
    generate {
        rect {
            origin.set(-1f, -1f, 0f)
            size.set(2f, 2f)
            if (mirrorTexCoordsY) {
                mirrorTexCoordsY()
            }
        }
    }
}
