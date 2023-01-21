package me.az.view

import ImageAtlas
import de.fabmax.kool.math.Mat4d
import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.modules.ui2.MutableStateValue
import de.fabmax.kool.scene.Group

class TextView (
    private val spriteSystem: SpriteSystem,
    private val text: MutableStateValue<String>,
    private val fontAtlas: ImageAtlas,
    private val defaultBgAlpha: Float = 0.75f,
    init: TextView.() -> Unit = {}
): Group() {
    private val chars = mutableListOf<SpriteInstance>()
    private val tmpMat = Mat4d()

//    private val actions = mutableListOf<Action>()

    init {
        //on change fontAtlas.tex
        text.onChange {
            dirty = true
        }
        fontAtlas.tex.onChange {
            dirty = true
        }

        onUpdate += {
            if (dirty) {
                refresh()
                dirty = false
            }
        }
        init()
    }

    // totally custom
    companion object {
        const val fallbackChar = 0
        val fontMap = mutableMapOf<Char, Int>().apply {
            for (c in '0'..'9') {
                this[c] = c - '0'
            }
            for (c in 'a'..'z') {
                this[c] = c - 'a' + 10
            }
            this['.'] = 36
            this['<'] = 37
            this['>'] = 38
            this['-'] = 39
            this[' '] = 43
            this[':'] = 44
            this['_'] = 45
        }
    }

    private fun rebuildSprites(): Iterable<SpriteInstance> {
        updateModelMat()
//        logd { "building sprites from \"${text.value}\" ${modelMat.dis()}"}
        return text.value.mapIndexed { index, c ->
            //spriteSystem.toLocalCoords(tmpPos)
            spriteSystem.sprite(
                atlasId = spriteSystem.cfg.atlasIdByName[fontAtlas.name]!!,
                modelMat = Mat4f().set(getCoords(index)), // copy
                tileIndex = fontMap[c.lowercaseChar()] ?: fallbackChar,
            ).also {
                it.bgAlpha.set(defaultBgAlpha)
            }
        }
    }

    private fun getCoords(x: Int): Mat4d {
        tmpMat.set(modelMat)
            .translate(x.toFloat(), 0f, 0f)
            .scale(1.0, -1.0, 1.0)
        return tmpMat
    }

    private fun refresh() {
        updateModelMat()
        //if ( chars.size != text.value.length ) {
            chars.forEach { it.unbind() }
            //rebuild array
            spriteSystem.sprites.removeAll(chars.toSet())
            spriteSystem.dirty = true
            chars.clear()
            chars.addAll(rebuildSprites())
        /*} else {
            // update pos
            //logd { "updating pos for \"${text.value}\"" }
            chars.forEachIndexed { index, spriteInstance ->
//                spriteSystem.toLocalCoords(tmpPos)
                //if (spriteInstance.modelMat.equals()) {
                //spriteInstance.writeModelMat(getCoords(index))
                spriteInstance.tileIndex.set(fontMap[text.value[index].lowercaseChar()] ?: fallbackChar)
                // huge hack atm
                spriteSystem.dirty = true
            }
        }*/
        // refresh until texture loads hack
    }

    private var dirty = true

}