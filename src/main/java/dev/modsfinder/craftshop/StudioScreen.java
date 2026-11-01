package dev.modsfinder.craftshop;

import com.google.gson.Gson;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public final class StudioScreen extends Screen {
    private static final Gson HISTORY_GSON = new Gson();
    private static final int HISTORY_LIMIT = 80;

    private final Screen parent;
    private final Deque<String> undoHistory = new ArrayDeque<>();
    private final Deque<String> redoHistory = new ArrayDeque<>();

    private SceneProject project = new SceneProject();
    private String activeTool = "Select";
    private String status = "Ready";
    private String selectedObjectId;
    private EditBox projectNameField;
    private boolean draggingViewport;
    private boolean dragChanged;

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

    private SceneProject.SceneObject selectedObject() {
        if (selectedObjectId == null || project.objects == null) return null;
        return project.objects.stream().filter(object -> selectedObjectId.equals(object.id)).findFirst().orElse(null);
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
        addRenderableWidget(Button.builder(Component.literal("Add Block"), button -> openAssetPicker(false))
                .bounds(left, top + 178, 66, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Add Entity"), button -> openAssetPicker(true))
                .bounds(left, top + 202, 66, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Export PNG"), button -> exportViewport())
                .bounds(left, top + 226, 66, 20).build());

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

        addRenderableWidget(Button.builder(Component.literal("Add Cube"), button -> addCube())
                .bounds(490, 12, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Duplicate"), button -> duplicateSelected())
                .bounds(562, 12, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), button -> deleteSelected())
                .bounds(634, 12, 58, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Gallery"), button -> returnToParent())
                .bounds(this.width - 82, 12, 68, 20).build());

        int row = 0;
        for (SceneProject.SceneObject object : project.objects) {
            if (row >= 4) break;
            final String id = object.id;
            int y = 66 + 40 + row * 19;
            addRenderableWidget(Button.builder(Component.literal(
                    (id.equals(selectedObjectId) ? "> " : "   ") + trim(object.name, 18)
            ), button -> {
                selectedObjectId = id;
                status = "Selected " + object.name;
                rebuildWidgets();
            }).bounds(rightPanelLeft() + 8, y, rightPanelWidth() - 16, 18).build());
            row++;
        }
    }

    private Button toolButton(String tool, int x, int y) {
        return Button.builder(Component.literal((activeTool.equals(tool) ? "> " : "") + tool),
                button -> {
                    activeTool = tool;
                    status = tool + " tool selected";
                    rebuildWidgets();
                }).bounds(x, y, 66, 20).build();
    }

    private void rebuildWidgets() {
        clearWidgets();
        init();
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

        graphics.text(this.font, "CRAFTSHOP", 18, 14, 0xFFFFFFFF, true);
        graphics.text(this.font, "3D STUDIO", 104, 14, 0xFF55DDEE, true);
        graphics.text(this.font, "File    Edit    View    Add    Object    Render    Help",
                18, 46, 0xFFB9C4D1, false);
        graphics.text(this.font, trim(status, 28), Math.max(320, this.width - 190), 46, 0xFF9DDDAA, false);

        graphics.text(this.font, "TOOL SHELF", 18, 68, 0xFF9AA8B8, false);
        graphics.text(this.font, "TRANSFORM", 18, 238, 0xFF6F8194, false);
        graphics.text(this.font, "Active: " + activeTool, 18, 255, 0xFFB5C0CD, false);

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

        graphics.horizontalLine(centerX - 78, centerX + 78, centerY, 0xAA4C8ED8);
        graphics.verticalLine(centerX, centerY - 62, centerY + 62, 0xAA55B879);
        graphics.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, 0xFFFFFFFF);
        graphics.text(this.font, "X", centerX + 82, centerY - 4, 0xFF6FA8FF, true);
        graphics.text(this.font, "Y", centerX + 5, centerY - 70, 0xFF70D58E, true);
        graphics.text(this.font, "Z", centerX - 90, centerY + 7, 0xFFE28A72, true);

        for (SceneProject.SceneObject object : project.objects) {
            if (!object.visible) continue;
            int[] screen = projectToScreen(object.transform.x, object.transform.y, object.transform.z);
            int x = screen[0];
            int y = screen[1];
            if (x < left + 8 || x > right - 8 || y < viewportTop + 24 || y > viewportBottom - 8) continue;

            boolean selected = object.id.equals(selectedObjectId);
            int markerColor = selected ? 0xFFFFD166 : 0xFF55DDEE;
            drawWireCube(graphics, object, selected ? 0xFFFFD166 : 0xFF7B91A8);
            SceneAssetRenderer.extract(graphics, object, this.minecraft, x, y, 18);
            graphics.fill(x - 3, y - 3, x + 4, y + 4, markerColor);
            if (selected) graphics.outline(x - 7, y - 7, 14, 14, 0xFFFFFFFF);
            graphics.text(this.font, object.name, x + 13, y - 4, selected ? 0xFFFFD166 : 0xFFD0D9E2, false);
        }

        if (project.objects.isEmpty()) {
            graphics.centeredText(this.font, "Empty scene", centerX, centerY + 76, 0xFF8495A8);
            graphics.centeredText(this.font, "Use Add Cube to create a transformable 3D object.",
                    centerX, centerY + 91, 0xFF68798B);
        }

        graphics.text(this.font, "OUTLINER", rightPanel + 10, viewportTop + 9, 0xFF55DDEE, true);
        graphics.horizontalLine(rightPanel + 8, this.width - 18, viewportTop + 27, 0xFF465363);
        graphics.text(this.font, "v  Scene", rightPanel + 12, viewportTop + 40, 0xFFFFFFFF, false);
        graphics.text(this.font, "   v  Collection", rightPanel + 12, viewportTop + 59, 0xFFB5C0CD, false);
        if (project.objects.isEmpty()) {
            graphics.text(this.font, "      (empty)", rightPanel + 12, viewportTop + 82, 0xFF718397, false);
        }

        int propertiesTop = viewportTop + 122;
        graphics.horizontalLine(rightPanel + 8, this.width - 18, propertiesTop, 0xFF465363);
        graphics.text(this.font, "PROPERTIES", rightPanel + 10, propertiesTop + 10, 0xFF55DDEE, true);
        SceneProject.SceneObject selected = selectedObject();
        if (selected == null) {
            graphics.text(this.font, "No object selected", rightPanel + 10, propertiesTop + 34, 0xFF8995A4, false);
            graphics.text(this.font, "Select an item in the", rightPanel + 10, propertiesTop + 52, 0xFF68798B, false);
            graphics.text(this.font, "viewport or Outliner.", rightPanel + 10, propertiesTop + 66, 0xFF68798B, false);
        } else {
            graphics.text(this.font, trim(selected.name, 24), rightPanel + 10, propertiesTop + 32, 0xFFFFFFFF, false);
            graphics.text(this.font, "LOC  " + format(selected.transform.x) + " / "
                    + format(selected.transform.y) + " / " + format(selected.transform.z),
                    rightPanel + 10, propertiesTop + 54, 0xFF8995A4, false);
            graphics.text(this.font, "ROT  " + format(selected.transform.rotationX) + " / "
                    + format(selected.transform.rotationY), rightPanel + 10, propertiesTop + 73, 0xFF8995A4, false);
            graphics.text(this.font, "SCL  " + format(selected.transform.scaleX) + " / "
                    + format(selected.transform.scaleY) + " / " + format(selected.transform.scaleZ),
                    rightPanel + 10, propertiesTop + 92, 0xFF8995A4, false);
        }

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
        graphics.text(this.font, selected == null ? "Scene" : selected.name,
                timelineLeft + 4, timelineTop() + 78, 0xFFB5C0CD, false);
        graphics.fill(timelineLeft + 54, timelineTop() + 73, timelineRight - 8, timelineTop() + 88, 0xFF293746);
        graphics.text(this.font, "Keyframes will be added in the animation workflow.",
                timelineLeft + 62, timelineTop() + 76, 0xFF718397, false);

        graphics.fill(0, this.height - 8, this.width, this.height, 0xFF0B0E13);
        graphics.text(this.font, "CRAFTSHOP  •  " + project.name + "  •  " + project.objects.size() + " objects",
                10, this.height - 7, 0xFF7F8D9C, false);
        graphics.text(this.font, "G Move  R Rotate  S Scale  Ctrl+Z Undo  Ctrl+Y Redo",
                Math.max(260, this.width - 310), this.height - 7, 0xFF7F8D9C, false);
    }

    private void drawWireCube(GuiGraphicsExtractor graphics, SceneProject.SceneObject object, int color) {
        float half = 0.5F;
        float[][] local = {
                {-half, -half, -half}, {half, -half, -half},
                {half, half, -half}, {-half, half, -half},
                {-half, -half, half}, {half, -half, half},
                {half, half, half}, {-half, half, half}
        };
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        int[][] projected = new int[8][];
        for (int i = 0; i < local.length; i++) {
            projected[i] = projectObjectPoint(object, local[i][0], local[i][1], local[i][2]);
        }
        for (int[] edge : edges) {
            drawLine(graphics, projected[edge[0]][0], projected[edge[0]][1],
                    projected[edge[1]][0], projected[edge[1]][1], color);
        }
    }

    private int[] projectObjectPoint(SceneProject.SceneObject object, float localX, float localY, float localZ) {
        SceneProject.Transform transform = object.transform;
        double x = localX * transform.scaleX;
        double y = localY * transform.scaleY;
        double z = localZ * transform.scaleZ;

        double rx = Math.toRadians(transform.rotationX);
        double ry = Math.toRadians(transform.rotationY);
        double rz = Math.toRadians(transform.rotationZ);

        double y1 = y * Math.cos(rx) - z * Math.sin(rx);
        double z1 = y * Math.sin(rx) + z * Math.cos(rx);
        double x2 = x * Math.cos(ry) + z1 * Math.sin(ry);
        double z2 = -x * Math.sin(ry) + z1 * Math.cos(ry);
        double x3 = x2 * Math.cos(rz) - y1 * Math.sin(rz);
        double y3 = x2 * Math.sin(rz) + y1 * Math.cos(rz);

        return projectToScreen(transform.x + (float) x3, transform.y + (float) y3,
                transform.z + (float) z2);
    }

    private static void drawLine(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) {
            graphics.fill(x1, y1, x1 + 2, y1 + 2, color);
            return;
        }
        for (int step = 0; step <= steps; step++) {
            float progress = (float) step / steps;
            int x = Math.round(x1 + (x2 - x1) * progress);
            int y = Math.round(y1 + (y2 - y1) * progress);
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    private int[] projectToScreen(float worldX, float worldY, float worldZ) {
        SceneProject.Camera camera = project.camera;
        double yaw = Math.toRadians(camera.yaw);
        double pitch = Math.toRadians(camera.pitch);
        double dx = worldX - camera.targetX;
        double dy = worldY - camera.targetY;
        double dz = worldZ - camera.targetZ;

        double rotatedX = dx * Math.cos(yaw) - dz * Math.sin(yaw);
        double rotatedZ = dx * Math.sin(yaw) + dz * Math.cos(yaw);
        double rotatedY = dy * Math.cos(pitch) - rotatedZ * Math.sin(pitch);
        double depth = dy * Math.sin(pitch) + rotatedZ * Math.cos(pitch);
        double perspective = 320.0 / Math.max(80.0, 320.0 + depth * 18.0);
        double scale = 30.0 * camera.zoom * perspective;

        int screenX = (int) Math.round((viewportLeft() + viewportRight()) / 2.0 + rotatedX * scale);
        int screenY = (int) Math.round((66 + timelineTop() - 8) / 2.0 - rotatedY * scale);
        return new int[]{screenX, screenY};
    }

    private static String format(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String trim(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum - 3) + "...";
    }

    private void checkpoint() {
        undoHistory.push(HISTORY_GSON.toJson(project));
        while (undoHistory.size() > HISTORY_LIMIT) undoHistory.removeLast();
        redoHistory.clear();
    }

    private void undo() {
        if (undoHistory.isEmpty()) {
            status = "Nothing to undo";
            return;
        }
        redoHistory.push(HISTORY_GSON.toJson(project));
        project = HISTORY_GSON.fromJson(undoHistory.pop(), SceneProject.class);
        validateSelection();
        status = "Undo";
        rebuildWidgets();
    }

    private void redo() {
        if (redoHistory.isEmpty()) {
            status = "Nothing to redo";
            return;
        }
        undoHistory.push(HISTORY_GSON.toJson(project));
        project = HISTORY_GSON.fromJson(redoHistory.pop(), SceneProject.class);
        validateSelection();
        status = "Redo";
        rebuildWidgets();
    }

    private void validateSelection() {
        if (project.objects == null) project.objects = new java.util.ArrayList<>();
        if (project.camera == null) project.camera = new SceneProject.Camera();
        if (selectedObject() == null) selectedObjectId = null;
    }

    private void openAssetPicker(boolean entities) {
        this.minecraft.gui.setScreen(new SceneAssetPickerScreen(this, entities, asset -> {
            checkpoint();
            SceneProject.SceneObject object = new SceneProject.SceneObject();
            object.kind = asset.entity() ? "minecraft_entity" : "minecraft_block";
            object.assetId = asset.id();
            object.name = asset.id();
            object.transform.x = project.objects.size() * 1.25F;
            project.objects.add(object);
            selectedObjectId = object.id;
            status = "Added " + asset.id();
            rebuildWidgets();
        }));
    }

    private void exportViewport() {
        status = "Exporting viewport...";
        SceneExporter.exportViewport(this.minecraft, viewportLeft() + 1, 87,
                Math.max(1, viewportRight() - viewportLeft() - 2),
                Math.max(1, timelineTop() - 96), message -> status = message);
    }

    private void addCube() {
        checkpoint();
        SceneProject.SceneObject object = new SceneProject.SceneObject();
        object.name = "Cube " + (project.objects.size() + 1);
        object.kind = "mesh_cube";
        object.transform.x = project.objects.size() * 1.5F;
        project.objects.add(object);
        selectedObjectId = object.id;
        status = "Added " + object.name;
        rebuildWidgets();
    }

    private void duplicateSelected() {
        SceneProject.SceneObject source = selectedObject();
        if (source == null) {
            status = "Select an object to duplicate";
            return;
        }
        checkpoint();
        SceneProject.SceneObject copy = new SceneProject.SceneObject();
        copy.id = UUID.randomUUID().toString();
        copy.name = source.name + " Copy";
        copy.kind = source.kind;
        copy.assetId = source.assetId;
        copy.visible = source.visible;
        copy.locked = source.locked;
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
        selectedObjectId = copy.id;
        status = "Duplicated " + source.name;
        rebuildWidgets();
    }

    private void deleteSelected() {
        SceneProject.SceneObject selected = selectedObject();
        if (selected == null) {
            status = "Select an object to delete";
            return;
        }
        checkpoint();
        project.objects.removeIf(object -> {
            if (!selected.id.equals(object.id)) return false;
            SceneAssetRenderer.forget(object.id);
            return true;
        });
        selectedObjectId = null;
        status = "Deleted " + selected.name;
        rebuildWidgets();
    }

    private void openProjectBrowser() {
        this.minecraft.gui.setScreen(new ProjectBrowserScreen(this, loaded -> {
            checkpoint();
            project = loaded;
            selectedObjectId = null;
            validateSelection();
            if (projectNameField != null) projectNameField.setValue(project.name);
            status = "Project loaded";
            rebuildWidgets();
        }));
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

    private void newProject() {
        checkpoint();
        project = new SceneProject();
        selectedObjectId = null;
        activeTool = "Select";
        status = "New scene created";
        if (projectNameField != null) projectNameField.setValue(project.name);
        rebuildWidgets();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && mouseX >= viewportLeft() && mouseX <= viewportRight()
                && mouseY >= 66 && mouseY <= timelineTop() - 8) {
            if ("Select".equals(activeTool)) {
                selectAt(mouseX, mouseY);
                return true;
            }
            if ("Move".equals(activeTool) || "Rotate".equals(activeTool) || "Scale".equals(activeTool)
                    || "Orbit".equals(activeTool) || "Pan".equals(activeTool) || "Zoom".equals(activeTool)) {
                if (("Move".equals(activeTool) || "Rotate".equals(activeTool) || "Scale".equals(activeTool))
                        && selectedObject() == null) {
                    status = "Select an object first";
                    return true;
                }
                checkpoint();
                draggingViewport = true;
                dragChanged = false;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectAt(double mouseX, double mouseY) {
        SceneProject.SceneObject nearest = null;
        double nearestDistance = 16.0 * 16.0;
        for (SceneProject.SceneObject object : project.objects) {
            int[] point = projectToScreen(object.transform.x, object.transform.y, object.transform.z);
            double dx = point[0] - mouseX;
            double dy = point[1] - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = object;
            }
        }
        selectedObjectId = nearest == null ? null : nearest.id;
        status = nearest == null ? "Selection cleared" : "Selected " + nearest.name;
        rebuildWidgets();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!draggingViewport || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        SceneProject.SceneObject selected = selectedObject();
        float zoom = Math.max(0.2F, project.camera.zoom);
        switch (activeTool) {
            case "Move" -> {
                selected.transform.x += (float) (dragX / (30.0 * zoom));
                selected.transform.y -= (float) (dragY / (30.0 * zoom));
            }
            case "Rotate" -> {
                selected.transform.rotationY += (float) dragX;
                selected.transform.rotationX += (float) dragY;
            }
            case "Scale" -> {
                float factor = (float) Math.exp(dragX * 0.01);
                selected.transform.scaleX = clamp(selected.transform.scaleX * factor, 0.05F, 20.0F);
                selected.transform.scaleY = clamp(selected.transform.scaleY * factor, 0.05F, 20.0F);
                selected.transform.scaleZ = clamp(selected.transform.scaleZ * factor, 0.05F, 20.0F);
            }
            case "Orbit" -> {
                project.camera.yaw += (float) (dragX * 0.5);
                project.camera.pitch = clamp(project.camera.pitch + (float) (dragY * 0.5), -89.0F, 89.0F);
            }
            case "Pan" -> {
                project.camera.targetX -= (float) (dragX / (30.0 * zoom));
                project.camera.targetY += (float) (dragY / (30.0 * zoom));
            }
            case "Zoom" -> project.camera.zoom = clamp(
                    (float) (project.camera.zoom * Math.exp(-dragY * 0.01)), 0.2F, 4.0F
            );
            default -> { }
        }
        dragChanged = true;
        status = activeTool + " • " + (selected == null ? "Camera" : selected.name);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingViewport) {
            draggingViewport = false;
            if (!dragChanged && !undoHistory.isEmpty()) {
                undoHistory.pop();
            }
            dragChanged = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= viewportLeft() && mouseX <= viewportRight()
                && mouseY >= 66 && mouseY <= timelineTop() - 8) {
            checkpoint();
            float factor = (float) Math.pow(1.12, verticalAmount);
            project.camera.zoom = clamp(project.camera.zoom * factor, 0.2F, 4.0F);
            status = "Viewport zoom: " + format(project.camera.zoom) + "x";
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (projectNameField != null && projectNameField.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (control && keyCode == GLFW.GLFW_KEY_Z) {
            undo();
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_D) {
            duplicateSelected();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_G) {
            activeTool = "Move";
            status = "Move tool selected";
            rebuildWidgets();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            activeTool = "Rotate";
            status = "Rotate tool selected";
            rebuildWidgets();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_S) {
            activeTool = "Scale";
            status = "Scale tool selected";
            rebuildWidgets();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            deleteSelected();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !"Select".equals(activeTool)) {
            activeTool = "Select";
            status = "Select tool restored";
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
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
