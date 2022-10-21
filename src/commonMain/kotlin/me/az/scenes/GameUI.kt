package me.az.scenes

import ImageAtlas
import ImageAtlasSpec
import LevelSpec
import backgroundImageFile
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
import me.az.view.TextDrawer
import simpleTextureProps
import simpleValueTextureProps
import sprite
import kotlin.experimental.and
import kotlin.math.max
import kotlin.math.min

class GameUI(val game: Game, val assets: AssetManager, val gameSettings: GameSettings, val conf: LevelSpec = LevelSpec()) : AsyncScene() {
    private val tileSet = gameSettings.spriteMode
    private var fontAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "text"))
    override suspend fun AssetManager.loadResources(ctx: KoolContext) {
        fontAtlas.load(this)
    }

    init {
        // ui camera
        camera = App.createCamera( conf.visibleWidth, conf.visibleHeight )
        mainRenderPass.clearColor = null
    }
    override fun setup(ctx: KoolContext) {
        +StatusView(game, StringDrawer(fontAtlas, TextDrawer.fontMap, TextDrawer.fontMap[' ']!!))
    }
}
