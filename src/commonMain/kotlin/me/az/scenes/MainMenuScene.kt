package me.az.scenes

import Action
import ImageAtlas
import ImageAtlasSpec
import MainMenuState.exitGame
import MainMenuState.startnewGame
import TileSet
import de.fabmax.kool.AssetManager
import de.fabmax.kool.InputManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.FontProps
import me.az.ilode.Game
import me.az.ilode.InputSpec
import me.az.ilode.toInputSpec
import me.az.utils.addDebugAxis
import me.az.view.ImageText
import me.az.view.TextDrawer
import registerActions
import simpleTextureProps
import sprite


data class MainMenuContext(
    val tileSet: MutableStateValue<TileSet> = MutableStateValue( TileSet.SPRITES_ATARI8BIT ),
    val level: MutableStateValue<Int> = mutableStateOf(0)
)

class MainMenuScene(val context: MainMenuContext) : AsyncScene() {

    private val fontAtlas = ImageAtlas(ImageAtlasSpec(context.tileSet.value, "text"))

    val fontProps = FontProps(
        family = "text",
        sizePts = 22f,
        chars = TextDrawer.fontMap.keys.joinToString("")
    )

    override suspend fun AssetManager.loadResources(ctx: KoolContext) {
         fontAtlas.load(this)
    }

    val transparent = Color(0.25f,0.25f,0.25f,0f)
    private fun ButtonModifier.themeButtons(): ButtonModifier {
        width(Grow.Std)
        margin(Dp(20f))
        textAlign(AlignmentX.Center, AlignmentY.Center)
        background(RectBackground(Color.WHITE))
//        background(RectBackground(pc))
        isClickFeedback(false)

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

    private fun UiScope.ImageButton(text: String, scale: Float = 2f, action: () -> Unit) {
        Button() {
            println(text)
            modifier
                .themeButtons()

                .width( (scale * text.length * fontAtlas.spec.tileWidth).dp )
                .height( (scale * fontAtlas.spec.tileHeight).dp )
                .onClick {
                    action()
                }
            ImageText(fontAtlas, text) {
                modifier.padding(start = 5.dp, bottom = 5.dp)
                    .width(this@Button.modifier.width)
                    .height(this@Button.modifier.height)
                    .alignX(AlignmentX.Center)
                    .alignY(AlignmentY.Center)
            }
        }
    }

    enum class MenuActions(
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
    override fun setup(ctx: KoolContext) {

        +sprite(Texture2d(simpleTextureProps) {
            it.loadTextureData("images/cover.jpg")
        })

        selected.set(0)

        registerActions(ctx.inputMgr, this, MenuActions.values().asIterable())

        setupUiScene(clearScreen = true)


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


            ImageButton("${marks[0].use()}new game", scale) {
                startnewGame = true
            }

            Button("${marks[1].use()}level:${context.level.use().toString().padStart(3, '0')}") {
                modifier.onClick {
                    context.level.set( context.level.value + 1)
                }
                    .font(fontProps)
                    .isClickFeedback(false)
                    .colors(
                        buttonColor = transparent,
                        buttonHoverColor = transparent
                    )

            }

            ImageButton("${marks[1].use()}level: ${context.level.use()}", scale) {
                context.level.set( context.level.value + 1)
            }

            ImageButton( "${marks[2].use()}exit" ) {
                exitGame = true
            }
        }
    }

    //@KeySpec(ARROW_UP)
    fun select(select: Int) { selected.set(select.mod(totalItems)) }
    fun selectUp() = select (selected.value - 1)
    fun selectDown() = select( selected.value + 1)
}