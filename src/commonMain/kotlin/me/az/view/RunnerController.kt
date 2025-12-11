import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.*
import de.fabmax.kool.scene.Node
import me.az.ilode.Controllable

enum class RunnerAction(
    val keyCode: KeyCode,
    val onPress: RunnerController.(KeyEvent) -> Unit,
    val onRelease: RunnerController.(KeyEvent) -> Unit
) {
    RIGHT(KeyboardInput.KEY_CURSOR_RIGHT, { runner.inputVec.x = 1 }, { runner.inputVec.x = 0 }),
    LEFT(KeyboardInput.KEY_CURSOR_LEFT, { runner.inputVec.x = -1 }, { runner.inputVec.x = 0 }),
    UP(KeyboardInput.KEY_CURSOR_UP, { runner.inputVec.y = -1 }, { runner.inputVec.y = 0 }),
    DOWN(KeyboardInput.KEY_CURSOR_DOWN, { runner.inputVec.y = 1 }, { runner.inputVec.y = 0 }),
    STOP(UniversalKeyCode(' '), { with(runner.inputVec) { x = 0; y = 0 }}, { } ),
    DIG_LEFT(UniversalKeyCode('z'), { runner.digLeft = true }, { runner.digLeft = false } ),
    DIG_RIGHT(UniversalKeyCode('x'), { runner.digRight = true }, { runner.digRight = false } ),
}
class RunnerController(private val inputManager: KeyboardInput, val runner: Controllable): Node() {

    val subs = mutableListOf<InputStack.SimpleKeyListener>()
    init {
        subs.addAll( RunnerAction.values().map { act->
            inputManager.addKeyListener(act.keyCode, "runner_action_${act.name}_pressed",
                { ev -> ev.isPressed || ev.isReleased },
                { ev -> if ( ev.isReleased ) act.onRelease.invoke(this, ev) else
                    if (ev.isPressed) act.onPress.invoke(this, ev) })
        } )
    }

    override fun dispose(ctx: KoolContext) {
        super.dispose(ctx)
        subs.forEach { inputManager.removeKeyListener(it) }
    }
}