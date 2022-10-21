package me.az.scenes

import App
import AppContext
import LevelSet
import LevelView
import LevelsRep
import ViewSpec
import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.*
import de.fabmax.kool.math.spatial.BoundingBox
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.*
import de.fabmax.kool.scene.animation.*
import de.fabmax.kool.util.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.yield
import me.az.ilode.*
import me.az.shaders.MaskShader
import me.az.utils.format
import me.az.view.CameraController
import kotlin.coroutines.CoroutineContext
import kotlin.math.ceil
import kotlin.math.sqrt

class GameLevelScene (
    game: Game,
    ctx: KoolContext,
    assets: AssetManager = ctx.assetMgr,
    appContext: AppContext,
    name: String? = null,
    private val startNewGame: Boolean = false,

    ) : GameScene(game, assets, appContext, ViewSpec(), name), CoroutineScope {

    private val currentLevel get() = levels.getLevel(appContext.currentLevel.value, true)

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job

    private var off: OffscreenRenderPass2d? = null

    private val levels = LevelsRep(assets, tilesAtlas, this)

    private val shatterRadiusAnim = LinearAnimator(InterpolatedFloat(0f, 1f)).apply {
        duration = 1f
    }
    val currentShutter get() = shatterRadiusAnim.value.value
    val debug = MutableStateValue("")

    var levelView: LevelView? = null
    var cameraController: CameraController? = null
    var mask: Group? = null
    //cam
    private val visibleWidth = conf.visibleWidth
    private val visibleHeight = conf.visibleHeight
    private val maxShatterRadius = ceil(sqrt((visibleWidth* visibleWidth + visibleHeight * visibleHeight).toDouble()) / 2).toInt()

    override suspend fun loadResources(assets: AssetManager, ctx: KoolContext) {
        super.loadResources(assets, ctx)
        levels.load(LevelSet.CLASSIC)
    }

    fun dispose() {
        job.cancel()
    }

    override fun setup(ctx: KoolContext) {


        if ( startNewGame ) {
            game.runner.startNewGame()
        }

        game.onStateChanged += {
            println("GameScene. gameState = $name")
            when(name) {
                GameState.GAME_START -> {
                    // timer to demo, or run
                    startIntro(ctx)
                }
                GameState.GAME_RUNNING -> stopIntro(ctx)
                GameState.GAME_FINISH -> {
                    sounds.playSound("goldFinish")
                    startOutro(ctx)
                }

                GameState.GAME_RUNNER_DEAD -> {
                    startOutro(ctx)
                }
                GameState.GAME_PREV_LEVEL -> {
                    if ( shatterRadiusAnim.speed == 0f ) startOutro(ctx)

                    with(appContext.currentLevel) {
                        set( value - 1 )
                        if ( value < 0 ) {
                            set( value + levels.levels.size )
                        }
                    }
                }

                GameState.GAME_NEXT_LEVEL -> {
                    // black
                    if ( shatterRadiusAnim.speed == 0f ) startOutro(ctx)
                    println("nextlevel: ${appContext.currentLevel} ${levels.levels.size}")
                    with(appContext.currentLevel) {
                        set( value + 1 )
                        if ( value >= levels.levels.size ) {
                            // finish
                            TODO("FINISH")
                        }
                    }
                }
                GameState.GAME_NEW_LEVEL -> {
                    game.level = currentLevel.also {
                        it.holesAnims = holeAnims
                    }

                    addLevelView(ctx)
                    // startPlay()
                    for ( g in game.guards ) {
                        g.sounds = sounds
                    }
                }
                else -> Unit
            }
        }

        super.setup(ctx)
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
        )

        levelView?.run {
            off = OffscreenRenderPass2d(this, renderPassConfig {
                this.name = "bg"

                setSize(visibleWidth, visibleHeight)
                setDepthTexture(false)
                addColorTexture {
                    colorFormat = TexFormat.RGBA
                    minFilter = FilterMethod.NEAREST
                    magFilter = FilterMethod.NEAREST
                }
            })
        }

        off?.run {
            camera = App.createCamera( visibleWidth, visibleHeight ).apply {
                projCorrectionMode = Camera.ProjCorrectionMode.OFFSCREEN
            }
            clearColor = Color(0.00f, 0.00f, 0.00f, 0.00f)
            this@GameLevelScene.addOffscreenPass(this)

            cameraController = CameraController(this@run.camera as OrthographicCamera, ctx = ctx)
        }

        cameraController?.run {
            this@GameLevelScene += this
            levelView?.run { startTrack(game, this@run, runnerView) }
        }

        // minimap TBD
//        +sprite(Texture2d(simpleValueTextureProps, game.level!!.updateTileMap())).apply {
//            translate(100f, 230f, 0f)
//            scale(5f)
//            grayScaled = true
//        }

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

        onUpdate += ticker // start play
    }

    private val ticker = { ev: RenderPass.UpdateEvent ->
        if ((ev.time - lastUpdate) * 1000 >= appContext.speed.value.msByPass) {
            game.tick(ev)
            lastUpdate = ev.time
        }
    }

    private fun removeLevelView(ctx: KoolContext) {
        cameraController?.run {
            stopTrack(game)
            this@GameLevelScene -= this
        }


        onUpdate -= ticker // stop ticker

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
                    with(game) {
                        debug.set(
                            """
                                global runner center = %.1f x %.1f
                                camera = %.1f/%.1f x %.1f/%.1f
                                act = %s
                                barrier = %b
                                has guard below = %b
                                can move down: %b
                                guards: %s
                                level.gold=%d
                               
                                %s""".trimIndent()
                            .format(
                                levelView?.runnerView?.globalCenter?.x, levelView?.runnerView?.globalCenter?.y,
                                off?.camera?.position?.x, camera.position.x, off?.camera?.position?.y, camera.position.y,
                                level?.act?.get(runner.x)?.get(runner.y) ?: "<no level>",
                                level?.isBarrier(runner.x, runner.y) ?: false,
                                level?.hasGuard(runner.x, runner.y + 1) ?: false,
                                level?.run { runner.canMoveDown } ?: false,
                                guards.joinToString(" ") { it.hasGold.toString() },
                                level?.gold ?: 0,
                                runner.toString()
                            )
                        )
                    }
                }
            }
        }
        Row {LabeledSwitch("stop animations", game.stopAnims) }
        Row {LabeledSwitch("stop guards", appContext.stopGuards) }
        Row {LabeledSwitch("immortal", appContext.immortal) }
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
            value.from = maxShatterRadius.toFloat()
            value.to = 0f
        }

    private suspend fun Animator<Float, InterpolatedFloat>.playAnim(from: Float, to: Float) {
        speed = 1f
        progress = 0f
        value.from = 0f
        value.to = maxShatterRadius.toFloat()
        while (progress < 1) yield()
    }

    private fun startIntro(ctx: KoolContext) =
        shatterRadiusAnim.apply {
            speed = 1f
            progress = 0f
            value.from = 0f
            value.to = maxShatterRadius.toFloat()
        }

    private fun stopIntro(ctx: KoolContext) = shatterRadiusAnim.apply {
//        progress = 1f
        speed = 100f
    }
}

val OrthographicCamera.height get() = top - bottom
val OrthographicCamera.width get () = right - left

val BoundingBox.width get() = max.x - min.x
val BoundingBox.height get() = max.y - min.y
