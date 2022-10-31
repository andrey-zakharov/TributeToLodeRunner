import com.russhwolf.settings.Settings
import com.russhwolf.settings.boolean
import com.russhwolf.settings.float
import com.russhwolf.settings.int
import de.fabmax.kool.InputManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.UniversalKeyCode
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.math.isFuzzyZero
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.az.ilode.*
import me.az.scenes.*
import me.az.utils.b
import me.az.utils.buildStateMachine
import me.az.utils.choice
import me.az.utils.enumDelegate
import me.az.view.GameControls
import kotlin.native.concurrent.ThreadLocal
import kotlin.random.Random

val simpleTextureProps = TextureProps(TexFormat.RGBA,
    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
    FilterMethod.NEAREST, FilterMethod.NEAREST, mipMapping = false, maxAnisotropy = 1
)
val simpleValueTextureProps = TextureProps(TexFormat.R,
    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
    FilterMethod.NEAREST, FilterMethod.NEAREST, mipMapping = false, maxAnisotropy = 1
)
enum class GameSpeed(val fps: Int) {
    SPEED_VERY_SLOW(14),
    SPEED_SLOW      (18),
    SPEED_NORMAL    (23),
    SPEED_FAST      (29),
    SPEED_VERY_FAST (35), // 14, 18, 23, 29, 35
    ;
    val msByPass get() = 1000 / fps
    val dis get() = name.removePrefix("SPEED_").lowercase().replace('_', ' ')
}

class ViewSpec (
    val tileSize: Vec2i = Vec2i(20, 22),
    val visibleSize: Vec2i = Vec2i(28, 16+2) // in tiles  + ground + status
) {
    val visibleWidth get() = visibleSize.x * tileSize.x
    val visibleHeight get() = visibleSize.y * tileSize.y
}

const val backgroundImageFile = "images/cover.jpg"

sealed class State <T> {
    open fun enterState(obj: T) {}
    open fun exitState(obj: T) {}
    abstract fun update(obj: T): State<T>?
}

object MainMenuState : State<App>() {
    var continueGame = false
    var startnewGame = false
    var exitGame = false
    var mainMenu: Scene? = null
    var mainUiMenu: Scene? = null
    val menuContext get() = (mainMenu as? MainMenuScene)?.menuContext ?: throw IllegalStateException()

    override fun enterState(app: App) {
        super.enterState(app)
        val game = Game(app.context)
        // preload
        mainMenu = MainMenuScene(app.context, game, app.ctx.assetMgr).also {
            app.ctx.scenes += it
//            mainUiMenu = it.setupUi()
        }
        mainUiMenu?.run { app.ctx.scenes += this }

        game.reset()
    }

