package dev.modsfinder.craftshop.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.modsfinder.craftshop.CraftShop;
import dev.modsfinder.craftshop.LiveWorldRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow @Final @Mutable private LevelRenderState levelRenderState;

    @Unique
    private boolean craftshop$secondaryRender;

    @Inject(method = "render", at = @At("HEAD"))
    private void craftshop$renderIndependentViewport(
            GraphicsResourceAllocator resourceAllocator,
            DeltaTracker deltaTracker,
            boolean renderOutline,
            CameraRenderState cameraState,
            Matrix4fc modelViewMatrix,
            GpuBufferSlice terrainFog,
            Vector4f fogColor,
            boolean shouldRenderSky,
            CallbackInfo ci
    ) {
        if (craftshop$secondaryRender || !LiveWorldRenderer.isActive()) return;

        if (LiveWorldRenderer.beginFrame() == null) return;
        LevelRenderState primaryState = levelRenderState;
        boolean rendered = false;
        craftshop$secondaryRender = true;
        try {
            LevelRenderer renderer = (LevelRenderer) (Object) this;
            CameraRenderState viewportCamera = LiveWorldRenderer.prepareSecondary(
                    Minecraft.getInstance(), renderer, deltaTracker, cameraState
            );
            LevelRenderState viewportState = LiveWorldRenderer.secondaryState();
            if (viewportCamera == null || viewportState == null) {
                LiveWorldRenderer.cancelFrame();
                return;
            }

            levelRenderState = viewportState;
            Matrix4f viewportModelView = new Matrix4f(viewportCamera.viewRotationMatrix)
                    .translate((float) -viewportCamera.pos.x,
                            (float) -viewportCamera.pos.y,
                            (float) -viewportCamera.pos.z);
            renderer.render(
                    resourceAllocator,
                    deltaTracker,
                    false,
                    viewportCamera,
                    viewportModelView,
                    terrainFog,
                    fogColor,
                    shouldRenderSky
            );
            rendered = true;
        } catch (Throwable throwable) {
            LiveWorldRenderer.reportRenderFailure(throwable);
        } finally {
            levelRenderState = primaryState;
            craftshop$secondaryRender = false;
            if (rendered) LiveWorldRenderer.finishFrame();
            else LiveWorldRenderer.cancelFrame();
        }
    }
}
