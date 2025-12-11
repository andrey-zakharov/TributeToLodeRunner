package zakharov.kit.fsm

//stack
fun<E> MutableList<E>.pop() = removeLastOrNull()
fun<E> MutableList<E>.push(e: E): Int {
    add(e)
    return size - 1
}

fun<E> MutableList<E>.current() = lastOrNull()

open class StackedState<STATEKEY, CONTEXT>(val name: STATEKEY) {

    private val stateEnterAction = mutableListOf<(StackedState<STATEKEY, CONTEXT>) -> Unit>()
    private val stateExitAction = mutableListOf<(StackedState<STATEKEY, CONTEXT>) -> Unit>()
    private val stateActions = mutableListOf<CONTEXT.() -> Any?>()
    // TBD sortedList
    private val edgeList = mutableListOf<StackedStateEdge<STATEKEY, CONTEXT>>()
    // Add an action which will be called when the state is entered
    fun onEnter(action: (StackedState<STATEKEY, CONTEXT>) -> Unit) = stateEnterAction.push(action)
    fun onExit(action: (StackedState<STATEKEY, CONTEXT>) -> Unit) = stateExitAction.push(action)

    // return next state obj or self
    fun onUpdate(action: CONTEXT.() -> Any?) = stateActions.push(action)
    fun onUpdate(index: Int, action: CONTEXT.() -> Any?) = stateActions.add(index, action)
    open fun suspendState() = exitState()
    open fun wakeupState(previous: STATEKEY) = enterState(previous)

    fun dynamicEdge(replace: Boolean = true, weight: Int = 0) {

    }
    fun edge(targetState: STATEKEY, replace: Boolean = true, weight: Int = 0, init: StackedStateEdge<STATEKEY, CONTEXT>.() -> Unit) {
        val edge = StackedStateEdge<STATEKEY, CONTEXT>(targetState, replace, weight)
        edge.init()
        edgeList.add(edge)
        edgeList.sortBy { it.weight }
    }

    fun beforeUpdate(event: CONTEXT) = edgeList.firstOrNull {
        it.canHandleEvent(event)
    }

    fun enterState(previous: STATEKEY) = stateEnterAction.forEach { it(this) }
    fun exitState() = stateExitAction.reversed().forEach { it(this) }
    fun update(event: CONTEXT) = stateActions.firstNotNullOfOrNull { it(event) }
    fun debugOn() {
        onEnter { println("-> $name"); }
        edgeList.forEach {
            when(it) {
                is StackedStateEdge -> {
                    it.action {
                        println("  edge action: ${this@StackedState.name} -> ${it.targetState}")
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