    override fun exitState(app: App) {
        super.exitState(app)
        //game.stop()

        mainUiMenu?.run {
            app.ctx.scenes -= this
            app.ctx.runDelayed(1) { dispose(app.ctx) }
        }
        mainUiMenu = null

        mainMenu?.run {
            app.ctx.scenes -= this
            app.ctx.runDelayed(1) { dispose(app.ctx) }
        }
        mainMenu = null
    }
    override fun update(obj: App): State<App>? {

        if ( startnewGame || continueGame ) {
            if ( startnewGame  || obj.context.runnerLifes.value <= 0 ) {
                with( obj.context ) {
                    currentLevel.set(menuContext.level.value)
                    levelSet.set(menuContext.levelSet.value)
                    runnerLifes.set( START_HEALTH )
                    score.set(0)
                }
            }
            startnewGame = false
            continueGame = false
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
    private var listener: InputManager.KeyEventListener? = null

    override fun enterState(app: App) {
        super.enterState(app)
        val game = Game(app.context)
        val conf = ViewSpec()

        gameScene = GameLevelScene(game, app.ctx,
            appContext = app.context,
            name = "level").apply {
            +GameControls(game, app.ctx.inputMgr)
        }

        infoScene = GameUI(game, assets = app.ctx.assetMgr, app.context)
        app.ctx.scenes += gameScene!!
        app.ctx.scenes += infoScene!!
        debugScene = UiScene {
            +Panel {
                gameScene?.setupUi(this)!!
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

//        app.ctx.scenes += debugScene!!
    }

    override fun exitState(app: App) {
        super.exitState(app)
        with(app.ctx) {
            listener?.run { inputMgr.removeKeyListener(this) }
            gameScene?.run { scenes -= this; runDelayed(1) { dispose(this@with) } }
            gameScene = null
            infoScene?.run { scenes -= this; runDelayed(1) { dispose(this@with) } }
            infoScene = null
            debugScene?.run { app.ctx.scenes -= this; runDelayed(1) { dispose(this@with) } }
            debugScene = null
        }
    }

    override fun update(app: App): State<App>? {
        return null
    }

}

object Exit : State<App>() {
    override fun enterState(obj: App) = obj.ctx.close()
    override fun update(obj: App): State<App>? = null
}

class GameSettings(val settings: Settings) {
    var curScore: Int by settings.int(defaultValue = 0)
    var currentLevel: Int by settings.int(defaultValue = 0)
    var runnerLifes by settings.int(defaultValue = START_HEALTH) // max = MAX_HEALTH
    var speed: GameSpeed by enumDelegate(settings, defaultValue = GameSpeed.SPEED_SLOW)
    var spriteMode: TileSet by enumDelegate(settings, defaultValue = TileSet.SPRITES_APPLE2)
    var version: LevelSet by enumDelegate(settings, defaultValue = LevelSet.CLASSIC)
    var introDuration by settings.float(defaultValue = 60f)
    var sometimePlayInGodMode by settings.boolean()

    // { s:curScore, l:curLevel, r:runnerLife, m: maxLevel, g: sometimePlayInGodMode, p: passedLevel};
    var immortal by settings.boolean(defaultValue = true)
    var stopGuards by settings.boolean(defaultValue = false)
}

class AppContext(val gameSettings: GameSettings) {
    val score = mutableStateOf(gameSettings.curScore).also { it.onChange { gameSettings.curScore = it } }

    val spriteMode = mutableStateOf(gameSettings.spriteMode).also { it.onChange { gameSettings.spriteMode = it } }
    val levelSet = mutableStateOf(gameSettings.version).also { it.onChange { gameSettings.version = it } }
    val currentLevel = mutableStateOf(gameSettings.currentLevel).also { it.onChange { gameSettings.currentLevel = it } }
    val speed = mutableStateOf(gameSettings.speed).also { it.onChange { gameSettings.speed = it } }
    val stopGuards = MutableStateValue(gameSettings.stopGuards).also { it.onChange { v -> gameSettings.stopGuards = v } }
    val immortal = MutableStateValue(gameSettings.immortal).also { it.onChange { v -> gameSettings.immortal = v } }
    val runnerLifes = MutableStateValue(gameSettings.runnerLifes).also { it.onChange { v -> gameSettings.runnerLifes = v } }
}

class App(val ctx: KoolContext) {
    private val job = Job()
    private val scope = CoroutineScope(job)

    private val settings = Settings()
    val gameSettings = GameSettings(settings)
    val context = AppContext(gameSettings)

    private var state: State<App>? = null

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

        registerActions(ctx.inputMgr, this, AppActions.values().asIterable())
        changeState(MainMenuState)
//        changeState(RunGameState)


        ctx.onRender += {
            val newState = state?.update(this)
            newState?.run { changeState(this) }
        }

        ctx.run()

        job.cancel()

        test1()
    }

    companion object {
        // for 3 scenes
        fun createCamera(width: Int, height: Int): OrthographicCamera {
            return OrthographicCamera("plain").apply {
                projCorrectionMode = Camera.ProjCorrectionMode.ONSCREEN
                isClipToViewport = false
                isKeepAspectRatio = true
                val hw = width / 2f
                top = height * 1f
                bottom = 0f
                left = -hw
                right = hw
                clipFar = 10f
                clipNear = 0.1f
            }
        }
    }

    private fun changeState(newState: State<App>) {
        state?.exitState(this@App)
        state = newState
        state?.enterState(this@App)
    }

    fun test1() {
        expect( ViewCell(false, 0).pack == 0x00.b )
//        expect( ViewCell(true, 0).pack == 0x80.b ) { ViewCell(true, 0).pack.toString(2) }
        expect( ViewCell(false, 16).pack == 0x10.b )
        expect( ViewCell(false, 127).pack == 0x7f.b )
//        expect( ViewCell(true, 127).pack == 0xff.b )
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

    enum class AppActions(override val keyCode: InputSpec,
                          override val onPress: App.(InputManager.KeyEvent) -> Unit,
                          override val onRelease: App.(InputManager.KeyEvent) -> Unit) : Action<App> {
        ESC(InputManager.KEY_ESC.toInputSpec(), onPress = {
            fsm.popState()
            changeState(MainMenuState)
        }, onRelease = {})
    }
}

interface Action<E> {
    val name: String
    val keyCode: InputSpec
    val onPress: E.(InputManager.KeyEvent) -> Unit
    val onRelease: E.(InputManager.KeyEvent) -> Unit
}

fun<E> registerActions(inputManager: InputManager, root: E, actions: Iterable<Action<E>>, ) =
    actions.map { action ->
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

fun unregisterActions(inputManager: InputManager, actions: Iterable<InputManager.KeyEventListener>) {
    actions.forEach {
        inputManager.removeKeyListener(it)
    }
}


/*
    Game(settings).startLevel()
    +GameView(game) -> +LevelView(level) -> +me.az.view.ActorView(runner)

 */
