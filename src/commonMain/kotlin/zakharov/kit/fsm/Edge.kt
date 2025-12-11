package zakharov.kit.fsm
import kotlin.collections.mutableListOf

interface StateEdge<CONTEXT> {
    fun validWhen(guard: CONTEXT.() -> Boolean)
    operator fun invoke(action: StateEdge<CONTEXT>.() -> Unit)
}

/**
 * @sample
 * edge("forward", MOVE) {
 *  onEnter {
 *
 *  }
 * }
 */
class Edge<STATEKEY, CONTEXT>(val targetState: STATEKEY): StateEdge<CONTEXT> {
    private val validateList = mutableListOf<(CONTEXT) -> Boolean>()
    private val actionList = mutableListOf<(Edge<STATEKEY, CONTEXT>) -> Any?>()
    override fun validWhen(guard: CONTEXT.() -> Boolean) { validateList.push(guard) }
    override fun invoke(action: StateEdge<CONTEXT>.() -> Unit) { actionList.push(action) }

    /// Invoked when machine goes down the edge to another state
    fun enterEdge(retrieveState: (STATEKEY) -> State<STATEKEY, CONTEXT>): State<STATEKEY, CONTEXT> {
        actionList.forEach { it(this@Edge) }
        return retrieveState(targetState)
        // if get from actionList returns, dynamically, we could dynamic decide -\
        // go left -> edge check: isBar -> leftBar, isEmpty -> leftRun
    }
    fun canHandleEvent(event: CONTEXT): Boolean = validateList.any { it(event) }
}
/*
when { } then { }
 */