import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.mvpMatrix
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
    var secondaryTiles by texture2d("secondaryTiles")
    var tileSize by uniform2i("tileSize")
    val tileFrames by uniform2fv("frames", conf.totalTiles)
    var field by texture2d("tileMap") // field
    var time by uniform1f("time")

    class Program(conf: TileMapShaderConf) : KslProgram("tilemap") {
        val uv = interStageFloat2("uv")

        init {
//            dumpCode = true
            vertexStage {
                val uMvp = mvpMatrix()
                main {
                    uv.input set vertexAttribFloat2(Attribute.TEXTURE_COORDS.name)
                    outPosition set uMvp.matrix * float4Value(vertexAttribFloat3(Attribute.POSITIONS.name), 1f)
                }
            }
            fragmentStage {
                val field = texture2d("tileMap")
                val tileSet = texture2d("tiles")
                val secondaryTileSet = texture2d("secondaryTiles")
                val time = uniformFloat1("time")
                val tileSize = uniformInt2("tileSize")
                val tileFrames = uniformFloat2Array("frames", conf.totalTiles)

                main {
                    val tileSetSize = float2Var(textureSize2d(tileSet).toFloat2())

                    // in tiles
                    val fieldSize = float2Var(textureSize2d(field).toFloat2())

                    val inField = fieldSize * uv.output
                    val tilePos = floor(inField)
                    val xyWithinTile = (inField - tilePos) * tileSize.toFloat2()

                    val tx = texelFetch(field, tilePos.toInt2())
                    val texelValue = int1Var((tx.r * 255f.const).toInt1())
                    val isHole = (texelValue and 0x80.const) shr 7.const
                    val tileIndex = texelValue and 0x7f.const

                    `if` ( isHole gt 0.const ) {
                        val secTileSetSize = float2Var(textureSize2d(secondaryTileSet).toFloat2())
                        val tilesInTileSet = (secTileSetSize / tileSize.toFloat2()).toInt2()
                        val y = floor(tileIndex.toFloat1() / tilesInTileSet.x.toFloat1()).toInt1()
                        val x = tileIndex - y * tilesInTileSet.x
                        val tileOffset = int2Value(x, y) * tileSize

//                        val d= (xyWithinTile + tileOffset.toFloat2()) / secTileSetSize
//                        colorOutput(float3Value(d.x, 0f.const, d.y))
                        colorOutput(texelFetch(secondaryTileSet,
                            (xyWithinTile + tileOffset.toFloat2()).toInt2()).xyz)

//                        colorOutput(float3Value(0f.const, 0.5f.const, 0.5f.const))
                    }.`else` {

//                        colorOutput(float4Value(0f.const, 0.2f.const, 0f.const, 1f.const))
//                    colorOutput(sampleTexture(field, uv.output) * conf.totalTiles.const.toFloat1())
//                    colorOutput(float4Value(tx.x* conf.totalTiles.const.toFloat1(), t.x * conf.totalTiles.const.toFloat1(), 0f.const, 1f.const))

//                    colorOutput(float4Value(sampleTexture(field, uv.output).r * conf.totalTiles.const.toFloat1(),
//                        tileIndex.toFloat1() / tileSetSize.x , 0f.const, 1f.const))
//                        colorOutput(float3Value(0f.const, 0f.const, isHole.toFloat1()))
                        colorOutput( texelFetch(tileSet, (xyWithinTile + tileFrames[tileIndex]).toInt2()) )
                    }
                }
            }
        }
    }
}

fun Mesh.generateQuad(width: Float, height: Float, mirrorTexCoordsY: Boolean = true) {
    isFrustumChecked = false
    generate {
        rect {
            origin.set(-width/2f, -height/2f, 0f)
            size.set(width, height)
            if (mirrorTexCoordsY) {
                mirrorTexCoordsY()
            }
        }
    }
}
