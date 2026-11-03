package dev.modsfinder.craftshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

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
            if (object.object().id.equals(project.selectedObjectId)) {
                int outlineSize = Math.max(8, object.halfSize() + 5);
                graphics.outline(object.x() - outlineSize, object.y() - outlineSize,
                        outlineSize * 2, outlineSize * 2, 0xFF43E8F5);
                graphics.fill(object.x() - 2, object.y() - outlineSize - 5,
                        object.x() + 3, object.y() - outlineSize, 0xFF43E8F5);
            }
        }
        drawPreviewHelp(graphics);
    }

    private void drawPreviewHelp(GuiGraphicsExtractor graphics) {
        if (parent == null) return;
        graphics.fill(8, height - 31, width - 8, height - 8, 0xAA10151D);
        graphics.text(font, "CLICK: Select  •  WASD: Camera  •  IJKL/UO: Move Object  •  SHIFT+IJKL/UO: Rotate  •  +/-: Scale  •  DRAG: Look  •  ESC: Back",
                14, height - 22, 0xFFFFFFFF, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (parent == null || minecraft == null) return;

        var options = minecraft.options;
        float side = (options.keyRight.isDown() ? 1.0F : 0.0F)
                - (options.keyLeft.isDown() ? 1.0F : 0.0F);
        float forward = (options.keyUp.isDown() ? 1.0F : 0.0F)
                - (options.keyDown.isDown() ? 1.0F : 0.0F);
        float vertical = (options.keyJump.isDown() ? 1.0F : 0.0F)
                - (options.keyShift.isDown() ? 1.0F : 0.0F);

        float horizontalLength = (float) Math.sqrt(side * side + forward * forward);
        if (horizontalLength > 1.0F) {
            side /= horizontalLength;
            forward /= horizontalLength;
        }
        if (side == 0.0F && forward == 0.0F && vertical == 0.0F) return;

        float speed = options.keySprint.isDown() ? 0.70F : 0.22F;
        if (project.camera == null) project.camera = new SceneProject.Camera();
        SceneProject.Camera camera = project.camera;
        double yaw = Math.toRadians(camera.yaw);
        camera.x += (float) (Math.cos(yaw) * side - Math.sin(yaw) * forward) * speed;
        camera.y += vertical * speed;
        camera.z += (float) (-Math.sin(yaw) * side - Math.cos(yaw) * forward) * speed;
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (parent == null || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !isReady()) {
            return super.mouseClicked(event, doubleClick);
        }
        CanvasBounds bounds = canvasBounds(width, height, imageWidth, imageHeight);
        List<ProjectedObject> projected = projectObjects(bounds);
        ProjectedObject hit = null;
        double bestDistance = Double.MAX_VALUE;
        for (int i = projected.size() - 1; i >= 0; i--) {
            ProjectedObject candidate = projected.get(i);
            double dx = event.x() - candidate.x();
            double dy = event.y() - candidate.y();
            double radius = Math.max(10.0, candidate.halfSize() + 7.0);
            double distance = dx * dx + dy * dy;
            if (distance <= radius * radius && distance < bestDistance) {
                hit = candidate;
                bestDistance = distance;
            }
        }
        if (hit != null) {
            project.selectedObjectId = hit.object().id;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
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
        if (parent != null && project.selectedObjectId != null && !project.selectedObjectId.isBlank()) {
            SceneProject.SceneObject selected = project.objects.stream()
                    .filter(object -> project.selectedObjectId.equals(object.id))
                    .findFirst().orElse(null);
            if (selected != null && !selected.locked) {
                int key = event.input();
                boolean rotate = event.hasShiftDown();
                float step = 0.25F;
                float angle = 15.0F;
                SceneProject.Transform transform = selected.transform;
                switch (key) {
                    case GLFW.GLFW_KEY_J -> { if (rotate) transform.rotationY -= angle; else transform.x -= step; }
                    case GLFW.GLFW_KEY_L -> { if (rotate) transform.rotationY += angle; else transform.x += step; }
                    case GLFW.GLFW_KEY_I -> { if (rotate) transform.rotationX -= angle; else transform.z -= step; }
                    case GLFW.GLFW_KEY_K -> { if (rotate) transform.rotationX += angle; else transform.z += step; }
                    case GLFW.GLFW_KEY_U -> { if (rotate) transform.rotationZ -= angle; else transform.y += step; }
                    case GLFW.GLFW_KEY_O -> { if (rotate) transform.rotationZ += angle; else transform.y -= step; }
                    case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
                        transform.scaleX = Math.min(20.0F, transform.scaleX * 1.1F);
                        transform.scaleY = Math.min(20.0F, transform.scaleY * 1.1F);
                        transform.scaleZ = Math.min(20.0F, transform.scaleZ * 1.1F);
                    }
                    case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
                        transform.scaleX = Math.max(0.05F, transform.scaleX * 0.9F);
                        transform.scaleY = Math.max(0.05F, transform.scaleY * 0.9F);
                        transform.scaleZ = Math.max(0.05F, transform.scaleZ * 0.9F);
                    }
                    case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
                        project.objects.remove(selected);
                        SceneAssetRenderer.forget(selected.id);
                        project.selectedObjectId = "";
                    }
                    default -> { return super.keyPressed(event); }
                }
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (parent != null) {
            try {
                ProjectManager.save(project);
            } catch (java.io.IOException exception) {
                CraftShop.LOGGER.error("Could not save scene camera after preview", exception);
            }
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
