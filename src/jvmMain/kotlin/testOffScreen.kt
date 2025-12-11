import de.fabmax.kool.KoolApplication
import de.fabmax.kool.KoolConfig
import de.fabmax.kool.createContext
import de.fabmax.kool.platform.Lwjgl3Context

fun main() = KoolApplication(  KoolConfig(
    renderBackend = Lwjgl3Context.Backend.OPEN_GL,
    windowTitle = "Kool App"
)) {

    //run()
}