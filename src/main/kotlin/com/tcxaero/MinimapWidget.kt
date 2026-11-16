package com.tcxaero

import top.fifthlight.data.IntOffset
import top.fifthlight.data.IntSize
import top.fifthlight.touchcontroller.common.control.ControllerWidget
import top.fifthlight.touchcontroller.common.layout.Context
import top.fifthlight.touchcontroller.common.layout.align.Align
import top.fifthlight.touchcontroller.common.util.name.TranslatableName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 把小地图“当作一个控件”放进 TouchController 的布局里。
 *
 * 这个控件本身**不画任何像素**——Xaero 的小地图有自己一整套渲染管线（FBO、区块贴图、
 * 雷达、路径点、指南针），在 TC 的 Canvas 里重画一遍既不现实也没必要。
 *
 * 它做的是**占位 + 报坐标**：TC 会按玩家拖到的位置算出这个控件的屏幕矩形
 * （`Context.screenOffset` / `Context.size`），控件在 [layout] 里把这个矩形交给
 * [MinimapBridge]，由桥接层把 Xaero 的 `ModuleTransform` 改成同一个位置。
 * 于是玩家在 TC 编辑器里拖这个控件 = 拖 Xaero 的小地图本身。
 */
@OptIn(ExperimentalUuidApi::class)
class MinimapWidget(
    override val id: Uuid,
    override val name: TranslatableName,
    override val align: Align,
    override val autoAlign: Boolean,
    override val offset: IntOffset,
    override val opacity: Float,
    override val lockMoving: Boolean,
) : ControllerWidget() {

    /**
     * 控件边长（GUI 缩放像素）。Xaero 小地图实际显示尺寸由它自己的配置决定，
     * 这里只是给 TC 编辑器一个可拖拽 / 可对齐的方块。
     */
    override val size: IntSize
        get() = DEFAULT_SIZE

    override fun layout(context: Context) {
        // TC 在调用 layout 之前已经按 align + offset 算好了本控件的矩形，放进 Context。
        val rect = context.screenOffset
        val rectSize = context.size

        MinimapBridge.onWidgetLayout(
            screenX = rect.x,
            screenY = rect.y,
            width = rectSize.width,
            height = rectSize.height,
        )
    }

    override fun cloneBase(
        id: Uuid,
        name: TranslatableName,
        align: Align,
        autoAlign: Boolean,
        offset: IntOffset,
        opacity: Float,
        lockMoving: Boolean,
    ): ControllerWidget = MinimapWidget(id, name, align, autoAlign, offset, opacity, lockMoving)

    companion object {
        /** 默认 100x100（GUI 像素），接近 Xaero 小地图的常见尺寸。 */
        val DEFAULT_SIZE: IntSize = IntSize(100, 100)
    }
}
