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
    private String status = "Ready";
    private EditBox projectNameField;

    public StudioScreen(Screen parent) {
        super(Component.literal("CraftShop 3D Studio"));
        this.parent = parent;
    }

    private int leftPanelWidth() {
        return 82;
    }

    private int rightPanelWidth() {
        return Math.max(174, Math.min(214, this.width / 4));
    }

    private int rightPanelLeft() {
        return this.width - rightPanelWidth() - 12;
    }

    private int timelineTop() {
        return this.height - 132;
    }

    private int viewportLeft() {
        return leftPanelWidth() + 14;
    }

    private int viewportRight() {
        return rightPanelLeft() - 10;
    }

    @Override
    protected void init() {
        int left = 16;
        int top = 76;

        addRenderableWidget(toolButton("Select", left, top));
        addRenderableWidget(toolButton("Move", left, top + 25));
        addRenderableWidget(toolButton("Rotate", left, top + 50));
        addRenderableWidget(toolButton("Scale", left, top + 75));
        addRenderableWidget(toolButton("Orbit", left, top + 100));
        addRenderableWidget(toolButton("Pan", left, top + 125));
        addRenderableWidget(toolButton("Zoom", left, top + 150));

        addRenderableWidget(Button.builder(Component.literal("New"), button -> newProject())
                .bounds(174, 12, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Open"), button -> openProjectBrowser())
                .bounds(226, 12, 52, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> saveProject())
                .bounds(282, 12, 52, 20).build());

        projectNameField = new EditBox(this.font, 340, 12, 142, 20, Component.literal("Project name"));
        projectNameField.setMaxLength(64);
        projectNameField.setValue(project.name);
        addRenderableWidget(projectNameField);

        addRenderableWidget(Button.builder(Component.literal("Gallery"), button -> returnToParent())
                .bounds(this.width - 82, 12, 68, 20).build());
    }

    private Button toolButton(String tool, int x, int y) {
        return Button.builder(Component.literal((activeTool.equals(tool) ? "> " : "") + tool),
                button -> {
                    activeTool = tool;
                    status = tool + " tool selected";
                }).bounds(x, y, 66, 20).build();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);

        graphics.fill(0, 0, this.width, this.height, 0xFF101319);
        graphics.fill(8, 8, this.width - 8, 36, 0xFF1D232D);
        graphics.fill(8, 40, this.width - 8, 58, 0xFF171C24);

        int viewportTop = 66;
        int viewportBottom = timelineTop() - 8;
        int left = viewportLeft();
        int right = viewportRight();
        int rightPanel = rightPanelLeft();

        graphics.fill(10, viewportTop, 10 + leftPanelWidth(), viewportBottom, 0xFF202630);
        graphics.fill(left, viewportTop, right, viewportBottom, 0xFF111720);
        graphics.fill(rightPanel, viewportTop, this.width - 10, viewportBottom, 0xFF202630);
        graphics.fill(10, timelineTop(), this.width - 10, this.height - 10, 0xFF1B212A);

        graphics.outline(left, viewportTop, right - left, viewportBottom - viewportTop, 0xFF394655);
        graphics.outline(rightPanel, viewportTop, this.width - rightPanel - 10,
                viewportBottom - viewportTop, 0xFF394655);
        graphics.outline(10, timelineTop(), this.width - 20, this.height - timelineTop() - 10, 0xFF394655);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int viewportTop = 66;
        int viewportBottom = timelineTop() - 8;
        int left = viewportLeft();
        int right = viewportRight();
        int rightPanel = rightPanelLeft();
        int centerX = (left + right) / 2;
        int centerY = (viewportTop + viewportBottom) / 2;

        // Header and menu strip
        graphics.text(this.font, "CRAFTSHOP", 18, 14, 0xFFFFFFFF, true);
        graphics.text(this.font, "3D STUDIO", 104, 14, 0xFF55DDEE, true);
        graphics.text(this.font, "File    Edit    View    Add    Object    Render    Help",
                18, 46, 0xFFB9C4D1, false);
        graphics.text(this.font, status, Math.max(490, this.width - 250), 19, 0xFF9DDDAA, false);

        // Tool shelf
        graphics.text(this.font, "TOOL SHELF", 18, 68, 0xFF9AA8B8, false);
        graphics.text(this.font, "TRANSFORM", 18, 238, 0xFF6F8194, false);
        graphics.text(this.font, "Active: " + activeTool, 18, 255, 0xFFB5C0CD, false);

        // Viewport header and grid
        graphics.fill(left + 1, viewportTop + 1, right - 1, viewportTop + 20, 0xFF1C2530);
        graphics.text(this.font, "VIEWPORT  /  PERSPECTIVE", left + 10, viewportTop + 7, 0xFF9EADBD, false);
        graphics.text(this.font, "SOLID", Math.max(left + 150, right - 52), viewportTop + 7, 0xFF55DDEE, false);

        int gridStartY = viewportTop + 28;
        int gridEndY = viewportBottom - 12;
        for (int x = left + 18; x < right - 8; x += 24) {
            graphics.verticalLine(x, gridStartY, gridEndY, 0x223B4A5B);
        }
        for (int y = gridStartY; y < gridEndY; y += 24) {
            graphics.horizontalLine(left + 8, right - 8, y, 0x223B4A5B);
        }

        // World origin and orientation gizmo
        graphics.horizontalLine(centerX - 78, centerX + 78, centerY, 0xAA4C8ED8);
        graphics.verticalLine(centerX, centerY - 62, centerY + 62, 0xAA55B879);
        graphics.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, 0xFFFFFFFF);
        graphics.text(this.font, "X", centerX + 82, centerY - 4, 0xFF6FA8FF, true);
        graphics.text(this.font, "Y", centerX + 5, centerY - 70, 0xFF70D58E, true);
        graphics.text(this.font, "Z", centerX - 90, centerY + 7, 0xFFE28A72, true);
        graphics.centeredText(this.font, "3D VIEWPORT", centerX, centerY + 76, 0xFF8495A8);
        graphics.centeredText(this.font, "Scene renderer is being built in Milestone 3.",
                centerX, centerY + 91, 0xFF68798B);

        // Outliner
        graphics.text(this.font, "OUTLINER", rightPanel + 10, viewportTop + 9, 0xFF55DDEE, true);
        graphics.horizontalLine(rightPanel + 8, this.width - 18, viewportTop + 27, 0xFF465363);
        graphics.text(this.font, "v  Scene", rightPanel + 12, viewportTop + 40, 0xFFFFFFFF, false);
        graphics.text(this.font, "   v  Collection", rightPanel + 12, viewportTop + 59, 0xFFB5C0CD, false);
        graphics.text(this.font, "      Camera", rightPanel + 12, viewportTop + 78, 0xFF9FB2C6, false);
        graphics.text(this.font, "      Objects (" + project.objects.size() + ")",
                rightPanel + 12, viewportTop + 97, 0xFF9FB2C6, false);

        int propertiesTop = viewportTop + 132;
        graphics.horizontalLine(rightPanel + 8, this.width - 18, propertiesTop, 0xFF465363);
        graphics.text(this.font, "PROPERTIES", rightPanel + 10, propertiesTop + 10, 0xFF55DDEE, true);
        graphics.text(this.font, "SCENE", rightPanel + 10, propertiesTop + 31, 0xFFB5C0CD, false);
        graphics.text(this.font, "Active tool: " + activeTool, rightPanel + 10, propertiesTop + 50,
                0xFF8995A4, false);
        graphics.text(this.font, "CAMERA", rightPanel + 10, propertiesTop + 75, 0xFFB5C0CD, false);
        graphics.text(this.font, "X  " + format(project.camera.x) + "   Y  " + format(project.camera.y),
                rightPanel + 10, propertiesTop + 94, 0xFF8995A4, false);
        graphics.text(this.font, "Z  " + format(project.camera.z) + "   Zoom  " + format(project.camera.zoom),
                rightPanel + 10, propertiesTop + 110, 0xFF8995A4, false);
        graphics.text(this.font, "Object transforms will appear here.",
                rightPanel + 10, propertiesTop + 138, 0xFF68798B, false);

        // Timeline
        int timelineLeft = 20;
        int timelineRight = this.width - 20;
        graphics.text(this.font, "TIMELINE", timelineLeft, timelineTop() + 10, 0xFF55DDEE, true);
        graphics.text(this.font, "FRAME", timelineLeft, timelineTop() + 31, 0xFF8D9BAB, false);
        int rulerY = timelineTop() + 48;
        graphics.horizontalLine(timelineLeft + 52, timelineRight - 8, rulerY, 0xFF596879);
        int rulerWidth = Math.max(1, timelineRight - timelineLeft - 70);
        for (int frame = 0; frame <= 100; frame += 5) {
            int x = timelineLeft + 54 + (rulerWidth * frame / 100);
            int tickHeight = frame % 10 == 0 ? 10 : 5;
            graphics.verticalLine(x, rulerY - tickHeight / 2, rulerY + tickHeight / 2, 0xFF8797A8);
            if (frame % 10 == 0) {
                graphics.text(this.font, Integer.toString(frame), x - 5, rulerY + 9, 0xFF8797A8, false);
            }
        }
        graphics.text(this.font, "Camera", timelineLeft + 4, timelineTop() + 78, 0xFFB5C0CD, false);
        graphics.fill(timelineLeft + 54, timelineTop() + 73, timelineRight - 8, timelineTop() + 88, 0xFF293746);
        graphics.text(this.font, "No keyframes yet", timelineLeft + 62, timelineTop() + 76, 0xFF718397, false);

        // Footer status bar
        graphics.fill(0, this.height - 8, this.width, this.height, 0xFF0B0E13);
        graphics.text(this.font, "CRAFTSHOP  •  " + project.name + "  •  " + project.objects.size() + " objects",
                10, this.height - 7, 0xFF7F8D9C, false);
        graphics.text(this.font, "Client-side scene • world remains unchanged",
                Math.max(260, this.width - 260), this.height - 7, 0xFF7F8D9C, false);
    }

    private static String format(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= viewportLeft() && mouseX <= viewportRight()
                && mouseY >= 66 && mouseY <= timelineTop() - 8) {
            float factor = (float) Math.pow(1.12, verticalAmount);
            project.camera.zoom = Math.max(0.2F, Math.min(4.0F, project.camera.zoom * factor));
            status = "Viewport zoom: " + format(project.camera.zoom) + "x";
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
