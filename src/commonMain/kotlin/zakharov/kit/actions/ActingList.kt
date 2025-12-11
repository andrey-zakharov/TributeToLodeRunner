package zakharov.kit.actions

import zakharov.kit.fsm.pop
import zakharov.kit.fsm.push

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
            fresh.pop()?.let { a ->
                a.enter(context)
                actions.push(a)
            }
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

    override fun clear() {
        fresh.clear()
        actions.clear()
    }

    fun delayed(n: Int, cb: CONTEXT.() -> Iterable<Act<CONTEXT>>?) = Delayed(n, cb)
}