package dev.modsfinder.craftshop;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class StudioScreen extends Screen {
    private final Screen parent;
    private SceneProject project = new SceneProject();
    private String activeTool = "Select";
    private String status = "";
    private EditBox projectNameField;

    public StudioScreen(Screen parent) {
        super(Component.literal("CraftShop 3D Studio"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = 16;
        int top = 48;

        addRenderableWidget(Button.builder(Component.literal("Select"), button -> activeTool = "Select")
                .bounds(left, top, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Move"), button -> activeTool = "Move")
                .bounds(left, top + 25, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Rotate"), button -> activeTool = "Rotate")
                .bounds(left, top + 50, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Scale"), button -> activeTool = "Scale")
                .bounds(left, top + 75, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Orbit"), button -> activeTool = "Orbit")
                .bounds(left, top + 100, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Pan"), button -> activeTool = "Pan")
                .bounds(left, top + 125, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Zoom"), button -> activeTool = "Zoom")
                .bounds(left, top + 150, 68, 20).build());

        addRenderableWidget(Button.builder(Component.literal("New"), button -> newProject())
                .bounds(174, 12, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Open"), button -> openProjectBrowser())
                .bounds(226, 12, 52, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> saveProject())
                .bounds(282, 12, 52, 20).build());

        projectNameField = new EditBox(this.font, 340, 12, 150, 20, Component.literal("Project name"));
        projectNameField.setMaxLength(64);
        projectNameField.setValue(project.name);
        addRenderableWidget(projectNameField);

        addRenderableWidget(Button.builder(Component.literal("Back to Gallery"), button -> returnToParent())
                .bounds(this.width - 126, 12, 110, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(8, 8, this.width - 8, this.height - 8, 0xF014171E);

        int top = 42;
        int bottom = this.height - 58;
        int leftPanelRight = 94;
        int rightPanelLeft = this.width - 214;
        int viewportLeft = 104;
        int viewportRight = rightPanelLeft - 10;
        graphics.fill(14, top, leftPanelRight, bottom, 0xFF202630);
        graphics.fill(viewportLeft, top, viewportRight, bottom, 0xFF11151C);
        graphics.fill(rightPanelLeft, top, this.width - 14, bottom, 0xFF202630);
        graphics.fill(14, bottom + 8, this.width - 14, this.height - 16, 0xFF202630);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.text(this.font, "CRAFTSHOP", 18, 14, 0xFFFFFFFF, true);
        graphics.text(this.font, "3D STUDIO", 110, 14, 0xFF55DDEE, false);
        if (!status.isEmpty()) graphics.text(this.font, status, 500, 18, 0xFF9DDDAA, false);

        int top = 42;
        int bottom = this.height - 58;
        int leftPanelRight = 94;
        int rightPanelLeft = this.width - 214;
        int viewportLeft = 104;
        int viewportRight = rightPanelLeft - 10;

        graphics.text(this.font, "TOOLS", 25, 31, 0xFF9AA8B8, false);
        graphics.text(this.font, "OUTLINER", rightPanelLeft + 12, 52, 0xFF55DDEE, true);
        graphics.horizontalLine(rightPanelLeft + 10, this.width - 24, 69, 0xFF465363);
        graphics.text(this.font, "Scene", rightPanelLeft + 14, 82, 0xFFFFFFFF, false);
        graphics.text(this.font, "  Collection", rightPanelLeft + 14, 101, 0xFFB5C0CD, false);
        graphics.text(this.font, "    Camera", rightPanelLeft + 14, 120, 0xFFB5C0CD, false);
        graphics.text(this.font, "PROPERTIES", rightPanelLeft + 12, 153, 0xFF55DDEE, true);
        graphics.horizontalLine(rightPanelLeft + 10, this.width - 24, 170, 0xFF465363);
        graphics.text(this.font, "Active tool: " + activeTool, rightPanelLeft + 12, 184, 0xFFFFFFFF, false);
        graphics.text(this.font, "Objects: " + project.objects.size(), rightPanelLeft + 12, 201, 0xFF8995A4, false);
        graphics.text(this.font, "LOCATION", rightPanelLeft + 12, 224, 0xFFB5C0CD, false);
        graphics.text(this.font, "X: " + project.camera.x + "  Y: " + project.camera.y,
                rightPanelLeft + 12, 240, 0xFF8995A4, false);
        graphics.text(this.font, "Z: " + project.camera.z + "  Zoom: " + project.camera.zoom,
                rightPanelLeft + 12, 256, 0xFF8995A4, false);
        graphics.text(this.font, "ROTATION   /   SCALE", rightPanelLeft + 12, 278, 0xFFB5C0CD, false);

        graphics.text(this.font, "3D VIEWPORT", viewportLeft + 12, top + 10, 0xFF8B9AAC, false);
        int centerX = (viewportLeft + viewportRight) / 2;
        int centerY = (top + bottom) / 2;
        for (int x = viewportLeft + 20; x < viewportRight - 10; x += 24) {
            graphics.verticalLine(x, top + 30, bottom - 12, 0x222F3A49);
        }
        for (int y = top + 30; y < bottom - 12; y += 24) {
            graphics.horizontalLine(viewportLeft + 12, viewportRight - 12, y, 0x222F3A49);
        }
        graphics.horizontalLine(centerX - 70, centerX + 70, centerY, 0xAA55AAAA);
        graphics.verticalLine(centerX, centerY - 70, centerY + 70, 0xAA55AAAA);
        graphics.centeredText(this.font, "3D scene viewport", centerX, centerY - 10, 0xFFFFFFFF);
        graphics.centeredText(this.font, "Viewport rendering and object manipulation are next.", centerX,
                centerY + 10, 0xFF9AA8B8);

        graphics.text(this.font, "TIMELINE", 24, bottom + 20, 0xFF55DDEE, true);
        graphics.text(this.font, "Animation tracks and keyframes will be added with the entity workflow.",
                24, bottom + 39, 0xFF9AA8B8, false);
        graphics.text(this.font, "Interface prototype • no world blocks or entities are modified.",
                18, this.height - 28, 0xFF8995A4, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int viewportLeft = 104;
        int viewportRight = this.width - 224;
        int viewportTop = 42;
        int viewportBottom = this.height - 58;

        if (mouseX >= viewportLeft && mouseX <= viewportRight
                && mouseY >= viewportTop && mouseY <= viewportBottom) {
            float factor = (float) Math.pow(1.12, verticalAmount);
            project.camera.zoom = Math.max(0.2F, Math.min(4.0F, project.camera.zoom * factor));
            status = "Viewport zoom: " + String.format(java.util.Locale.ROOT, "%.2f", project.camera.zoom) + "x";
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void newProject() {
        project = new SceneProject();
        activeTool = "Select";
        status = "New scene created";
        if (projectNameField != null) projectNameField.setValue(project.name);
    }

    private void saveProject() {
        if (projectNameField != null) project.name = projectNameField.getValue();
        try {
            ProjectManager.save(project);
            status = "Project saved";
        } catch (java.io.IOException exception) {
            CraftShop.LOGGER.error("Could not save CraftShop project", exception);
            status = "Save failed";
        }
    }

    private void openProjectBrowser() {
        this.minecraft.gui.setScreen(new ProjectBrowserScreen(this, loaded -> {
            project = loaded;
            if (projectNameField != null) projectNameField.setValue(project.name);
            status = "Project loaded";
        }));
    }

    private void returnToParent() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
