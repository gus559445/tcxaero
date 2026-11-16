package com.tcxaero.mixin;

import com.tcxaero.MinimapBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fifthlight.touchcontroller.common.control.widget.custom.CustomWidget;
import top.fifthlight.touchcontroller.common.layout.Context;
import top.fifthlight.data.IntOffset;
import top.fifthlight.data.IntSize;

/**
 * 在 TouchController 的 {@code CustomWidget.layout(Context)} 里挂小地图联动。
 *
 * <h2>为什么拦这里</h2>
 * TC 每帧对「布局层里的每个控件」调用 {@code layout()}，并且调用前已经把
 * 该控件的屏幕矩形算好放进 {@link Context}（`screenOffset` + `size`）。
 * 这是外部能拿到的、最精确的控件位置信息。
 *
 * <h2>为什么不用自己的 ControllerWidget 子类</h2>
 * TC 的控件存档是 kondor 的静态 sealed 表（`JControllerWidget`），只认
 * `boat_button` / `custom` / `dpad` / `joystick`。新类型进不去，
 * 保存布局时会被静默丢弃。所以这里借用 `CustomWidget` 当载体，
 * 只对名字匹配 [MinimapBridge.MARKER_NAME] 的那一个实例生效。
 *
 * <p>注入点是 {@code At("HEAD")}：先拿到矩形去驱动 Xaero 位置，
 * 再让原方法继续画它自己的按钮外观（占位控件本身是个普通按钮）。</p>
 */
@Mixin(value = CustomWidget.class, remap = false)
public abstract class MinimapLayoutMixin {

    @Inject(method = "layout", at = @At("HEAD"), remap = false)
    private void tcxaero$onLayout(Context context, CallbackInfo ci) {
        CustomWidget self = (CustomWidget) (Object) this;

        // 只处理被标记成小地图占位的那一个控件。
        String name = self.getName() == null ? null : self.getName().asString();
        if (!MinimapBridge.isMarker(name)) {
            return;
        }

        // Context 里的 screenOffset / size 是 TC 已按 align + offset 算好的绝对矩形。
        IntOffset rect = context.getScreenOffset();
        IntSize size = context.getSize();

        MinimapBridge.onWidgetLayout(
                IntOffset.getX(rect),
                IntOffset.getY(rect),
                IntSize.getWidth(size),
                IntSize.getHeight(size)
        );
    }
}
