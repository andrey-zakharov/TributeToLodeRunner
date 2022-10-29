package me.az.shaders

import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.mvpMatrix
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.Mesh

class TileMapShaderConf() {

}
class TileMapShader(conf: TileMapShaderConf = TileMapShaderConf()) : KslShader(Program(conf), PipelineConfig().apply {
    blendMode = BlendMode.BLEND_PREMULTIPLIED_ALPHA
    cullMethod = CullMethod.NO_CULLING
    depthTest = DepthCompareOp.DISABLED
}) {

    var tiles by texture3d("tiles") // tile set
    var tileSize by uniform2i("tileSize")
    var tileSizeInTileMap by uniform2i("tileSizeInTileMap")
    var field by texture2d("tileMap") // field
    var fieldSize by uniform2f("size") // as we have map rounded to power of 2
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
                val tileSet = texture3d("tiles")
                val time = uniformFloat1("time")

                val tileSize = uniformInt2("tileSize")
                val tileSizeInTileMap = uniformInt2("tileSizeInTileMap")
                val fieldSize = uniformFloat2("size")

                main {

                    // in tiles
//                    val fieldSize = float2Var(textureSize2d(field).toFloat2())
                    val tileSetSize = int3Var(textureSize3d(tileSet))
                    val inField = fieldSize * uv.output

                    val tilePos = floor(inField)
                    val xyWithinTile = (inField - tilePos) * tileSize.toFloat2()

                    val tx = texelFetch(field, tilePos.toInt2())
                    val tileIndex = int1Var((tx.r * 255f.const).toInt1())
                    val tileXY = (xyWithinTile * tileSizeInTileMap.toFloat2() / tileSize.toFloat2()).toInt2()
                    colorOutput(texelFetch(tileSet, int3Value(tileXY.x, tileXY.y, tileIndex) ))
//                    colorOutput(texelFetch(tileSet, int3Value((uv.output.x * tileSizeInTileMap.x.toFloat1()).toInt1(), (uv.output.y * tileSizeInTileMap.y.toFloat1()).toInt1(), tileIndex) ))
//                    colorOutput(float3Value(tileIndex.toFloat1() / tileSetSize.z.toFloat1(), tileIndex.toFloat1() / tileSetSize.z.toFloat1(), 0f.const))
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
