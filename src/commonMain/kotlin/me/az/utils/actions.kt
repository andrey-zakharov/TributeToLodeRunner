package me.az.utils

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

            if ( ret == ActionStatus.DONE || ret == ActionStatus.ERROR ) {
                u.remove()
            }

            if ( ret == ActionStatus.ERROR ) {
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

class ActingList<CONTEXT>(
    private val context: CONTEXT,
    private val actions: MutableList<Act<CONTEXT>> = mutableListOf()
) : MutableList<Act<CONTEXT>> by actions {

    private val fresh = mutableListOf<Act<CONTEXT>>()

    // delayed add
    override fun add(element: Act<CONTEXT>): Boolean = fresh.add(element)
    override fun addAll(elements: Collection<Act<CONTEXT>>) = fresh.addAll(elements)

    fun update(dt: Float) {
        // start new
        while(fresh.isNotEmpty()) {
            val a = fresh.pop()!!
            a.enter(context)
            actions.push(a)
        }

        val i = actions.iterator()
        val toAdd = mutableListOf<Act<CONTEXT>>()

        while(i.hasNext()) {
            val a = i.next()
            when( a.update(context, dt) ) {
                ActionStatus.ERROR -> {
                    a.error?.invoke(context)
                    i.remove()
                }
                ActionStatus.DONE -> {
                    toAdd.addAll(a.exit(context))
                    i.remove()
                }
                // what if we want to execute new anim in the middle
                // maybe new type of result? ActionStatus.Emit(listOf(Act)) ?
                ActionStatus.CONTINUE -> {

                }
            }
        }

        addAll(toAdd)
    }

    override fun remove(element: Act<CONTEXT>): Boolean {
        fresh.remove(element)
        return actions.remove(element)
    }

    fun delayed(n: Int, cb: CONTEXT.() -> Iterable<Act<CONTEXT>>?) = Delayed(n, cb)
}

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

class Forever<CONTEXT>(val actionFactory: () -> Act<CONTEXT>) : Act<CONTEXT>(/*startBlock = action::enter, exitBlock = action::exit*/) {
    var action: Act<CONTEXT>
    init {
        action = actionFactory()
        onStart { action.enter(this) }
        onEnd { action.exit(this) }
        onUpdate {
            when( action.update(this, it) ) {
                ActionStatus.DONE -> {
                    action = actionFactory()
                    ActionStatus.CONTINUE // show must go on
                }
                ActionStatus.CONTINUE -> ActionStatus.CONTINUE
                ActionStatus.ERROR -> ActionStatus.ERROR
            }
        }
    }
}