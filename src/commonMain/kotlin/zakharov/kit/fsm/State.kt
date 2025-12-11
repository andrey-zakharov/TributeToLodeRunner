package zakharov.kit.fsm


class State<STATEKEY, CONTEXT>(val name: STATEKEY) {

    //Add an action which will be called when the state is entered
    fun onEnter(action: (State<STATEKEY, CONTEXT>) -> Unit) = stateEnterAction.add(action)
    fun onExit(action: (State<STATEKEY, CONTEXT>) -> Unit) = stateExitAction.add(action)
    // return next state obj or self
    fun onUpdate(action: CONTEXT.() -> Any?) = stateActions.push(action)
    fun onUpdate(index: Int, action: CONTEXT.() -> Any?) = stateActions.add(index, action)

    fun edge(targetState: STATEKEY, init: Edge<STATEKEY, CONTEXT>.() -> Unit) {
        val edge = Edge<STATEKEY, CONTEXT>( targetState )
        edge.init()

        edgeList.add(edge)
        //edgeList.sortBy { it.weight }
    }

    //Get the appropriate Edge for the Event
    fun beforeUpdate(event: CONTEXT) = edgeList.firstOrNull {
        it.canHandleEvent(event)
    }

    internal fun enterState() = stateEnterAction.forEach { it(this) }
    internal fun exitState() = stateExitAction.reversed().forEach { it(this) }

    internal fun update(event: CONTEXT) = stateActions.firstNotNullOfOrNull { it(event) }

    private val stateEnterAction = mutableListOf<(State<STATEKEY, CONTEXT>) -> Unit>()
    private val stateExitAction = mutableListOf<(State<STATEKEY, CONTEXT>) -> Unit>()
    private val stateActions = mutableListOf<CONTEXT.() -> Any?>()

    private val edgeList = mutableListOf<Edge<STATEKEY, CONTEXT>>()

}