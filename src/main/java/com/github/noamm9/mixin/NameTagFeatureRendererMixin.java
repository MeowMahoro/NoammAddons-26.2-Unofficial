package com.github.noamm9.mixin;

import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 26.1.2 中通过拦截 Font#drawInBatch 去掉名条背景/加阴影；26.2 名条已改为
 * RenderTypeFeatureRenderer + PreparedText 管线，无对应注入点。
 * TODO(26.2): 在 NameTagFeatureRenderer#buildGroup 阶段为新管线重新实现
 * "Disable Nametag Background" 与 "Add Name Tag Text Shadow"。
 */
@Mixin(NameTagFeatureRenderer.class)
public class NameTagFeatureRendererMixin {
}
