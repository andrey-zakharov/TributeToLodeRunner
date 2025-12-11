package zakharov.kit.fsm

fun<STATEKEY, E> stackedStateMachine(
    initialState: STATEKEY,
    init: StackedStateMachine<STATEKEY, E>.() -> Unit): StackedStateMachine<STATEKEY, E> {

    val machine = StackedStateMachine<STATEKEY, E>(initialState)
    machine.init()
//    machine.reset(true)
    return machine
}


class StackedStateMachine<STATEKEY, E>(private val initialState: STATEKEY) {

    fun debugOn() {
        stateList.forEach { with(it.value) {
            debugOn()
        } }
    }

    private val states = mutableListOf<STATEKEY>()
    val currentStateName get() = states.current()!!
    val currentState get() = stateList[states.current()!!]!!
    val onStateChanged = mutableListOf<StackedState<STATEKEY, E>.() -> Unit>()

    // builder
    private val stateList = mutableMapOf<STATEKEY, StackedState<STATEKEY, E>>()
    // 2 diff ways to work with: +PredefinedObjectState
    // or via dsl: state {
    // }
    operator fun plusAssign(state: StackedState<STATEKEY, E>) { stateList[state.name] = state }

    fun state(name: STATEKEY, init: StackedState<STATEKEY, E>.() -> Unit) {
        val state = StackedState<STATEKEY, E>(name)
        state.init()
        this += state
    }
    fun getState(name: STATEKEY): StackedState<STATEKEY, E> = stateList[name] ?: throw NoSuchElementException(name.toString())

    fun update(event: E, safe: Boolean = false /* treat unknown states as exit from fsm or not*/) {

        var edge = currentState.beforeUpdate(event)

        // fsm for updating fsm???
        if (edge is StackedStateEdge<STATEKEY, E> && edge.targetState != currentStateName) {
            if ( safe && edge.targetState !in stateList.keys ) {
                println("finish by unknown edge: ${edge.targetState}")
                //exit
                finish()
                return
            }

            val newState = edge.enterEdge {
                getState(it)
            }

            pushState(newState, edge.replace)
        }

        currentState.update(event)?.run {
            (this as? STATEKEY)?.run {
                if (this != Unit && this != currentStateName) {
                    pushState(getState(this), true)
                }
            }

//            popState()
        }
    }

    // forcefully set state
    fun setState(stateName: STATEKEY) {
        pushState(getState(stateName), true)
    }
    fun pushState(newState: StackedState<STATEKEY, E>, replace: Boolean = false) {
        val prev = if ( replace ) {
            states.pop()?.run {
                getState(this).exitState()
                this
            }
        } else {
            states.current()?.run {
                getState(this).suspendState()
                this
            }
        } ?: initialState

        states.push(newState.name)
        newState.enterState(prev)
        onStateChanged.forEach { it(newState) }
    }

    fun finish() {
        while( states.isNotEmpty() ) {
            states.pop()?.run { getState(this) }.also { it?.exitState() }
        }
    }
    val finished get() = states.isEmpty()

    fun toggleState(stateName: STATEKEY) = if ( currentStateName == stateName ) {
        popState()!!
    } else {
        pushState(getState(stateName), false)
        currentStateName
    }

    fun popState(): STATEKEY? {
        val old = states.pop()?.run { getState(this) }.also { it?.exitState() }
        if ( states.isEmpty() ) states.add(initialState)

        currentState.wakeupState(old?.name ?: initialState)
        if ( old?.name != currentStateName )
            onStateChanged.forEach { it(currentState!!) }
        return currentStateName
    }
    fun reset(force: Boolean = true) {
        // with exit states?
        if ( force ) {
            states.clear()
            states.add(initialState)
            onStateChanged.forEach { it(currentState) }
        } else {
            states.clear()
            pushState(getState(initialState))
        }
    }
}