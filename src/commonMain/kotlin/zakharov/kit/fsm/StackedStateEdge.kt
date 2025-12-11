package zakharov.kit.fsm

class StackedStateEdge<STATEKEY, EVENT>(val targetState: STATEKEY, val replace: Boolean = true, val weight: Int = 0) {
    private val validateList = mutableListOf<(EVENT) -> Boolean>()
    private val actionList = mutableListOf<(StackedStateEdge<STATEKEY, EVENT>) -> Any?>()
    fun validWhen(guard: EVENT.() -> Boolean) = validateList.push(guard)
    fun action(action: StackedStateEdge<STATEKEY, EVENT>.() -> Unit) = actionList.push(action)
    //Invoke when you go down the edge to another state
    fun enterEdge(retrieveState: (STATEKEY) -> StackedState<STATEKEY, EVENT>): StackedState<STATEKEY, EVENT> {
        actionList.forEach { it(this@StackedStateEdge) }
        return retrieveState(targetState) // if get from actionList returns, dynamically, we could dynamic decide -\
        // go left -> edge check: isBar -> leftBar, isEmpty -> leftRun
    }
    fun canHandleEvent(event: EVENT): Boolean = validateList.any { it(event) }
}