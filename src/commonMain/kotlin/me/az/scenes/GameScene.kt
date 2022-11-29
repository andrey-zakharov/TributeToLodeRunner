package me.az.scenes

import AnimationFrames
import App
import App.Companion.bg
import AppContext
import ImageAtlas
import ImageAtlasSpec
import LevelView
import RunnerController
import SoundPlayer
import TileSet
import ViewSpec
import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.MutableStateValue
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.FullscreenShaderUtil.generateFullscreenQuad
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
import me.az.shaders.CRTShader
import me.az.shaders.MaskShader
import me.az.view.CameraController
import me.az.view.SpriteConfig
import me.az.view.SpriteSystem
import kotlin.math.ceil
import kotlin.math.sqrt

typealias Sequences = Map<String, Pair<Int, List<Int>>>

open class GameScene(val game: Game,
                     val assets: AssetManager,
                     val appContext: AppContext,
                     val conf: ViewSpec = ViewSpec(tileSize = Vec2i(game.state.spriteMode.value.tileWidth, game.state.spriteMode.value.tileHeight)),
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
    protected var crt: OffscreenRenderPass2d? = null


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

        // recreate on tileWidth change TBD

        camera = App.createCamera( conf.visibleWidth, conf.visibleHeight ).apply {
            projCorrectionMode = Camera.ProjCorrectionMode.ONSCREEN
        }


        mainRenderPass.clearColor = Color.BLACK
    }

    protected val currentSpriteSet = mutableStateOf(ImageAtlasSpec(appContext.spriteMode.value))
    private val atlasOrder = listOf("tiles", "runner", "guard", "text")
    protected val atlases = atlasOrder.map { ImageAtlas(it) }
    protected val tilesAtlas = atlases[0]
    protected val fontAtlas = atlases[3]

    val spriteSystem by lazy { SpriteSystem(SpriteConfig {
        this@GameScene.atlases.forEach { this += it }
    }).apply {
//        mirrorTexCoordsY = true
    }}

    protected var runnerAnims = AnimationFrames(atlasOrder.indexOf("runner"), "runner")
    protected var guardAnims = AnimationFrames(atlasOrder.indexOf("guard"), "guard")
    protected var tilesAnims = AnimationFrames(atlasOrder.indexOf("tiles"), "tiles")

    // val sequencesAtlas = mutableMapOf<String, List<Int>>() // pair of atlasId, tile id
    // val sequences = mutableMapOf<String, List<Int>>() // pair of atlasId, tile id

    protected val sounds = SoundPlayer(assets)

    private fun refreshSequences() {
//        println(tilesAnims.sequence)
//        sequences.clear()
//        // but order remains same. TBD reinvent some animation system ;)
//        holeAnims.sequence.forEach { (name, list) -> sequences[name] = Pair(0, list) }
//        runnerAnims.sequence.forEach { (name, list) -> sequences[name] = Pair(1, list) }
//        guardAnims.sequence.forEach { (name, list) -> sequences[name] = Pair(2, list) }
    }
    private suspend fun reload(newts: TileSet) {
        val newSpec = ImageAtlasSpec(tileset = newts)
        // awaitAll
        atlases.forEach { it.load(newts, assets) }

        runnerAnims.loadAnimations(newSpec, assets)
        guardAnims.loadAnimations(newSpec, assets)
        tilesAnims.loadAnimations(newSpec, assets)
        tilesAnims.appendFrom(assets, newSpec, "hole")

        // sequences could changed from tileset to tileset
        refreshSequences()

        currentSpriteSet.set(ImageAtlasSpec(appContext.spriteMode.value))

        game.level?.run {
            fillGround(tilesAtlas.nameIndex)
            dirty = true
        }

    }
    override suspend fun loadResources(assets: AssetManager, ctx: KoolContext) = with(assets) {
        val newSpec = ImageAtlasSpec(tileset = appContext.spriteMode.value)

        atlases.forEach { it.load(appContext.spriteMode.value, this) }

        runnerAnims.loadAnimations(newSpec, this)
        guardAnims.loadAnimations(newSpec, this)
        tilesAnims.loadAnimations(newSpec, this)
        tilesAnims.appendFrom(assets, newSpec, "hole")

        sounds.loadSounds()

        spriteSystem.cfg.atlases.map {
            it.load(appContext.spriteMode.value, this@with)
        }
        refreshSequences()

        // default one frame anim of tiles from atlas
        tilesAtlas.nameIndex.forEach { (k, v) ->
            if ( !tilesAnims.sequence.containsKey(k) ) {
                tilesAnims.sequence[k] = listOf(v)
            }
        }

        Unit
    }

    fun SpriteSystem.text(text: MutableStateValue<String>) {
        //sprite(3)
    }

    override fun setup(ctx: KoolContext) {
        game.soundPlayer = sounds
//        addNode(bg, 0)
        +RunnerController(ctx.inputMgr, game.runner)

        spriteSystem.scale(
            appContext.spriteMode.value.tileWidth.toFloat(),
            appContext.spriteMode.value.tileHeight.toFloat(), 1f
        )
        appContext.spriteMode.onChange {
            spriteSystem.transform.resetScale()
                .scale(
                    it.tileWidth.toDouble(),
                    it.tileHeight.toDouble(), 1.0
                )
        }
//        for ( y in -5 until 5 ) {
//            for (i in -2 .. 2) {
//                spriteSystem.sprite(
//                    0, i.toFloat(), y.toFloat(), i+2
//                )
//            }
//        }
//        +spriteSystem
        spriteSystem.dirty = true

        game.reset() // start game

    }

    protected fun addLevelView(ctx: KoolContext, level: GameLevel) {
        removeLevelView(ctx)

        // views
        levelView = LevelView(
            spriteSystem,
            game,
            level,
            conf,
            tilesAnims,
            runnerAnims,
            guardAnims,
            sounds.bank
        )

        levelView?.run {
            // draw by sprite system, but still need updates
            this@GameScene += this
            off = OffscreenRenderPass2d(spriteSystem, renderPassConfig {
                this.name = "bg"

                setSize(visibleWidth, visibleHeight)
                setDepthTexture(false)
                addColorTexture {
                    colorFormat = TexFormat.RGBA
                    minFilter = FilterMethod.LINEAR
                    magFilter = FilterMethod.LINEAR
                }
            })
        }

        off?.let { pass ->
            pass.camera = App.createCamera( visibleWidth, visibleHeight ).apply {
                projCorrectionMode = Camera.ProjCorrectionMode.OFFSCREEN
            }
            pass.clearColor = Color(0.00f, 0.00f, 0.00f, 0.00f)
            addOffscreenPass(pass)

            cameraController = CameraController(pass.camera as OrthographicCamera, ctx = ctx)

//            crt = OffscreenRenderPass2d(Group(), renderPassConfig {
//                setDynamicSize()
//                setDepthTexture(false)
//                addColorTexture {
//                    colorFormat = TexFormat.RGBA
//                    minFilter = FilterMethod.NEAREST
//                    magFilter = FilterMethod.NEAREST
//                }
//            })

            crt?.let {
                (it.drawNode as Group).apply {
                    +textureMesh {
                        generateFullscreenQuad()
                        shader = CRTShader().apply {
                            tex = off!!.colorTexture!!
                        }
                    }
                }
                it.dependsOn(pass)
                addOffscreenPass(it)
                onRenderScene += { ctx ->
                    val mapW = off!!.viewport.width
                    val mapH = off!!.viewport.height

                    if (it.isEnabled && mapW > 0 && mapH > 0 && (mapW != it.width || mapH != it.height)) {
                        it.resize(mapW, mapH, ctx)
                    }
                }
            }
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
                shader = MaskShader { color { textureColor((crt?:off!!).colorTexture) } }
                onUpdate += {
                    (shader as MaskShader).visibleRadius = shatterRadiusAnim.tick(it.ctx)
                    // hack to sync anims
                    if (shatterRadiusAnim.progress >= 1f) {
                        shatterRadiusAnim.progress = 0f
                        game.animEnds = true
                    }
                    else if (!game.animEnds && game.runner.anyKeyPressed) {
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

    private val bg by lazy { bg() }

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