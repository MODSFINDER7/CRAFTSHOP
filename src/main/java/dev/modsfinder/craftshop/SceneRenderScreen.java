package dev.modsfinder.craftshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SceneRenderScreen extends Screen {
    private final SceneProject project;
    private final Path imagePath;
    private final Screen parent;
    private int imageWidth = 1;
    private int imageHeight = 1;
    private String error = "";

    public SceneRenderScreen(SceneProject project, Path imagePath) {
        this(project, imagePath, null);
    }

    public SceneRenderScreen(SceneProject project, Path imagePath, Screen parent) {
        super(Component.literal(parent == null ? "CraftShop PNG Render" : "CraftShop Scene Preview"));
        this.project = project;
        this.imagePath = imagePath;
        this.parent = parent;
        try {
            var image = javax.imageio.ImageIO.read(imagePath.toFile());
            if (image == null) throw new java.io.IOException("PNG could not be decoded");
            imageWidth = image.getWidth();
            imageHeight = image.getHeight();
            image.flush();
            if (GalleryTextureCache.get(imagePath) == null) {
                throw new java.io.IOException("PNG texture could not be loaded by Minecraft");
            }
        } catch (Exception exception) {
            error = exception.getMessage() == null ? "PNG could not be read" : exception.getMessage();
        }
    }

    public boolean isReady() {
        return error.isBlank() && imageWidth > 0 && imageHeight > 0;
    }

    public static CanvasBounds canvasBounds(int screenWidth, int screenHeight, int imageWidth, int imageHeight) {
        float scale = Math.min((float) screenWidth / Math.max(1, imageWidth),
                (float) screenHeight / Math.max(1, imageHeight));
        int width = Math.max(1, Math.round(imageWidth * scale));
        int height = Math.max(1, Math.round(imageHeight * scale));
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;
        return new CanvasBounds(x, y, width, height);
    }

    public record CanvasBounds(int x, int y, int width, int height) {}

    private List<ProjectedObject> projectObjects(CanvasBounds bounds) {
        List<ProjectedObject> projected = new ArrayList<>();
        SceneProject.Camera camera = project.camera == null ? new SceneProject.Camera() : project.camera;
        double yaw = Math.toRadians(camera.yaw);
        double pitch = Math.toRadians(camera.pitch);
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double sinPitch = Math.sin(pitch);
        double cosPitch = Math.cos(pitch);

        double forwardX = -sinYaw * cosPitch;
        double forwardY = -sinPitch;
        double forwardZ = -cosYaw * cosPitch;
        double rightX = cosYaw;
        double rightZ = -sinYaw;
        double upX = -sinYaw * sinPitch;
        double upY = cosPitch;
        double upZ = -cosYaw * sinPitch;

        float focal = Math.min(bounds.width(), bounds.height()) * 0.92F;
        float zoom = Math.max(0.2F, Math.min(4.0F, camera.zoom));
        float centerX = bounds.x() + bounds.width() * 0.5F;
        float centerY = bounds.y() + bounds.height() * 0.5F;

        if (project.objects == null) return projected;
        for (SceneProject.SceneObject object : project.objects) {
            if (object == null || !object.visible || object.transform == null) continue;
            double dx = object.transform.x - camera.x;
            double dy = object.transform.y - camera.y;
            double dz = object.transform.z - camera.z;
            double depth = dx * forwardX + dy * forwardY + dz * forwardZ;
            if (depth <= 0.15) continue;

            double horizontal = dx * rightX + dz * rightZ;
            double vertical = dx * upX + dy * upY + dz * upZ;
            int screenX = Math.round((float) (centerX + horizontal * focal * zoom / depth));
            int screenY = Math.round((float) (centerY - vertical * focal * zoom / depth));
            float baseScale = Math.max(0.05F, Math.min(4.0F,
                    (object.transform.scaleX + object.transform.scaleY + object.transform.scaleZ) / 3.0F));
            int halfSize = Math.max(5, Math.min(Math.min(bounds.width(), bounds.height()) / 3,
                    Math.round((float) (focal * 0.48F * zoom / depth))));
            int cullSize = Math.max(4, Math.round(halfSize * baseScale));
            if (screenX + cullSize < bounds.x() || screenX - cullSize > bounds.x() + bounds.width()
                    || screenY + cullSize < bounds.y() || screenY - cullSize > bounds.y() + bounds.height()) {
                continue;
            }
            projected.add(new ProjectedObject(object, screenX, screenY, halfSize, (float) depth));
        }
        projected.sort(Comparator.comparingDouble(ProjectedObject::depth).reversed());
        return projected;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(0, 0, width, height, 0xFFFFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (!error.isBlank()) {
            graphics.centeredText(font, Component.literal("CraftShop render error: " + error),
                    width / 2, height / 2, 0xFFFF3333);
            return;
        }
        GalleryTextureCache.TextureEntry texture = GalleryTextureCache.get(imagePath);
        if (texture == null) {
            graphics.centeredText(font, Component.literal("CraftShop could not load the selected PNG"),
                    width / 2, height / 2, 0xFFFF3333);
            return;
        }
        CanvasBounds bounds = canvasBounds(width, height, imageWidth, imageHeight);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), bounds.x(), bounds.y(), 0.0F, 0.0F,
                bounds.width(), bounds.height(), texture.width(), texture.height());

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            graphics.centeredText(font, Component.literal("A loaded Minecraft world is required to render entity models"),
                    width / 2, height - 42, 0xFFFF5555);
            drawPreviewHelp(graphics);
            return;
        }
        for (ProjectedObject object : projectObjects(bounds)) {
            SceneAssetRenderer.extract(graphics, object.object(), minecraft,
                    object.x(), object.y(), object.halfSize());
        }
        drawPreviewHelp(graphics);
    }

    private void drawPreviewHelp(GuiGraphicsExtractor graphics) {
        if (parent == null) return;
        graphics.fill(8, height - 31, width - 8, height - 8, 0xAA10151D);
        graphics.text(font, "DRAG: Look  •  WASD: Move  •  SPACE/C: Rise/Lower  •  Scroll: Zoom  •  ESC: Back",
                14, height - 22, 0xFFFFFFFF, false);
    }

    private void moveCamera(float side, float vertical, float forward) {
        if (project.camera == null) project.camera = new SceneProject.Camera();
        SceneProject.Camera camera = project.camera;
        double yaw = Math.toRadians(camera.yaw);
        camera.x += (float) (Math.cos(yaw) * side - Math.sin(yaw) * forward) * 0.35F;
        camera.y += vertical * 0.35F;
        camera.z += (float) (-Math.sin(yaw) * side - Math.cos(yaw) * forward) * 0.35F;
    }

    private void rotateCamera(float yaw, float pitch) {
        if (project.camera == null) project.camera = new SceneProject.Camera();
        project.camera.yaw += yaw;
        project.camera.pitch = Math.max(-89.0F, Math.min(89.0F, project.camera.pitch + pitch));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (parent == null) return super.mouseDragged(event, deltaX, deltaY);
        rotateCamera((float) (deltaX * 0.45F), (float) (deltaY * 0.45F));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (parent == null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (project.camera == null) project.camera = new SceneProject.Camera();
        project.camera.zoom = Math.max(0.2F, Math.min(4.0F,
                project.camera.zoom + (float) scrollY * 0.1F));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (parent != null) {
            int key = event.input();
            switch (key) {
                case 'w' -> moveCamera(0.0F, 0.0F, 1.0F);
                case 's' -> moveCamera(0.0F, 0.0F, -1.0F);
                case 'a' -> moveCamera(-1.0F, 0.0F, 0.0F);
                case 'd' -> moveCamera(1.0F, 0.0F, 0.0F);
                case ' ' -> moveCamera(0.0F, 1.0F, 0.0F);
                case 'c' -> moveCamera(0.0F, -1.0F, 0.0F);
                default -> {
                    return super.keyPressed(event);
                }
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (parent != null) {
            minecraft.gui.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    private record ProjectedObject(SceneProject.SceneObject object, int x, int y, int halfSize, float depth) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
