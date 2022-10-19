package me.az.scenes

import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.scene.Scene

abstract class AsyncScene(name: String? = null) : Scene(name) {
    private var sceneState = State.NEW
    init {
        onRenderScene += {
            checkState(it)
        }
    }

    private fun checkState(ctx: KoolContext) {
        if (sceneState == State.NEW) {
            // load resources (async from AssetManager CoroutineScope)
            sceneState = State.LOADING
            ctx.assetMgr.launch {
                loadResources(ctx)
                sceneState = State.SETUP
            }
        }

        if (sceneState == State.SETUP) {
            setup(ctx)
            sceneState = State.RUNNING
        }
    }

    abstract suspend fun AssetManager.loadResources(ctx: KoolContext)
    abstract fun setup(ctx: KoolContext)

    enum class State {
        NEW,
        LOADING,
        SETUP,
        RUNNING
    }
}