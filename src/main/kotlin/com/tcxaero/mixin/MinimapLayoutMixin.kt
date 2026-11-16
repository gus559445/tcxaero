package com.tcxaero.mixin

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import com.tcxaero.MinimapBridge
import top.fifthlight.touchcontroller.common.control.widget.custom.CustomWidget
import top.fifthlight.touchcontroller.common.layout.Context

/**
 * 在 TouchController 的 `CustomWidget.layout(Context)` 里挂小地图联动。
 *
 * <h2>为什么拦这里</h2>
 * TC 每帧对「布局层里的每个控件」调用 `layout()`，调用前已经把该控件的屏幕矩形
 * 算好放进 [Context]（`screenOffset` + `size`）。这是外部能拿到的最精确的控件位置。
 *
 * <h2>为什么用 Kotlin 写这个 mixin</h2>
 * `Context.screenOffset` 的类型是 `IntOffset`、`size` 是 `IntSize`，
 * 两者都是 Kotlin **value class**。它们的方法在 JVM 上带连字符后缀
 * （`getX-impl` / `getWidth-impl`），Java 源码里没法直接调用
 * （编译器会报 cannot find symbol）。用 Kotlin 写就能正常用 `.x` / `.width` 属性。
 *
 * <h2>为什么不用自己的 ControllerWidget 子类</h2>
 * TC 的控件存档是 kondor 的静态 sealed 表（`JControllerWidget`），只认
 * `boat_button` / `custom` / `dpad` / `joystick`。新类型进不去，保存布局时会被静默丢弃。
 * 所以借用 [CustomWidget] 当载体，只对名字匹配 [MinimapBridge.MARKER_NAME] 的实例生效。
 */
@Mixin(value = [CustomWidget::class], remap = false)
abstract class MinimapLayoutMixin {

    @Inject(method = ["layout"], at = [At("HEAD")], remap = false)
    fun tcxaeroOnLayout(context: Context, ci: CallbackInfo) {
        val self = this as CustomWidget

        // 只处理被命名成小地图占位的那一个控件。
        val name = self.name?.asString()
        if (!MinimapBridge.isMarker(name)) {
            return
        }

        // Context 里的 screenOffset / size 是 TC 已按 align + offset 算好的绝对矩形。
        val rect = context.screenOffset
        val size = context.size

        MinimapBridge.onWidgetLayout(
            screenX = rect.x,
            screenY = rect.y,
            width = size.width,
            height = size.height,
        )
    }
}
