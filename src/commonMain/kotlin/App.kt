import com.russhwolf.settings.Settings
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.math.isFuzzyZero
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.*
import de.fabmax.kool.scene.ui.*
import me.az.ilode.Game
import me.az.ilode.GameSettings
import me.az.ilode.Tile
import me.az.ilode.ViewCell
import me.az.utils.b
import me.az.utils.choice
import kotlin.random.Random

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

class LevelSpec (
    val tileSize: Vec2i = Vec2i(20, 22)
) {}

sealed class State <T> {
    open fun enterState(obj: T) {}
    open fun exitState(obj: T) {}
    abstract fun update(obj: T): State<T>?
}

class MainMenuState : State<App>() {
    var cachedTex: TextureData? = null
    val bgTex = Texture2d( simpleTextureProps ) {assets ->
        if ( cachedTex == null ) {
            cachedTex = assets.loadTextureData("images/start-screen.png")
        }
        cachedTex!!
    }
    val bg by lazy { scene {
        +sprite( texture = bgTex )
    } }

    var startnewGame = false

    val mainMenu = uiScene {scene ->
        +container("main menu container") {
//            layoutSpec.setOrigin(dps(50f), dps(50f), dps(0f) )
            layoutSpec.setSize(full(), full(), full())

            var y = -90f
            val centered = Gravity(Alignment.CENTER, Alignment.CENTER)
            +button("new game") {
                layoutSpec.setOrigin(zero(), dps(y), zero())
                layoutSpec.setSize(full(), dps(30f), full())
                textAlignment = centered
                onClick += { _ , _ , _ ->
                    startnewGame = true
                }
            }

            y-= 30f
            +label("level: ") {
                layoutSpec.setOrigin(zero(), dps(y), zero())
                layoutSpec.setSize(full(), dps(30f), full())
                textAlignment = centered
            }

            y-= 30f
            +label( "exit" ) {
                layoutSpec.setOrigin(zero(), dps(y), zero())
                layoutSpec.setSize(full(), dps(30f), full())
                textAlignment = centered
            }
        }

    }
    override fun enterState(app: App) {
        super.enterState(app)
        // preload
        app.ctx.scenes += bg
        app.ctx.scenes += mainMenu
    }

    override fun exitState(app: App) {
        super.exitState(app)
        app.ctx.scenes -= mainMenu
        app.ctx.scenes -= bg
    }
    override fun update(obj: App): State<App>? {

        if ( startnewGame ) {
            startnewGame = false
            return RunGameState()
        }
        return null
    }
}

class RunGameState : State<App>() {
    var gameScene: GameLevelScene? = null
    var infoScene: Scene? = null

    override fun enterState(app: App) {
        super.enterState(app)
        val game = Game(app.gameSettings)
        gameScene = GameLevelScene(game, app.ctx.assetMgr, app.gameSettings, "level")
        app.ctx.scenes += gameScene!!
        infoScene = GameUI(game, assets = app.ctx.assetMgr, app.gameSettings)
        app.ctx.scenes += infoScene!!
    }

    override fun exitState(app: App) {
        super.exitState(app)
        gameScene?.run { app.ctx.scenes -= this }
        infoScene?.run { app.ctx.scenes -= this }
    }

    override fun update(app: App): State<App>? {
        return null
    }
}

class App(val ctx: KoolContext) {
    val settings = Settings()
    val gameSettings = GameSettings(settings)

    var state: State<App>? = null

    init {
        println(settings.keys)
        test3()
        test2()
        ctx.assetMgr.assetsBaseDir = "." // = resources

//        changeState(MainMenuState())
        changeState(RunGameState())

        ctx.onRender += {
            val newState = state?.update(this)
            newState?.run { changeState(this) }
        }
        ctx.run()

        test1()
    }

    fun changeState(newState: State<App>) {
        state?.exitState(this)
        state = newState
        state?.enterState(this)
    }

    fun test1() {
        expect( ViewCell(false, 0).pack == 0x00.b )
        expect( ViewCell(true, 0).pack == 0x80.b ) { ViewCell(true, 0).pack.toString(2) }
        expect( ViewCell(false, 16).pack == 0x10.b )
        expect( ViewCell(false, 127).pack == 0x7f.b )
        expect( ViewCell(true, 127).pack == 0xff.b )
    }

    fun test2(choices: List<Int> = listOf(100, 200, 50, 1000)) {
        val steps = 10000
        val eps = 100f / steps
        val r = Random(0)
        val results = mutableMapOf<Int, Int>()
        (0 until steps).forEach {
            val c = r.choice(choices)
            results[c] = results.getOrPut(c) { 0 } + 1
        }

        val t = choices.sum().toFloat()
        val expectedProbs = choices.map { it / t }
        println(expectedProbs)

        val rt = results.values.sum().toFloat()
        val resultProbs = results.map { it.key to it.value / rt }.toMap()
        expectedProbs.forEachIndexed { i, v ->
            expect( (v - resultProbs[i]!!).isFuzzyZero(eps) ) { "expect $v but got ${resultProbs[i]} (eps = $eps)" }
        }
    }

    fun test3() {
        val a = arrayOf(Tile.LADDER, Tile.BRICK)
        val b = arrayOf(Tile.LADDER, Tile.BRICK)
        val c = arrayOf(Tile.EMPTY, Tile.PLAYER)
        expect( a.contentHashCode() == b.contentHashCode() ) { "expect $a.hashCode = ${a.contentHashCode()} == $b.hashCode = ${b.contentHashCode()}"}
        expect ( a.contentHashCode() != c.contentHashCode() )
        expect ( b.contentHashCode() != c.contentHashCode())

    }

    private fun expect(cond: Boolean, msg: () -> String = { "assert error" }) {
        if ( !cond ) throw AssertionError(msg())
    }
}



/*
    Game(settings).startLevel()
    +GameView(game) -> +LevelView(level) -> +ActorView(runner)

 */
