package me.az.scenes

import AppContext
import LevelsRep
import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.spatial.BoundingBox
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.OrthographicCamera
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.animation.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.yield
import me.az.ilode.Game
import me.az.ilode.GameState
import me.az.utils.debugOnly
import me.az.utils.format
import me.az.utils.logd
import me.az.view.dialog
import me.az.view.sprite2d
import simpleTextureProps
import kotlin.coroutines.CoroutineContext
import kotlin.math.sign

fun<V, T : InterpolatedValue<V>> Animator<V, T>.dis(): String = "duration=%.3f speed=%.3f progress=%.3f repeating=%d".format(
    duration, speed, progress, repeating
)

class GameLevelScene (
    game: Game,
    ctx: KoolContext,
    assets: AssetManager = ctx.assetMgr,
    appContext: AppContext,
    name: String? = null,
) : GameScene(game, assets, appContext, name = name), CoroutineScope {

    val gameOverSprite by lazy {
        // on exit - break anim. TBD

        sprite2d(gameOverTex, "gameover", mirrorTexCoordsY = false).also {
            val startSpeed = 10f
            val speedAnim = InverseSquareAnimator(InterpolatedFloat(startSpeed, 1f)).apply {
                speed = 0.75f
            }

            val scaleAnim = CosAnimator(InterpolatedFloat(-1f, 1f)).apply {
                repeating = Animator.REPEAT_TOGGLE_DIR
                speed = startSpeed
            }

            val tmpPos = MutableVec3f()

            onUpdate += { ev->
                it.transform.resetScale() // still remains negative -1
                //            it.translate(this@GameLevelScene.camera.globalCenter)
                tmpPos.set(ev.viewport.width / 2f, ev.viewport.height / 2f, 0f)
                levelView?.toLocalCoords(tmpPos)
                it.transform.setTranslate( tmpPos.toVec3d() )

                // -1.0 zoom glitches?
                val scale = scaleAnim.tick(ev.ctx) / appContext.spriteMode.value.tileHeight
                it.scale(1.0 /  appContext.spriteMode.value.tileWidth, scale.toDouble()
                        //hack
                         * it.transform[1, 1].sign, 1.0)

                scaleAnim.speed = speedAnim.tick(ev.ctx) * scaleAnim.speed.sign
                if ( scaleAnim.speed > 0 && scaleAnim.speed <= 1f && scaleAnim.repeating != Animator.ONCE ) {
                    scaleAnim.repeating = Animator.ONCE
                }
                if (scaleAnim.progress == 1f && scaleAnim.repeating == Animator.ONCE && !game.animEnds) {// end game
                    logd { "game over done" }
                    game.animEnds = true
                }
            }
        }
    }

    init {
        game.onStateChanged += {
            logd { "GameScene. gameState = ${this.name}" }
            if ( this.name != GameState.GAME_OVER_ANIMATION ) {
                // exit state
                if (levelView?.findNode("gameover") != null) {
                    logd { "removing gameover sprite"}
                    levelView?.run { -gameOverSprite }
                }
            }
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
//                        holesAnims = holeAnims
                        reset()
                        addLevelView(ctx, this)
                    }
                }
                GameState.GAME_OVER_ANIMATION -> {

                    levelView?.run { +gameOverSprite }
                    ctx.runDelayed((ctx.fps * 5).toInt()) {
                        game.animEnds = false
                        startOutro(ctx) // await
                    }
                }
                else -> Unit
            }
        }
    }
    private val currentLevel get() = levels.getLevel(appContext.currentLevel.value, tilesAnims, false)

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job

    private lateinit var gameOverTex: Texture2d
    private val levels = LevelsRep(assets, this)

    private val debug = MutableStateValue("")

    override suspend fun loadResources(assets: AssetManager, ctx: KoolContext) {
        super.loadResources(assets, ctx)
        gameOverTex = assets.loadAndPrepareTexture("images/game-over.png", simpleTextureProps)
        levels.load(appContext.levelSet.value)
    }

    fun setupUi(scene: Scene) = debugOnly { with(scene) {
        +Panel {
            modifier
                .width(Grow(1f, max = 300f.dp))
                .height(FitContent)
                .margin(start = 25.dp, top = 25.dp, bottom = 60.dp)
                .layout(ColumnLayout)
                .alignX(AlignmentX.End)
                .alignY(AlignmentY.Bottom)
            Row {
                Text(debug.use()) {
                    modifier
                        .width(FitContent)
                        .height(FitContent)
                    onUpdate += {
                        with(game) {

                            debug.set(
                                """
                                    #speedAnim = %%s %%.3f
                                    #scaleAnim = %%s %%.3f
                                    shatter = %.3f
                                    
                                global runner center = %.1f x %.1f
                                off.camera = %.1f x %.1f ${cameraController?.debug}
                                ui.camera =  %.1f x %.1f
                                act = %s
                                barrier = %b
                                has guard below = %b
                                can move down: %b
                                guards: %s
                                level.gold=%d
                               
                                %s""".trimIndent()
                                    .format(
//                                        speedAnim.dis(), speedAnim.value.value, scaleAnim.dis(), scaleAnim.value.value,
                                        currentShutter,
                                        levelView?.runnerView?.instance?.modelMat?.get(12), // x
                                        levelView?.runnerView?.instance?.modelMat?.get(13), // x
                                        off?.camera?.position?.x,
                                        off?.camera?.position?.y,
                                        camera.position.x,
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
            Row {
                Text("camera") {
                    labelStyle(Grow.Std)
                }
            }
            Row { LazyList(
                width = Grow.Std,
                height = Grow(1f, max = 400f.dp)
            ) {

                itemsIndexed(uiSpriteSystem.sprites) { i, item ->
                    Text("${item.atlasId.value} ${item.tileIndex.value}") {
                        modifier
                            .width(Grow.Std)
                            .height(sizes.normalText.sizePts.dp)
                            .textAlignY(AlignmentY.Center)

                    }

                }
            }}
//            Row { Image(off?.colorTexture) {
//                modifier.width = 240.dp
//                modifier.height = 240.dp
//            } }
        }
    } }

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

    val pauseMenu by lazy { dialog("level title") { parent ->
        Button("main menu") {
            modifier.onClick += {
                game.overGameInstant()
            }
        }

        Button("continue") {
            modifier.onClick += {
                game.resumeGame()
                parent.hideMenu()
            }
        }

        Button("restart") {
            modifier.onClick += {
                game.abortGame()
                parent.hideMenu()
            }
        }
    } }
}

val OrthographicCamera.height get() = top - bottom
val OrthographicCamera.width get () = right - left

val BoundingBox.width get() = max.x - min.x
val BoundingBox.height get() = max.y - min.y
