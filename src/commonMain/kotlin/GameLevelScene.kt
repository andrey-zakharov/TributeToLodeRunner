import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.UniversalKeyCode
import de.fabmax.kool.math.*
import de.fabmax.kool.math.spatial.BoundingBox
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.*
import de.fabmax.kool.scene.animation.*

import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Viewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.yield
import me.az.ilode.*
import me.az.shaders.MaskShader
import me.az.view.GameControls
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

abstract class AsyncScene(name: String? = null) : Scene(name) {
    private var sceneState = State.NEW
    init {
        onRenderScene += {
            checkState(it)
        }
    }

    private fun checkState(ctx: KoolContext) {
        if (sceneState == State.NEW) {
            // load resources (async from AssetManager CoroutineScope)
            sceneState = State.LOADING
            ctx.assetMgr.launch {
                loadResources(ctx)
                sceneState = State.SETUP
            }
        }

        if (sceneState == State.SETUP) {
            setup(ctx)
            sceneState = State.RUNNING
        }
    }

    abstract suspend fun AssetManager.loadResources(ctx: KoolContext)
    abstract fun setup(ctx: KoolContext)

    enum class State {
        NEW,
        LOADING,
        SETUP,
        RUNNING
    }
}

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

const val visibleTilesX = 28
const val visibleTilesY = 16 + 1 + 1 // + ground + status
expect fun String.format(vararg args: Any?): String

