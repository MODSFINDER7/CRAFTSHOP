package dev.modsfinder.craftshop;

import com.google.gson.Gson;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.function.Consumer;

public final class StudioScreen extends Screen {
    private static final Gson HISTORY_GSON = new Gson();
    private static final int HISTORY_LIMIT = 80;
    private static final float MOVE_STEP = 0.25F;
    private static final float ROTATE_STEP = 15.0F;
    private static final float SCALE_STEP = 0.1F;

    private final Screen parent;
    private final Deque<String> undoHistory = new ArrayDeque<>();
    private final Deque<String> redoHistory = new ArrayDeque<>();

    private SceneProject project = new SceneProject();
    private String status = "Ready";
    private String selectedObjectId;
    private EditBox projectNameField;
    private int scenePage;

    public StudioScreen(Screen parent) {
        super(Component.literal("CraftShop 3D Studio"));
        this.parent = parent;
    }

    private int leftX() { return 12; }
    private int leftWidth() { return 150; }
    private int middleX() { return 172; }
    private int middleWidth() { return 258; }
    private int rightX() { return 440; }
    private int rightWidth() { return Math.max(150, this.width - rightX() - 12); }
    private int panelTop() { return 68; }
    private int panelBottom() { return this.height - 30; }

    private SceneProject.SceneObject selectedObject() {
        if (selectedObjectId == null || project.objects == null) return null;
        return project.objects.stream()
                .filter(object -> selectedObjectId.equals(object.id))
                .findFirst().orElse(null);
    }

    @Override
    protected void init() {
        int top = 10;
        addButton("New", 10, top, 42, 20, this::newProject);
        addButton("Open", 55, top, 46, 20, this::openProjectBrowser);
        addButton("Save", 104, top, 44, 20, this::saveProject);

        int nameX = 151;
        int nameWidth = Math.max(72, this.width - 355);
        projectNameField = new EditBox(this.font, nameX, top, nameWidth, 20,
                Component.literal("Project name"));
        projectNameField.setMaxLength(64);
        projectNameField.setValue(project.name == null ? "Untitled Scene" : project.name);
        addRenderableWidget(projectNameField);

        addButton("2D PNG", this.width - 132, top, 62, 20,
                () -> this.minecraft.gui.setScreen(new Canvas2DScreen(this)));
        addButton("Gallery", this.width - 64, top, 54, 20, this::returnToParent);

        int buttonX = leftX() + 10;
        int buttonWidth = leftWidth() - 20;
        int y = 100;
        addButton("Place Block", buttonX, y, buttonWidth, 22, () -> openAssetPicker(false));
        addButton("Spawn Mob", buttonX, y + 28, buttonWidth, 22, () -> openAssetPicker(true));
        addButton("Add Cube", buttonX, y + 56, buttonWidth, 22, this::addCube);

        int cameraY = 224;
        int smallW = 41;
        addButton("Cam <", buttonX, cameraY, smallW, 20, () -> moveCamera(-1, 0, 0));
        addButton("Cam >", buttonX + 44, cameraY, smallW, 20, () -> moveCamera(1, 0, 0));
        addButton("Cam ^", buttonX + 88, cameraY, smallW, 20, () -> moveCamera(0, 1, 0));
        addButton("Cam v", buttonX, cameraY + 25, smallW, 20, () -> moveCamera(0, -1, 0));
        addButton("Forward", buttonX + 44, cameraY + 25, smallW, 20,
                () -> moveCamera(0, 0, -1));
        addButton("Back", buttonX + 88, cameraY + 25, smallW, 20,
                () -> moveCamera(0, 0, 1));
        addButton("Reset", buttonX, cameraY + 52, buttonWidth, 20, this::resetCamera);

        int mx = middleX() + 10;
        int pairW = 52;
        int pairGap = 4;
        SceneProject.SceneObject selected = selectedObject();
        boolean hasSelection = selected != null;
        boolean unlocked = hasSelection && !selected.locked;

        addButton("X -", mx, 128, pairW, 20, () -> nudge(0, -MOVE_STEP));
        addButton("X +", mx + pairW + pairGap, 128, pairW, 20, () -> nudge(0, MOVE_STEP));
        addButton("Y -", mx + (pairW + pairGap) * 2, 128, pairW, 20,
                () -> nudge(1, -MOVE_STEP));
        addButton("Y +", mx + (pairW + pairGap) * 3, 128, pairW, 20,
                () -> nudge(1, MOVE_STEP));
        addButton("Z -", mx, 152, pairW, 20, () -> nudge(2, -MOVE_STEP));
        addButton("Z +", mx + pairW + pairGap, 152, pairW, 20, () -> nudge(2, MOVE_STEP));

        addButton("X -", mx, 202, pairW, 20, () -> rotate(0, -ROTATE_STEP));
        addButton("X +", mx + pairW + pairGap, 202, pairW, 20, () -> rotate(0, ROTATE_STEP));
        addButton("Y -", mx + (pairW + pairGap) * 2, 202, pairW, 20,
                () -> rotate(1, -ROTATE_STEP));
        addButton("Y +", mx + (pairW + pairGap) * 3, 202, pairW, 20,
                () -> rotate(1, ROTATE_STEP));
        addButton("Z -", mx, 226, pairW, 20, () -> rotate(2, -ROTATE_STEP));
        addButton("Z +", mx + pairW + pairGap, 226, pairW, 20, () -> rotate(2, ROTATE_STEP));

        addButton("Scale -", mx, 276, 78, 20, () -> scale(1.0F - SCALE_STEP));
        addButton("Scale +", mx + 82, 276, 78, 20, () -> scale(1.0F + SCALE_STEP));
        addButton("Duplicate", mx, 312, 78, 20, this::duplicateSelected);
        addButton("Delete", mx + 82, 312, 78, 20, this::deleteSelected);
        addButton(selected != null && selected.visible ? "Hide" : "Show",
                mx, 338, 78, 20, this::toggleVisibility);
        addButton(selected != null && selected.locked ? "Unlock" : "Lock",
                mx + 82, 338, 78, 20, this::toggleLock);
        addButton("Undo", mx, 370, 78, 20, this::undo);
        addButton("Redo", mx + 82, 370, 78, 20, this::redo);

        int listX = rightX() + 8;
        int listW = rightWidth() - 16;
        int pageSize = visibleObjectRows();
        int start = scenePage * pageSize;
        int end = Math.min(project.objects.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            SceneProject.SceneObject object = project.objects.get(i);
            String id = object.id;
            String prefix = id.equals(selectedObjectId) ? "> " : "";
            String lock = object.locked ? " [L]" : "";
            String hidden = object.visible ? "" : " [H]";
            String label = prefix + trim(object.name, Math.max(8, listW / 6 - 8)) + lock + hidden;
            int rowY = 102 + (i - start) * 24;
            addButton(label, listX, rowY, listW, 21, () -> {
                selectedObjectId = id;
                status = "Selected " + object.name;
                rebuild();
            });
        }

        int pageY = this.height - 54;
        addButton("Prev", listX, pageY, 54, 20, () -> {
            scenePage = Math.max(0, scenePage - 1);
            rebuild();
        });
        addButton("Next", listX + 58, pageY, 54, 20, () -> {
            if ((scenePage + 1) * pageSize < project.objects.size()) scenePage++;
            rebuild();
        });
    }

