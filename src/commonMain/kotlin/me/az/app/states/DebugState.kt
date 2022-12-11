package me.az.app.states

import App
import AppState
import TileSet
import de.fabmax.kool.math.*
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.CullMethod
import de.fabmax.kool.pipeline.DepthCompareOp
import de.fabmax.kool.pipeline.shadermodel.vertexStage
import de.fabmax.kool.pipeline.shading.UnlitMaterialConfig
import de.fabmax.kool.pipeline.shading.UnlitShader
import de.fabmax.kool.pipeline.shading.unlitShader
import de.fabmax.kool.scene.*
import de.fabmax.kool.scene.PointMesh.Companion.ATTRIB_POINT_SIZE
import de.fabmax.kool.scene.animation.*
import de.fabmax.kool.scene.geometry.PrimitiveType
import de.fabmax.kool.toString
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Time
import kotlinx.coroutines.*
import me.az.utils.StackedState
import me.az.utils.addDebugAxis
import me.az.view.SpriteConfig
import me.az.view.SpriteSystem
import me.az.view.TextView
import kotlin.math.PI

class DebugState(private val app: App) : StackedState<AppState, App>(AppState.DEBUG) {

    val debug = mutableStateOf("")
    val eachFrame = 100
    val n = 100

    val randomPos get() = randomF(-5f, 5f)
    val randomScale get() = randomF(0.5f, 1.5f)