class GameLevelScene (
    val game: Game,
    val assets: AssetManager,
    val gameSettings: GameSettings,
    name: String? = null,
    private val startNewGame: Boolean = false,

) : AsyncScene(name), CoroutineScope {
    var tileSet: TileSet = TileSet.SPRITES_APPLE2
    val conf = LevelSpec()
    private val currentLevel get() = levels.getLevel(gameSettings.currentLevel, false)
    private val immortal = MutableStateValue(game.state.immortal).also {
        it.onChange { v -> game.state.immortal = v } // save to settings
    }

    protected val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job

    private lateinit var bg: Texture2d
    private var off: OffscreenRenderPass2d? = null
    private var tilesAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "tiles"))
    private var runnerAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "runner"))
    private var guardAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "guard"))
    private var holeAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "hole"))
    private var fontAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "text"))
    private var runnerAnims = AnimationFrames("runner")
    private var guardAnims = AnimationFrames("guard")
    private var holeAnims = AnimationFrames("hole")

    private val sounds = SoundPlayer(assets)
    private val levels = LevelsRep(assets, tilesAtlas, holeAtlas, holeAnims, this)


    class InterpolatedVec3f(val from: MutableVec3f, val `to`: MutableVec3f) : InterpolatedValue<Vec3f>(from) {
        override fun updateValue(interpolationPos: Float) {
            val x = InterpolatedFloat(from.x, to.x)
            val y = InterpolatedFloat(from.y, to.y)
            val z = InterpolatedFloat(from.z, to.z)
            x.interpolate(interpolationPos)
            y.interpolate(interpolationPos)
            z.interpolate(interpolationPos)
            from.set(x.value, y.value, z.value)
        }
    }
    private val cameraPos = InterpolatedVec3f(camera.position, camera.position)
    private val cameraAnimator = SquareAnimator(cameraPos).apply {
        duration = 5000f
        repeating = Animator.REPEAT
        progress = 0f
        speed = 1f
    }
    private val shatterRadiusAnim = LinearAnimator(InterpolatedFloat(0f, 1f)).apply {
        duration = 1f
    }
    val currentShutter get() = shatterRadiusAnim.value.value
    val debug = MutableStateValue("")

    var levelView: LevelView? = null
    var mask: Group? = null
    //cam
    val visibleWidth get() = visibleTilesX * conf.tileSize.x
    val visibleHeight get() = visibleTilesY * conf.tileSize.y

    private val createCamera get() = OrthographicCamera("plain").apply {
        projCorrectionMode = Camera.ProjCorrectionMode.ONSCREEN
        isClipToViewport = false
        isKeepAspectRatio = true
        val hw = visibleWidth / 2f
        top = visibleHeight * 1f
        bottom = 0f
        left = -hw
        right = hw
        clipFar = 10f
        clipNear = 0.1f
    }

    init {
        camera = createCamera.apply {
            projCorrectionMode = Camera.ProjCorrectionMode.ONSCREEN
        }
    }

    override suspend fun AssetManager.loadResources(ctx: KoolContext) {
        tilesAtlas.load(this)
        runnerAtlas.load(this)
        guardAtlas.load(this)
        holeAtlas.load(this)
        fontAtlas.load(this)
        runnerAnims.loadAnimations(ctx)
        guardAnims.loadAnimations(ctx)
        holeAnims.loadAnimations(ctx)

        sounds.loadSounds()
        levels.load(LevelSet.CLASSIC)
        bg = loadAndPrepareTexture("images/cover.jpg", simpleTextureProps)

    }

    fun dispose() {

    }

    override fun setup(ctx: KoolContext) {

        if ( startNewGame ) {
            game.runner.startNewGame()
        }
        game.runner.sounds = sounds

        +sprite(bg).apply {
            grayScaled = true
            val imageMinSide = min(bg.loadedTexture!!.width, bg.loadedTexture!!.height)
            val camSide = max((camera as OrthographicCamera).width, (camera as OrthographicCamera).height)
            scale(camSide / imageMinSide)
            // paralax TBD
        }

        game.onStateChanged += {
            println("GameScene. gameState = $name")
            when(name) {
                "start" -> {
                    // timer to demo, or run
                    startIntro(ctx)
                }
                "run" -> stopIntro(ctx)
                "finish" -> {
                    sounds.playSound("goldFinish")
                    startOutro(ctx)
                }

                "dead" -> {
                    startOutro(ctx)
                }
                "prevlevel" -> {
                    if ( shatterRadiusAnim.speed == 0f ) startOutro(ctx)
                    gameSettings.currentLevel -= 1
                    if ( gameSettings.currentLevel < 0 ) gameSettings.currentLevel += levels.levels.size

                }
                "nextlevel" -> {
                    // black
                    if ( shatterRadiusAnim.speed == 0f ) startOutro(ctx)
                    gameSettings.currentLevel += 1
                    println("nextlevel: ${gameSettings.currentLevel} ${levels.levels.size}")
                    if ( gameSettings.currentLevel >= levels.levels.size ) {
                        // finish game
                    }
                }
                "newlevel" -> {
                    game.level = currentLevel
                    addLevelView(ctx)
                    for ( g in game.guards ) {
                        g.sounds = sounds
                    }
                }
                else -> Unit
            }
        }

        game.reset()

        +RunnerController(ctx.inputMgr, game.runner)
        +lineMesh("x") { addLine(Vec3f.ZERO, Vec3f(1f, 0f, 0f), Color.RED) }
        +lineMesh("y") { addLine(Vec3f.ZERO, Vec3f(0f, 1f, 0f), Color.GREEN) }
        +lineMesh("z") { addLine(Vec3f.ZERO, Vec3f(0f, 0f, 1f), Color.BLUE) }
    }

    private fun addLevelView(ctx: KoolContext) {
        removeLevelView(ctx)

        // views
        levelView = LevelView(
            game,
            currentLevel,
            conf,
            tilesAtlas,
            holeAtlas,
            runnerAtlas,
            runnerAnims,
            guardAtlas,
            guardAnims
        ).also {
            off = OffscreenRenderPass2d(it, renderPassConfig {
                this.name = "bg"

                setSize(visibleWidth, visibleHeight)
                setDepthTexture(false)
                addColorTexture {
                    colorFormat = TexFormat.RGBA
                    minFilter = FilterMethod.NEAREST
                    magFilter = FilterMethod.NEAREST
                }
            }).apply {
                camera = createCamera.apply {
                    projCorrectionMode = Camera.ProjCorrectionMode.OFFSCREEN
                }
                clearColor = Color(0.00f, 0.00f, 0.00f, 0.00f)
            }
        }

        println(levelView)

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

        addOffscreenPass(off!!)

        val cal = calculateCamera(levelView!!, levelView!!.runnerView, off!!.camera as OrthographicCamera)

        // each redraw tick
        updater = { ev ->
            if (game.isPlaying) {
                val pos = cal()
                // anim?
                cameraPos.to.set(
                    pos.x /*+ (ctx.inputMgr.pointerState.primaryPointer.x.toFloat() - it.viewport.width/2) / 50f*/,
                    pos.y /*+ (ctx.inputMgr.pointerState.primaryPointer.y.toFloat() - it.viewport.height/2) / 50f*/,
                    10f
                )
                cameraPos.from.set(cameraPos.value)
            }

            if ((ev.time - lastUpdate) * 1000 >= gameSettings.speed.msByPass) {
                game.tick(ev)
                lastUpdate = ev.time
            }
        }

        onUpdate += updater!!
        // each game tick
        subscriber = updateCamera(off!!.camera as OrthographicCamera, ctx)
        game.onPlayGame += subscriber!!

    }

    private fun removeLevelView(ctx: KoolContext) {
        updater?.run { onUpdate -= this }
        subscriber?.run { game.onPlayGame -= this }
        subscriber = null

        mask?.run {
            this@GameLevelScene.removeNode(this)
            dispose()
        }
        mask = null

        off?.run {
            removeOffscreenPass(this)
            dispose(ctx)
        }
        off = null

        levelView?.dispose(ctx)
        levelView = null
    }

    private var updater: ((RenderPass.UpdateEvent) -> Unit)? = null
    private var subscriber: ((Game, RenderPass.UpdateEvent?) -> Unit)? = null

    private fun calculateCamera(boundNode: Group, followNode: Node, camera: OrthographicCamera): () -> MutableVec3f {

        val deadZone = with(camera) {
            BoundingBox().apply {
                add(Vec3f(-this@with.width / 4, -this@with.height / 4, 0f))
                add(Vec3f(this@with.width/4, this@with.height/4, 0f))
            }
        }
        val borderZone = with(camera) {
            val scaledMin = MutableVec3f(boundNode.bounds.min)
            val scaledMax = MutableVec3f(boundNode.bounds.max)
            boundNode.transform.transform(scaledMin)
            boundNode.transform.transform(scaledMax)

            BoundingBox().apply {
                add(Vec3f( scaledMin.x + this@with.width / 2f, 0f, 0f))
                add(Vec3f( scaledMax.x - this@with.width / 2f, scaledMax.y - this@with.height, 0f))
            }
        }

        return {
            val resultPos = MutableVec3f(followNode.globalCenter)

            with(camera) {
                //camera shift
                resultPos -= Vec3f(0f, height / 2f, 0f)
                // get distance from camera. if more than viewport / 4 - move
                val diff = resultPos - globalLookAt
                if ( !deadZone.contains(diff) ) {
                    if ( diff.x > deadZone.max.x ) {
                        resultPos.x = globalPos.x + diff.x - deadZone.max.x
                    } else if ( diff.x < deadZone.min.x ) {
                        resultPos.x = globalPos.x - (deadZone.min.x - diff.x)
                    } else {
                        resultPos.x = globalPos.x

                    }

                    if ( diff.y > deadZone.max.y ) {
                        resultPos.y = globalPos.y + diff.y - deadZone.max.y
                    } else if ( diff.y < deadZone.min.y ) {
                        resultPos.y = globalPos.y - (deadZone.min.y - diff.y)
                    } else {
                        resultPos.y = globalPos.y
                    }
                } else {
                    resultPos.x = globalPos.x
                    resultPos.y = globalPos.y
                }

                borderZone.clampToBounds(resultPos)

                resultPos

            }
        }

    }
    private fun updateCamera(camera: OrthographicCamera, ctx: KoolContext) = {_: Game, ev: RenderPass.UpdateEvent? ->
        with(camera) {
            position.set(cameraAnimator.tick(ctx))
            lookAt.set(position.x, position.y, 0f)
        }
        Unit
    }

    var lastUpdate = 0.0

    fun setupUi(scope: UiScope) = with(scope) {
        modifier
            .width(Grow(1f, max = WrapContent))
            .height(WrapContent)
            .margin(start = 25.dp, top = 25.dp, bottom = 60.dp)
            .layout(ColumnLayout)
            .alignX(AlignmentX.End)
            .alignY(AlignmentY.Bottom)
        Row {
            Text(debug.use()) {
                modifier
                    .width(WrapContent)
                    .height(WrapContent)
                onUpdate += {
                    debug.set("act = %s (%b)\ninput: %d %d\n%d %d x %d %d\n%s[ %d ]\nguards: %s\nlevel.gold=%d".format(
                        game.level?.act?.get(game.runner.x)?.get(game.runner.y) ?: "<no level>",
                        game.level?.isBarrier(game.runner.x, game.runner.y),
                        game.runner.inputVec.x ?: "<no runner>", game.runner.inputVec.y ?: "<no runner>",
                        game.runner.x, game.runner.ox, game.runner.y, game.runner.oy,
                        game.runner.action.id, game.runner.frameIndex,
                        game.guards.joinToString(" ") { it.hasGold.toString() },
                        game.level?.gold
                    ))
                }
            }
        }
        Row {LabeledSwitch("stop animations", game.stopAnims) }
        Row {LabeledSwitch("stop guards", game.stopGuards) }
        Row {LabeledSwitch("immortal", immortal) }
    }

    fun TextScope.labelStyle(width: Dimension = WrapContent) {
        modifier
            .width(width)
            .align(yAlignment = AlignmentY.Center)
    }

    fun UiScope.LabeledSwitch(label: String, toggleState: MutableStateValue<Boolean>) {
        Text(label) {
            labelStyle(Grow.Std)
            modifier.onClick { toggleState.toggle() }
        }
        Switch(toggleState.use()) {
            modifier
                .alignY(AlignmentY.Center)
                .onToggle { toggleState.set(it) }
        }
    }

    private fun startOutro(ctx: KoolContext) =
        shatterRadiusAnim.apply {
            speed = 1f
            progress = 0f
            value.from = (sqrt((visibleWidth* visibleWidth + visibleHeight * visibleHeight).toDouble()) / 2).toFloat()
            value.to = 0f
        }

    private suspend fun Animator<Float, InterpolatedFloat>.playAnim(from: Float, to: Float) {
        speed = 1f
        progress = 0f
        value.from = 0f
        value.to = (sqrt((visibleWidth* visibleWidth + visibleHeight * visibleHeight).toDouble()) / 2).toFloat()
        while (progress < 1) yield()
    }

    private fun startIntro(ctx: KoolContext) =
        shatterRadiusAnim.apply {
            speed = 1f
            progress = 0f
            value.from = 0f
            value.to = (sqrt((visibleWidth* visibleWidth + visibleHeight * visibleHeight).toDouble()) / 2).toFloat()
        }

    private fun stopIntro(ctx: KoolContext) = shatterRadiusAnim.apply {
//        progress = 1f
        speed = 100f
    }

}

private operator fun Vec3f.minus(position: Vec3f) = Vec3f(
    this.x - position.x,
    this.y - position.y,
    this.z - position.z,
)

val OrthographicCamera.height get() = top - bottom
val OrthographicCamera.width get () = right - left
val OrthographicCamera.circumcircleRadius get() = sqrt(width * width + height * height) / 2
val Viewport.circumcircleRadius: Float get() = (sqrt((width * width + height * height).toDouble()) / 2f).toFloat()
val BoundingBox.width get() = max.x - min.x
val BoundingBox.height get() = max.y - min.y
val BoundingBox.circumcircleRadius get() = sqrt(width * width + height * height) / 2
val Group.globalBounds: BoundingBox get() {
    val scaledMin = MutableVec3f(bounds.min)
    val scaledMax = MutableVec3f(bounds.max)

    transform.transform(scaledMin)
    transform.transform(scaledMax)
    return BoundingBox().apply {
        add(scaledMin)
        add(scaledMax)
    }
}