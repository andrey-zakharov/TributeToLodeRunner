package me.az.utils

//stack
fun<E> MutableList<E>.pop() = removeLastOrNull()
fun<E> MutableList<E>.push(e: E): Int {
    add(e)
    return size - 1
}

fun<E> MutableList<E>.current() = lastOrNull()

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

//interface Edge<STATEKEY, CONTEXT> {
//    val weight: Int
//    fun canHandleEvent(event: CONTEXT): Boolean
//    fun enterEdge(stateResolver: (STATEKEY) -> StackedState<CONTEXT>): StackedState<CONTEXT>
//    // dsl actions
//    fun action(action: Edge<STATEKEY, CONTEXT>.() -> Unit): Any? // breakable or not?
//}

class StackedStateEdge<STATEKEY, CONTEXT>(val targetState: STATEKEY, val replace: Boolean = true, val weight: Int = 0) {
    private val validateList = mutableListOf<(CONTEXT) -> Boolean>()
    private val actionList = mutableListOf<(StackedStateEdge<STATEKEY, CONTEXT>) -> Any?>()
    fun action(action: StackedStateEdge<STATEKEY, CONTEXT>.() -> Unit) = actionList.push(action)
    fun validWhen(guard: CONTEXT.() -> Boolean) = validateList.push(guard)
    //Invoke when you go down the edge to another state
    fun enterEdge(retrieveState: (STATEKEY) -> StackedState<CONTEXT>): StackedState<CONTEXT> {
        actionList.forEach { it(this@StackedStateEdge) }
        return retrieveState(targetState) // if get from actionList returns, dynamically, we could dynamic decide -\
        // go left -> edge check: isBar -> leftBar, isEmpty -> leftRun
    }
    fun canHandleEvent(event: CONTEXT): Boolean = validateList.any { it(event) }
}
///// could not believe i need wrapper for usual cascade ifs
//class DynamicEdge<E>(override val weight: Int) : Edge<String, E> {
//    override fun canHandleEvent(event: E): Boolean {
//        TODO("Not yet implemented")
//    }
//
//    override fun enterEdge(stateResolver: (String) -> StackedState<E>): StackedState<E> {
//        TODO("Not yet implemented")
//    }
//
//    override fun action(action: Edge<String, E>.() -> Unit): Any? {
//        TODO("Not yet implemented")
//    }
//
//}
open class StackedState<E>(val name: String) {

    private val stateEnterAction = mutableListOf<(StackedState<E>) -> Unit>()
    private val stateExitAction = mutableListOf<(StackedState<E>) -> Unit>()
    private val stateActions = mutableListOf<E.() -> Any?>()
    // TBD sortedList
    private val edgeList = mutableListOf<StackedStateEdge<String, E>>()
    // Add an action which will be called when the state is entered
    fun onEnter(action: (StackedState<E>) -> Unit) = stateEnterAction.push(action)
    fun onExit(action: (StackedState<E>) -> Unit) = stateExitAction.push(action)

    // return next state obj or self
    fun onUpdate(action: E.() -> Any?) = stateActions.push(action)
    open fun suspendState() = exitState()
    open fun wakeupState(previous: String) = enterState(previous)

    fun dynamicEdge(replace: Boolean = true, weight: Int = 0) {

    }
    fun edge(targetState: String, replace: Boolean = true, weight: Int = 0, init: StackedStateEdge<String, E>.() -> Unit) {
        val edge = StackedStateEdge<String, E>(targetState, replace, weight)
        edge.init()
        edgeList.add(edge)
        edgeList.sortBy { it.weight }
    }

    fun beforeUpdate(event: E) = edgeList.firstOrNull {
        /*it.targetState != this.name && */it.canHandleEvent(event)
    }

    fun enterState(previous: String) = stateEnterAction.forEach { it(this) }
    fun exitState() = stateExitAction.reversed().forEach { it(this) }
    fun update(event: E) = stateActions.firstNotNullOfOrNull { it(event) }
    fun debugOn() {
        onEnter { println("-> $name"); }
        edgeList.forEach {
            when(it) {
                is StackedStateEdge -> {
                    it.action {
                        println("  edge action: ${it.targetState}")
                    }
                }
                else -> {

                    it.action {
                        println(" dynamic edge transition ")
                    }
                }
            }
        }
        onExit { println( "<- $name" ); }

    }
}

abstract class CompoundState<E>(name: String) : StackedState<E>(name) {
    abstract val internalFsm: StackedStateMachine<E>
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

class StackedStateMachine<E>(private val initialState: String) {

    fun debugOn() {
        stateList.forEach { with(it.value) {
            debugOn()
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

    fun update(event: E, safe: Boolean = false /* treat unknown states as exit from fsm or not*/) {
        if ( finished ) return

        val edge = currentState.beforeUpdate(event)

        // fsm for updating fsm???
        if (edge is StackedStateEdge<String, E> && edge.targetState != currentStateName) {
            if ( safe && edge.targetState !in stateList.keys ) {
                println("finish by unknown edge: ${edge.targetState}")
                //exit
                finish()
                return
            }
            val newState = edge.enterEdge {
                getState(it)
            }
            pushState(newState, edge.replace )
        }

        currentState.update(event)?.run {
            when(this) {
                is String -> if (this != currentStateName) {
                    pushState(getState(this), true)
                }
            }

//            popState()
        }
    }

    // forcefully set state
    fun setState(stateName: String) {
        pushState(getState(stateName), true)
    }
    fun pushState(newState: StackedState<E>, replace: Boolean = false) {
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

    fun popState(): String? {
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

fun<E> buildStateMachine(initialState: String, init: StackedStateMachine<E>.() -> Unit): StackedStateMachine<E> {
    val machine = StackedStateMachine<E>(initialState)
    machine.init()
//    machine.reset(true)
    return machine
}