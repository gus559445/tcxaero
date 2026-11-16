package com.tcxaero

import org.slf4j.LoggerFactory
import top.fifthlight.combine.core.data.Text
import top.fifthlight.touchcontroller.common.control.builtin.BuiltInWidget
import top.fifthlight.touchcontroller.common.control.builtin.BuiltinWidgets
import top.fifthlight.touchcontroller.common.layout.align.Align
import top.fifthlight.touchcontroller.common.util.name.TranslatableName
import top.fifthlight.data.IntOffset
import kotlin.uuid.Uuid

/**
 * 把 [MinimapWidget] 注册进 TouchController 的控件注册表。
 *
 * <h2>为什么注册就能进编辑器</h2>
 * TC 0.3.1 的自定义布局编辑器，控件列表直接读
 * `BuiltinWidgets.INSTANCE.registry.values()`；配置存档走 `JBuiltInWidget`，
 * 而它同样用这个 registry 做 id ↔ 控件的映射。
 * 所以只要往这个 registry 里塞一条，控件就会**自动**出现在编辑器的控件列表里，
 * 也能被正常保存/读取，不需要额外注册序列化表。
 *
 * <h2>关于 API 与内部类</h2>
 * TC 的公开 API（`top.fifthlight.touchcontroller.api.v1`）只允许注册「按钮型」控件
 * ——贴图 + 触发动作，没有自定义渲染或自定义布局的入口。
 * 而我们要的是一个「占位并报坐标」的控件，只能直接用 common 层的内部类型。
 * 这也是本模组把 TC 声明为硬依赖、并且要在 TC 之后加载的原因。
 */
object WidgetRegistration {
    private val logger = LoggerFactory.getLogger("tcxaero/register")

    /** 注册进 TC 的控件 id；带命名空间避免和别的附属模组撞名。 */
    const val WIDGET_ID = "tcxaero:minimap"

    private var registered = false

    /**
     * 注册小地图控件。重复调用安全（TC 重载配置时可能触发多次）。
     *
     * @return 是否成功注册（或此前已注册）
     */
    fun register(): Boolean {
        if (registered) {
            return true
        }

        return runCatching {
            val registry = BuiltinWidgets.INSTANCE.registry

            // BuiltInWidget 的第一个参数是「按贴图风格产出控件实例」的工厂。
            // 我们的小地图不依赖贴图风格，直接返回同一个 MinimapWidget。
            val factory: (Any?) -> MinimapWidget = { _ -> newWidget() }

            val widget = BuiltInWidget(
                factory,
                false,   // hero：不置顶，按普通控件排在列表里
                null,    // hidden：不隐藏
            )

            registry.register(WIDGET_ID, widget)
            registered = true
            logger.info("已注册 TouchController 控件：{}", WIDGET_ID)
            true
        }.onFailure {
            logger.error("注册 TouchController 控件失败", it)
        }.getOrDefault(false)
    }

    /** 造一个新的小地图控件实例，默认挂在左上角。 */
    fun newWidget(): MinimapWidget = MinimapWidget(
        id = Uuid.random(),
        name = TranslatableName.Literal("Xaero 小地图"),
        align = Align.LEFT_TOP,
        autoAlign = true,
        offset = IntOffset(0, 0),
        opacity = 1.0f,
        lockMoving = false,
    )
}
