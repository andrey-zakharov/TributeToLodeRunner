import de.fabmax.kool.createDefaultContext
suspend fun main() {
    val ctx = createDefaultContext()
    val app = App(ctx)
}

