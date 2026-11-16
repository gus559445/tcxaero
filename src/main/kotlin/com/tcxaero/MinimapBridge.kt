package com.tcxaero

import org.slf4j.LoggerFactory
import java.lang.reflect.Field

/**
 * TouchController 控件矩形 → Xaero's Minimap 位置 的桥接。
 *
 * <h2>为什么不自己实现一个 ControllerWidget</h2>
 * 试过了，走不通：TC 的控件存档走 kondor 的 `JControllerWidget`，
 * 那是个**静态构建**的 sealed 类型表，只登记了
 * `boat_button` / `custom` / `dpad` / `joystick` 四种。
 * 自己新写的 `ControllerWidget` 子类不在表里，注册进 registry 后能出现在控件库，
 * 但一旦保存布局就会被**静默丢弃**
 * （实测：编辑器里能看到、能拖进去、保存后配置文件里根本没有）。
 *
 * <h2>所以改挂到 CustomWidget 上</h2>
 * 用一个普通的「自定义按钮」控件当载体，靠 [MinimapLayoutMixin] 在它的 `layout()` 里
 * 把 TC 已经算好的屏幕矩形读出来，交给本对象去改 Xaero 的 `ModuleTransform`。
 *
 * 识别方式是控件名：玩家把这个按钮命名成 [MARKER_NAME]，
 * 它就从普通按钮变成「小地图占位」。这样不需要新类型、不需要改序列化表，
 * 存档完全走 TC 原生路径。
 */
object MinimapBridge {
    private val logger = LoggerFactory.getLogger("tcxaero/bridge")

    /**
     * 触发小地图联动的控件名。
     *
     * 玩家在 TC 编辑器里把任意「自定义按钮」重命名成这个名字，
     * 它就会变成小地图的占位控件。
     */
    const val MARKER_NAME = "Xaero 小地图"

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

    @Volatile
    private var layoutLogCount: Int = 0

    private var hudModInstanceField: Field? = null
    private var xaeroInitTried: Boolean = false

    /**
     * 判断某个控件是不是「小地图占位」控件：只看最终显示名字。
     */
    @JvmStatic
    fun isMarker(nameText: String?): Boolean = nameText?.trim() == MARKER_NAME

    /**
     * 由 mixin 在 `CustomWidget.layout()` 里每帧调用，传入 TC 算好的控件矩形
     * （GUI 缩放像素，绝对坐标：距左 / 距上）。
     */
    @JvmStatic
    fun onWidgetLayout(
        screenX: Int,
        screenY: Int,
        width: Int,
        height: Int,
    ) {
        targetX = screenX
        targetY = screenY
        hasTarget = true

        if (layoutLogCount < 3) {
            layoutLogCount++
            logger.info("小地图控件 layout：rect=({}, {}) size={}x{}", screenX, screenY, width, height)
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
            if (lastApplyOk) {
                logger.warn("Xaero 小地图模块消失了，停止接管")
                lastApplyOk = false
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
     * 取 `ModuleTransform`。
     *
     * `getUsedTransform()` 在有 Screen 打开时返回 `unconfirmedTransform`（编辑器临时态），
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
