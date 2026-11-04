package dev.modsfinder.craftshop.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.modsfinder.craftshop.LiveWorldRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void craftshop$useViewportTarget(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget target = LiveWorldRenderer.overrideTarget();
        if (target != null) cir.setReturnValue(target);
    }
}
