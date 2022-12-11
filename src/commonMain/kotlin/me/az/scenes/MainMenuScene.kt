package me.az.scenes

import AppContext
import GameSpeed
import LevelSet
import LevelsRep
import TileSet
import de.fabmax.kool.AssetManager
import de.fabmax.kool.InputManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.MutableVec3d
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.*
import kotlinx.coroutines.launch
import me.az.Version
import me.az.ilode.Game
import me.az.ilode.GameLevel
import me.az.ilode.GameState
import me.az.utils.logd
import me.az.utils.plus
import me.az.view.TextView
import unregisterActions
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow


data class MainMenuContext(
    val levelSet: MutableStateValue<LevelSet>,
    val level: MutableStateValue<Int> = mutableStateOf(0)
)

class MainMenuScene(context: AppContext, game: Game, assets: AssetManager) :
    GameScene(game, assets, context, name = "mainmenu") {

    val menuContext = MainMenuContext(
        mutableStateOf(context.levelSet.value), mutableStateOf(context.currentLevel.value)
    )

    private val subs = mutableListOf<InputManager.KeyEventListener>()

    private val currentSpriteName = mutableStateOf(appContext.spriteMode.value.dis)
    private val currentSpeedName = mutableStateOf(appContext.speed.value.dis)
    private val currentLevelSet = mutableStateOf(menuContext.levelSet.value.dis)
    private var maxLevelId = Int.MAX_VALUE // reload

    private fun Int.digit(n: Int): Int = (mod( 10f.pow(n).toInt() ) / 10f.pow(n - 1)).toInt()
    private fun Int.replaceDigit(digitPos: Int, value: Int): Int {
        val m = 10f.pow(digitPos - 1).toInt()
        return this - digit(digitPos) * m + value * m
    }

    private val currentLevelDigit1: MutableStateValue<String>
    private val currentLevelDigit2: MutableStateValue<String>
    private val currentLevelDigit3: MutableStateValue<String>

    private val commands = mutableListOf<MenuCommand>()

    data class StaticLabel(
        val text: String, val x: Float, val y: Float
    )
    private val staticLabels = mutableListOf<StaticLabel>()
    fun staticLabel(text: String, x: Float, y: Float) {
        staticLabels += StaticLabel(text, x, y)
    }

    var startnewGame = false
    var continueGame = false

    private val map by lazy {
        val mx = 16
        // this is not actually model. with basic view support as TextureData2d exporter TBD
        val end =    "  # $ # "
        val sep =    "  ##H## "
        val choice = "  $-H-$ "
        val start  = "H @ H& $"
        listOf(
            end,
            choice,
            "  # H#",
            "  @ H-  HHH@",
            "  # H#@@",
            choice,
            sep,
            choice,
            "0 # H # ",
            start

        ).map { it.padEnd(mx, ' ') }
    }

    private val reloadLevels get() = scope.launch {
        val rep = LevelsRep(assets, scope)
        rep.load(menuContext.levelSet.value)
        maxLevelId = rep.levels.size - 1
    }

    init {
        appContext.spriteMode.onChange {
            currentSpriteName.set(it.dis)
        }
        appContext.speed.onChange {
            currentSpeedName.set(it.dis)
        }
        menuContext.levelSet.onChange {
            currentLevelSet.set(it.dis)
            reloadLevels
        }

        reloadLevels

        with(menuContext.level.value + 1) {
            currentLevelDigit1 = mutableStateOf(digit(1).toString())
            currentLevelDigit2 = mutableStateOf(digit(2).toString())
            currentLevelDigit3 = mutableStateOf(digit(3).toString())
        }
        menuContext.level.onChange {
            with(it + 1) {
                currentLevelDigit1.set(digit(1).toString())
                currentLevelDigit2.set(digit(2).toString())
                currentLevelDigit3.set(digit(3).toString())
            }
        }

        val levelHeight = map.size
        val levelWidth = map.first().length
        // in level's space
        // 0 -> x
        // |
        // V y
        val leftCol = 2
        val rightCol = 6
        val dy = 2
        var y = 1

        val labelsX = rightCol.toFloat() + 2f

        // level set
        staticLabel( "level set", labelsX, y - 1f)
        commands += MenuCommand(pos = Vec2i(leftCol, y)) {
            with(menuContext.levelSet) {
                maxLevelId = Int.MAX_VALUE
                set( LevelSet.values()[ (value.ordinal - 1).mod(LevelSet.values().size) ] )
                game.teleportRunnerRight()
            }
        }

        commands += LabeledMenuCommand(pos = Vec2i(rightCol, y), label = currentLevelSet) {
            with(menuContext.levelSet) {
                maxLevelId = 0
                set( LevelSet.values()[ (value.ordinal + 1) % LevelSet.values().size ] )
                game.teleportRunnerLeft()
            }
        }

        y += dy

        // level
        val origin = Vec2i(rightCol + 2, y)
        staticLabel( "level", labelsX, y.toFloat() - 1)
        commands += LabeledMenuCommand(
            pos = origin + Vec2i(0, -1),
            label = currentLevelDigit3,
            labelDelta = Vec2i(0, 1)
        ) {
            game.teleportRunnerDown()
            with(menuContext.level) {
                set( min(maxLevelId, value.replaceDigit(3, value.digit(3) + 1) ) )
            }
        }
        commands += MenuCommand(pos = origin + Vec2i(0, 1)) {
            game.teleportRunnerUp()
            with(menuContext.level) {
                set( max(0, value.replaceDigit(3, value.digit(3) - 1) ) )
            }
        }

        // tens
        commands += LabeledMenuCommand(
            pos = origin + Vec2i(1, -1), label = currentLevelDigit2, labelDelta = Vec2i(0, 1)) {
            game.teleportRunnerDown()
            with(menuContext.level) {
                set( min(maxLevelId, value.replaceDigit(2, value.digit(2) + 1) ) )
            }
        }

        commands += MenuCommand(pos = origin + Vec2i(1, 1)) {
            game.teleportRunnerUp()
            with(menuContext.level) {
                set( max(0, value.replaceDigit(2, value.digit(2) - 1) ) )
            }
        }

        commands += LabeledMenuCommand(
            pos = origin + Vec2i(2, -1), label = currentLevelDigit1, labelDelta = Vec2i(0, 1)) {
            game.teleportRunnerDown()
            with(menuContext.level) {
                set( min(maxLevelId, value.replaceDigit(1, value.digit(1) + 1) ) )
            }
        }

        commands += MenuCommand(pos = origin + Vec2i(2, 1)) {
            game.teleportRunnerUp()
            with(menuContext.level) {
                set( max(0, value.replaceDigit(1, value.digit(1) - 1) ) )
            }
        }


        y += dy
        // game speed
        staticLabel("speed", labelsX, y.toFloat() - 1)
        commands += MenuCommand(pos = Vec2i(leftCol, y)) {
            with(appContext.speed) {
                set( GameSpeed.values()[ (value.ordinal - 1).mod(GameSpeed.values().size) ] )
                game.teleportRunnerRight()
            }
        }

        commands += LabeledMenuCommand(pos = Vec2i(rightCol, y), label = currentSpeedName) {
            with(appContext.speed) {
                set( GameSpeed.values()[ (value.ordinal + 1) % GameSpeed.values().size ] )
                game.teleportRunnerLeft()
            }
        }

        y += dy

        // tileset
        staticLabel("sprites", labelsX, y - 1f)
        commands += MenuCommand(pos = Vec2i(leftCol, y)) {
            appContext.prevSpriteSet()
            game.teleportRunnerRight()
        }

        commands += LabeledMenuCommand(pos = Vec2i(rightCol, y), label = currentSpriteName) {
            appContext.nextSpriteSet()
            game.teleportRunnerLeft()
        }

        commands += LabeledMenuCommand(
            pos = Vec2i(7, levelHeight - 1), label = mutableStateOf("continue game")
        ) {
            continueGame = true
        }

        commands += LabeledMenuCommand(
            pos = Vec2i(4, 0), label = mutableStateOf("new game"),
            labelDelta = Vec2i(0, -2)
        ) {
            startnewGame = true
        }
    }

    open class MenuCommand(
        val pos: Vec2i, // on level in tiles - actuator pos
        val onEnter: MainMenuScene.() -> Unit = {}
    )

    class LabeledMenuCommand(
        pos: Vec2i, // on level in tiles - actuator pos
        val label: MutableStateValue<String>,
        val labelDelta: Vec2i = Vec2i(2, 0),
        onEnter: MainMenuScene.() -> Unit = {}
    ) : MenuCommand(pos, onEnter)

    private val runnerPosString = mutableStateOf("${game.runner.x} x ${game.runner.y}")

    private fun setupStaticLabels() {
        levelView?.let { view ->
            staticLabels.forEach {
                view.addNode(
                    spriteSystem.textView(it.text) {
                        translate(it.x.toDouble(), it.y.toDouble(), 0.0)
                        scale(0.5f, 0.5f, 1f)
                    }
                )
            }
        }
    }

    private fun setupCommands() {
        commands.filterIsInstance<LabeledMenuCommand>().forEach {
            //arranging to level coords
            levelView?.addNode(
                spriteSystem.textView(it.label) {
                    translate( (it.pos.x + it.labelDelta.x).toDouble(), (it.pos.y + it.labelDelta.y).toDouble(), 0.0)
                }
            )
        }
    }

    override fun setup(ctx: KoolContext) {

        super.setup(ctx)
        game.level = GameLevel(0, map, tilesSequences = tilesAnims.sequence )
        game.onPlayGame += { g: Game, _ ->
            runnerPosString.set("${g.runner.x} x ${g.runner.y}")

            commands.firstOrNull {
                it.pos.x == g.runner.block.x && it.pos.y == g.runner.block.y
            }?.run {
                sounds.playSound("getGold")
                onEnter()
            }
        }

        game.onStateChanged += {
            logd { "main menu game change: $name" }
            when(name) {
                GameState.GAME_NEW_LEVEL -> {
                    addLevelView(ctx, game.level!!)

                    val version = "$Version by Andrey Zakharov"
                    +uiSpriteSystem.textView(version) {
//                        translate(10f, currentSpriteSet.value.tileHeight.toFloat() / 2f , 0f)
                        scale(1f / 3f, 1f / 3f, 1f)
                        translate(-(version.length/ 2f), 0f, 0f)
                    }

                    setupStaticLabels()
                    setupCommands()
                }

                GameState.GAME_START -> startIntro(ctx)
                GameState.GAME_FINISH -> game.runner.stop()
                else -> Unit
//                GameState.GAME_RUNNING -> TODO()
//                GameState.GAME_FINISH -> TODO()
//                GameState.GAME_FINISH_SCORE_COUNT -> TODO()
//                GameState.GAME_WAITING -> TODO()
//                GameState.GAME_PAUSE -> TODO()
//                GameState.GAME_RUNNER_DEAD -> TODO()
//                GameState.GAME_OVER_ANIMATION -> TODO()
//                GameState.GAME_OVER -> TODO()
//                GameState.GAME_NEXT_LEVEL -> TODO()
//                GameState.GAME_PREV_LEVEL -> TODO()
//                GameState.GAME_LOADING -> TODO()
//                GameState.GAME_WIN -> TODO()
            }
        }

//        subs.addAll(
//            registerActions(ctx.inputMgr, this, MenuCommands.values().asIterable())
//        )

        game.startGame()
    }

    override fun dispose(ctx: KoolContext) {
        //game.stop()
        game.runner.stop()
        super.dispose(ctx)
        ctx.inputMgr.unregisterActions(subs)
        subs.clear()
    }


    private fun Game.afterTeleport() {
        runner.offset.x = 0
        runner.offset.y = 0
        runner.stop()
    }
    private fun Game.teleportRunnerLeft() {
        runner.block.x --
        afterTeleport()
    }
    private fun Game.teleportRunnerRight() {
        runner.block.x ++
        afterTeleport()
    }
    private fun Game.teleportRunnerDown() {
        runner.block.y ++
        afterTeleport()
    }
    private fun Game.teleportRunnerUp() {
        runner.block.y --
        afterTeleport()
    }

}