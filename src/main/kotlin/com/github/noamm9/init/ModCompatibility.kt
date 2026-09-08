package com.github.noamm9.init

import com.github.noamm9.NoammAddons.mc
import com.github.noamm9.init.types.ICustomMenu
import com.github.noamm9.utils.catch
import net.fabricmc.loader.api.FabricLoader

object ModCompatibility {
    val customMenus = mutableListOf<ICustomMenu>()

    @JvmStatic fun isModLoaded(modid: String) = FabricLoader.getInstance().isModLoaded(modid)
    @JvmStatic fun isCustomMenuActive() = customMenus.any(ICustomMenu::isActive)

    fun disableBlockstateCulling() = catch {
        if (! isModLoaded("moreculling")) return@catch
        val main = Class.forName("ca.fxco.moreculling.MoreCulling")
        val config = main.getDeclaredField("CONFIG").get(null)

        val blockStateCulling = config?.javaClass?.getDeclaredField("useBlockStateCulling")
        blockStateCulling?.isAccessible = true
        blockStateCulling?.setBoolean(config, false)
        refreshLevelRenderer()
    }

    /**
     * 26.2 渲染管线下重建区块渲染数据的入口。
     *
     * 注意：不能直接调 levelRenderer.resetLevelRenderData() —— 该方法只会把 viewArea 置空并销毁
     * sectionRenderDispatcher，并不重建。在世界已加载的情况下调用，下一帧 render -> repositionCamera
     * 会因 viewArea == null 直接 NPE（26.1.2 及更早的渲染实现不受此影响）。
     * 需要刷新区块渲染时应走 invalidateCompiledGeometry(...)，它会重建 ViewArea、编译器并重定位相机，
     * 等价于"修改渲染距离/资源重载"时 vanilla 所做的重建。
     */
    fun refreshLevelRenderer() {
        val level = mc.level ?: return
        mc.levelRenderer.invalidateCompiledGeometry(
            level,
            mc.options,
            mc.gameRenderer.mainCamera(),
            mc.blockColors
        )
    }

    const val bobby_chunk = "de.johni0702.minecraft.bobby.FakeChunk"
    val bobbyManagesWorlds by lazy {
        runCatching {
            if (! isModLoaded("bobby")) return@runCatching false
            val bobby = Class.forName("de.johni0702.minecraft.bobby.Bobby").getMethod("getInstance").invoke(null)
            val config = bobby.javaClass.getMethod("getConfig").invoke(bobby)
            config.javaClass.getMethod("isDynamicMultiWorld").invoke(config) as Boolean
        }.getOrDefault(false)
    }
}