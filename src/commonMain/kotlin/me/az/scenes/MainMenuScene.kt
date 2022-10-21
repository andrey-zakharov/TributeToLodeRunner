package me.az.scenes

import Action
import AppContext
import MainMenuState.exitGame
import MainMenuState.startnewGame
import TileSet
import de.fabmax.kool.AssetManager
import de.fabmax.kool.InputManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.OrthographicCamera
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.FontProps
import me.az.ilode.InputSpec
import me.az.ilode.toInputSpec
import me.az.view.TextDrawer
import registerActions
import simpleTextureProps
import sprite
import unregisterActions
import kotlin.math.max
import kotlin.math.min


data class MainMenuContext(
    val tileSet: MutableStateValue<TileSet> = MutableStateValue( TileSet.SPRITES_ATARI8BIT ),
    val level: MutableStateValue<Int> = mutableStateOf(0)
)

class MainMenuScene(context: AppContext) : AsyncScene() {
    val context = MainMenuContext()
    val fontProps = FontProps(
        family = "text",
        sizePts = 22f,
        chars = TextDrawer.fontMap.keys.joinToString("")
    )

    val transparent = Color(0.25f,0.25f,0.25f,0f)
    private fun ButtonModifier.themeButtons(): ButtonModifier {
        width(Grow.Std)
        margin(Dp(20f))
        textAlign(AlignmentX.Start, AlignmentY.Center)
//        background(RectBackground(Color.WHITE))
//        background(RectBackground(pc))
        isClickFeedback(false)
        font(fontProps)
        colors(
            buttonColor = transparent,
            buttonHoverColor = transparent
        )

        return this
    }

    val totalItems = 3
    val selected by lazy { mutableStateOf(-1).also {
         it.onChange {
             marks.forEachIndexed { index, _ -> marks[index].set(if ( it == index ) ">" else " ") }
         }
    } }

    val marks by lazy { Array(totalItems) {
        mutableStateOf(" ")
    } }

    enum class MenuCommands(
        override val keyCode: InputSpec, // or no
        override val onPress: MainMenuScene.(InputManager.KeyEvent) -> Unit = {},
        override val onRelease: MainMenuScene.(InputManager.KeyEvent) -> Unit = {}
    ) : Action<MainMenuScene> {
        SELUP(InputManager.KEY_CURSOR_UP.toInputSpec(), onPress = {
            selectUp()
        }),
        SELDN(InputManager.KEY_CURSOR_DOWN.toInputSpec(), onPress = {
            selectDown()
        }),
        SELECT(InputManager.KEY_ENTER.toInputSpec(), onPress = {
            println(children[selected.value])
        })
    }

    override suspend fun loadResources(assetManager: AssetManager, ctx: KoolContext) {

    }

    private val subs = mutableListOf<InputManager.KeyEventListener>()

    override fun setup(ctx: KoolContext) {
        selected.set(0)

        subs.addAll(
            registerActions(ctx.inputMgr, this, MenuCommands.values().asIterable())
        )

        setupUiScene(clearScreen = true)

        +sprite(Texture2d(simpleTextureProps) {
            it.loadTextureData("images/cover.jpg", simpleTextureProps.format)
//        it.loadAndPrepareTexture("images/cover.jpg")
        }).apply {
            grayScaled = true
            onResize += { w, h ->
//                translate(0f, h.toFloat() / 2f, 0f)
                val imageMinSide = min(w, h)
                val camSide = max((camera as OrthographicCamera).width, (camera as OrthographicCamera).height)
                scale(camSide / imageMinSide)

            }
        }

        selected.onChange {
            println(it)
        }

        +UiSurface(
            Colors.darkColors(),
            Sizes.large()
        ) {
            modifier
                .width(WrapContent)
                .height(WrapContent)
                .background(background = null)
                .layout(ColumnLayout)
                .alignX(AlignmentX.Center)
                .alignY(AlignmentY.Center)
            var scale = 2f


            Button("${marks[0].use()}new game") {
                modifier.onClick {
                    startnewGame = true
                }.themeButtons()
            }

            Button("${marks[1].use()}level: ${context.level.use().toString().padStart(3, '0')}") {
                modifier.onClick {
                    context.level.set(context.level.value + 1)
                }.themeButtons()
            }

            Button( "${marks[2].use()}exit" ) {
                modifier.onClick {
                    exitGame = true
                }.themeButtons()
            }
        }
    }

    override fun dispose(ctx: KoolContext) {
        super.dispose(ctx)
        unregisterActions(ctx.inputMgr, subs)
        subs.clear()
    }

    //@KeySpec(ARROW_UP)
    fun select(select: Int) { selected.set(select.mod(totalItems)) }
    fun selectUp() = select (selected.value - 1)
    fun selectDown() = select( selected.value + 1)
}