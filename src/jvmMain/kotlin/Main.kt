import de.fabmax.kool.createContext
import de.fabmax.kool.platform.Lwjgl3Context

suspend fun main() {
    println("Starting with Java = ${System.getProperty("java.version")}")
    val assetsDir = "assets"
    val title = "Tribute to Lode Runner by Andrey Zakharov 2022"
    val ctx = /*try {
        createContext {
            assetsBaseDir = assetsDir
            renderBackend = Lwjgl3Context.Backend.VULKAN
            this.title = title
//        customFonts += "text" to "fonts/daugsmith/daugsmith.ttf"
        }
    } catch (e: java.lang.Exception) {*/
        createContext {
            localAssetPath = assetsDir
            renderBackend = Lwjgl3Context.Backend.OPEN_GL
            this.title = title
//        customFonts += "text" to "fonts/daugsmith/daugsmith.ttf"
        }
    /*}*/
    val app = App(ctx)
}