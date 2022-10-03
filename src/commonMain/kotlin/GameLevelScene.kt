import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.UniversalKeyCode
import de.fabmax.kool.math.*
import de.fabmax.kool.math.spatial.BoundingBox
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.*
import de.fabmax.kool.scene.animation.*

import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Viewport
import me.az.ilode.Game
import me.az.ilode.GameSettings
import me.az.ilode.GameState
import me.az.ilode.SCORE_COUNTER
import me.az.shaders.MaskShader
import me.az.utils.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

abstract class AsyncScene(name: String? = null) : Scene(name) {
    protected var sceneState = State.NEW
    init {
        onRenderScene += {
            checkState(it)
        }
    }

    fun checkState(ctx: KoolContext) {
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

) : AsyncScene(name) {
    var tileSet: TileSet = TileSet.SPRITES_APPLE2
    val conf = LevelSpec()
    val currentLevelId = 2
    val currentLevel
        get() = levels.getLevel(currentLevelId)

    private lateinit var bg: Texture2d
    private var tilesAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "tiles"))
    private var runnerAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "runner"))
    private var guardAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "guard"))
    private var holeAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "hole"))
    private var fontAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "text"))
    private var runnerAnims = AnimationFrames("runner")
    private var guardAnims = AnimationFrames("guard")
    private var holeAnims = AnimationFrames("hole")

    private val sounds = SoundPlayer(assets)
    private val levels = LevelsRep(assets, tilesAtlas, holeAtlas, holeAnims)


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
        duration = 30f
    }
    val currentShutter get() = shatterRadiusAnim.value.value
    val uiShiftX = MutableStateValue(0f)
    val uiShiftY = MutableStateValue(0f)
    val debug = MutableStateValue("")

    lateinit var levelView: LevelView
    //cam
    val visibleWidth get() = visibleTilesX*conf.tileSize.x
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


        game.onStatusChanged += {
            when(it) {
                GameState.GAME_START -> startIntro()
                GameState.GAME_RUNNING -> stopIntro()
                GameState.GAME_FINISH -> {
                    sounds.playSound("pass")
                    val scoreDuration = sounds["pass"]?.duration?.div((SCORE_COUNTER + 1)) ?: 0
                    startOutro()
                }
                else -> Unit
            }
        }
        game.levelStartup(currentLevel, guardAnims)
        game.runner!!.sounds = sounds
        for ( g in game.guards ) {
            g.sounds = sounds
        }

        +sprite(bg).apply {
            grayScaled = true
            val imageMinSide = min(bg.loadedTexture!!.width, bg.loadedTexture!!.height)
            val camSide = max((camera as OrthographicCamera).width, (camera as OrthographicCamera).height)
            scale(camSide / imageMinSide)
            // paralax TBD
        }

        +RunnerController(ctx.inputMgr, game.runner!!)
        +lineMesh("x") { addLine(Vec3f.ZERO, Vec3f(1f, 0f, 0f), Color.RED) }
        +lineMesh("y") { addLine(Vec3f.ZERO, Vec3f(0f, 1f, 0f), Color.GREEN) }
        +lineMesh("z") { addLine(Vec3f.ZERO, Vec3f(0f, 0f, 1f), Color.BLUE) }

        // views
        levelView = LevelView(game, currentLevel, conf, tilesAtlas, holeAtlas, runnerAtlas, runnerAnims, guardAtlas, guardAnims)
        game.startGame()
        val off = OffscreenRenderPass2d(levelView, renderPassConfig {
            this.name = "bg"

            width = visibleWidth
            height = visibleHeight

            addColorTexture {
                colorFormat = TexFormat.RGBA
            }
        }).apply {
            camera = createCamera.apply {
                projCorrectionMode = Camera.ProjCorrectionMode.OFFSCREEN
            }
            clearColor = Color(0.00f, 0.00f, 0.00f, 0.00f)
            //?
//            this.camera.position.set(this@GameLevelScene.camera.position)
        }
        //mask
        +textureMesh {
            generate {
                rect {
                    size.set(visibleWidth.toFloat(), visibleHeight.toFloat())
                    origin.set(-width/2, 0f, 0f)
                    mirrorTexCoordsY()
                }
            }
            shader = MaskShader { color { textureColor(off.colorTexture) } }
            (shader as MaskShader).visibleRadius = ( sqrt(visibleWidth.toFloat() * visibleWidth + visibleHeight * visibleHeight) / 2f )
            onUpdate += {
                (shader as MaskShader).visibleRadius = shatterRadiusAnim.tick( it.ctx )
            }
        }
        addOffscreenPass(off)

        val deadZone = with(off.camera as OrthographicCamera) {
            BoundingBox().apply {
                add(Vec3f(-this@with.width / 4, -this@with.height / 4, 0f))
                add(Vec3f(this@with.width/4, this@with.height/4, 0f))
            }
        }
        val borderZone = with(off.camera as OrthographicCamera) {
            val scaledMin = MutableVec3f(levelView.bounds.min)
            val scaledMax = MutableVec3f(levelView.bounds.max)
            levelView.transform.transform(scaledMin)
            levelView.transform.transform(scaledMax)

            BoundingBox().apply {
                add(Vec3f( scaledMin.x + this@with.width / 2f, 0f, 0f))
                add(Vec3f( scaledMax.x - this@with.width / 2f, scaledMax.y - this@with.height, 0f))
            }
        }
        // each redraw tick
        onUpdate += { ev ->

            if ( game.isPlaying ) {
                val resultPos = MutableVec3f(levelView.runnerView.globalCenter)

                with(off.camera as OrthographicCamera) {
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
//                    resultPos.z = 0f

                    cameraPos.to.set(
                        resultPos.x /*+ (ctx.inputMgr.pointerState.primaryPointer.x.toFloat() - it.viewport.width/2) / 50f*/,
                        resultPos.y /*+ (ctx.inputMgr.pointerState.primaryPointer.y.toFloat() - it.viewport.height/2) / 50f*/, 10f)
                    cameraPos.from.set(cameraPos.value)
                    //                    cameraAnimator.progress = 0f
                    //                    cameraAnimator.speed = 1f
                }
            }

            if ( (ev.time - lastUpdate) * 1000 >= gameSettings.speed.msByPass ) {
                game.tick(ev.ctx)
                lastUpdate = ev.time
            }

//            (camera as? OrthographicCamera)?.let { cam ->
//                cam.left = 0f
//                cam.top = 0f
//                cam.right = ev.renderPass.viewport.width.toFloat()
//                cam.bottom = -ev.renderPass.viewport.height.toFloat()
//            }
        }

        // each game tick
        game.onPlayGame += {
            with(off.camera) {
                position.set ( cameraAnimator.tick(ctx) )
                lookAt.set(position.x, position.y, 0f)
            }
        }
    }

    var lastUpdate = 0.0

    fun setupUi(scope: UiScope) = with(scope) {
        modifier
            .width(Grow(1f, max = WrapContent))
            .height(WrapContent)
            .margin(start = 25.dp, top = 25.dp, bottom = 60.dp)
            .layout(ColumnLayout)
            .alignX(AlignmentX.Start)
            .alignY(AlignmentY.Top)
        Row {
            Text(debug.use()) {
                modifier
                    .width(WrapContent)
                    .height(WrapContent)
                onUpdate += {
                    debug.set("%.1f %.1f %.1f".format(shatterRadiusAnim.value.from, currentShutter, shatterRadiusAnim.value.to))
                }
            }
        }
    }

    private fun startOutro() =
        shatterRadiusAnim.apply {
            speed = 1f
            progress = 0f
            value.from = levelView.globalBounds.circumcircleRadius
            value.to = 0f
        }

    private fun startIntro() =
        shatterRadiusAnim.apply {
            speed = 1f
            progress = 0f
            value.from = 0f
            value.to = levelView.globalBounds.circumcircleRadius
        }

    private fun stopIntro() = shatterRadiusAnim.apply {
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