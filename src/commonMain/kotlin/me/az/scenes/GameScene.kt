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
import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.*
import de.fabmax.kool.math.spatial.BoundingBox
import de.fabmax.kool.modules.ui2.MutableStateValue
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.FullscreenShaderUtil.generateFullscreenQuad
import de.fabmax.kool.pipeline.shading.UnlitShader
import de.fabmax.kool.pipeline.shading.unlitShader
import de.fabmax.kool.scene.*
import de.fabmax.kool.scene.animation.InterpolatedFloat
import de.fabmax.kool.scene.animation.LinearAnimator
import de.fabmax.kool.util.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.az.ilode.Game
import me.az.ilode.GameLevel
import me.az.ilode.GameState
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
    private val outerCamera get() = camera as OrthographicCamera

    init {
        appContext.spriteMode.onChange {
            scope.launch {
                reload(it)
                levelView?.fullRefresh()
            }
        }

        // recreate on tileWidth change TBD

        camera = App.createCamera(
            appContext.spriteMode.value.screenWidth, appContext.spriteMode.value.screenHeight
        ).apply {
            projCorrectionMode = Camera.ProjCorrectionMode.ONSCREEN
        }
        mainRenderPass.clearColor = Color.BLACK

        game.onStateChanged += {
            when(this.name) {
                GameState.GAME_START -> {
                    levelView!!.runnerView.startBlink()
                }
                GameState.GAME_RUNNING -> {
                    levelView!!.runnerView.stopBlink()
                }
                else -> {}
            }
        }
    }

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
                    with(tile.modelMat) {
                        set(
                            Mat4d()
                                .translate(index.toFloat() - game.level!!.width /2f, 1f, 0f)
                                .mul(mask!!.modelMat)
                                .translate(-off!!.camera.position.x, -off!!.camera.position.y, 0f)

//                                .scale(1.0, -1.0, 1.0)
                        )
                        println(tile.modelMat.dump())
                    }
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
    }

    override suspend fun loadResources(assets: AssetManager, ctx: KoolContext) = with(assets) {
        reload(appContext.spriteMode.value)
        sounds.loadSounds()
        Unit
    }

    fun SpriteSystem.textView(text: MutableStateValue<String>, init: TextView.() -> Unit = {})
        = TextView(this, text, cfg.atlases[cfg.atlasIdByName["text"]!!], init = init)
    fun SpriteSystem.textView(text: String, init: TextView.() -> Unit = {})
        = textView( mutableStateOf(text), init)

    val systems = listOf(spriteSystem, uiSpriteSystem)
    override fun setup(ctx: KoolContext) {
        game.soundPlayer = sounds
        addNode(bg, 0)
        +RunnerController(ctx.inputMgr, game.runner)

        appContext.spriteMode.onChange {
            updateScales(it)
            outerCamera.top = off!!.height + appContext.spriteMode.value.tileHeight * 2f
        }

        +uiSpriteSystem
        spriteSystem.dirty = true
        uiSpriteSystem.dirty = true

        updateScales()
        game.reset() // start game
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
            tilesAnims,
            runnerAnims,
            guardAnims,
            sounds.bank
        )

        levelView?.run {
            // draw by sprite system, but still need updates
            this@GameScene += this
        }

        off = OffscreenRenderPass2d(spriteSystem, renderPassConfig {
            this.name = "bg"
            setSize(levelWidth, levelHeight)
            setDepthTexture(false)
            addColorTexture {
                colorFormat = TexFormat.RGBA
                minFilter = FilterMethod.LINEAR
                magFilter = FilterMethod.LINEAR
            }
            println("offscreen pass size: $width x $height")
        }).also { pass ->
            pass.clearColor = Color(0.00f, 0.00f, 0.00f, 0.00f)
/*                val RENDER_SIZE_FACTOR = 1f
                onRenderScene += {ctx ->
                    val mapW = (mainRenderPass.viewport.width * RENDER_SIZE_FACTOR).toInt()
                    val mapH = (mainRenderPass.viewport.height * RENDER_SIZE_FACTOR).toInt()

                    if (mapW > 0 && mapH > 0 && (mapW != pass.width || mapH != pass.height)) {
//                        pass.resize(mapW, mapH, ctx)
                    }
                }*/
            val innerCamera = OrthographicCamera(
//                appContext.spriteMode.value.screenWidth, appContext.spriteMode.value.screenHeight

            ).apply {
                val passWidth = pass.width
                val passHeight = pass.height
                projCorrectionMode = Camera.ProjCorrectionMode.OFFSCREEN
                isClipToViewport = false
                isKeepAspectRatio = true
                val hw = passWidth / 2f
                top = passHeight * 1f
                bottom = 0f
                left = -hw
                right = hw
                clipFar = 10f
                clipNear = 0.1f
            }


            pass.camera = innerCamera
            addOffscreenPass(pass)

            cameraController = CameraController(innerCamera, ctx = ctx, viewGroup = this@GameScene.spriteSystem)

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

            levelView!!.updateModelMat()
            spriteSystem.updateModelMat()

            val borderZone = with(cameraToControl) {
                val scaledMin = MutableVec3f(-0f, -0f, 0f)
                val scaledMax = MutableVec3f(game.level!!.width.toFloat(), game.level!!.height.toFloat(), 0f)
                levelView!!.toGlobalCoords(scaledMin)
                spriteSystem.toGlobalCoords(scaledMin)
                levelView!!.toGlobalCoords(scaledMax)
                spriteSystem.toGlobalCoords(scaledMax)
                println(scaledMin)
                println(scaledMax)

                val halfWidth = this.width / 2f
                val halfHeight = this.height / 2f

                BoundingBox().apply {
                    add(listOf(
                        Vec3f( max(0f, scaledMin.x + halfWidth), max(0f, scaledMax.y - halfHeight), 0f ),
                        Vec3f( min(0f, scaledMax.x - halfWidth),  min(0f, scaledMin.y + halfHeight), 0f),
                    ))
                }
            }

            println(borderZone)

            levelView?.also { startTrack(game, borderZone, it.runnerView.instance.modelMat) }
        }

        // minimap TBD
//        +sprite(Texture2d(simpleValueTextureProps, game.level!!.updateTileMap())).apply {
//            translate(100f, 230f, 0f)
//            scale(5f)
//            grayScaled = true
//        }

        //mask
        mask = group {
            // align top
            // translate(0f, outerCamera.height - levelHeight, 0f)
            +textureMesh {
                generate {
                    rect {
                        size.set( off!!.width.toFloat(), off!!.height.toFloat())
                        origin.set(-width / 2f, 0f, 0f)
                        mirrorTexCoordsY()
                    }
                }

                shader = MaskShader { color { textureColor((crt?:off!!).colorTexture) } }
//                     unlitShader { useColorMap(off!!.colorTexture) }
//                     unlitShader { useStaticColor(Color.RED) }
                onUpdate += {

                    setIdentity()
                    //(ctx.windowHeight - visibleHeight).toFloat()
                    translate(0f, appContext.spriteMode.value.tileHeight * 2f, 0f)

                    (shader as? MaskShader)?.visibleRadius = shatterRadiusAnim.tick(it.ctx)
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

        outerCamera.top = off!!.height + appContext.spriteMode.value.tileHeight * 2f
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