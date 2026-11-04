package dev.modsfinder.craftshop;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class LiveWorldRenderer {
    private static boolean active;
    private static boolean frameReady;
    private static SceneProject.Camera camera;
    private static TextureTarget target;
    private static RenderTarget overrideTarget;
    private static int viewportWidth;
    private static int viewportHeight;

    private LiveWorldRenderer() {}

    public static void begin(SceneProject.Camera sceneCamera, Minecraft minecraft, int width, int height) {
        active = minecraft != null && minecraft.level != null && minecraft.player != null;
        frameReady = false;
        camera = sceneCamera;
        viewportWidth = Math.max(64, width - 24);
        viewportHeight = Math.max(64, height - 66);
        if (!active || camera == null) return;

        if (camera.x == 0.0F && camera.y == 2.0F && camera.z == 8.0F
                && camera.yaw == 0.0F && camera.pitch == 12.0F) {
            camera.x = (float) minecraft.player.getX();
            camera.y = (float) (minecraft.player.getY() + minecraft.player.getEyeHeight());
            camera.z = (float) minecraft.player.getZ();
            camera.yaw = minecraft.player.getYRot();
            camera.pitch = minecraft.player.getXRot();
        }
        ensureTarget();
    }

    public static void end() {
        active = false;
        frameReady = false;
        overrideTarget = null;
        camera = null;
    }

    public static boolean isActive() {
        return active && camera != null;
    }

    public static RenderTarget overrideTarget() {
        return overrideTarget;
    }

    public static RenderTarget beginFrame() {
        if (!isActive()) return null;
        ensureTarget();
        frameReady = false;
        overrideTarget = target;
        return target;
    }

    public static void finishFrame() {
        frameReady = true;
        overrideTarget = null;
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
        if (target != null && target.width == width && target.height == height) return;
        if (target != null) target.destroyBuffers();
        target = new TextureTarget("CraftShop independent world viewport",
                width, height, true, GpuFormat.RGBA8_UNORM);
    }

    public static CameraRenderState createCameraState(CameraRenderState source) {
        if (source == null || camera == null) return source;

        CameraRenderState state = new CameraRenderState();
        state.pos = new Vec3(camera.x, camera.y, camera.z);
        state.blockPos = BlockPos.containing(camera.x, camera.y, camera.z);
        state.xRot = camera.pitch;
        state.yRot = camera.yaw;
        state.initialized = source.initialized;
        state.isPanoramicMode = false;
        state.isFrustumCaptured = false;
        state.smartCull = source.smartCull;
        state.fogType = source.fogType;
        state.fogData = source.fogData;
        state.hudFov = source.hudFov;
        state.depthFar = source.depthFar;
        state.entityRenderState = source.entityRenderState;

        state.orientation = new Quaternionf().rotationYXZ(
                (float) Math.PI - camera.yaw * ((float) Math.PI / 180.0F),
                -camera.pitch * ((float) Math.PI / 180.0F),
                0.0F
        );
        state.viewRotationMatrix = new Matrix4f().rotation(new Quaternionf(state.orientation).conjugate());
        float zoom = Math.max(0.2F, Math.min(4.0F, camera.zoom));
        state.projectionMatrix = new Matrix4f(source.projectionMatrix).scale(zoom, zoom, 1.0F);

        Matrix4f modelView = new Matrix4f(state.viewRotationMatrix)
                .translate(-camera.x, -camera.y, -camera.z);
        state.cullFrustum = new Frustum(modelView, state.projectionMatrix);
        state.cullFrustum.prepare(camera.x, camera.y, camera.z);
        return state;
    }

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (!frameReady || target == null) return;
        graphics.blit(target.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                x, y, x + width, y + height, 0.0F, 1.0F, 0.0F, 1.0F);
    }
}
