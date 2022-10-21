package me.az.scenes

import AnimationFrames
import App
import AppContext
import ImageAtlas
import ImageAtlasSpec
import RunnerController
import SoundPlayer
import TileSet
import ViewSpec
import backgroundImageFile
import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.Camera
import de.fabmax.kool.scene.OrthographicCamera
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import me.az.ilode.Game
import simpleTextureProps
import sprite
import kotlin.math.max
import kotlin.math.min

open class GameScene(val game: Game,
                val assets: AssetManager,
                val appContext: AppContext,
                val conf: ViewSpec = ViewSpec(),
                name: String?,
) : AsyncScene(name) {
    private val job = Job()
    private val scope = CoroutineScope(job)
    var lastUpdate = 0.0 // last game tick

    init {
        appContext.spriteMode.onChange {
            scope.launch {
                reload(it)
            }
        }


        camera = App.createCamera( conf.visibleWidth, conf.visibleHeight ).apply {
            projCorrectionMode = Camera.ProjCorrectionMode.ONSCREEN
        }


        mainRenderPass.clearColor = null
    }

    protected val tileSet get() = appContext.spriteMode.value

    protected var tilesAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "tiles"))
    protected var runnerAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "runner"))
    protected var guardAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "guard"))
    protected var holeAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "hole"))
    protected var fontAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "text"))

    protected var runnerAnims = AnimationFrames("runner")
    protected var guardAnims = AnimationFrames("guard")
    protected var holeAnims = AnimationFrames("hole")

    protected val sounds = SoundPlayer(assets)

    suspend fun asyncAtlas(tileSet: TileSet, name: String) = scope.async {
        ImageAtlas(ImageAtlasSpec(tileSet, name)).also { it.load(assets) }
    }

    suspend fun reload(newts: TileSet) {
        tilesAtlas = asyncAtlas(newts, "tiles").await()
        runnerAtlas = asyncAtlas(newts, "runner").await()
        guardAtlas = asyncAtlas(newts, "guard").await()
        holeAtlas = asyncAtlas(newts, "hole").await()
        fontAtlas = asyncAtlas(newts, "text").await()

        runnerAnims = AnimationFrames("runner")
        guardAnims = AnimationFrames("guard")
        holeAnims = AnimationFrames("hole")
    } 
    override suspend fun loadResources(assets: AssetManager, ctx: KoolContext) = with(assets) {
        tilesAtlas.load(this)
        runnerAtlas.load(this)
        guardAtlas.load(this)
        holeAtlas.load(this)
        fontAtlas.load(this)
        runnerAnims.loadAnimations(ctx)
        guardAnims.loadAnimations(ctx)
        holeAnims.loadAnimations(ctx)

        sounds.loadSounds()
    }

    override fun setup(ctx: KoolContext) {
        game.runner.sounds = sounds
        +bg

        game.reset()
        +RunnerController(ctx.inputMgr, game.runner)
    }

    private val bg by lazy {
        sprite( texture = Texture2d(simpleTextureProps) {
            it.loadTextureData(backgroundImageFile, simpleTextureProps.format)
        } ).apply {
            grayScaled = true
            onResize += { w, h ->
                val imageMinSide = min(w, h)
                val camSide = max((camera as OrthographicCamera).width, (camera as OrthographicCamera).height)
                scale(camSide / imageMinSide)
            }
            // paralax TBD
        }
    }

    override fun dispose(ctx: KoolContext) {
        super.dispose(ctx)
        job.cancel()
    }

}