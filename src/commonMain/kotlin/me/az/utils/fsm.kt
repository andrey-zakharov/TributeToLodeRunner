package me.az.utils

import me.az.ilode.*

class Tested {
    class Edge<E>(val name: String, val targetState: String) {
        lateinit var eventHandler: (E) -> Boolean

        private val actionList = mutableListOf<(Edge<E>) -> Unit>()

        fun onEnter(action: (Edge<E>) -> Unit) {
            actionList.add(action)
        }

        //Invoke when you go down the edge to another state
        fun enterEdge(retrieveState: (String) -> State<E>): State<E> {
            actionList.forEach { it(this) }

            return retrieveState(targetState)
        }

        fun canHandleEvent(event: E): Boolean {
            return eventHandler(event)
        }
    }

    class State<E>(val name: String) {
        private val edgeList = mutableListOf<Edge<E>>()

        fun edge(name: String, targetState: String, init: Edge<E>.() -> Unit) {
            val edge = Edge<E>(name, targetState)
            edge.init()

            edgeList.add(edge)
        }

        private val stateEnterAction = mutableListOf<(State<E>) -> Unit>()
        private val stateExitAction = mutableListOf<(State<E>) -> Unit>()

        //Add an action which will be called when the state is entered
        fun onEnter(action: (State<E>) -> Unit) = stateEnterAction.add(action)
        fun onExit(action: (State<E>) -> Unit) = stateExitAction.add(action)

        fun enterState() = stateEnterAction.forEach { it(this) }
        fun exitState() = stateExitAction.forEach { it(this) }

        //Get the appropriate Edge for the Event
        fun getEdgeForEvent(event: E): Edge<E> {
            return edgeList.first { it.canHandleEvent(event) }
        }
    }

    class StateMachine<E>(private val initialStateName: String) {
        private lateinit var currentState: State<E>

        private val stateList = mutableListOf<State<E>>()

        fun state(name: String, init: State<E>.() -> Unit) {
            val state = State<E>(name)
            state.init()

            stateList.add(state)
        }

        fun getStateByName(name: String): State<E> {
            val result = stateList.firstOrNull { it.name == name }
                ?: throw NoSuchElementException(name)

            return result
        }

        fun initialize() {
            currentState = getStateByName(initialStateName)

            currentState.enterState()
        }

        fun eventOccured(event: E) {
            val edge = currentState.getEdgeForEvent(event)

            if (edge is Edge<E>) {
                val newState = edge.enterEdge { getStateByName(it) }

                newState.enterState()

                currentState = newState
            }
        }
    }

    fun <E> buildStateMachine(initialStateName: String, init: StateMachine<E>.() -> Unit): StateMachine<E> {
        val stateMachine = StateMachine<E>(initialStateName)

        stateMachine.init()

        return stateMachine
    }
}

class StackedStateEdge<E>(val targetState: String, val replace: Boolean = true ) {
    private val validateList = mutableListOf<(E) -> Boolean>()
    private val actionList = mutableListOf<(StackedStateEdge<E>) -> Unit>()
    fun action(action: (StackedStateEdge<E>) -> Unit) = actionList.add(action)
    fun validWhen(guard: E.() -> Boolean) = validateList.add(guard)

    //Invoke when you go down the edge to another state
    fun enterEdge(retrieveState: (String) -> StackedState<E>): StackedState<E> {
        actionList.forEach { it(this@StackedStateEdge) }
        return retrieveState(targetState) // if get from actionList returns, dynamically, we could dynamic decide -\
        // go left -> edge check: isBar -> leftBar, isEmpty -> leftRun
    }
    fun canHandleEvent(event: E): Boolean = validateList.any { it(event) }
}

open class StackedState<E>(val name: String) {

    private val stateEnterAction = mutableListOf<(StackedState<E>) -> Unit>()
    private val stateExitAction = mutableListOf<(StackedState<E>) -> Unit>()
    private val stateActions = mutableListOf<E.() -> String?>()
    private val edgeList = mutableListOf<StackedStateEdge<E>>()
    //Add an action which will be called when the state is entered
    fun onEnter(action: (StackedState<E>) -> Unit) = stateEnterAction.add(action)
    fun onExit(action: (StackedState<E>) -> Unit) = stateExitAction.add(action)

    // return next state obj or self
    fun onUpdate(action: E.() -> String?) = stateActions.add(action)
    open fun suspendState() = exitState()
    open fun wakeupState() = enterState()

    fun edge(targetState: String, replace: Boolean = true, init: StackedStateEdge<E>.() -> Unit) {
        val edge = StackedStateEdge<E>(targetState, replace)
        edge.init()

        edgeList.add(edge)
    }

    fun beforeUpdate(event: E) = edgeList.firstOrNull { it.targetState != this.name && it.canHandleEvent(event) }
    fun enterState() = stateEnterAction.forEach { it(this) }
    fun exitState() = stateExitAction.reversed().forEach { it(this) }
    fun update(event: E) = stateActions.firstNotNullOfOrNull { it(event) }
}

class StackedStateMachine<E>(private val initialState: String) {

    fun debugOn() {
        stateList.forEach { with(it.value) {
            onEnter { println("-> $name"); }
            onExit { println( "<- $name" ); }
        } }
    }

    private val states = mutableListOf<String>()
    val currentStateName get() = states.current()!!
    val currentState get() = stateList[states.current()!!]!!
    val onStateChanged = mutableListOf<StackedState<E>.() -> Unit>()

    // builder
    private val stateList = mutableMapOf<String, StackedState<E>>()
    // 2 diff ways to work with: +PredefinedObjectState
    // or via dsl: state {
    // }
    operator fun plusAssign(state: StackedState<E>) { stateList[state.name] = state }

    fun state(name: String, init: StackedState<E>.() -> Unit) {
        val state = StackedState<E>(name)
        state.init()
        this += state
    }
    fun getState(name: String): StackedState<E> = stateList[name] ?: throw NoSuchElementException(name)

    fun update(event: E) {

        val edge = currentState?.beforeUpdate(event)

        if (edge is StackedStateEdge<E> && edge.targetState != currentStateName) {
            val newState = with(edge) { enterEdge { getState(it) } }
            pushState(newState, edge.replace )
        }

        currentState?.update(event)?.run {
            if (this != currentStateName) {
                pushState(getState(this), true)
            }
//            popState()
        }
    }

    // forcefully set state
    fun setState(stateName: String) {
        pushState(getState(stateName), true)
    }
    fun pushState(newState: StackedState<E>, replace: Boolean = false) {
        if ( replace ) {
            states.pop()?.run { getState(this).exitState() }
        } else {
            states.current()?.run { getState(this).suspendState() }
        }

        states.push(newState.name)
        newState.enterState()
        onStateChanged.forEach { it(newState) }
    }
    fun popState(): String? {
        val old = states.pop()?.run { getState(this) }.also { it?.exitState() }
        if ( states.isEmpty() ) states.add(initialState)
        currentState?.wakeupState()
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
            TODO("no soft reset")
        }
    }
}

fun<E> buildStateMachine(initialState: String, init: StackedStateMachine<E>.() -> Unit): StackedStateMachine<E> {
    val machine = StackedStateMachine<E>(initialState)
    machine.init()
//    machine.reset(true)
    return machine
}