    private int visibleObjectRows() {
        return Math.max(1, Math.min(12, (this.height - 174) / 24));
    }

    private void addButton(String label, int x, int y, int width, int height, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> action.run())
                .bounds(x, y, Math.max(1, width), height).build());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(0, 0, this.width, this.height, 0xFF101319);
        graphics.fill(8, 8, this.width - 8, 36, 0xFF1D232D);
        graphics.fill(leftX(), panelTop(), leftX() + leftWidth(), panelBottom(), 0xFF202630);
        graphics.fill(middleX(), panelTop(), middleX() + middleWidth(), panelBottom(), 0xFF171E28);
        graphics.fill(rightX(), panelTop(), this.width - 12, panelBottom(), 0xFF202630);
        graphics.outline(leftX(), panelTop(), leftWidth(), panelBottom() - panelTop(), 0xFF394655);
        graphics.outline(middleX(), panelTop(), middleWidth(), panelBottom() - panelTop(), 0xFF394655);
        graphics.outline(rightX(), panelTop(), this.width - rightX() - 12,
                panelBottom() - panelTop(), 0xFF394655);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "CRAFTSHOP  /  3D SCENE TOOLS", 14, 48, 0xFF55DDEE, true);
        graphics.text(this.font, trim(status, 54),
                Math.max(180, this.width - this.font.width(trim(status, 54)) - 14), 48,
                0xFF9DDDAA, false);

        graphics.text(this.font, "SPAWN", leftX() + 10, 82, 0xFF55DDEE, true);
        graphics.text(this.font, "SPECTATOR CAMERA", leftX() + 10, 204, 0xFF9AA8B8, false);

        graphics.text(this.font, "SELECTED OBJECT", middleX() + 10, 82, 0xFF55DDEE, true);
        SceneProject.SceneObject selected = selectedObject();
        if (selected == null) {
            graphics.text(this.font, "Select an object from the list.", middleX() + 10, 102,
                    0xFF8995A4, false);
        } else {
            graphics.text(this.font, trim(selected.name, 32), middleX() + 10, 102,
                    0xFFFFFFFF, false);
            graphics.text(this.font, selected.kind + "  •  " + selected.assetId,
                    middleX() + 10, 116, 0xFF8995A4, false);
            graphics.text(this.font, selected.locked ? "Locked" : "Unlocked",
                    middleX() + 174, 102, selected.locked ? 0xFFFFB86B : 0xFF9DDDAA, false);
        }
        graphics.text(this.font, "MOVE  /  0.25 BLOCK", middleX() + 10, 116 + (selected == null ? 20 : 30),
                0xFF9AA8B8, false);
        graphics.text(this.font, "ROTATE  /  15 DEGREES", middleX() + 10, 188,
                0xFF9AA8B8, false);
        graphics.text(this.font, "UNIFORM SCALE", middleX() + 10, 262,
                0xFF9AA8B8, false);
        graphics.text(this.font, "OBJECT ACTIONS", middleX() + 10, 298,
                0xFF9AA8B8, false);
        graphics.text(this.font, "HISTORY", middleX() + 10, 356,
                0xFF9AA8B8, false);

        graphics.text(this.font, "SCENE OBJECTS  (" + project.objects.size() + ")",
                rightX() + 10, 82, 0xFF55DDEE, true);
        if (project.objects.isEmpty()) {
            graphics.text(this.font, "No objects yet.", rightX() + 10, 108, 0xFF8995A4, false);
            graphics.text(this.font, "Use Place Block or Spawn Mob.", rightX() + 10, 124,
                    0xFF68798B, false);
        }
        graphics.fill(0, this.height - 8, this.width, this.height, 0xFF0B0E13);
        graphics.text(this.font, "CRAFTSHOP  •  " + project.name + "  •  "
                + project.objects.size() + " objects  •  .craftshop project",
                10, this.height - 7, 0xFF7F8D9C, false);
    }

    private static String trim(String value, int maximum) {
        if (value == null) return "";
        if (maximum < 4) return value.substring(0, Math.min(value.length(), maximum));
        return value.length() <= maximum ? value : value.substring(0, maximum - 3) + "...";
    }

    private void checkpoint() {
        undoHistory.push(HISTORY_GSON.toJson(project));
        while (undoHistory.size() > HISTORY_LIMIT) undoHistory.removeLast();
        redoHistory.clear();
    }

    private void validateProject() {
        if (project == null) project = new SceneProject();
        if (project.objects == null) project.objects = new java.util.ArrayList<>();
        if (project.camera == null) project.camera = new SceneProject.Camera();
        project.objects.removeIf(object -> object == null);
        for (SceneProject.SceneObject object : project.objects) {
            if (object.id == null || object.id.isBlank()) object.id = UUID.randomUUID().toString();
            if (object.transform == null) object.transform = new SceneProject.Transform();
            if (object.name == null) object.name = "Object";
            if (object.kind == null) object.kind = "mesh_cube";
            if (object.assetId == null) object.assetId = "minecraft:stone";
        }
        if (selectedObject() == null) selectedObjectId = null;
        scenePage = Math.min(scenePage, Math.max(0, (project.objects.size() - 1) / visibleObjectRows()));
    }

    private void undo() {
        if (undoHistory.isEmpty()) {
            status = "Nothing to undo";
            rebuild();
            return;
        }
        redoHistory.push(HISTORY_GSON.toJson(project));
        project = HISTORY_GSON.fromJson(undoHistory.pop(), SceneProject.class);
        validateProject();
        status = "Undo complete";
        rebuild();
    }

    private void redo() {
        if (redoHistory.isEmpty()) {
            status = "Nothing to redo";
            rebuild();
            return;
        }
        undoHistory.push(HISTORY_GSON.toJson(project));
        project = HISTORY_GSON.fromJson(redoHistory.pop(), SceneProject.class);
        validateProject();
        status = "Redo complete";
        rebuild();
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
            scenePage = Math.max(0, (project.objects.size() - 1) / visibleObjectRows());
            status = (asset.entity() ? "Spawned " : "Placed ") + asset.id();
            rebuild();
        }));
    }

    private void addCube() {
        checkpoint();
        SceneProject.SceneObject object = new SceneProject.SceneObject();
        object.name = "Cube " + (project.objects.size() + 1);
        object.kind = "mesh_cube";
        object.transform.x = project.objects.size() * 1.5F;
        project.objects.add(object);
        selectedObjectId = object.id;
        scenePage = Math.max(0, (project.objects.size() - 1) / visibleObjectRows());
        status = "Added " + object.name;
        rebuild();
    }

    private void withSelected(String action, Consumer<SceneProject.SceneObject> mutation) {
        SceneProject.SceneObject selected = selectedObject();
        if (selected == null) {
            status = "Select an object first";
            rebuild();
            return;
        }
        if (selected.locked) {
            status = "Object is locked";
            rebuild();
            return;
        }
        checkpoint();
        mutation.accept(selected);
        status = action + " • " + selected.name;
        rebuild();
    }

    private void nudge(int axis, float amount) {
        withSelected("Moved", object -> {
            if (axis == 0) object.transform.x += amount;
            else if (axis == 1) object.transform.y += amount;
            else object.transform.z += amount;
        });
    }

    private void rotate(int axis, float amount) {
        withSelected("Rotated", object -> {
            if (axis == 0) object.transform.rotationX += amount;
            else if (axis == 1) object.transform.rotationY += amount;
            else object.transform.rotationZ += amount;
        });
    }

    private void scale(float factor) {
        withSelected("Scaled", object -> {
            object.transform.scaleX = clamp(object.transform.scaleX * factor, 0.05F, 20.0F);
            object.transform.scaleY = clamp(object.transform.scaleY * factor, 0.05F, 20.0F);
            object.transform.scaleZ = clamp(object.transform.scaleZ * factor, 0.05F, 20.0F);
        });
    }

    private void duplicateSelected() {
        SceneProject.SceneObject source = selectedObject();
        if (source == null) {
            status = "Select an object to duplicate";
            rebuild();
            return;
        }
        checkpoint();
        SceneProject.SceneObject copy = new SceneProject.SceneObject();
        copy.id = UUID.randomUUID().toString();
        copy.name = source.name + " Copy";
        copy.kind = source.kind;
        copy.assetId = source.assetId;
        copy.visible = source.visible;
        copy.locked = false;
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
        scenePage = Math.max(0, (project.objects.size() - 1) / visibleObjectRows());
        status = "Duplicated " + source.name;
        rebuild();
    }

    private void deleteSelected() {
        SceneProject.SceneObject selected = selectedObject();
        if (selected == null) {
            status = "Select an object to delete";
            rebuild();
            return;
        }
        checkpoint();
        project.objects.removeIf(object -> selected.id.equals(object.id));
        selectedObjectId = null;
        validateProject();
        status = "Deleted " + selected.name;
        rebuild();
    }

    private void toggleVisibility() {
        SceneProject.SceneObject selected = selectedObject();
        if (selected == null) {
            status = "Select an object first";
            rebuild();
            return;
        }
        checkpoint();
        selected.visible = !selected.visible;
        status = selected.visible ? "Object shown" : "Object hidden";
        rebuild();
    }

    private void toggleLock() {
        SceneProject.SceneObject selected = selectedObject();
        if (selected == null) {
            status = "Select an object first";
            rebuild();
            return;
        }
        checkpoint();
        selected.locked = !selected.locked;
        status = selected.locked ? "Object locked" : "Object unlocked";
        rebuild();
    }

    private void moveCamera(float side, float vertical, float forward) {
        checkpoint();
        SceneProject.Camera camera = project.camera;
        double yaw = Math.toRadians(camera.yaw);
        camera.x += (float) (Math.cos(yaw) * side + Math.sin(yaw) * forward) * MOVE_STEP;
        camera.y += vertical * MOVE_STEP;
        camera.z += (float) (Math.sin(yaw) * side - Math.cos(yaw) * forward) * MOVE_STEP;
        status = "Spectator camera position updated";
        rebuild();
    }

    private void resetCamera() {
        checkpoint();
        project.camera = new SceneProject.Camera();
        status = "Spectator camera reset";
        rebuild();
    }

    private void openProjectBrowser() {
        this.minecraft.gui.setScreen(new ProjectBrowserScreen(this, loaded -> {
            checkpoint();
            project = loaded;
            selectedObjectId = null;
            scenePage = 0;
            validateProject();
            if (projectNameField != null) projectNameField.setValue(project.name);
            status = "Project loaded";
            rebuild();
        }));
    }

    private void saveProject() {
        if (projectNameField != null) project.name = projectNameField.getValue();
        try {
            ProjectManager.save(project);
            status = "Project saved";
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not save CraftShop project", exception);
            status = "Save failed: " + exception.getMessage();
        }
        rebuild();
    }

    private void newProject() {
        checkpoint();
        project = new SceneProject();
        selectedObjectId = null;
        scenePage = 0;
        status = "New scene created";
        rebuild();
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void returnToParent() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (projectNameField != null && projectNameField.isFocused()) {
            return super.keyPressed(event);
        }
        int key = event.input();
        if (event.hasControlDown() && key == 'z') {
            undo();
            return true;
        }
        if (event.hasControlDown() && key == 'y') {
            redo();
            return true;
        }
        if (event.hasControlDown() && key == 'd') {
            duplicateSelected();
            return true;
        }
        if (key == 127 || key == 8) {
            deleteSelected();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        SceneAssetRenderer.clear();
        returnToParent();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
