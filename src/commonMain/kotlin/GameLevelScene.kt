import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.*
import de.fabmax.kool.scene.Camera
import de.fabmax.kool.scene.OrthographicCamera
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.animation.Animator.Companion.ONCE
import de.fabmax.kool.scene.animation.InterpolatedFloat
import de.fabmax.kool.scene.animation.InterpolatedValue
import de.fabmax.kool.scene.animation.SquareAnimator
import de.fabmax.kool.scene.lineMesh
import de.fabmax.kool.util.Color
import me.az.ilode.Game
import me.az.ilode.GameSettings

class GameLevelScene (val game: Game, val assets: AssetManager, name: String?, val gameSettings: GameSettings) : Scene(name) {
    var sceneState = State.NEW
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

    private val visibleTilesX = 28
    private val visibleTilesY = 16 + 1 + 1 // + ground + status

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
        duration = 5f
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
//            bottom = -((visibleTilesY+3) * conf.tileSize.y / 2f)
            left = -hw
            right = hw
            clipFar = 10f
            clipNear = 0.1f
//            setCentered((visibleTilesY * tilesAtlas.spec.tileHeight).toFloat(), 0.1f, 10f)

        }

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
            setupMainScene(ctx)
            sceneState = State.RUNNING
        }
    }

    suspend fun AssetManager.loadResources(ctx: KoolContext) {
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

    fun setupMainScene(ctx: KoolContext) {

        game.levelStartup(currentLevel, guardAnims)
        +RunnerController(ctx.inputMgr, game.runner)
        +lineMesh("x") { addLine(Vec3f.ZERO, Vec3f(1f, 0f, 0f), Color.RED) }
        +lineMesh("y") { addLine(Vec3f.ZERO, Vec3f(0f, 1f, 0f), Color.GREEN) }
        +lineMesh("z") { addLine(Vec3f.ZERO, Vec3f(0f, 0f, 1f), Color.BLUE) }
        game.runner.sounds = sounds
        for ( g in game.guards ) {
            g.sounds = sounds
        }

        // views
        levelView = LevelView(game, currentLevel, conf, tilesAtlas, holeAtlas, runnerAtlas, runnerAnims, guardAtlas, guardAnims)
        +levelView

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

        onUpdate += {
            if ( (it.time - lastUpdate) * 1000 >= gameSettings.speed.msByPass ) {
                game.tick()
                lastUpdate = it.time
            }

            if ( game.isPlaying ) {
                val runnerPos = MutableVec3f(levelView.runnerView.globalCenter)
                with(camera as OrthographicCamera) {
                    runnerPos -= Vec3f(0f, this.height / 2f, 0f)
                }

                if ( runnerPos != cameraPos.to ) {
                    cameraPos.from.set( camera.position )

                    cameraPos.to.set( runnerPos.x, runnerPos.y, 1f )
                    cameraAnimator.progress = 0f
                    cameraAnimator.speed = 1f
                }
            }
        }

        game.onPlayGame += {

            camera.position.set ( cameraAnimator.tick(ctx) )
            camera.lookAt.set(camera.position.x, camera.position.y, 0f)
        }
    }

    var lastUpdate = 0.0

    enum class State {
        NEW,
        LOADING,
        SETUP,
        RUNNING
    }

}

val OrthographicCamera.height get() = top - bottom
val OrthographicCamera.width get () = right - left