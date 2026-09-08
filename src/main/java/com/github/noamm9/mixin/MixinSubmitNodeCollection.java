package com.github.noamm9.mixin;

import com.github.noamm9.features.impl.misc.NameTagTweaks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.2 builds the name tag background inside SubmitNodeCollection#submitNameTag (the old
 * Font#drawInBatch injection point no longer exists). We zero out the computed background
 * ARGB so the name tag background disappears.
 */
@Mixin(SubmitNodeCollection.class)
public abstract class MixinSubmitNodeCollection {
    @ModifyExpressionValue(
        method = "submitNameTag",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/ARGB;color(FI)I"
        )
    )
    private int noamm$onNameTagBackground(int originalBackground) {
        return NameTagTweaks.INSTANCE.enabled && NameTagTweaks.getDisableNametagBackground().getValue() ? 0 : originalBackground;
    }
}
