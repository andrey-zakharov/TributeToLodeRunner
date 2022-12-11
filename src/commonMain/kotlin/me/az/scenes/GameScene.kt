package me.az.scenes

import AnimationFrames
import App
import App.Companion.bg
import AppContext
import ImageAtlas
import ImageAtlasSpec
import RunnerController
import SoundPlayer
import TileSet
import ViewSpec
import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.Mat4f
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
import me.az.ilode.TileLogicType
import me.az.ilode.anyKeyPressed
import me.az.shaders.CRTShader
import me.az.shaders.MaskShader
import me.az.view.*
import kotlin.math.ceil
import kotlin.math.sqrt

typealias Sequences = Map<String, List<Int>>

open class GameScene(val game: Game,
                     val assets: AssetManager,
                     val appContext: AppContext,
                     private val visibleSize: Vec2i = Vec2i(28, 16), // in tiles only level, but + ground + status?
                     val conf: ViewSpec = ViewSpec(tileSize = Vec2i(game.state.spriteMode.value.tileWidth, game.state.spriteMode.value.tileHeight)),
                     name: String?,
) : AsyncScene(name) {

    val currentShutter get() = shatterRadiusAnim.value.value
    var levelView: LevelView? = null

    //cam
    protected val screenWidth get() = appContext.spriteMode.value.screenWidth
    protected val screenHeight get() = appContext.spriteMode.value.screenHeight

    // for level view

    protected val levelWidth get() = game.state.spriteMode.value.tileWidth * visibleSize.x
    protected val levelHeight get() = game.state.spriteMode.value.tileHeight * visibleSize.y

    protected val maxShatterRadius = ceil(sqrt((levelWidth* levelWidth + levelHeight * levelHeight).toDouble()) / 2).toInt()

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
    private val orthoCam get() = camera as OrthographicCamera

    init {
        appContext.spriteMode.onChange {
            scope.launch {
                reload(it)
                //game.level?.run { dirty = true }
                //levelView?.fullRefresh()
            }
        }

        // recreate on tileWidth change TBD

        camera = App.createCamera(
            appContext.spriteMode.value.screenWidth, appContext.spriteMode.value.screenHeight
        ).apply {
            projCorrectionMode = Camera.ProjCorrectionMode.ONSCREEN
        }
        mainRenderPass.clearColor = Color.BLACK
    }

    protected val currentSpriteSet = mutableStateOf(ImageAtlasSpec(appContext.spriteMode.value))
    private val atlasOrder = listOf("tiles", "runner", "guard", "text")
    protected val atlases = atlasOrder.map { ImageAtlas(it) }
    protected val tilesAtlas = atlases[atlasOrder.indexOf("tiles")]
    protected val fontAtlas = atlases[atlasOrder.indexOf("text")]

    val spriteSystem by lazy { SpriteSystem(SpriteConfig {
        this@GameScene.atlases.forEach { this += it }
    }).apply {
//        mirrorTexCoordsY = true
    }}

    // out of mask, for status text
    val uiSpriteSystem by lazy {
        SpriteSystem(SpriteConfig {
            this += fontAtlas
            this += tilesAtlas // for ground
        }).also { ss ->
            ss.onUpdate += { ev ->
                groundTiles.forEachIndexed { index, tile ->
                    //update pos
                    // under level view - level view posed by world coords aligned to top edge of camera's view
                    // but we need here local coords 'in tiles' because of uiSprite is scaled.
                    tile.modelMat.setIdentity().translate(index.toFloat() - game.level!!.width/2f, 0.4f, 0f)
                        //.translate( -levelWidth / 2f, 0f, 0f)
                }
            }
             // layout depends on tileset


        }
    }

    protected var runnerAnims = AnimationFrames(atlasOrder.indexOf("runner"), "runner")
    protected var guardAnims = AnimationFrames(atlasOrder.indexOf("guard"), "guard")
    protected var tilesAnims = AnimationFrames(atlasOrder.indexOf("tiles"), "tiles")

    // val sequencesAtlas = mutableMapOf<String, List<Int>>() // pair of atlasId, tile id
    // val sequences = mutableMapOf<String, List<Int>>() // pair of atlasId, tile id

    protected val sounds = SoundPlayer(assets)

    private fun refreshSequences() {

        // default one frame anim of tiles from atlas
        tilesAtlas.nameIndex.forEach { (k, v) ->
            if ( !tilesAnims.sequence.containsKey(k) ) {
                tilesAnims.sequence[k] = listOf(v)
            }
        }
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
    }

    override suspend fun loadResources(assets: AssetManager, ctx: KoolContext) = with(assets) {
        reload(appContext.spriteMode.value)
        sounds.loadSounds()
        Unit
    }

    fun SpriteSystem.textView(text: MutableStateValue<String>, init: TextView.() -> Unit = {})
        = TextView(this, text, cfg.atlases[cfg.atlasIdByName["text"]!!], init)
    fun SpriteSystem.textView(text: String, init: TextView.() -> Unit = {})
        = textView( mutableStateOf(text), init)

    val systems = listOf(spriteSystem, uiSpriteSystem)
    override fun setup(ctx: KoolContext) {
        game.soundPlayer = sounds
        addNode(bg, 0)
        +RunnerController(ctx.inputMgr, game.runner)

        appContext.spriteMode.onChange {
            updateScales(it)
        }

        +uiSpriteSystem
        spriteSystem.dirty = true
        uiSpriteSystem.dirty = true

        game.reset() // start game
        updateScales()
    }

    private val groundTiles: List<SpriteInstance> by lazy {
        val a = uiSpriteSystem.cfg.atlasIdByName["tiles"]!!
        val ground = tilesAnims.sequence["ground"]!!.first()
        (0 until (game.level?.width ?: 28)).map { x ->
            uiSpriteSystem.sprite( a, ground, Mat4f() )
        }
    }

    private fun updateScales(tileSet: TileSet = appContext.spriteMode.value) {
        systems.forEach {
            it.transform.resetScale()
            it.scale(
                tileSet.tileWidth.toFloat(),
                tileSet.tileHeight.toFloat(), 1f
            )
            it.updateModelMat()
        }
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

/*        val textAtlas = spriteSystem.cfg.atlasIdByName["text"]!!
        for (i in 0 until 10 ) {
            spriteSystem.sprite(
                atlasId = textAtlas,
                pos = Vec2f(i.toFloat(), 0f),
                tileIndex = i
            )
            spriteSystem.sprite(
                atlasId = textAtlas,
                pos = Vec2f(i.toFloat(), 1f),
                tileIndex = i + 1
            )
            spriteSystem.sprite(
                atlasId = textAtlas,
                pos = Vec2f(-i.toFloat(), 1f),
                tileIndex = i + 2
            )
        }*/
        levelView?.run {
            // draw by sprite system, but still need updates
            this@GameScene += this
            off = OffscreenRenderPass2d(spriteSystem, renderPassConfig {
                this.name = "bg"
                setSize(levelWidth, levelHeight)
                setDepthTexture(false)
                addColorTexture {
                    colorFormat = TexFormat.RGBA
                    minFilter = FilterMethod.LINEAR
                    magFilter = FilterMethod.LINEAR
                }
            })
        }

        off?.let { pass ->
            pass.camera = App.createCamera(
//                appContext.spriteMode.value.screenWidth, appContext.spriteMode.value.screenHeight
                pass.width, pass.height
            ).apply {
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
            translate(0f, orthoCam.height - levelHeight, 0f)
            +textureMesh {
                generate {
                    rect {
                        size.set( off!!.width.toFloat(), off!!.height.toFloat())
                        origin.set(-width / 2f, 0f, 0f)
                        mirrorTexCoordsY()
                    }
                }
                shader = MaskShader { color { textureColor((crt?:off!!).colorTexture) } }
                onUpdate += {

                    //setIdentity()
                    //(ctx.windowHeight - visibleHeight).toFloat()
                    //translate(0f, appContext.spriteMode.value.screenHeight - visibleHeight.toFloat(), 0f)

                    (shader as MaskShader).visibleRadius = shatterRadiusAnim.tick(it.ctx)
                    // hack to sync anims
                    if (shatterRadiusAnim.progress >= 1f) {
                        shatterRadiusAnim.progress = 0f
                        game.animEnds = true
                    } else if (!game.animEnds && game.runner.anyKeyPressed) {
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