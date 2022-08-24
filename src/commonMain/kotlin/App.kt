import com.russhwolf.settings.Settings
import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.audio.AudioClip
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.az.ilode.Game
import me.az.ilode.GameSettings
import me.az.utils.b

val simpleTextureProps = TextureProps(TexFormat.RGBA,
    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
    FilterMethod.NEAREST, FilterMethod.NEAREST, mipMapping = false, maxAnisotropy = 1
)
val simpleValueTextureProps = TextureProps(TexFormat.R,
    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
    FilterMethod.NEAREST, FilterMethod.NEAREST, mipMapping = false, maxAnisotropy = 1
)
enum class GameSpeed(val msByPass: Int) {
    SPEED_VERY_SLOW(55),
    SPEED_SLOW      (40),
    SPEED_NORMAL    (25),
    SPEED_FAST      (15),
    SPEED_VERY_FAST (10),
}

class LevelSceneSpec (
    val gameWidth: Int = 560, // total screen with toolbar and level
    val gameHeight: Int = 384, // total screen with toolbar and level
    val tileSize: Vec2i = Vec2i(20, 22)
) {}

class App(ctx: KoolContext) {
    val settings = Settings()
    val gameSettings = GameSettings(settings)

    init {
        println(settings)
        ctx.assetMgr.assetsBaseDir = "." // = resources

        val game = Game(gameSettings)

        ctx.scenes += GameLevelScene(game, ctx.assetMgr, "level", gameSettings)
        ctx.run()

        test1()
    }

    fun test1() {
        expect( ViewCell(false, 0).pack == 0x00.b )
        expect( ViewCell(true, 0).pack == 0x80.b ) { ViewCell(true, 0).pack.toString(2) }
        expect( ViewCell(false, 16).pack == 0x10.b )
        expect( ViewCell(false, 127).pack == 0x7f.b )
        expect( ViewCell(true, 127).pack == 0xff.b )
    }

    private fun expect(cond: Boolean, msg: () -> String = { "assert error" }) {
        if ( !cond ) throw AssertionError(msg())
    }
}

class GameLevelScene(val game: Game, val assets: AssetManager, name: String?, val gameSettings: GameSettings) : Scene(name) {
    var levelState = State.NEW
    var tileSet: TileSet = TileSet.SPRITES_APPLE2
    val conf = LevelSceneSpec()


    private var tilesAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "tiles"))
    private var runnerAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "runner"))
    private var guardAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "guard"))
    private var holeAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "hole"))
    private var runnerAnims = AnimationFrames("runner")
    private var guardAnims = AnimationFrames("guard")
    private var holeAnims = AnimationFrames("hole")

    private val sounds = SoundPlayer(assets)


    private val levels = LevelsRep(assets, tilesAtlas)
    val currentLevelId = 0
    val currentLevel
        get() = levels.getLevel(currentLevelId)


    init {

        camera = OrthographicCamera("plain").apply {
            projCorrectionMode = Camera.ProjCorrectionMode.ONSCREEN
            isClipToViewport = false
            isKeepAspectRatio = true
            setCentered(conf.gameHeight.toFloat(), 0.1f, 10f)
        }

        onRenderScene += {
            checkState(it)
        }
    }

    fun checkState(ctx: KoolContext) {
        if (levelState == State.NEW) {
            // load resources (async from AssetManager CoroutineScope)
            levelState = State.LOADING
            ctx.assetMgr.launch {
                loadResources(ctx)
                levelState = State.SETUP
            }
        }

        if (levelState == State.SETUP) {
            setupMainScene(ctx)
            levelState = State.RUNNING
        }
    }

    suspend fun AssetManager.loadResources(ctx: KoolContext) {
        tilesAtlas.load(this)
        runnerAtlas.load(this)
        guardAtlas.load(this)
        holeAtlas.load(this)
        levels.load(LevelSet.CLASSIC)
        runnerAnims.loadAnimations(ctx)
        guardAnims.loadAnimations(ctx)

        sounds.loadSounds()

    }

    fun dispose() {

    }

    fun setupMainScene(ctx: KoolContext) {

        game.levelStartup(currentLevel, guardAnims)
        +RunnerController(ctx.inputMgr, game.runner)
        game.runner.sounds = sounds

        // views
        +LevelView(game, currentLevel, conf, tilesAtlas, runnerAtlas, runnerAnims, guardAtlas, guardAnims)

        onUpdate += {
            if ( (it.time - lastUpdate) * 1000 >= gameSettings.speed.msByPass ) {
                game.tick()
                lastUpdate = it.time
            }
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

/*
    Game(settings).startLevel()
    +GameView(game) -> +LevelView(level) -> +ActorView(runner)

 */