    // astronomical
    enum class ScalesKm(val inKm: Double) {
        lightYear( 9.46E+12 ),
        astroUnit(149_597_870.7 )
        ;
        val scale get() = 1.0 / inKm
    }
    val Float.astroUnit: Double get() = this.toDouble().astroUnit
    val Double.astroUnit: Double get() = ScalesKm.astroUnit.inKm * this
    init {
        onEnter {
//            app.ctx.scenes += scene1()
//            app.ctx.scenes += scene2()
            val job = Job()
            val scope = CoroutineScope(job)

            app.ctx.scenes += scene {
                camera.position.z = 10f

                class Particle(var ttl: Int = -1, var x: Float, var y: Float, var scale: Float = 1f) {
                    val randomV get() = randomF(-0.2f, 0.2f)
                    val vx = randomV
                    val vy = randomV
                    fun update(dt: Float) {
                        x += vx * dt
                        y += vy * dt
                        ttl --
                    }
                }

                val parts = Array(1000) { Particle(-1, 0f, 0f) }
                var firstNotAlive = 0

                fun findFirstNotAlive(): Int {
                    var older = Int.MAX_VALUE
                    var olderId = 0

                    for ( i in firstNotAlive until parts.size) {
                        if ( parts[i].ttl <= 0 ) return i
                        if ( parts[i].ttl < older ) {
                            older = parts[i].ttl
                            olderId = i
                        }
                    }
                    // from begining
                    for ( i in 0 until firstNotAlive ) {
                        if ( parts[i].ttl <= 0 ) return i
                        if ( parts[i].ttl < older ) {
                            older = parts[i].ttl
                            olderId = i
                        }

                    }
                    // or most old
                    return olderId
                }

                fun addTrailPoint(x: Float, y: Float) {
                    with(parts[firstNotAlive]) {
                        ttl = 200
                        this.x = x
                        this.y = y

                        firstNotAlive = findFirstNotAlive()
                    }
                }

                val particleMesh = mesh(listOf(Attribute.POSITIONS, ATTRIB_POINT_SIZE, Attribute.COLORS)) {
                    geometry.primitiveType = PrimitiveType.POINTS
                    generate {
                        geometry.addIndex(vertex {
                            this.position.set(0f, 0f, 0f)
                            this.color.set(color)
                            getFloatAttribute(ATTRIB_POINT_SIZE)?.f = 2f
                        })
                    }

                    val unlitCfg = UnlitMaterialConfig().apply {
                        useStaticColor(Color.LIGHT_BLUE)
                        isInstanced = true
                    }

                    val unlitModel = UnlitShader.defaultUnlitModel(unlitCfg).apply {
                        vertexStage {
                            pointSize(attributeNode(ATTRIB_POINT_SIZE).output)
                        }
                    }
                    instances = MeshInstanceList(listOf(Attribute.INSTANCE_MODEL_MAT))
                    shader = UnlitShader(unlitCfg, unlitModel)

                    onUpdate += { ev ->
                        instances!!.clear()
                        parts.filter { it.ttl > 0 }.forEach {
                            it.update(ev.deltaT)
                            instances!!.addInstance {
                                put(Mat4f().translate(it.x, it.y, 0f).matrix)
                            }

                        }
                    }
                }

                val universeScale = mutableStateOf(ScalesKm.astroUnit) // 1 light year in km
                +particleMesh
                val timeScale = 0.010f
                +group("sun") {
                    //scale(universeScale.value.scale)
                    +group("earth") {
                        val angle = LinearAnimator(InterpolatedFloat(0f, 360f)).apply {
                            repeating = Animator.REPEAT
                            duration = 365f
                            speed = 1f / timeScale
                        }
                        onUpdate += { ev->
                            setIdentity()
                            rotate(angle.tick(ev.ctx), Vec3f.Z_AXIS) // this is not true. respect orbits!
                            translate(2.5, 0.0, 0.0)
                        }
                        //addDebugAxis()


                        +group("moon") {
                            val angle = LinearAnimator(InterpolatedFloat(0f, 360f)).apply {
                                repeating = Animator.REPEAT
                                duration = 28f
                                speed = 1f / timeScale
                            }
                            onUpdate += {ev->
                                setIdentity()
                                rotate(angle.tick(ev.ctx), Vec3f.Z_AXIS) // this is not true. respect orbits!
                                translate(0.5, 0.0, 0.0)
                                addTrailPoint(globalCenter.x, globalCenter.y)

                            }
                            //addDebugAxis()
                        }
                    }
                }

                val tileIndexes = Array(n) { i -> mutableStateOf(randomI(0, 5)) }
//                val scales = Array(n) { CosAnimator<}
                val anims = Array(n) { i -> SpringDamperFloat(randomScale).apply {
                    stiffness = 1500f
                } }

                val cfg = SpriteConfig {
                    this += "text"
                    this += "runner"
                    this += "guard"
                    this += "tiles"
                }

                val ss = SpriteSystem(cfg).apply {
                    mirrorTexCoordsY = true
                }

                ss.sprite(0, 0, Mat4f().set(modelMat))

                app.ctx.assetMgr.launch {
                    cfg.atlases.map { async { it.load(TileSet.SPRITES_APPLE2, this@launch) } }.awaitAll()
                    ss.dirty = true
                }
                val ins = Array(n) { i ->
                    val m = Mat4f().translate(randomPos, randomPos, 0f).scale(randomScale)
                    ss.sprite(randomI(0, cfg.atlases.size - 1), tileIndexes[i], m)
                }.toMutableList()

                onUpdate += { ev ->
                    ins.forEachIndexed { index, s ->
                        // preserve pos
                        val o = MutableVec3f()
                        s.modelMat.getOrigin(o)
                        s.modelMat.setIdentity().translate(o).scale( anims[index].animate(Time.deltaT) )
                    }
                    ss.dirty = true
                }

                //addDebugAxis()
                +ss

                scope.launch {
                    var mode = 0 // pos scale tileindex
                    while (true) {
                        delay(1001)
                        // sprite toggle, not respect to atlases size, just random
                        cfg.atlases[randomI(0, cfg.atlases.size - 1)].geometry?.run {
                            tileIndexes[randomI(0, tileIndexes.size - 1)].set(randomI(0, total - 1))
                        }
                    }
                }
                scope.launch {
                    val d: Long = (2000 / n).toLong()
                    while ( true ) {
                        for ( a in anims ) {
                            delay(d)
                            // animate scale
                            a.desired = randomScale
                        }
                    }
                }
/*                scope.launch {
                    var di = -1
                    while (true) {
                        delay(500)
                        if ( di < 0 ) {
                            if (ins.isNotEmpty()) {
                                val removed = ins.removeAt(0)
                                removed.unbind()
                                ss.sprites.remove(removed)
                            } else {
                                di = 1
                            }
                        } else {
                            if ( ins.size < n ) {
                                ins.add(
                                    ss.sprite(randomI(0, cfg.atlases.size - 1), tileIndexes[ins.size],
                                        Mat4f().translate(randomPos, randomPos, 0f).scale(scales[ins.size].value)
                                    )
                                )
                            } else {
                                di = -1
                            }
                        }
                        ss.dirty = true
                    }
                }*/

                scope.launch {
                    while(true) {
                        delay(2000)
                        app.context.nextSpriteSet()
                    }
                }

                app.context.spriteMode.onChange {
                    println("Changed $it")
                }

                val fontAtlas = cfg.atlases[cfg.atlasIdByName["text"]!!]
                +TextView(ss, mutableStateOf("abcd"), fontAtlas) {

                }

                // test text
                val text = mutableStateOf("test")

                +TextView(ss, text, fontAtlas) {
                    scale(0.5f)
                    rotate(45f, Vec3f.Z_AXIS)
                    translate(-2f, 2f, 0f)
                }
                scope.launch {
                    while(true) {
                        delay(250)
                        if ( text.value == "test" ) {
                            text.set("tested")
                        } else {
                            text.set("test")
                        }
                    }
                }
            }

        }
        onUpdate {
            if ( Time.frameCount % eachFrame == 0 ) {
                println(ctx.engineStats.numDrawCommands)
            }
        }

        fun scene3() = scene {

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