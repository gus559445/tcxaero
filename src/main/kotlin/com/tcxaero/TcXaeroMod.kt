package com.tcxaero

import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import org.slf4j.LoggerFactory
import thedarkcolour.kotlinforforge.KotlinModLoadingContext

/**
 * TouchController × Xaero's Minimap 联动。
 *
 * <p>目标：把 Xaero 的小地图当成 TouchController 布局里的一个控件，
 * 让玩家在 TC 编辑器里拖位置 / 对齐，小地图就跟着走。</p>
 *
 * <h2>为什么用 KotlinModLoadingContext 而不是 FMLJavaModLoadingContext</h2>
 * 本模组的 modLoader 是 `kotlinforforge`，KFF 会把 `ModLoadingContext.get()`
 * 换成一个 `KotlinModLoadingContext` 子类。这时候再调
 * `FMLJavaModLoadingContext.get()` 拿到的实际对象是 Kotlin 那个，
 * 强转成 `FMLJavaModLoadingContext` 会抛 ClassCastException（实测直接把构造器炸掉）。
 * KFF 提供 `getKEventBus()` 拿同一个 MOD 总线，是这里唯一正确的入口。
 *
 * <h2>为什么不用 @Mod.EventBusSubscriber</h2>
 * 那个机制要求订阅方法是 Java static 方法；Kotlin object 里即使加 `@JvmStatic`
 * 也拿不到 Forge 期望的签名，订阅方法完全不被调用（实测静默失败）。
 * 手动 addListener 行为可预期。
 *
 * <h2>为什么注册放在 FMLClientSetupEvent</h2>
 * TC 的控件注册表（`BuiltinWidgets.registry`）由它的静态初始化建立，
 * 必须等 TC 的类加载之后才能往里面塞东西。这个阶段所有模组构造都已完成，
 * 用 `enqueueWork` 排到主线程执行，顺序安全。
 */
@Mod("tcxaero")
class TcXaeroMod {

    private val logger = LoggerFactory.getLogger("tcxaero")

    init {
        logger.info("TouchController × Xaero's Minimap 联动加载中…")

        KotlinModLoadingContext.get().getKEventBus().addListener(this::onClientSetup)

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
