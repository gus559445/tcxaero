package com.tcxaero

import org.slf4j.LoggerFactory
import java.lang.reflect.Field

/**
 * TouchController 控件矩形 → Xaero's Minimap 位置 的桥接。
 *
 * <h2>为什么不直接画</h2>
 * Xaero 的小地图不是一张贴图：它自己管 FBO、区块贴图缓存、雷达实体、路径点、指南针，
 * 整套渲染挂在 `xaero.hud.minimap.module.MinimapSession` 的生命周期里。
 * 在 TouchController 的 Canvas 里重画一份既拿不到这些状态，也会和它自己的 HUD 渲染打架。
 *
 * <h2>所以改成「搬位置」</h2>
 * Xaero 的 HUD 是模块化的，小地图是一个 `HudModule`，位置存在 `ModuleTransform` 里。
 * 我们不去改它的渲染，而是每帧把它的 transform 改成 TouchController 控件所在的位置。
 *
 * <h2>坐标语义（来自 ModuleSession.getEffectiveX/getEffectiveY）</h2>
 * - `centered == false && fromRight == false` → 生效值就是 `x` 本身
 * - `fromBottom == false`                     → 生效值就是 `y` 本身
 *
 * 所以只要把 `x` / `y` 写成绝对像素、并把 `centered` / `fromRight` / `fromBottom`
 * 全部清成 false，就是「距左边 x 像素、距上边 y 像素」。
 */
object MinimapBridge {
    private val logger = LoggerFactory.getLogger("tcxaero/bridge")

    /** Xaero 小地图模块的注册 id（ResourceLocation 的 path 部分）。 */
    private const val MINIMAP_MODULE_ID = "minimap"

    @Volatile
    private var targetX: Int = 0

    @Volatile
    private var targetY: Int = 0

    @Volatile
    private var hasTarget: Boolean = false

    @Volatile
    private var lastApplyOk: Boolean = false

    /** 诊断用：只打印前几次 layout 的矩形，确认控件真的在布局里。 */
    @Volatile
    private var layoutLogCount: Int = 0

    private var hudModInstanceField: Field? = null
    private var xaeroInitTried: Boolean = false

    /**
     * 由 [MinimapWidget.layout] 每帧调用，传入 TC 算好的控件矩形（GUI 缩放像素）。
     */
    fun onWidgetLayout(
        screenX: Int,
        screenY: Int,
        width: Int,
        height: Int,
        windowWidth: Int,
        windowHeight: Int,
    ) {
        targetX = screenX
        targetY = screenY
        hasTarget = true

        // 前 3 次把完整矩形打出来：如果这里一直是 0，说明控件没被真正排进布局。
        if (layoutLogCount < 3) {
            layoutLogCount++
            logger.info(
                "控件 layout：rect=({}, {}) size={}x{} window={}x{}",
                screenX, screenY, width, height, windowWidth, windowHeight,
            )
        }

        applyToXaero()
    }

    /** 把目标位置写进 Xaero 的小地图 `ModuleTransform`。全程反射，失败安全降级。 */
    private fun applyToXaero() {
        if (!hasTarget) {
            return
        }

        val hudModule = resolveMinimapModule()
        if (hudModule == null) {
            if (!lastApplyOk) {
                logger.debug("未找到 Xaero 小地图模块，跳过位置接管")
            }
            return
        }

        runCatching {
            val transform = obtainTransform(hudModule) ?: return
            setInt(transform, "x", targetX)
            setInt(transform, "y", targetY)
            setBoolean(transform, "centered", false)
            setBoolean(transform, "fromRight", false)
            setBoolean(transform, "fromBottom", false)
            // fromOldSystem 必须清掉，否则 Xaero 会拿旧系统坐标再转一次。
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
            val hud = invokeNoArg(hudMod, "getHud") ?: return null
            val moduleManager = invokeNoArg(hud, "getModuleManager") ?: return null
            val modules = invokeNoArg(moduleManager, "getModules") as? Iterable<*> ?: return null

            for (module in modules) {
                if (module == null) continue
                val id = invokeNoArg(module, "getId") ?: continue
                // ResourceLocation.toString() == "namespace:path"
                if (id.toString().endsWith(":$MINIMAP_MODULE_ID")) {
                    return module
                }
            }
            logger.debug("模块列表里没有 {} 结尾的模块", MINIMAP_MODULE_ID)
            return null
        }.onFailure {
            logger.debug("解析 Xaero 模块失败：{}", it.toString())
        }

        return null
    }

    /**
     * 取 `ModuleTransform`。
     *
     * `getUsedTransform()` 在「有 Screen 打开」时返回 `unconfirmedTransform`（编辑器临时态），
     * 关掉界面后返回真正的 `transform`——两者都是实对象，直接改字段有效。
     */
    private fun obtainTransform(hudModule: Any): Any? {
        for (name in listOf("getUsedTransform", "getConfirmedTransform")) {
            val result = invokeNoArg(hudModule, name)
            if (result != null) {
                return result
            }
        }
        return null
    }

    private fun invokeNoArg(target: Any, methodName: String): Any? = runCatching {
        target.javaClass.methods
            .firstOrNull { it.name == methodName && it.parameterCount == 0 }
            ?.invoke(target)
    }.getOrNull()

    /** Xaero 的 `HudMod.INSTANCE`（public static 字段）。 */
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
        target.javaClass.getField(fieldName).setInt(target, value)
    }

    private fun setBoolean(target: Any, fieldName: String, value: Boolean) {
        target.javaClass.getField(fieldName).setBoolean(target, value)
    }
}
