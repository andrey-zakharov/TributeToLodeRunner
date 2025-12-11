package zakharov.kit.actions

open class Delayed<CONTEXT>(val framesPass: Int, cb: (CONTEXT.() -> Iterable<Act<CONTEXT>>?)? = null) : Act<CONTEXT>() {
    private var updateCounter = 0
    init {
        cb?.let { onEnd(it) }
        onUpdate {
            if ( updateCounter >= framesPass ) {
                return@onUpdate ActionStatus.DONE
            }
            updateCounter++
            ActionStatus.CONTINUE
        }
    }
}