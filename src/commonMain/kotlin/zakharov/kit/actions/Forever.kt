package zakharov.kit.actions

class Forever<CONTEXT>(val actionFactory: () -> Act<CONTEXT>) : Act<CONTEXT>(/*startBlock = action::enter, exitBlock = action::exit*/) {
    var action: Act<CONTEXT>
    init {
        action = actionFactory()
        onStart { action.enter(this) }
        onEnd { action.exit(this) }
        onUpdate {
            when( action.update(this, it) ) {
                ActionStatus.DONE -> {
                    action = actionFactory()
                    ActionStatus.CONTINUE // show must go on
                }
                ActionStatus.CONTINUE -> ActionStatus.CONTINUE
                ActionStatus.ERROR -> ActionStatus.ERROR
            }
        }
    }
}