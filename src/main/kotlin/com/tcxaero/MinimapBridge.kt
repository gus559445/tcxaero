package com.tcxaero

import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.lang.reflect.Field

/**
 * TouchController 控件矩形 → Xaero's Minimap 位置 的桥接。
 *
 * <h2>为什么不直接画</h2>
 * Xaero 的小地图不是一张贴图：它自己管 FBO、区块贴图缓存、雷达实体、路径点、指南针，
 * 整套渲染在 `xaero.hud.minimap.module.MinimapSession` 的生命周期里。
 * 在 TouchController 的 Canvas 里重画一份既拿不到这些状态，也会和它自己的 HUD 渲染打架。
 *
 * <h2>所以改成「搬位置」</h2>
 * Xaero 的 HUD 是模块化的，小地图是一个 `HudModule`，位置存在 `ModuleTransform` 里
 * （public 字段 `x` / `y` / `fromRight` / `fromBottom` / `centered`）。
 * 我们不去改它的渲染，而是**每一帧把它的 transform 改成 TouchController 控件所在的位置**。
 * 结果就是：玩家在 TC 编辑器里拖那个控件，Xaero 的小地图跟着走。
 *
 * <h2>坐标语义（来自 Xaero 的 OldSystemCompatibility.convertTransform）</h2>
 * - `fromRight == false`：`x` 是距**左**边的像素
 * - `fromRight == true` ：`x` 是距**右**边的像素（`x = screenWidth - moduleWidth - 右边距`）
 * - `fromBottom == false`：`y` 是距**上**边的像素
 * - `fromBottom == true` ：`y` 是距**下**边的像素
 *
 * 这里统一换算成「距左边 / 距上边」的绝对像素，`fromRight` / `fromBottom` 一律置 false，
 * 免得两套坐标系来回算错。
 */
object MinimapBridge {
    private val logger = LoggerFactory.getLogger("tcxaero/bridge")

    /** Xaero 小地图模块的注册 ID。 */
    private const val MINIMAP_MODULE_ID = "minimap"

    /** 本次 layout 算出来的目标矩形；由控件每帧写入。 */
    @Volatile
    private var targetX: Int = 0

    @Volatile
    private var targetY: Int = 0

    @Volatile
    private var hasTarget: Boolean = false

    /** 记录最近一次是否成功接管，避免每帧刷日志。 */
    @Volatile
    private var lastApplyOk: Boolean = false

    /** 反射缓存：Xaero 的类只在第一次用时查，之后复用。 */
    private var hudModInstanceField: Field? = null
    private var moduleManagerGetField: Field? = null
    private var transformField: Field? = null
    private var xaeroInitTried: Boolean = false

    /**
     * 由 [MinimapWidget.layout] 每帧调用。
     *
     * 传入的是 TouchController 算好的控件矩形（GUI 缩放像素），
     * 直接作为 Xaero 小地图左上角的目标位置。
     */
    fun onWidgetLayout(
        screenX: Int,
        screenY: Int,
        width: Int,
        height: Int,
        windowWidth: Int,
        windowHeight: Int,
    ) {
        // 控件被拖出屏幕（TC 允许拖到边缘外）时不做限制，照原样传下去，
        // 让 Xaero 自己按它的边界逻辑处理，避免两边判断不一致。
        targetX = screenX
        targetY = screenY
        hasTarget = true

        applyToXaero()
    }

