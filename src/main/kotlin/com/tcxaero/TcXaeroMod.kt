package com.tcxaero

import net.minecraftforge.fml.common.Mod
import org.slf4j.LoggerFactory

/**
 * TouchController × Xaero's Minimap 联动。
 *
 * <p>把 Xaero 的小地图位置交给 TouchController 的布局控件控制：
 * 在 TC 编辑器里把任意一个「自定义按钮」重命名为 [MinimapBridge.MARKER_NAME]
 * （Xaero 小地图），它就会变成小地图的占位控件，拖动它 = 拖动小地图。</p>
 *
 * <h2>为什么没有注册任何控件</h2>
 * 试过注册自定义控件，走不通：TC 的控件存档是 kondor 的**静态** sealed 类型表
 * （`JControllerWidget`），只认 `boat_button` / `custom` / `dpad` / `joystick`。
 * 新类型能进控件库，但保存布局时会被静默丢弃（配置文件里根本不会出现）。
 *
 * <p>所以改成「借用 CustomWidget 当载体 + mixin 拦截 layout」：
 * 控件本身是 TC 原生类型，存档完全正常；联动逻辑在
 * [com.tcxaero.mixin.MinimapLayoutMixin] 里，只对名字匹配的那一个实例生效。</p>
 *
 * <h2>为什么事件不需要手动注册</h2>
 * 本模组不再依赖任何生命周期事件——mixin 由 Forge 的 Mixin 框架在类加载时织入，
 * 不需要在模组构造阶段挂监听器。这避开了之前两个坑：
 * `@EventBusSubscriber` 在 Kotlin object 上不生效、
 * 以及 KFF 环境下 `FMLJavaModLoadingContext.get()` 的 ClassCastException。
 */
@Mod("tcxaero")
class TcXaeroMod {

    private val logger = LoggerFactory.getLogger("tcxaero")

    init {
        logger.info("TouchController × Xaero's Minimap 联动已加载")
        logger.info(
            "用法：在 TC 自定义布局里添加一个「自定义按钮」，把名字改成「{}」，拖动它即可移动小地图",
            MinimapBridge.MARKER_NAME,
        )
    }
}
