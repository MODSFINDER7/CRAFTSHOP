package dev.modsfinder.craftshop.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

import dev.modsfinder.craftshop.CraftShop;
import dev.modsfinder.craftshop.LiveWorldRenderer;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow @Final private LevelRenderState levelRenderState;

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

        var target = LiveWorldRenderer.beginFrame();
        if (target == null) return;

        CameraRenderState viewportCamera = LiveWorldRenderer.createCameraState(cameraState);
        CameraRenderState previousCamera = levelRenderState.cameraRenderState;
        List<?> entities = new ArrayList<>(levelRenderState.entityRenderStates);
        List<?> blockEntities = new ArrayList<>(levelRenderState.blockEntityRenderStates);
        List<?> sectionUpdates = new ArrayList<>(levelRenderState.sectionUpdateRenderStates);
        List<?> breakingBlocks = new ArrayList<>(levelRenderState.blockBreakingRenderStates);
        int previousEntityCount = levelRenderState.lastEntityRenderStateCount;

        levelRenderState.cameraRenderState = viewportCamera;
        craftshop$secondaryRender = true;
        try {
            Matrix4f viewportModelView = new Matrix4f(viewportCamera.viewRotationMatrix)
                    .translate((float) -viewportCamera.pos.x,
                            (float) -viewportCamera.pos.y,
                            (float) -viewportCamera.pos.z);
            ((LevelRenderer) (Object) this).render(
                    resourceAllocator, deltaTracker, renderOutline, viewportCamera,
                    viewportModelView, terrainFog, fogColor, shouldRenderSky
            );
            LiveWorldRenderer.finishFrame();
        } catch (Throwable throwable) {
            LiveWorldRenderer.cancelFrame();
            CraftShop.LOGGER.error("CraftShop independent world render failed", throwable);
        } finally {
            craftshop$secondaryRender = false;
            levelRenderState.cameraRenderState = previousCamera;
            craftshop$restore(levelRenderState.entityRenderStates, entities);
            craftshop$restore(levelRenderState.blockEntityRenderStates, blockEntities);
            craftshop$restore(levelRenderState.sectionUpdateRenderStates, sectionUpdates);
            craftshop$restore(levelRenderState.blockBreakingRenderStates, breakingBlocks);
            levelRenderState.lastEntityRenderStateCount = previousEntityCount;
        }
    }

    @Unique
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void craftshop$restore(List target, List saved) {
        target.clear();
        target.addAll(saved);
    }
}
