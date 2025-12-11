package zakharov.kit.fsm

abstract class CompoundState<STATEKEY, E>(name: STATEKEY) : StackedState<STATEKEY, E>(name) {
    abstract val internalFsm: StackedStateMachine<STATEKEY, E>
    init {
        onEnter {
            internalFsm.reset()
        }
        onExit {
            internalFsm.finish()
        }

        onUpdate {
            if ( internalFsm.finished ) return@onUpdate null

            val oldstate = internalFsm.currentStateName
            internalFsm.update(this, true)
            if ( internalFsm.finished ) return@onUpdate null
            if ( oldstate != internalFsm.currentStateName ) {
                internalFsm.currentStateName
            } else {
                null
            }
        }
    }
}