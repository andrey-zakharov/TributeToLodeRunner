package me.az.scenes

import ImageAtlas
import ImageAtlasSpec
import LevelSpec
import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.TextureData2d
import de.fabmax.kool.scene.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.createUint8Buffer
import me.az.ilode.Game
import me.az.ilode.GameSettings
import me.az.shaders.TileMapShader
import me.az.shaders.TileMapShaderConf
import me.az.view.StatusView
import me.az.view.StringDrawer
import simpleValueTextureProps
import kotlin.experimental.and

class GameUI(val game: Game, val assets: AssetManager, val gameSettings: GameSettings, val conf: LevelSpec = LevelSpec()) : AsyncScene() {
    private val tileSet = gameSettings.spriteMode
    private var fontAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "text"))
    override suspend fun AssetManager.loadResources(ctx: KoolContext) {
        fontAtlas.load(this)
    }

    init {
        // ui camera
        camera = OrthographicCamera("plain").apply {
            projCorrectionMode = Camera.ProjCorrectionMode.ONSCREEN
            isClipToViewport = false
            isKeepAspectRatio = true
            val hw = (visibleTilesX / 2f)*conf.tileSize.x
            top = visibleTilesY * conf.tileSize.y * 1f
            bottom = 0f
            left = -hw
            right = hw
            clipFar = 10f
            clipNear = 0.1f
        }
        mainRenderPass.clearColor = null
    }
    override fun setup(ctx: KoolContext) {
        val fontMap = mutableMapOf<Char, Int>()
        for ( c in '0' .. '9' ) {
            fontMap[c] = c - '0'
        }
        for ( c in 'a' .. 'z') {
            fontMap[c] = c - 'a' + 10
        }
        fontMap['.'] = 36
        fontMap['<'] = 37
        fontMap['>'] = 38
        fontMap['-'] = 39
        fontMap[' '] = 43
        fontMap[':'] = 44
        fontMap['_'] = 45

        +StatusView(game, StringDrawer(fontAtlas, fontMap, fontMap[' ']!!))
    }
}
