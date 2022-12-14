import com.russhwolf.settings.Settings
import com.russhwolf.settings.boolean
import com.russhwolf.settings.float
import com.russhwolf.settings.int
import de.fabmax.kool.InputManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.*
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.Camera
import de.fabmax.kool.scene.OrthographicCamera
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.az.app.controls.InputSpec
import me.az.app.controls.toInputSpec
import me.az.app.states.DebugState
import me.az.ilode.Controllable
import me.az.ilode.START_HEALTH
import me.az.ilode.Tile
import me.az.scenes.height
import me.az.scenes.width
import me.az.utils.*
import me.az.view.sprite2d
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

val simpleTextureProps = TextureProps(TexFormat.RGBA,
    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
    FilterMethod.NEAREST, FilterMethod.NEAREST, mipMapping = false, maxAnisotropy = 1
)

val bluredTextureProps = TextureProps(TexFormat.RGBA,
    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
    FilterMethod.LINEAR, FilterMethod.LINEAR, mipMapping = false, maxAnisotropy = 1
)
enum class GameSpeed(val fps: Int) {
    SPEED_VERY_SLOW(14),
    SPEED_SLOW      (18),
    SPEED_NORMAL    (28),
    SPEED_FAST      (35),
    SPEED_VERY_FAST (40), // 14, 18, 23, 29, 35
    INSTANT (1000),
    ;
    val msByPass get() = 1000 / fps
    val dis get() = name.removePrefix("SPEED_").lowercase().replace('_', ' ')
}

const val backgroundImageFile = "images/cover.jpg"

enum class AppState {
    MAINMENU, RUNGAME, EXIT, SCORES, DEBUG
}

/*class ScoresState(private val app: App) : StackedState<AppState, App>(AppState.SCORES) {
    var scoresScreen: Scene? = null
    init {
        onEnter {
            scoresScreen = HiScoreScene()
            app.ctx.scenes += scoresScreen!!
        }

        onExit {
            scoresScreen?.run {
                app.ctx.scenes -= this
                dispose(app.ctx)
            }
            scoresScreen = null
        }


    }
}*/

class ExitState(private val app: App) : StackedState<AppState, App>(AppState.EXIT) {
    init {
        onEnter { app.ctx.close() }
    }
}


class GameSettings(val settings: Settings) {
    var curScore: Int by settings.int(defaultValue = 0)
    var currentLevel: Int by settings.int(defaultValue = 0)
    var runnerLifes by settings.int(defaultValue = START_HEALTH) // max = MAX_HEALTH
    var speed: GameSpeed by enumDelegate(settings, defaultValue = GameSpeed.SPEED_NORMAL)
    var spriteMode: TileSet by enumDelegate(settings, defaultValue = TileSet.SPRITES_APPLE2)
    var version: LevelSet by enumDelegate(settings, defaultValue = LevelSet.CLASSIC)
    var introDuration by settings.float(defaultValue = 60f)
    var sometimePlayInGodMode by settings.boolean()

    // { s:curScore, l:curLevel, r:runnerLife, m: maxLevel, g: sometimePlayInGodMode, p: passedLevel};
    var immortal by settings.boolean(defaultValue = false)
    var stopGuards by settings.boolean(defaultValue = false)
    var actorMoveX = 4
    var actorMoveY = 4
}

class AppContext(val gameSettings: GameSettings) {
    val score = mutableStateOf(gameSettings.curScore).also { it.onChange { gameSettings.curScore = it } }
    val spriteMode = mutableStateOf(gameSettings.spriteMode).also { it.onChange { gameSettings.spriteMode = it } }
    val levelSet = mutableStateOf(gameSettings.version).also { it.onChange { gameSettings.version = it } }
    val currentLevel = mutableStateOf(gameSettings.currentLevel).also { it.onChange { gameSettings.currentLevel = it } }
    val speed = mutableStateOf(gameSettings.speed).also { it.onChange { gameSettings.speed = it } }
    val stopGuards = MutableStateValue(gameSettings.stopGuards).also { it.onChange { v ->
        gameSettings.stopGuards = v
        gameSettings.sometimePlayInGodMode = true
    } }
    val immortal = MutableStateValue(gameSettings.immortal).also { it.onChange { v ->
        gameSettings.immortal = v
        gameSettings.sometimePlayInGodMode = true
    } }
    val runnerLifes = MutableStateValue(gameSettings.runnerLifes).also { it.onChange { v -> gameSettings.runnerLifes = v } }

    fun nextSpriteSet() = with(spriteMode) {
        set(TileSet.values()[(value.ordinal + 1) % TileSet.values().size])
    }

    fun prevSpriteSet() = with(spriteMode) {
        set( TileSet.values()[ (value.ordinal - 1).mod(TileSet.values().size) ] )
    }
}

