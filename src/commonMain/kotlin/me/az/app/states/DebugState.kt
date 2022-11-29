package me.az.app.states

import App
import AppState
import TileSet
import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.randomF
import de.fabmax.kool.math.randomI
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.CullMethod
import de.fabmax.kool.pipeline.DepthCompareOp
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.MeshInstanceList
import de.fabmax.kool.scene.animation.Animator
import de.fabmax.kool.scene.animation.SpringDamperFloat
import de.fabmax.kool.scene.colorMesh
import de.fabmax.kool.scene.scene
import de.fabmax.kool.toString
import de.fabmax.kool.util.Time
import kotlinx.coroutines.*
import me.az.utils.StackedState
import me.az.view.SpriteConfig
import me.az.view.SpriteSystem

class DebugState(private val app: App) : StackedState<AppState, App>(AppState.DEBUG) {

    val debug = mutableStateOf("")
    val eachFrame = 100
    val n = 25

    val randomPos get() = randomF(0f, 5f)
    val randomScale get() = randomF(0.5f, 1.5f)
    init {
        onEnter {
//            app.ctx.scenes += scene1()
//            app.ctx.scenes += scene2()
            val job = Job()
            val scope = CoroutineScope(job)

            app.ctx.scenes += scene {
                camera.position.z = 10f

                val context = Array(n) { i -> mutableStateOf(randomI(0, 5)) }
                val anims = Array(n) { i -> SpringDamperFloat(randomScale).apply {
                    stiffness = 1500f
                } }
                val scales = Array(n) { i -> mutableStateOf(anims[i].actual) }
                onUpdate += { ev ->
                    scales.forEachIndexed {
                        index, mutableStateValue -> mutableStateValue.set(
                            anims[index].animate(Time.deltaT)
                        )
                    }
                }

                val cfg = SpriteConfig {
                    this += "text"
                    this += "runner"
                    this += "guard"
                    this += "tiles"
                }

                val ss = SpriteSystem(cfg).apply {
                    mirrorTexCoordsY = true
                }

                app.ctx.assetMgr.launch {
                    cfg.atlases.map { async { it.load(TileSet.SPRITES_APPLE2, this@launch) } }.awaitAll()
                    ss.dirty = true
                }

                for (i in 0 until n) {
                    ss.sprite(randomI(0, cfg.atlases.size - 1), Vec2f(randomPos, randomPos), context[i], scales[i])
                }
                +ss

                scope.launch {
                    var mode = 0 // pos scale tileindex
                    while (true) {
                        delay(100)
                        // sprite toggle, not respect to atlases size, just random
                        cfg.atlases[randomI(0, cfg.atlases.size - 1)].geometry?.run {
                            context[randomI(0, context.size - 1)].set(randomI(0, total - 1))
                        }


                    }
                }

                scope.launch {
                    while ( true ) {
                        delay(2000)
                        // animate scale
                        anims.forEach {
                            it.desired = randomScale
                        }
                    }
                }
            }

        }
        onUpdate {
            if ( de.fabmax.kool.util.Time.frameCount % eachFrame == 0 ) {
                println(ctx.engineStats.numDrawCommands)
            }
        }

        fun scene2() = scene("debug") {
            val randomCh = { randomF(0f, 1f) }
            +colorMesh {
                instances = MeshInstanceList(
                    listOf(Attribute.INSTANCE_MODEL_MAT, Attribute.INSTANCE_COLOR), 0
                )


                instances?.addInstances(n) { buf ->
                    for (i in 0 until n) {
                        buf.put(Mat4f().translate(randomPos, randomPos, 0f).matrix)
                        buf.put(randomCh())
                        buf.put(randomCh())
                        buf.put(randomCh())
                        buf.put(randomCh())
                    }
                }

                shader = KslUnlitShader {
                    isInstanced = true
                    pipeline {
                        cullMethod = CullMethod.NO_CULLING
                        depthTest = DepthCompareOp.DISABLED
                    }

                    color {
                        instanceColor()
//                            constColor(Color.RED.gamma(0.8f), ColorBlockConfig.MixMode.Subtract)
                    }
                }
                generate {
                    rect {
                        size.set(0.05f, 0.05f)
                        origin.set(-width / 2f, -height / 2f, 0f)
                    }
                }
            }

            onUpdate += { ev ->
                val c = 50
                for (i in 0 until c) {

                    children.filterIsInstance<Mesh>().firstOrNull()?.run {
                        val idx = randomI(0 until n)
                        instances?.let {
                            val offset = it.instanceSizeF * idx

                            val matmod = Mat4f().translate(randomPos, randomPos, 0f).matrix
                            matmod.forEachIndexed { index, fl ->
                                it.dataF[offset + index] = fl
                            }
                            it.dataF[offset + matmod.size] = randomCh()
                            it.dataF[offset + matmod.size + 1] = randomCh()
                            it.dataF[offset + matmod.size + 2] = randomCh()
                            it.dataF[offset + matmod.size + 3] = 1f

                            it.hasChanged = true
                        }
                    }
                }
            }
        }

        fun scene1() = UiScene() {
            +Panel(sizes = Sizes.medium) {
                modifier
                    .alignY(AlignmentY.Top)
                    .alignX(AlignmentX.Center)

                Row {
                    Text(debug.use()) {
//                            modifier.font.setScale(.3f, app.ctx)

                    }
                }

            }
            onUpdate {
                debug.set(ctx.inputMgr.pointerState.pointers
                    //.filter { it.isValid && !it.isConsumed() && it.isAnyButtonDown }
                    .joinToString("\n") {
                        "id: ${it.id}\t valid: ${it.isValid}\t consumed=${it.isConsumed()} ${it.x.toString(2)} x ${
                            it.y.toString(
                                2
                            )
                        } mask=${it.buttonMask.toString(2)}"
                    })
            }
//                onUpdate += { ev->
//                    debug.set(ev.ctx.engineStats.numDrawCommands.toString())
//                }
        }


    }
}