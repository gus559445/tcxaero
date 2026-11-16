package com.tcxaero

import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent
import org.slf4j.LoggerFactory

/**
 * TouchController × Xaero's Minimap 联动。
 *
 * <p>目标：把 Xaero 的小地图当成 TouchController 布局里的一个控件，
 * 让玩家在 TC 编辑器里拖位置/对齐，小地图就跟着走。</p>
 *
 * <h2>加载顺序</h2>
 * TC 的控件注册表（`BuiltinWidgets.registry`）在它自己的静态初始化里建好，
 * 必须在 TC 的类被加载之后再往里面塞东西。
 * 所以注册放在 [FMLClientSetupEvent] 里（那时所有模组构造都已完成），
 * 并且用 `enqueueWork` 排到主线程——TC 自己也是这么初始化的。
 *
 * <h2>为什么用 Dist.CLIENT</h2>
 * TC 与 Xaero 都是纯客户端模组（它们的 mods.toml 里 side = CLIENT），
 * 本模组也一样：服务端没有任何要做的。
 */
@Mod("tcxaero")
object TcXaeroMod {
    private val logger = LoggerFactory.getLogger("tcxaero")

    init {
        logger.info("TouchController × Xaero's Minimap 联动加载中…")
    }

    /** 客户端初始化：注册控件。 */
    @Mod.EventBusSubscriber(
        modid = "tcxaero",
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = [Dist.CLIENT],
    )
    object ClientSetup {
        @SubscribeEvent
        fun onClientSetup(event: FMLClientSetupEvent) {
            event.enqueueWork {
                WidgetRegistration.register()
            }
        }
    }
}
