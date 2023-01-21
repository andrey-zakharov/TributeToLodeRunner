import de.fabmax.kool.createContext
import de.fabmax.kool.platform.Lwjgl3Context

fun main() {

    createContext {
        renderBackend = Lwjgl3Context.Backend.OPEN_GL
        title = "Kool App"
    }.apply {



        run()
    }
}