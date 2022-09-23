import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.*
import de.fabmax.kool.scene.*
import de.fabmax.kool.scene.animation.Animator
import de.fabmax.kool.scene.animation.InterpolatedFloat
import de.fabmax.kool.scene.animation.InterpolatedValue
import de.fabmax.kool.scene.animation.SquareAnimator
import de.fabmax.kool.scene.ui.*
import de.fabmax.kool.util.Color
import me.az.ilode.Game
import me.az.ilode.GameSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    val currentLevelId = 0
    val currentLevel
        get() = levels.getLevel(currentLevelId)

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

    lateinit var levelView: LevelView

    init {

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

    }

    fun dispose() {

    }

    override fun setup(ctx: KoolContext) {

        game.levelStartup(currentLevel, guardAnims)
        game.runner!!.sounds = sounds
        for ( g in game.guards ) {
            g.sounds = sounds
        }

        +RunnerController(ctx.inputMgr, game.runner!!)
        +lineMesh("x") { addLine(Vec3f.ZERO, Vec3f(1f, 0f, 0f), Color.RED) }
        +lineMesh("y") { addLine(Vec3f.ZERO, Vec3f(0f, 1f, 0f), Color.GREEN) }
        +lineMesh("z") { addLine(Vec3f.ZERO, Vec3f(0f, 0f, 1f), Color.BLUE) }

        // views
        levelView = LevelView(game, currentLevel, conf, tilesAtlas, holeAtlas, runnerAtlas, runnerAnims, guardAtlas, guardAnims)
        +levelView

        val uiRoot = UiRoot(this).apply {
            theme = UiTheme.LIGHT
            isFillViewport = true

            +label("debug") {

                layoutSpec.setSize(full(), dps(100f), full())
                //layoutSpec.setOrigin(zero(), zero(), uns(0f))
                onUpdate += {
                    text = "%.3f %.3f\n%.3f %.3f".format(
                        levelView.runnerView.globalCenter.x - cameraPos.to.x,
                        levelView.runnerView.globalCenter.y - cameraPos.to.y,
                        camera.position.x,
                        camera.position.y
                    )
                }
            }

            onUpdate += {
                (camera as OrthographicCamera).run {
                    val origin = MutableVec3f(50f, 0f, 0f)
//                        projectScreen(Vec3f(-1f, -1f, 0f), it.viewport, ctx, origin )
                    content.layoutSpec.setOrigin(uns(origin.x, false), uns(origin.y, false), zero())
                }
                requestLayout()
            }
        }

//        +uiRoot

        // each redraw tick
        onUpdate += {
            if ( game.isPlaying ) {
                val resultPos = MutableVec3f(levelView.runnerView.globalCenter)
                with(camera as OrthographicCamera) {
                    resultPos -= Vec3f(0f, height / 2f, 0f)
                    if ( abs(resultPos.x - cameraPos.value.x) >= width/4 ) {
                        resultPos.x = if (resultPos.x < 0) resultPos.x - width/4 else resultPos.x + width/4
                    }
                    if (abs(resultPos.y - cameraPos.value.y) >= height/4 ) {
                        resultPos.y = if (resultPos.y < 0) resultPos.y - height/4 else resultPos.y + height/4
                    }

                    resultPos.y = max(0f, resultPos.y)
                    resultPos.y = min(levelView.bounds.max.y - height, resultPos.y)
                    resultPos.x = max(-levelView.bounds.size.x/2f + width / 2f, resultPos.x)
                    resultPos.x = min(levelView.bounds.size.x/2f - width / 2f, resultPos.x)
                    // get distance from camera. if more than viewport / 4 - move
                    resultPos.z = 0f



                    cameraPos.to.set(resultPos.x, resultPos.y, 10f)
                    cameraPos.from.set(cameraPos.value)
                    //                    cameraAnimator.progress = 0f
                    //                    cameraAnimator.speed = 1f
                }
            }


            if ( (it.time - lastUpdate) * 1000 >= gameSettings.speed.msByPass ) {
                game.tick()
                lastUpdate = it.time
            }
        }

        // each game tick
        game.onPlayGame += {
            camera.position.set ( cameraAnimator.tick(ctx) )
            camera.lookAt.set(camera.position.x, camera.position.y, 0f)
        }
    }

    var lastUpdate = 0.0

}

val OrthographicCamera.height get() = top - bottom
val OrthographicCamera.width get () = right - left