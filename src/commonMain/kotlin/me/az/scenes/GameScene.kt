package me.az.scenes

import AnimationFrames
import App
import AppContext
import ImageAtlas
import ImageAtlasSpec
import LevelView
import RunnerController
import SoundPlayer
import TileSet
import ViewSpec
import backgroundImageFile
import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.*
import de.fabmax.kool.scene.animation.InterpolatedFloat
import de.fabmax.kool.scene.animation.LinearAnimator
import de.fabmax.kool.util.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.az.ilode.Game
import me.az.ilode.GameLevel
import me.az.ilode.anyKeyPressed
import me.az.shaders.MaskShader
import me.az.view.CameraController
import simpleTextureProps
import sprite
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

open class GameScene(val game: Game,
                val assets: AssetManager,
                val appContext: AppContext,
                val conf: ViewSpec = ViewSpec(),
                name: String?,
) : AsyncScene(name) {
    val currentShutter get() = shatterRadiusAnim.value.value
    var levelView: LevelView? = null

    //cam
    protected val visibleWidth = conf.visibleWidth
    protected val visibleHeight = conf.visibleHeight
    protected val maxShatterRadius = ceil(sqrt((visibleWidth* visibleWidth + visibleHeight * visibleHeight).toDouble()) / 2).toInt()

    var cameraController: CameraController? = null
    private var mask: Group? = null

    protected val shatterRadiusAnim = LinearAnimator(InterpolatedFloat(0f, 1f)).apply {
        duration = 1f
    }


    protected var off: OffscreenRenderPass2d? = null


    var lastUpdate = 0.0 // last game tick

    private val ticker = { ev: RenderPass.UpdateEvent ->
        if ((ev.time - lastUpdate) * 1000 >= appContext.speed.value.msByPass) {
            game.tick(ev)
            lastUpdate = ev.time
        }
    }

    private val job = Job()
    protected val scope = CoroutineScope(job)

    init {
        appContext.spriteMode.onChange {
            scope.launch {
                reload(it)
                game.level?.run { dirty = true }
            }
        }


        camera = App.createCamera( conf.visibleWidth, conf.visibleHeight ).apply {
            projCorrectionMode = Camera.ProjCorrectionMode.ONSCREEN
        }


        mainRenderPass.clearColor = null
    }

    protected val currentSpriteSet = mutableStateOf(ImageAtlasSpec(appContext.spriteMode.value))
    protected var tilesAtlas = ImageAtlas("tiles")
    protected var runnerAtlas = ImageAtlas("runner")
    protected var guardAtlas = ImageAtlas("guard")
    protected var fontAtlas = ImageAtlas("text")

    protected var runnerAnims = AnimationFrames("runner")
    protected var guardAnims = AnimationFrames("guard")
    protected var holeAnims = AnimationFrames("hole")

    protected val sounds = SoundPlayer(assets)

    suspend fun reload(newts: TileSet) {
        val newSpec = ImageAtlasSpec(tileset = newts)
        // awaitAll
        tilesAtlas.load(newts, assets)
        runnerAtlas.load(newts, assets)
        guardAtlas.load(newts, assets)
        fontAtlas.load(newts, assets)

        runnerAnims.loadAnimations(newSpec, assets)
        guardAnims.loadAnimations(newSpec, assets)
        holeAnims.loadAnimations(newSpec, assets)

//        runnerAnims = AnimationFrames("runner")
//        guardAnims = AnimationFrames("guard")
//        holeAnims = AnimationFrames("hole")

        currentSpriteSet.set(ImageAtlasSpec(appContext.spriteMode.value))
        game.level?.run { dirty = true }

    }
    override suspend fun loadResources(assets: AssetManager, ctx: KoolContext) = with(assets) {
        val newSpec = ImageAtlasSpec(tileset = appContext.spriteMode.value)
        tilesAtlas.load(appContext.spriteMode.value, this)
        runnerAtlas.load(appContext.spriteMode.value, this)
        guardAtlas.load(appContext.spriteMode.value, this)
        fontAtlas.load(appContext.spriteMode.value, this)

        runnerAnims.loadAnimations(newSpec, this)
        guardAnims.loadAnimations(newSpec, this)
        holeAnims.loadAnimations(newSpec, this)

        sounds.loadSounds()
    }

    override fun setup(ctx: KoolContext) {
        game.soundPlayer = sounds
        +bg
        +RunnerController(ctx.inputMgr, game.runner)

        game.reset() // start game
    }

    protected fun addLevelView(ctx: KoolContext, level: GameLevel) {
        removeLevelView(ctx)

        // views
        levelView = LevelView(
            game,
            level,
            conf,
            tilesAtlas,
            runnerAtlas,
            runnerAnims,
            guardAtlas,
            guardAnims
        )

        levelView?.run {
            off = OffscreenRenderPass2d(this, renderPassConfig {
                this.name = "bg"

                setSize(visibleWidth, visibleHeight)
                setDepthTexture(false)
                addColorTexture {
                    colorFormat = TexFormat.RGBA
                    minFilter = FilterMethod.NEAREST
                    magFilter = FilterMethod.NEAREST
                }
            })
        }

        off?.run {
            camera = App.createCamera( visibleWidth, visibleHeight ).apply {
                projCorrectionMode = Camera.ProjCorrectionMode.OFFSCREEN
            }
            clearColor = Color(0.00f, 0.00f, 0.00f, 0.00f)
            addOffscreenPass(this)

            cameraController = CameraController(this@run.camera as OrthographicCamera, ctx = ctx)
        }

        cameraController?.run {
            this@GameScene += this
            levelView?.also { startTrack(game, it, it.runnerView) }
        }

        // minimap TBD
//        +sprite(Texture2d(simpleValueTextureProps, game.level!!.updateTileMap())).apply {
//            translate(100f, 230f, 0f)
//            scale(5f)
//            grayScaled = true
//        }

        //mask
        mask = group {
            +textureMesh {
                generate {
                    rect {
                        size.set(visibleWidth.toFloat(), visibleHeight.toFloat())
                        origin.set(-width / 2f, 0f, 0f)
                        mirrorTexCoordsY()
                    }
                }
                shader = MaskShader { color { textureColor(off!!.colorTexture) } }
                onUpdate += {
                    (shader as MaskShader).visibleRadius = shatterRadiusAnim.tick(it.ctx)
                    // hack to sync anims
                    if (shatterRadiusAnim.progress >= 1f) game.animEnds = true
                    else if (game.runner.anyKeyPressed) {
                        stopIntro(it.ctx)
                        game.skipAnims = true
                    }
                }
            }
        }
        +mask!!

        onUpdate += ticker // start play
    }

    protected fun removeLevelView(ctx: KoolContext) {
        cameraController?.run {
            stopTrack(game)
            this@GameScene -= this
        }


        onUpdate -= ticker // stop ticker
        off?.run {
            removeOffscreenPass(this)
            ctx.runDelayed(1) { dispose(ctx) }//dispose(ctx)
        }
        off = null
        mask?.run {
            this@GameScene.removeNode(this)
            ctx.runDelayed(1) { dispose(ctx) }
        }
        mask = null


        levelView?.run { ctx.runDelayed(1) { dispose(ctx) } }
        levelView = null
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

    protected fun startOutro(ctx: KoolContext) =
        shatterRadiusAnim.apply {
            speed = 1f
            progress = 0f
            value.from = maxShatterRadius.toFloat()
            value.to = 0f
        }

    protected fun startIntro(ctx: KoolContext) =
        shatterRadiusAnim.apply {
            speed = 1f
            progress = 0f
            value.from = 0f
            value.to = maxShatterRadius.toFloat()
        }

    protected fun stopIntro(ctx: KoolContext) = shatterRadiusAnim.apply {
//        progress = 1f
        speed = 100f
    }

    override fun dispose(ctx: KoolContext) {
        job.cancel()

//        removeLevelView(ctx)
        super.dispose(ctx)
    }

}