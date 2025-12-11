package zakharov.kit.fsm

// v1
fun <E, STATEKEY> buildStateMachine(initialStateName: STATEKEY, init: StateMachine<E, STATEKEY>.() -> Unit): StateMachine<E, STATEKEY> {
    val stateMachine = StateMachine<E, STATEKEY>(initialStateName)

    stateMachine.init()

    stateMachine.reset()

    return stateMachine
}

/**
 * Simple state machine: only current state exists with no retrospective
 */
class StateMachine<EVENT, STATEKEY>(private val initialStateName: STATEKEY) {

    private var _currentState: State<STATEKEY, EVENT>? = null
        set(value) {
            if ( value != _currentState ) {
                field = value
                onStateChanged.forEach { it(value!!) }
            }
        }
    val currentState: State<STATEKEY, EVENT>?
        get() = _currentState


    private val stateMap = mutableMapOf<STATEKEY, State<STATEKEY, EVENT>>()
    val onStateChanged = mutableListOf<State<STATEKEY, EVENT>.() -> Unit>()

    /**
     * State builder for declarative description, but also just for adding
     * state objects to machine
     * @sample
     * buildMachine() {
     *  state("state1") {
     *   onEnter {
     *   }
     *   onUpdate {
     *   }
     *   onExit {
     *   }
     *  }
     */
    fun state(name: STATEKEY, init: State<STATEKEY, EVENT>.() -> Unit) {
        if ( stateMap.contains(name) ) {
            throw IllegalStateException("double $name")
        }

        val state = State<STATEKEY, EVENT>(name)
        state.init()

        stateMap[name] = state
    }
    // operator style
    operator fun plusAssign(state: State<STATEKEY, EVENT>) {
        stateMap[state.name] = state
    }

    fun reset() = setState(initialStateName)

    private fun setState(stateName: STATEKEY) = setState(stateMap[stateName]!!)

    private fun setState(newState: State<STATEKEY, EVENT>) {
        currentState?.exitState()
        _currentState = newState
        newState.enterState()
    }

    fun update(event: EVENT) {
        // check all edges
        val edge = currentState?.beforeUpdate(event)

        // fsm for updating fsm???
        if (edge is Edge<STATEKEY, EVENT> && edge.targetState != currentState?.name) {
            if ( edge.targetState !in stateMap.keys ) {
                throw IllegalStateException("finish by unknown edge: ${edge.targetState}")
            }

            val newState = edge.enterEdge {
                stateMap[it]!!
            }

            setState(newState)
        }

        currentState?.update(event)?.run {
            (this as? STATEKEY)?.run {
                if (this != Unit && this != currentState?.name) {
                    setState(this)
                }
            }
        }
    }
}
