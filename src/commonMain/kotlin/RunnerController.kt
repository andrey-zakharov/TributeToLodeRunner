import de.fabmax.kool.InputManager
import de.fabmax.kool.KeyCode
import de.fabmax.kool.UniversalKeyCode
import de.fabmax.kool.scene.Node
import me.az.ilode.Runner

enum class PlayerAction(
    val keyCode: KeyCode,
    val onPress: RunnerController.(InputManager.KeyEvent) -> Unit,
    val onRelease: RunnerController.(InputManager.KeyEvent) -> Unit
) {
    RIGHT(InputManager.KEY_CURSOR_RIGHT, RunnerController::updateStance, { if ( !contMode ) runner.stance[1] = false }),
    LEFT(InputManager.KEY_CURSOR_LEFT, RunnerController::updateStance, { if ( !contMode ) runner.stance[3] = false }),
    UP(InputManager.KEY_CURSOR_UP, RunnerController::updateStance, { if ( !contMode ) runner.stance[0] = false }),
    DOWN(InputManager.KEY_CURSOR_DOWN, RunnerController::updateStance, { if ( !contMode ) runner.stance[2] = false }),
    STOP(UniversalKeyCode(' '), RunnerController::updateStance, { } ),
    DIG_LEFT(UniversalKeyCode('z'), { runner.digLeft = true }, { runner.digLeft = false} ),
    DIG_RIGHT(UniversalKeyCode('x'), { runner.digRight = true }, { runner.digRight = false} ),
}

class RunnerController(val inputManager: InputManager, val runner: Runner): Node() {
    var contMode = true
    init {

        PlayerAction.values().forEach { act->
            inputManager.registerKeyListener(act.keyCode, "player_action_${act.name}_pressed",
                { ev -> ev.isPressed || ev.isReleased },
                { ev -> if ( ev.isReleased ) act.onRelease.invoke(this, ev) else
                    if (ev.isPressed) act.onPress.invoke(this, ev) })
        }
    }

    fun updateStance(event: InputManager.KeyEvent) {
        runner.stance.fill(false)

        for( ev in inputManager.keyEvents ) {
            when(ev.keyCode) {
                InputManager.KEY_CURSOR_UP -> runner.stance[0] = true
                InputManager.KEY_CURSOR_RIGHT -> runner.stance[1] = true
                InputManager.KEY_CURSOR_DOWN -> runner.stance[2] = true
                InputManager.KEY_CURSOR_LEFT -> runner.stance[3] = true
                else -> Unit
            }
        }
    }
}