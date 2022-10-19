package me.az.view

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.ButtonNode
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Color

interface ImageButtonScope : UiScope {
    override val modifier: ImageButtonModifier
}

open class ImageButtonModifier(surface: UiSurface) : ImageModifier(surface) {
    var buttonColor: Color by property { it.colors.accentVariant }
}

fun <T: ImageButtonModifier> T.colors(
    buttonColor: Color = this.buttonColor,
): T {
    this.buttonColor = buttonColor
    return this
}

//inline fun UiScope.ImageButton(tex: Texture2d, block: ImageButtonScope.() -> Unit): ImageScope {
//    val button = uiNode.createChild(ImageButtonNode::class, ImageButtonNode.factory)
//    button.modifier
////        .text(text)
////        .colors(textColor = colors.onAccent)
////        .textAlign(AlignmentX.Center, AlignmentY.Center)
//        .padding(horizontal = sizes.gap, vertical = sizes.smallGap * 0.5f)
//        .onClick(button)
//    button.block()
//    return button
//}
//
//class ImageButtonNode(parent: UiNode?, surface: UiSurface) : UiNode(parent, surface), ImageButtonScope, Clickable {
//    override val modifier = ImageButtonModifier(surface)
//
//}