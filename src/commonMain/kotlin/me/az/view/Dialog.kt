package me.az.view

import de.fabmax.kool.KoolContext
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.animation.Animator
import de.fabmax.kool.scene.animation.InterpolatedFloat
import de.fabmax.kool.scene.animation.InverseSquareAnimator
import de.fabmax.kool.util.Color
import me.az.utils.buildStateMachine

// title mutable string?
fun dialog(title: String, block: (RowScope.(parent: Dialog) -> Unit)?) = Dialog(mutableStateOf(title), block)
fun dialog(title: MutableStateValue<String>, block: (RowScope.(parent: Dialog) -> Unit)?) = Dialog(title, block)

class Dialog(val title: MutableStateValue<String>, block: (RowScope.(parent: Dialog) -> Unit)? ) {
    private val panel = Panel(
        sizes = Sizes.large,
        colors = Colors.darkColors(
            primary = Color("efefef"),
            background = Color("101010a0"),
            backgroundVariant = Color(
                "143d52b0"
            ))
    ) {

        fun UiScope.defaultButtons() {
            Button("continue") {
                hideMenu()
            }
        }

        fun ButtonModifier.style(): ButtonModifier {
            isClickFeedback(false)
            textColor = colors.primary
            buttonColor = colors.backgroundVariant
            buttonHoverColor = colors.primary
            textHoverColor = colors.backgroundVariant
            margin(25.dp)
            width(Grow.Std)
            font = sizes.largeText
            return this
        }

        modifier
            .width((sizes.largeText.sizePts * 30f).dp)
            .height(FitContent)
            .padding(15.dp)
            .layout(ColumnLayout)
            .alignX(AlignmentX.Center)
            .alignY(AlignmentY.Center)
        Row(Grow.Std) {
            Text(title.use()) {
                modifier
                    .textAlignX(AlignmentX.Center)
                    .textAlignY(AlignmentY.Center)
                    .width(Grow.Std)
                    .height((sizes.largeText.sizePts * 3f).dp)
                    .font(sizes.largeText)
            }
        }
        Row(Grow.Std) {
            block?.invoke(this, this@Dialog) ?: defaultButtons()

            for ( b in this.uiNode.children.filterIsInstance<ButtonNode>() ) {
                b.modifier.style()
            }
        }
    }

    val ui: Scene = UiScene {
        onUpdate += {
            fsm.update(it.ctx to this@Dialog)
        }
    }

    private var show = false
    private var hide = false

    enum class DialogState {
        CLOSED, OPENING, OPENED, CLOSING
    }
    val fsm by lazy { buildStateMachine<DialogState, Pair<KoolContext, Dialog>>(DialogState.CLOSED) {
        state(DialogState.OPENING) {
            onEnter {
                pauseMenuAnimator.value.from = -1000f
                pauseMenuAnimator.value.to = 0f
                pauseMenuAnimator.progress = 0f
                pauseMenuAnimator.speed = 1f
                show = false
            }
            onUpdate {
                animate(first)
            }
            edge(DialogState.CLOSING) { validWhen { hide } }
            edge(DialogState.OPENED) {
                validWhen {
                    pauseMenuAnimator.progress >= 1f
                }
            }
        }
        state(DialogState.OPENED) {
            edge(DialogState.CLOSING) {
                validWhen { hide }
            }
        }
        state(DialogState.CLOSING) {
            onEnter {
                hide = false
                pauseMenuAnimator.speed = -1f
            }
            onUpdate {
                animate(first)
            }
            edge(DialogState.OPENING) { validWhen { show } }
            edge(DialogState.CLOSED) {
                validWhen {
                    pauseMenuAnimator.progress <= 0f
                }
            }
        }
        state(DialogState.CLOSED) {
            onEnter { ui -= panel }
            onExit { ui += panel }
            edge(DialogState.OPENING) {
                validWhen { show }
            }
        }
    }.also { it.reset(true) } }

    private fun animate(ctx: KoolContext) {
        val trans = pauseMenuAnimator.tick(ctx)
        panel.setIdentity().scale(1f, -1f, 1f).translate(0f, trans, 0f)
    }

    private val pauseMenuAnimator = InverseSquareAnimator(InterpolatedFloat(-2000f, 0f)).also {
        it.repeating = Animator.ONCE
        it.duration = 0.5f
    }
    fun showMenu() { show = true }
    fun hideMenu() { hide = true }
    val isShown get() = fsm.currentStateName == DialogState.OPENING || fsm.currentStateName == DialogState.OPENED
}