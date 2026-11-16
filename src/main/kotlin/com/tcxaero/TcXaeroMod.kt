package com.tcxaero

import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import org.slf4j.LoggerFactory

/**
 * TouchController × Xaero's Minimap 联动。
 *
 * <p>目标：把 Xaero 的小地图当成 TouchController 布局里的一个控件，
 * 让玩家在 TC 编辑器里拖位置 / 对齐，小地图就跟着走。</p>
 *
 * <h2>为什么事件要手动注册</h2>
 * 这里不用 `@Mod.EventBusSubscriber` 自动扫描：那个机制要求订阅方法是 **Java static** 方法，
 * 而 Kotlin 的 `object` 里就算加 `@JvmStatic` 也拿不到 Forge 期望的方法签名
 * （实测订阅方法完全不被调用，注册静默失败）。
 * 直接在构造函数里 `addListener` 最稳，行为可预期。
 *
 * <h2>为什么注册放在 FMLClientSetupEvent</h2>
 * TC 的控件注册表（`BuiltinWidgets.registry`）由它的静态初始化建立，
 * 必须等 TC 的类加载之后才能往里面塞东西。
 * FMLClientSetupEvent 阶段所有模组构造都已完成，且 TC 自己的初始化也在同一阶段，
 * 用 `enqueueWork` 排到主线程后执行，顺序安全。
 */
@Mod("tcxaero")
class TcXaeroMod {

    private val logger = LoggerFactory.getLogger("tcxaero")

    init {
        logger.info("TouchController × Xaero's Minimap 联动加载中…")

        // MOD 总线：生命周期事件（setup）
        FMLJavaModLoadingContext.get().modEventBus.addListener(this::onClientSetup)

        logger.info("事件监听器已注册")
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        logger.info("收到 FMLClientSetupEvent，准备注册小地图控件")
        event.enqueueWork {
            val ok = WidgetRegistration.register()
            logger.info("小地图控件注册结果：{}", if (ok) "成功" else "失败")
        }
    }
}
