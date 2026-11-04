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
    private SceneWorldRenderer.Frame sceneFrame;
    private boolean draggingObject;

    public SceneRenderScreen(SceneProject project, Path imagePath) {
        this(project, imagePath, null);
    }

    public SceneRenderScreen(SceneProject project, Screen parent) {
        this(project, null, parent);
    }

    public SceneRenderScreen(SceneProject project, Path imagePath, Screen parent) {
        super(Component.literal(imagePath == null ? "CraftShop 3D Viewport"
                : parent == null ? "CraftShop PNG Render" : "CraftShop Scene Preview"));
        this.project = project;
        this.imagePath = imagePath;
        this.parent = parent;
        if (imagePath != null) {
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

    private CanvasBounds interactionBounds() {
        return imagePath == null
                ? new CanvasBounds(12, 30, Math.max(1, width - 24), Math.max(1, height - 66))
                : canvasBounds(width, height, imageWidth, imageHeight);
    }

    private void rebuildSceneFrame(CanvasBounds bounds) {
        double scale = Math.min(2.0D, 1024.0D / Math.max(1, Math.max(bounds.width(), bounds.height())));
        int renderWidth = Math.max(64, (int) Math.round(bounds.width() * scale));
        int renderHeight = Math.max(64, (int) Math.round(bounds.height() * scale));
        sceneFrame = SceneWorldRenderer.render(project, renderWidth, renderHeight, minecraft, imagePath == null);
    }

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
        // The live viewport deliberately leaves the framebuffer untouched.
        // GameRenderer has already rendered the active ClientLevel through the
        // vanilla LevelRenderer before the screen GUI is extracted.
        if (imagePath == null && parent != null) return;
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(0, 0, width, height, imagePath == null ? 0xFF101820 : 0xFFFFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (!error.isBlank()) {
            graphics.centeredText(font, Component.literal("CraftShop render error: " + error),
                    width / 2, height / 2, 0xFFFF3333);
            return;
        }

        // Native live-world mode: do not approximate Minecraft's world with
        // sampled blocks or the CPU scene rasterizer. The actual level has
        // already been drawn by GameRenderer/LevelRenderer behind this GUI.
        if (imagePath == null && parent != null) {
            boolean inWorld = minecraft != null && minecraft.level != null && minecraft.player != null;
            graphics.fill(0, 0, width, 34, 0xA8101820);
            graphics.fill(0, height - 34, width, height, 0xA8101820);
            graphics.text(font, "CRAFTSHOP  /  NATIVE WORLD VIEW", 12, 10, 0xFF55DDEE, true);
            graphics.text(font, inWorld
                    ? "VANILLA LEVELRENDERER  •  LIVE CHUNKS  •  ENTITIES  •  BLOCK ENTITIES  •  WORLD EFFECTS"
                    : "JOIN A WORLD TO DISPLAY THE NATIVE MINECRAFT RENDERER",
                    12, 21, inWorld ? 0xFFE7F3FC : 0xFFFFC66D, false);
            graphics.text(font, "RIGHT DRAG: LOOK  •  ESC: RETURN TO CRAFTSHOP",
                    12, height - 21, 0xFFFFFFFF, false);
            return;
        }
        CanvasBounds bounds;
        if (imagePath == null) {
            bounds = new CanvasBounds(12, 30, Math.max(1, width - 24), Math.max(1, height - 66));
            graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(),
                    bounds.y() + bounds.height(), 0xFF17232E);
            for (int x = bounds.x(); x < bounds.x() + bounds.width(); x += 32) {
                graphics.fill(x, bounds.y(), x + 1, bounds.y() + bounds.height(), 0xFF22313D);
            }
            for (int y = bounds.y(); y < bounds.y() + bounds.height(); y += 32) {
                graphics.fill(bounds.x(), y, bounds.x() + bounds.width(), y + 1, 0xFF22313D);
            }
            graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0xFF42596A);
            graphics.text(font, "CRAFTSHOP 3D VIEWPORT  •  " + project.objects.size() + " OBJECTS",
                    18, 16, 0xFF55DDEE, true);
        } else {
            GalleryTextureCache.TextureEntry texture = GalleryTextureCache.get(imagePath);
            if (texture == null) {
                graphics.centeredText(font, Component.literal("CraftShop could not load the selected PNG"),
                        width / 2, height / 2, 0xFFFF3333);
                return;
            }
            bounds = canvasBounds(width, height, imageWidth, imageHeight);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), bounds.x(), bounds.y(), 0.0F, 0.0F,
                    bounds.width(), bounds.height(), texture.width(), texture.height());
        }

        if (sceneFrame == null) rebuildSceneFrame(bounds);
        SceneWorldRenderer.draw(graphics, sceneFrame, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        if (minecraft != null && minecraft.level != null) {
            for (ProjectedObject projected : projectObjects(bounds)) {
                if (!"minecraft_entity".equals(projected.object().kind)) continue;
                int sampleX = (int) ((projected.x() - bounds.x()) * sceneFrame.width() / bounds.width());
                int sampleY = (int) ((projected.y() - bounds.y()) * sceneFrame.height() / bounds.height());
                if (!projected.object().id.equals(SceneWorldRenderer.hitTest(sceneFrame, sampleX, sampleY))) {
                    continue;
                }
                SceneAssetRenderer.extract(graphics, projected.object(), minecraft,
                        projected.x(), projected.y(), projected.halfSize());
                if (projected.object().id.equals(project.selectedObjectId)) {
                    int radius = Math.max(7, Math.round(projected.halfSize()
                            * Math.max(0.05F, Math.min(4.0F,
                            (projected.object().transform.scaleX + projected.object().transform.scaleY
                                    + projected.object().transform.scaleZ) / 3.0F))));
                    graphics.outline(projected.x() - radius, projected.y() - radius,
                            radius * 2, radius * 2, 0xFF42E9F4);
                }
            }
        }
        drawPreviewHelp(graphics);
    }

    private void drawPreviewHelp(GuiGraphicsExtractor graphics) {
        if (parent == null) return;
        graphics.fill(8, height - 31, width - 8, height - 8, 0xAA10151D);
        graphics.text(font, "CLICK: Select  •  WASD: Camera  •  LEFT DRAG: Move Object  •  RIGHT DRAG: Look  •  IJKL/UO: Move  •  SHIFT+IJKL/UO: Rotate  •  +/-: Scale  •  ESC: Back",
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
        sceneFrame = null;
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
        if (imagePath == null && parent != null && minecraft != null && minecraft.player != null) {
            // The live viewport uses Minecraft's real camera entity. Updating
            // its view angles keeps the native world, entities and effects in
            // the same renderer and avoids a second, divergent camera model.
            minecraft.player.setYRot(minecraft.player.getYRot() + yaw);
            minecraft.player.setXRot(Math.max(-89.0F,
                    Math.min(89.0F, minecraft.player.getXRot() + pitch)));
            return;
        }
        if (project.camera == null) project.camera = new SceneProject.Camera();
        project.camera.yaw += yaw;
        project.camera.pitch = Math.max(-89.0F, Math.min(89.0F, project.camera.pitch + pitch));
        sceneFrame = null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (imagePath == null && parent != null) return true;
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !isReady()) {
            return super.mouseClicked(event, doubleClick);
        }
        CanvasBounds bounds = interactionBounds();
        if (event.x() >= bounds.x() && event.x() <= bounds.x() + bounds.width()
                && event.y() >= bounds.y() && event.y() <= bounds.y() + bounds.height()) {
            if (sceneFrame == null) rebuildSceneFrame(bounds);
            int localX = (int) ((event.x() - bounds.x()) * sceneFrame.width() / bounds.width());
            int localY = (int) ((event.y() - bounds.y()) * sceneFrame.height() / bounds.height());
            String selected = SceneWorldRenderer.hitTest(sceneFrame, localX, localY);
            boolean editableObject = selected != null && project.objects.stream()
                    .anyMatch(candidate -> selected.equals(candidate.id));
            project.selectedObjectId = editableObject ? selected : "";
            sceneFrame = null;
            SceneProject.SceneObject object = selected == null ? null : project.objects.stream()
                    .filter(candidate -> selected.equals(candidate.id)).findFirst().orElse(null);
            draggingObject = object != null && !object.locked;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (parent == null) return super.mouseDragged(event, deltaX, deltaY);
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingObject) {
            SceneProject.SceneObject selected = project.objects.stream()
                    .filter(object -> project.selectedObjectId.equals(object.id)).findFirst().orElse(null);
            if (selected != null && !selected.locked) {
                CanvasBounds bounds = canvasBounds(width, height, imageWidth, imageHeight);
                SceneProject.Camera camera = project.camera == null ? new SceneProject.Camera() : project.camera;
                double yaw = Math.toRadians(camera.yaw);
                double pitch = Math.toRadians(camera.pitch);
                double depth = Math.max(0.15D,
                        (selected.transform.x - camera.x) * -Math.sin(yaw) * Math.cos(pitch)
                        + (selected.transform.y - camera.y) * -Math.sin(pitch)
                        + (selected.transform.z - camera.z) * -Math.cos(yaw) * Math.cos(pitch));
                double unitsPerPixel = depth / (Math.max(1, Math.min(bounds.width(), bounds.height())) * 0.92D
                        * Math.max(0.2D, Math.min(4.0D, camera.zoom)));
                double worldRightX = Math.cos(yaw);
                double worldRightZ = -Math.sin(yaw);
                double worldUpX = -Math.sin(yaw) * Math.sin(pitch);
                double worldUpY = Math.cos(pitch);
                double worldUpZ = -Math.cos(yaw) * Math.sin(pitch);
                double horizontal = deltaX * unitsPerPixel;
                double vertical = -deltaY * unitsPerPixel;
                selected.transform.x += (float) (worldRightX * horizontal + worldUpX * vertical);
                selected.transform.y += (float) (worldUpY * vertical);
                selected.transform.z += (float) (worldRightZ * horizontal + worldUpZ * vertical);
                sceneFrame = null;
            }
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            rotateCamera((float) (deltaX * 0.45F), (float) (deltaY * 0.45F));
            sceneFrame = null;
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) draggingObject = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (parent == null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (project.camera == null) project.camera = new SceneProject.Camera();
        project.camera.zoom = Math.max(0.2F, Math.min(4.0F,
                project.camera.zoom + (float) scrollY * 0.1F));
        sceneFrame = null;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (parent != null && event.hasControlDown() && event.input() == GLFW.GLFW_KEY_D) {
            SceneProject.SceneObject source = project.objects.stream()
                    .filter(object -> project.selectedObjectId.equals(object.id)).findFirst().orElse(null);
            if (source != null) {
                SceneProject.SceneObject copy = new SceneProject.SceneObject();
                copy.id = java.util.UUID.randomUUID().toString();
                copy.name = source.name + " Copy";
                copy.kind = source.kind;
                copy.assetId = source.assetId;
                copy.blockProperties = source.blockProperties == null ? new java.util.LinkedHashMap<>()
                        : new java.util.LinkedHashMap<>(source.blockProperties);
                copy.visible = source.visible;
                copy.transform.x = source.transform.x + 0.5F;
                copy.transform.y = source.transform.y;
                copy.transform.z = source.transform.z;
                copy.transform.rotationX = source.transform.rotationX;
                copy.transform.rotationY = source.transform.rotationY;
                copy.transform.rotationZ = source.transform.rotationZ;
                copy.transform.scaleX = source.transform.scaleX;
                copy.transform.scaleY = source.transform.scaleY;
                copy.transform.scaleZ = source.transform.scaleZ;
                project.objects.add(copy);
                project.selectedObjectId = copy.id;
                sceneFrame = null;
                return true;
            }
        }
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
                sceneFrame = null;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        SceneAssetRenderer.clear();
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