class App(val ctx: KoolContext, initialState: AppState = AppState.MAINMENU) {

    private val job = Job()
    private val scope = CoroutineScope(job)

    private val settings = Settings()
    val gameSettings = GameSettings(settings)
    val context = AppContext(gameSettings)

    val fsm = buildStateMachine(initialState) {
        this += MainMenuState(this@App)
        this += RunGameState(this@App)
        this += ExitState(this@App)
        this += DebugState(this@App)
    }

    init {
        Log.level = Log.Level.ERROR
        debugOnly { Log.level = Log.Level.DEBUG }

        debugOnly {
            test3()
            test2()
        }

        ctx.inputMgr.registerActions(this, AppActions.values().asIterable())
//        changeState(RunGameState)


        ctx.onRender += {
            fsm.update(this)
        }

        fsm.reset(false)
        debugOnly { scope.launch { test4() } }
        ctx.run()

        job.cancel()

        debugOnly { test1() }

    }

    // need to pixelize in updateCamera
    class GameCamera(
        private val originalWidth: Int, private val originalHeight: Int
    ) : OrthographicCamera("plain") {
        private val originalRatio = originalWidth.toFloat() / originalHeight
        init {
            logd { "created camera $originalWidth x $originalHeight" }
        }
        override fun updateCamera(renderPass: RenderPass, ctx: KoolContext) {
            super.updateCamera(renderPass, ctx)
/*            if (isKeepAspectRatio ) {
                val (width, height) = if ( renderPass.viewport.aspectRatio < originalRatio ) {
                    val d = (renderPass.viewport.aspectRatio / originalRatio)
                    Pair( d * originalWidth.toFloat(), originalHeight.toFloat())
//                    Pair( renderPass.viewport.width.toFloat(), renderPass.viewport.width / originalRatio )
                } else {
                    Pair( originalHeight * originalRatio, originalHeight.toFloat())
                }
                top = height
                bottom = 0f
                val hw = width / 2
                left = - hw
                right = hw
            }*/

            /* fit pixel perfect
            val maxRatioX = floor(renderPass.viewport.width / originalWidth.toFloat())
            val maxRatioY = floor(renderPass.viewport.height / originalHeight.toFloat())
            var approxW = maxRatioX * originalWidth
            var approxH = maxRatioY * originalHeight
            val targetRatio = originalWidth.toFloat() / originalHeight.toFloat()
            val approxRatio = approxW / approxH

            when {
                approxRatio < targetRatio -> approxH = approxW / renderPass.viewport.aspectRatio
                approxRatio > targetRatio -> approxW = approxH * renderPass.viewport.aspectRatio
            }
            var scaleX = max(1f, approxW / originalWidth.toFloat())
            var scaleY = max(1f, approxH / originalHeight.toFloat())

            var err = Float.MAX_VALUE
            for( sx in listOf(floor(scaleX), ceil(scaleX))) {
                for ( sy in listOf(floor(scaleY), ceil(scaleY))) {
                    if ( abs((sx / sy) - targetRatio ) < err ) {
                        err = abs((sx / sy) - targetRatio )
                        scaleX = sx
                        scaleY = sy
                    }
                }
            }

            // final width height
//            top = scaleY * originalHeight.toFloat()
            top = renderPass.viewport.height.toFloat() /  scaleY
                    bottom = 0f
            val hw = renderPass.viewport.width / 2f /  scaleX //scaleX * originalWidth.toFloat() / 2f
            right = hw
            left = -hw*/
        }
    }
    companion object {
        // for 3 scenes
        fun createCamera(width: Int, height: Int): OrthographicCamera {
            return GameCamera(width, height).apply {
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

        fun Scene.makeBackground() =
            sprite2d(
                texture = Texture2d(simpleTextureProps) {
                    it.loadTextureData(backgroundImageFile, simpleTextureProps.format)
                },
                name = "bg"
            ).apply {
                grayScaled = true
                onResize += { w, h ->
                    val imageMinSide = min(w, h)
                    val camSide = max((camera as OrthographicCamera).width, (camera as OrthographicCamera).height)
                    transform.resetScale()
                    scale(camSide / imageMinSide.toDouble())
                }
                // paralax TBD
            }
    }

    fun test1() {
//        expect( LevelCellUpdate(false, 0).pack == 0x00.b )
////        expect( ViewCell(true, 0).pack == 0x80.b ) { ViewCell(true, 0).pack.toString(2) }
//        expect( LevelCellUpdate(false, 16).pack == 0x10.b )
//        expect( LevelCellUpdate(false, 127).pack == 0x7f.b )
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

    suspend fun test4() {
        val rep = LevelsRep(ctx.assetMgr, scope)
        for (s in LevelSet.values()) {
            rep.load(s)
            debugOnly {
                val fl = rep.levels.first()
                logd {"${s.dis} has ${rep.levels.size} levels: ${fl.first().length}x${fl.size}" }
                //rep.levels.forEachIndexed { levelId, strings ->
                //    println("#${levelId + 1}: ${strings.first().length}x${strings.size}")
                //}
            }
        }
    }

    fun test5() {

    }

    private fun expect(cond: Boolean, msg: () -> String = { "assert error" }) {
        if ( !cond ) throw AssertionError(msg())
    }

    enum class AppActions(override val keyCode: InputSpec,
                          override val onPress: App.(InputManager.KeyEvent) -> Unit = {},
                          override val onRelease: App.(InputManager.KeyEvent) -> Unit) : KeyAction<App> {
        EXIT(InputManager.KEY_BACKSPACE.toInputSpec(), onPress = {
            fsm.popState()
            fsm.setState(AppState.MAINMENU)
        }, onRelease = {}),
        DEBUG('d'.toInputSpec(InputManager.KEY_MOD_CTRL), onRelease = {
            if ( ctx.scenes.any { it.name == "debug-overlay" } ) {
                ctx.scenes.removeAll { it.name == "debug-overlay" }
            } else {
                ctx.scenes += de.fabmax.kool.util.debugOverlay()
            }
        }),
        PLAYMODE(InputManager.KEY_F2.toInputSpec(), onRelease = {
            fsm.toggleState(AppState.MAINMENU)
        }),
        DEBUGMODE(InputManager.KEY_F1.toInputSpec(), onRelease = {
            fsm.toggleState(AppState.DEBUG)
        })
    }
}


internal fun touchControls(controllable: Controllable): Scene {
//    val currentInputAxis = object : Controllable {
//        override val inputVec = MutableVec2i(0, 0)
//        override var digLeft: Boolean = false
//        override var digRight: Boolean = false
//    }
    val currentAnalog = object {
        val inputVec = MutableVec2f(0f, 0f)
    }
    val deadZone = 0.2f
    val vjcTex by lazy {
        Texture2d(bluredTextureProps) {
            return@Texture2d it.loadTextureData("images/JoystickSplitted.png", bluredTextureProps.format)
        }
    }
    val handleTex by lazy {
        Texture2d(bluredTextureProps) {
            return@Texture2d it.loadTextureData("images/SmallHandleFilledGrey.png", bluredTextureProps.format)
        }
    }
    val colors = Colors.darkColors(
        background = Color(0f, 0f, 0f, 0.25f)
    )
    val tmp = MutableVec3f()
    val ray = Ray()
    return UiScene("virtual joystick", false) {

        +sprite2d(vjcTex).apply {
//            onProcessInput += {
            onUpdate += { ev->

                // min Width
                // max width
                setIdentity()
                translate(
                    ev.viewport.width - this.bounds.width / 2.0,
                    - ev.viewport.height + this.bounds.height / 2.0,
                    0.0
                )
                currentAnalog.inputVec.x = 0f
                currentAnalog.inputVec.y = 0f
//                controllable.inputVec.x = 0
//                controllable.inputVec.y = 0
                if (handleTex.loadedTexture != null && vjcTex.loadedTexture != null) {
                    ev.ctx.inputMgr.pointerState.pointers
                        .filter { it.isValid && !it.isConsumed() && it.isAnyButtonDown }
                        .forEach {

                            if (!camera.computePickRay(ray, it, ev.viewport, ev.ctx))
                                return@forEach
                            ray.origin.z = 0f

                            logd  { "$bounds ${ray.origin} ${bounds.contains(ray.origin)}" }
                            if (bounds.contains(ray.origin)) {
                                handleTex.loadedTexture?.let { ht ->
                                    tmp.set(ray.origin)
                                    toLocalCoords(tmp)
                                    tmp.x /= (vjcTex.loadedTexture!!.width - handleTex.loadedTexture!!.width) / 2f
                                    tmp.y /= (vjcTex.loadedTexture!!.height - handleTex.loadedTexture!!.height) / 2f
                                    controllable.inputVec.x = if (tmp.x < -deadZone) -1 else if (tmp.x > deadZone) 1 else 0
                                    controllable.inputVec.y = - if (tmp.y < -deadZone) -1 else if (tmp.y > deadZone) 1 else 0
                                    currentAnalog.inputVec.x = tmp.x.clamp(-1f, 1f)
                                    currentAnalog.inputVec.y = tmp.y.clamp(-1f, 1f)
                                    val l = currentAnalog.inputVec.length()
                                    if ( l > 1 ) {
                                        currentAnalog.inputVec.x /= l
                                        currentAnalog.inputVec.y /= l
                                    }
//                                    println(currentAnalog.inputVec)
                                    it.consume()
                                }

                            }
                        }
                }
            }

            +sprite2d(handleTex).also { inner ->

                inner.onUpdate += {
                    if ( vjcTex.loadedTexture != null && handleTex.loadedTexture != null ) {
                        inner.setIdentity()
                        inner.translate(
                            ((currentAnalog.inputVec.x) * (vjcTex.loadedTexture!!.width - handleTex.loadedTexture!!.width) / 2f).clamp(
                                -vjcTex.loadedTexture!!.width + handleTex.loadedTexture!!.width / 2f,
                                vjcTex.loadedTexture!!.width - handleTex.loadedTexture!!.width / 2f
                            ),
                            ((currentAnalog.inputVec.y) * (vjcTex.loadedTexture!!.height - handleTex.loadedTexture!!.height) / 2f).clamp(
                                -vjcTex.loadedTexture!!.height + handleTex.loadedTexture!!.height / 2f,
                                vjcTex.loadedTexture!!.height - handleTex.loadedTexture!!.height / 2f
                            ),
                            0f
                        )
                    }
                }
            }

        }

        +Panel(colors = Colors.darkColors(
            background = Color(0f, 0f, 0f, 0.5f)
        )) {

            val W = 360.dp
            val H = 360.dp

            modifier.apply {
                alignY = AlignmentY.Bottom
                width( FitContent )
                height(FitContent)
                margin(30f.dp, 30f.dp)
            }
            Row(FitContent, FitContent) {
                Column(Grow.Std, FitContent) {
                    modifier.alignX = AlignmentX.Start
                    Row(Grow.Std, FitContent) {
                        Column(Grow.Std, FitContent) {
                            Button("dig left") {
                                modifier.apply {
                                    isClickFeedback = false
                                    textColor = Color.WHITE
                                    buttonColor = Color(0.5f, 0.5f, 0.5f, 0.25f)
                                    width( 45f.dp )
                                    height(45f.dp )
                                    margin(30f.dp, 30f.dp)
                                    onClick += {
                                        controllable.digLeft = true
                                    }
                                }

                            }
                        }
                        Column(Grow.Std, FitContent) {
                            Button("dig right") {
                                modifier.apply {
                                    isClickFeedback = false
                                    textColor = Color.WHITE
                                    buttonColor = Color(0.5f, 0.5f, 0.5f, 0.25f)
                                    width( 45f.dp )
                                    height(45f.dp )
                                    margin(30f.dp, 30f.dp)
                                    onClick += {
                                        controllable.digRight = true
                                    }
                                }
                            }
                        }
                    }
                }

//                Column(Grow.Std, FitContent) {
//
////                    Image(vjcTex) {
////                        modifier.alignX(AlignmentX.End)
////                            .width(W)
////                            .height(H)
////                            .onClick { ev->
////                                val axisGap = 50.dp.px
////                                val res = MutableVec2f(- W.px / 2f,  - H.px / 2f)
////
////                                res += ev.position
////
////                                currentInputAxis.inputVec.x = sign(res.x / W.px).toInt()
////                                currentInputAxis.inputVec.y = sign( res.y / H.px).toInt()
////                            }
////
////
////
////                        Image(handleTex) {
////
////                            onRenderScene += {
////                                this.modifier.margin(
////                                    start = ((currentInputAxis.inputVec.x - 1f) * W.px / 2f ).dp, end = 0f.dp,
////                                    top = ((currentInputAxis.inputVec.y -1f) * H.px / 2f).dp, bottom = 0f.dp
////                                )
////                                println(this.modifier.marginTop)
////                            }
////                            //translate( 360.dp.px / 2f, 360.dp.px / 2f, 0f )
////
////                        }
////                    }
//
//                }
            }
        }
    }
}


interface KeyAction<E> {
    val name: String
    val keyCode: InputSpec
    val onPress: E.(InputManager.KeyEvent) -> Unit
    val onRelease: E.(InputManager.KeyEvent) -> Unit
}
fun<E> InputManager.registerActions(root: E, actions: Array<KeyAction<E>>) = registerActions(root, actions.asIterable())
fun<E> InputManager.registerActions(root: E, actions: Iterable<KeyAction<E>>, ) =
    actions.map { action ->
        registerKeyListener(
            keyCode = action.keyCode.code,
            name = action.name,
            filter = { ev -> (action.keyCode.modificatorBitMask xor ev.modifiers) == 0 }
        ) { ev ->
            if ( ev.isReleased ) action.onRelease.invoke(root, ev)
            else if (ev.isPressed) action.onPress.invoke(root, ev)
        }
    }

fun InputManager.unregisterActions(actions: Iterable<InputManager.KeyEventListener>) {
    actions.forEach { removeKeyListener(it) }
}

/*
    Game(settings).startLevel()
    +GameView(game) -> +me.az.view.LevelView(level) -> +me.az.view.ActorView(runner)

 */
