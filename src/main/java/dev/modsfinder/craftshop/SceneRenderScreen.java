package dev.modsfinder.craftshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SceneRenderScreen extends Screen {
    private final SceneProject project;
    private final Path imagePath;
    private int imageWidth = 1;
    private int imageHeight = 1;
    private String error = "";

    public SceneRenderScreen(SceneProject project, Path imagePath) {
        super(Component.literal("CraftShop PNG Render"));
        this.project = project;
        this.imagePath = imagePath;
        try {
            var image = javax.imageio.ImageIO.read(imagePath.toFile());
            if (image == null) throw new java.io.IOException("PNG could not be decoded");
            imageWidth = image.getWidth();
            imageHeight = image.getHeight();
            image.flush();
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
        double upX = sinYaw * sinPitch;
        double upY = cosPitch;
        double upZ = cosYaw * sinPitch;

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
            int halfSize = Math.max(5, Math.min(Math.min(bounds.width(), bounds.height()) / 3,
                    Math.round((float) (focal * 0.48F * zoom / depth))));
            if (screenX + halfSize < bounds.x() || screenX - halfSize > bounds.x() + bounds.width()
                    || screenY + halfSize < bounds.y() || screenY - halfSize > bounds.y() + bounds.height()) {
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
                    width / 2, height - 16, 0xFFFF5555);
            return;
        }
        for (ProjectedObject object : projectObjects(bounds)) {
            SceneAssetRenderer.extract(graphics, object.object(), minecraft,
                    object.x(), object.y(), object.halfSize());
        }
    }

    private record ProjectedObject(SceneProject.SceneObject object, int x, int y, int halfSize, float depth) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
