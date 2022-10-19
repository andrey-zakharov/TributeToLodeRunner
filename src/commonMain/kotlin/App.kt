import com.russhwolf.settings.Settings
import de.fabmax.kool.InputManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.UniversalKeyCode
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.math.isFuzzyZero
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.*
import me.az.ilode.*
import me.az.utils.b
import me.az.utils.buildStateMachine
import me.az.utils.choice
import me.az.view.GameControls
import me.az.scenes.GameLevelScene
import me.az.scenes.GameUI
import me.az.scenes.MainMenuContext
import me.az.scenes.MainMenuScene
import me.az.view.ImageText
import me.az.view.SpriteSet
import me.az.view.TextDrawer
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

object MainMenuState : State<App>() {

    val bg by lazy { scene {
        +sprite( texture = Texture2d( simpleTextureProps ) { assets ->
            assets.loadTextureData("images/start-screen.png")
        } )
    } }

    var startnewGame = false
    var exitGame = false
    var mainMenu: Scene? = null

    val context by lazy {
        MainMenuContext().also {

        }
    }

    override fun enterState(app: App) {
        super.enterState(app)
        // preload
        mainMenu = MainMenuScene(context)

//        app.ctx.scenes += bg
        app.ctx.scenes += mainMenu!!
    }

    override fun exitState(app: App) {
        super.exitState(app)
        mainMenu?.run {
            app.ctx.scenes -= this
            dispose(app.ctx)
        }
        mainMenu = null

        app.ctx.scenes -= bg
    }
    override fun update(obj: App): State<App>? {

        if ( startnewGame ) {
            startnewGame = false
            return RunGameState
        }
        if ( exitGame ) {
            exitGame = false
            return Exit
        }
        return null
    }
}

object RunGameState : State<App>() {
    var gameScene: GameLevelScene? = null
    var infoScene: Scene? = null
    var debugScene: Scene? = null
    var listener: InputManager.KeyEventListener? = null

    override fun enterState(app: App) {
        super.enterState(app)


        val game = Game(app.gameSettings)

        gameScene = GameLevelScene(game, app.ctx.assetMgr, app.gameSettings, "level", true).apply {
            +GameControls(game, app.ctx.inputMgr)
        }

        app.ctx.scenes += gameScene!!
        infoScene = GameUI(game, assets = app.ctx.assetMgr, app.gameSettings)
        app.ctx.scenes += infoScene!!
        debugScene = Ui2Scene {
            +UiSurface {
                gameScene?.setupUi(this)
            }
        }

        listener = app.ctx.inputMgr.registerKeyListener(UniversalKeyCode('d'), "toggle debug") {
            when {
                debugScene == null -> Unit
                it.isPressed -> if ( app.ctx.scenes.contains(debugScene) ) {
                    app.ctx.scenes -= debugScene!!
                } else {
                    app.ctx.scenes += debugScene!!
                }
            }
        }

        app.ctx.scenes += debugScene!!
    }

    override fun exitState(app: App) {
        super.exitState(app)
        listener?.run { app.ctx.inputMgr.removeKeyListener(this) }
        gameScene?.run { app.ctx.scenes -= this;  dispose(app.ctx) }
        gameScene = null
        infoScene?.run { app.ctx.scenes -= this; dispose(app.ctx) }
        infoScene = null
        debugScene?.run { app.ctx.scenes -= this; dispose(app.ctx) }
        debugScene = null
    }

    override fun update(app: App): State<App>? {
        return null
    }

}

object Exit : State<App>() {
    override fun enterState(obj: App) = obj.ctx.close()
    override fun update(obj: App): State<App>? = null
}

sealed class AppContext {
    var tileSetName = MutableStateValue(SpriteSet.A8B)
}
class App(val ctx: KoolContext) {
    private val settings = Settings()
    val gameSettings = GameSettings(settings)

    var state: State<App>? = null

    val fsm = buildStateMachine<String, App>("mainmenu") {
        state("mainmenu") {

            onEnter {

            }

            onExit {

            }

            edge("play game") {

            }
        }

        state("play game") {
            onEnter {

            }

            onExit {

            }
        }
    }

    init {

        test3()
        test2()
        ctx.assetMgr.assetsBaseDir = "." // = resources

//        changeState(MainMenuState)
        changeState(RunGameState)


        ctx.onRender += {
            val newState = state?.update(this)
            newState?.run { changeState(this) }
        }

        ctx.run()


        test1()
    }

    private fun changeState(newState: State<App>) {
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

interface Action<E> {
    val name: String
    val keyCode: InputSpec
    val onPress: E.(InputManager.KeyEvent) -> Unit
    val onRelease: E.(InputManager.KeyEvent) -> Unit
}

fun<E> registerActions(inputManager: InputManager, root: E, actions: Iterable<Action<E>>, ) {
    actions.forEach { action ->
        inputManager.registerKeyListener(
            keyCode = action.keyCode.code,
            name = action.name,
            filter = {ev ->
                ev.isPressed &&
                (action.keyCode.modificatorBitMask or ev.modifiers) xor action.keyCode.modificatorBitMask == 0
            }
        ) { ev ->
            if ( ev.isReleased ) action.onRelease.invoke(root, ev)
            else
                if (ev.isPressed) action.onPress.invoke(root, ev)
        }
    }
}


/*
    Game(settings).startLevel()
    +GameView(game) -> +LevelView(level) -> +me.az.view.ActorView(runner)

 */
