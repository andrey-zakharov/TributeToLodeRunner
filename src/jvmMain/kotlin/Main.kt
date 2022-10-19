import de.fabmax.kool.createContext
import de.fabmax.kool.createDefaultContext
import de.fabmax.kool.platform.Lwjgl3Context

suspend fun main() {
    val ctx =  createContext {
        assetsBaseDir = "."
        renderBackend = Lwjgl3Context.Backend.OPEN_GL
        title = "Tribute to Lode Runner by Andrey Zakharov 2022"
        customFonts += "text" to "fonts/daugsmith/daugsmith.ttf"
    }
    val app = App(ctx)
}