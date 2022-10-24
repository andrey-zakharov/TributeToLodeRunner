package me.az.scenes

import App
import AppContext
import ImageAtlas
import ImageAtlasSpec
import ViewSpec
import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.modules.ui2.mutableStateOf
import me.az.ilode.Game
import me.az.view.StatusView
import me.az.view.StringDrawer
import me.az.view.TextView

class GameUI(val game: Game,
             val assets: AssetManager,
             val gameSettings: AppContext,
             val conf: ViewSpec = ViewSpec()) : AsyncScene() {
    private val tileSet = gameSettings.spriteMode
    private var fontAtlas: ImageAtlas = ImageAtlas("text") // sub to update
    private val spec = mutableStateOf(ImageAtlasSpec(tileset = gameSettings.spriteMode.value))

    override suspend fun loadResources(assets: AssetManager, ctx: KoolContext) {
        fontAtlas.load(ImageAtlasSpec(tileSet.value), assets)
    }

    init {
        // ui camera
        camera = App.createCamera( conf.visibleWidth, conf.visibleHeight )
        mainRenderPass.clearColor = null
    }
    override fun setup(ctx: KoolContext) {
        +StatusView(game, StringDrawer(fontAtlas, spec, TextView.fontMap, TextView.fontMap[' ']!!))
    }
}