    /**
     * 把目标位置写进 Xaero 的小地图 `ModuleTransform`。
     *
     * 全程反射，原因有两个：
     * 1. Xaero 是闭源模组，直接编译期依赖它的内部类会让本模组在没装 Xaero 时直接崩；
     * 2. 不同版本的 `HudMod` / `ModuleManager` 字段名可能变，反射失败可以安全降级。
     */
    private fun applyToXaero() {
        if (!hasTarget) {
            return
        }

        val hudModule = resolveMinimapModule()
        if (hudModule == null) {
            if (!lastApplyOk) {
                logger.debug("未找到 Xaero 小地图模块，跳过位置接管")
                lastApplyOk = false
            }
            return
        }

        runCatching {
            val transform = obtainTransform(hudModule) ?: return
            // 绝对坐标：距左 / 距上，不使用 fromRight / fromBottom，
            // 免得 Xaero 再按自己的屏幕宽度换算一次。
            setInt(transform, "x", targetX)
            setInt(transform, "y", targetY)
            setBoolean(transform, "fromRight", false)
            setBoolean(transform, "fromBottom", false)
            setBoolean(transform, "centered", false)
            // fromOldSystem 必须清掉：否则 Xaero 会拿旧系统坐标再转一次。
            setBoolean(transform, "fromOldSystem", false)

            if (!lastApplyOk) {
                logger.info("已接管 Xaero 小地图位置：({}, {})", targetX, targetY)
                lastApplyOk = true
            }
        }.onFailure {
            if (lastApplyOk) {
                logger.warn("写入 Xaero 小地图位置失败：{}", it.toString())
                lastApplyOk = false
            }
        }
    }

    /** 拿到 Xaero 的小地图 `HudModule`；没装 Xaero 或结构不符时返回 null。 */
    private fun resolveMinimapModule(): Any? {
        val hudMod = xaeroHudModInstance() ?: return null

        runCatching {
            val getHud = hudMod.javaClass.methods.firstOrNull {
                it.name == "getHud" && it.parameterCount == 0
            } ?: return null
            val hud = getHud.invoke(hudMod) ?: return null

            val getModuleManager = hud.javaClass.methods.firstOrNull {
                it.name == "getModuleManager" && it.parameterCount == 0
            } ?: return null
            val moduleManager = getModuleManager.invoke(hud) ?: return null

            val getModules = moduleManager.javaClass.methods.firstOrNull {
                it.name == "getModules" && it.parameterCount == 0
            } ?: return null
            val modules = getModules.invoke(moduleManager) as? Iterable<*> ?: return null

            for (module in modules) {
                if (module == null) continue
                val id = runCatching {
                    module.javaClass.methods.firstOrNull { it.name == "getId" && it.parameterCount == 0 }
                        ?.invoke(module)
                }.getOrNull() ?: continue
                // ResourceLocation 的 toString 是 "namespace:path"
                if (id.toString().endsWith(":$MINIMAP_MODULE_ID")) {
                    return module
                }
            }
            return null
        }.onFailure {
            logger.debug("解析 Xaero 模块失败：{}", it.toString())
        }

        return null
    }

    /**
     * 取到 `ModuleTransform`。
     *
     * 优先用 `getUsedTransform()`（Xaero 实际渲染时读的那个），
     * 拿不到再退回 `getConfirmedTransform()`。
     */
    private fun obtainTransform(hudModule: Any): Any? {
        for (name in listOf("getUsedTransform", "getConfirmedTransform", "getUnconfirmedTransform")) {
            val result = runCatching {
                hudModule.javaClass.methods
                    .firstOrNull { it.name == name && it.parameterCount == 0 }
                    ?.invoke(hudModule)
            }.getOrNull()
            if (result != null) {
                return result
            }
        }
        return null
    }

    /**
     * Xaero 的 `HudMod.INSTANCE`（public static 字段）。
     *
     * 这个字段在模组构造阶段才赋值，所以第一次调用拿不到是正常的，之后每次都会重试。
     */
    private fun xaeroHudModInstance(): Any? {
        if (!xaeroInitTried) {
            runCatching {
                val cls = Class.forName("xaero.common.HudMod")
                hudModInstanceField = cls.getField("INSTANCE")
            }.onFailure {
                logger.debug("未找到 xaero.common.HudMod（没装 Xaero's Minimap？）")
            }
            xaeroInitTried = true
        }
        return runCatching { hudModInstanceField?.get(null) }.getOrNull()
    }

    private fun setInt(target: Any, fieldName: String, value: Int) {
        val field = target.javaClass.getField(fieldName)
        field.setInt(target, value)
    }

    private fun setBoolean(target: Any, fieldName: String, value: Boolean) {
        val field = target.javaClass.getField(fieldName)
        field.setBoolean(target, value)
    }
}
