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
    ) : GameScene(game, assets, appContext, ViewSpec(), name), CoroutineScope {

    init {
        game.onStateChanged += {
            println("GameScene. gameState = ${this.name}")
            when(this.name) {
                GameState.GAME_START -> {
                    // timer to demo, or run
                    startIntro(ctx)
                }
                GameState.GAME_RUNNING -> stopIntro(ctx)
                GameState.GAME_FINISH -> {
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

                    with(appContext.currentLevel) {
                        set( value + 1 )
                        if ( value >= levels.levels.size ) {
                            // finish
                            TODO("FINISH")
                        }
                    }
                }
                GameState.GAME_NEW_LEVEL -> {
                    game.level = currentLevel
                    game.level?.run {
                        holesAnims = holeAnims
                        addLevelView(ctx, this)
                    }
                }
                else -> Unit
            }
        }

    }
    private val currentLevel get() = levels.getLevel(appContext.currentLevel.value, tilesAtlas, false)

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job

    private val levels = LevelsRep(assets, this)

    val debug = MutableStateValue("")

    override suspend fun loadResources(assets: AssetManager, ctx: KoolContext) {
        super.loadResources(assets, ctx)
        levels.load(appContext.levelSet.value)
    }

    fun setupUi(scope: UiScope) = with(scope) {
        modifier
            .width(Grow(1f, max = FitContent))
            .height(FitContent)
            .margin(start = 25.dp, top = 25.dp, bottom = 60.dp)
            .layout(ColumnLayout)
            .alignX(AlignmentX.End)
            .alignY(AlignmentY.Bottom)
        Panel {
            Row {
                Text(debug.use()) {
                    modifier
                        .width(FitContent)
                        .height(FitContent)
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
                                        levelView?.runnerView?.globalCenter?.x,
                                        levelView?.runnerView?.globalCenter?.y,
                                        off?.camera?.position?.x,
                                        camera.position.x,
                                        off?.camera?.position?.y,
                                        camera.position.y,
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
            Row { LabeledSwitch("stop animations", game.stopAnims) }
            Row { LabeledSwitch("stop guards", appContext.stopGuards) }
            Row { LabeledSwitch("immortal", appContext.immortal) }
        }
    }

    fun TextScope.labelStyle(width: Dimension = FitContent) {
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

    private suspend fun Animator<Float, InterpolatedFloat>.playAnim(from: Float, to: Float) {
        speed = 1f
        progress = 0f
        value.from = 0f
        value.to = maxShatterRadius.toFloat()
        while (progress < 1) yield()
    }

    override fun dispose(ctx: KoolContext) {
        job.cancel()
        super.dispose(ctx)
    }
}

val OrthographicCamera.height get() = top - bottom
val OrthographicCamera.width get () = right - left

val BoundingBox.width get() = max.x - min.x
val BoundingBox.height get() = max.y - min.y
