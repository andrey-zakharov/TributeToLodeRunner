package zakharov.kit.actions

enum class ActionStatus {
    DONE, CONTINUE, ERROR
}
// Difference with states - active state must be only top one
// but could be active several actions
abstract class Act<C>(
    // second form (inline) constructing actions
    startBlock: (C.() -> Unit)? = null,
    loop: (C.(dt: Float) -> ActionStatus)? = null,
    exitBlock: (C.() -> Iterable<Act<C>>?)? = null,
    val error: (C.() -> Unit)? = null // break
) {
    // builder
    private val updates = mutableListOf<C.(dt: Float) -> ActionStatus>()
    fun onUpdate(body: C.(dt: Float) -> ActionStatus) { updates += body }

    private val enterCallbacks = mutableListOf<C.() -> Unit>()
    fun onStart(body: C.() -> Unit) { enterCallbacks += body }

    private val exitCallbacks = mutableListOf<C.() -> Iterable<Act<C>>?>()
    fun onEnd(body: C.() -> Iterable<Act<C>>?) { exitCallbacks += body }

    init {
        startBlock?.also {this.onStart(it) }
        loop?.also { this.onUpdate(it) }
        exitBlock?.also { this.onEnd(it) }
    }

    fun update(context: C, dt: Float): ActionStatus {
        val u = updates.iterator()

        var hasError = false

        while( u.hasNext() ) {
            val cb = u.next()
            val ret = cb(context, dt)

            if ( ret == ActionStatus.DONE || ret == ActionStatus.ERROR) {
                u.remove()
            }

            if ( ret == ActionStatus.ERROR) {
                hasError = true
            }
        }

        if ( hasError ) return ActionStatus.ERROR

        if ( updates.isEmpty() ) return ActionStatus.DONE

        return ActionStatus.CONTINUE
    }
    fun enter(context: C) = enterCallbacks.forEach { it(context) }
    fun exit(context: C): Iterable<Act<C>> = exitCallbacks.mapNotNull { it(context) }.flatten()
}

