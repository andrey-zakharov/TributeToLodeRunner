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
import me.az.view.TextView

class GameUI(val game: Game,
             val assets: AssetManager,
             val gameSettings: AppContext,
             val conf: ViewSpec = ViewSpec()) : AsyncScene() {
    private val tileSet = gameSettings.spriteMode
    private var fontAtlas: ImageAtlas = ImageAtlas("text") // sub to update

    private val spec = mutableStateOf(ImageAtlasSpec(tileset = gameSettings.spriteMode.value))
    private var dirty = true

    override suspend fun loadResources(assets: AssetManager, ctx: KoolContext) {
        fontAtlas.load(tileSet.value, assets)
        dirty = true
    }

    private fun Game.getStatusText(): String {
        val scores = runner.score.toString().padStart(7, '0')
        val lives = runner.health.toString().padStart(3, '0')
        val level = ((level?.levelId ?: -1) + 1).toString().padStart(3, '0')
        return "score$scores men$lives level$level"
    }


    private val statusText = mutableStateOf(game.getStatusText())

    init {
        // ui camera
        camera = App.createCamera( conf.visibleWidth, conf.visibleHeight )
        mainRenderPass.clearColor = null
        gameSettings.score.onChange {
            dirty = true
        }
        gameSettings.currentLevel.onChange {
            dirty = true
        }
        gameSettings.runnerLifes.onChange {
            dirty = true
        }
    }
    override fun setup(ctx: KoolContext) {
//        +StatusView(game, StringDrawer(fontAtlas, spec, TextView.fontMap, TextView.fontMap[' ']!!))
        +TextView(statusText, fontAtlas, spec) {
            scale(conf.tileSize.x.toFloat(), conf.tileSize.y.toFloat(), 1f)
            translate(-(statusText.value.length / 2f), 0f, 0f)
        }

        onUpdate += {
            if ( dirty ) {
                statusText.set(game.getStatusText())
                dirty = false
            }
        }
    }
}
