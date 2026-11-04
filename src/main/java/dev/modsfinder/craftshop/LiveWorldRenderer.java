package dev.modsfinder.craftshop;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import dev.modsfinder.craftshop.mixin.CameraAccessor;
import org.joml.Matrix4f;

public final class LiveWorldRenderer {
    private static boolean active;
    private static boolean frameReady;
    private static SceneProject.Camera camera;
    private static TextureTarget target;
    private static RenderTarget overrideTarget;
    private static int viewportWidth;
    private static int viewportHeight;

    private static LevelRenderState secondaryState;
    private static LevelExtractor secondaryExtractor;
    private static Camera secondaryCamera;
    private static net.minecraft.client.multiplayer.ClientLevel secondaryLevel;
    private static LevelRenderer secondaryLevelRenderer;
    private static boolean failureLogged;

    private LiveWorldRenderer() {}

    public static void begin(SceneProject.Camera sceneCamera, Minecraft minecraft, int width, int height) {
        active = minecraft != null && minecraft.level != null && minecraft.player != null;
        frameReady = false;
        camera = sceneCamera;
        viewportWidth = Math.max(64, width - 24);
        viewportHeight = Math.max(64, height - 66);
        if (!active || camera == null) {
            releaseTarget();
            resetSecondaryRenderer();
            return;
        }

        if (camera.x == 0.0F && camera.y == 2.0F && camera.z == 8.0F
                && camera.yaw == 0.0F && camera.pitch == 12.0F) {
            camera.x = (float) minecraft.player.getX();
            camera.y = (float) (minecraft.player.getY() + minecraft.player.getEyeHeight());
            camera.z = (float) minecraft.player.getZ();
            camera.yaw = minecraft.player.getYRot();
            camera.pitch = minecraft.player.getXRot();
        }
        try {
            ensureTarget();
        } catch (Throwable throwable) {
            active = false;
            releaseTarget();
            reportRenderFailure(throwable);
        }
    }

    public static void reportRenderFailure(Throwable throwable) {
        if (!failureLogged) {
            failureLogged = true;
            CraftShop.LOGGER.error("CraftShop independent world viewport disabled after a render failure", throwable);
        }
    }

    private static void releaseTarget() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        overrideTarget = null;
        frameReady = false;
    }

    private static void resetSecondaryRenderer() {
        secondaryState = null;
        secondaryExtractor = null;
        secondaryCamera = null;
        secondaryLevel = null;
        secondaryLevelRenderer = null;
    }

    public static void end() {
        active = false;
        frameReady = false;
        overrideTarget = null;
        camera = null;
        releaseTarget();
        resetSecondaryRenderer();
        viewportWidth = 0;
        viewportHeight = 0;
    }

    public static boolean isActive() {
        return active && camera != null;
    }

    public static RenderTarget overrideTarget() {
        return overrideTarget;
    }

    public static LevelRenderState secondaryState() {
        return secondaryState;
    }

    public static RenderTarget beginFrame() {
        if (!isActive()) return null;
        try {
            ensureTarget();
            frameReady = false;
            overrideTarget = target;
            return target;
        } catch (Throwable throwable) {
            cancelFrame();
            reportRenderFailure(throwable);
            active = false;
            releaseTarget();
            return null;
        }
    }

    public static void finishFrame() {
        frameReady = target != null;
        overrideTarget = null;
        failureLogged = false;
    }

    public static void cancelFrame() {
        overrideTarget = null;
        frameReady = false;
    }

    private static void ensureTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        int scale = Math.max(1, (int) Math.ceil(minecraft.getWindow().getGuiScale()));
        int width = Math.max(64, viewportWidth * scale);
        int height = Math.max(64, viewportHeight * scale);
        long pixels = (long) width * height;
        final long maxPixels = 4_194_304L;
        if (pixels > maxPixels) {
            double reduction = Math.sqrt((double) maxPixels / pixels);
            width = Math.max(64, (int) Math.floor(width * reduction));
            height = Math.max(64, (int) Math.floor(height * reduction));
        }
        if (target != null && target.width == width && target.height == height) return;
        if (target != null) target.destroyBuffers();
        target = new TextureTarget("CraftShop independent world viewport",
                width, height, true, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);
    }

    public static CameraRenderState prepareSecondary(
            Minecraft minecraft,
            LevelRenderer levelRenderer,
            DeltaTracker deltaTracker,
            CameraRenderState source
    ) {
        if (!isActive() || minecraft.level == null || minecraft.player == null) return null;
        if (secondaryState == null || secondaryLevel != minecraft.level || secondaryLevelRenderer != levelRenderer) {
            secondaryState = new LevelRenderState();
            secondaryExtractor = new LevelExtractor(minecraft, secondaryState, levelRenderer);
            secondaryExtractor.setLevel(minecraft.level);
            secondaryCamera = new Camera();
            secondaryLevel = minecraft.level;
            secondaryLevelRenderer = levelRenderer;
        }

        secondaryCamera.setLevel(minecraft.level);
        secondaryCamera.setEntity(minecraft.player);
        secondaryCamera.update(deltaTracker);
        secondaryCamera.tick();

        CameraAccessor accessor = (CameraAccessor) secondaryCamera;
        accessor.craftshop$setDetached(true);
        accessor.craftshop$setRotation(camera.yaw, camera.pitch);
        accessor.craftshop$setPosition(camera.x, camera.y, camera.z);

        CameraRenderState cameraState = secondaryState.cameraRenderState;
        secondaryCamera.extractRenderState(
                cameraState,
                secondaryCamera.getCameraEntityPartialTicks(deltaTracker)
        );
        cameraState.fogType = secondaryCamera.getFluidInCamera();
        cameraState.fogData = source.fogData;
        cameraState.smartCull = source.smartCull;
        cameraState.isFrustumCaptured = false;

        float zoom = Math.max(0.2F, Math.min(4.0F, camera.zoom));
        cameraState.projectionMatrix.scale(zoom, zoom, 1.0F);
        Matrix4f viewRotation = new Matrix4f(cameraState.viewRotationMatrix);
        Frustum frustum = new Frustum(viewRotation, cameraState.projectionMatrix);
        frustum.prepare(camera.x, camera.y, camera.z);
        accessor.craftshop$setCullFrustum(frustum);
        cameraState.cullFrustum = frustum;

        secondaryExtractor.extract(
                deltaTracker,
                secondaryCamera,
                deltaTracker.getGameTimeDeltaPartialTick(false)
        );
        return cameraState;
    }

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (!frameReady || target == null) return;
        graphics.blit(target.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                x, y, x + width, y + height, 0.0F, 1.0F, 0.0F, 1.0F);
    }
}
