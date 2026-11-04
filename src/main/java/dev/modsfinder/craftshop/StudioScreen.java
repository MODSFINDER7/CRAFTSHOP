package dev.modsfinder.craftshop;

import com.google.gson.Gson;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
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
        this(parent, null);
    }

    public StudioScreen(Screen parent, Path initialImage) {
        super(Component.literal("CraftShop 3D Studio"));
        this.parent = parent;
        if (initialImage != null) {
            try {
                project.canvasImage = ProjectManager.importCanvasImage(initialImage);
                status = "Gallery PNG loaded";
            } catch (IOException exception) {
                CraftShop.LOGGER.error("Could not import selected gallery PNG", exception);
                status = "Could not load selected PNG";
            }
        }
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
        String targetId = selectedObjectId == null || selectedObjectId.isBlank()
                ? project.selectedObjectId : selectedObjectId;
        if (targetId == null || targetId.isBlank() || project.objects == null) return null;
        return project.objects.stream()
                .filter(object -> targetId.equals(object.id))
                .findFirst().orElse(null);
    }

    @Override
    protected void init() {
        int top = 10;
        addButton("New", 10, top, 42, 20, this::newProject);
        addButton("Open", 55, top, 46, 20, this::openProjectBrowser);
        addButton("Save", 104, top, 44, 20, this::saveProject);

        addButton("PNG", 151, top, 42, 20, this::choosePng);
        addButton("Render", 197, top, 54, 20, this::renderPng);
        addButton("Viewport", 255, top, 54, 20, this::previewScene);

        int nameX = 315;
        int nameWidth = Math.max(72, this.width - 457);
        projectNameField = new EditBox(this.font, nameX, top, nameWidth, 20,
                Component.literal("Project name"));
        projectNameField.setMaxLength(64);
        projectNameField.setValue(project.name == null ? "Untitled Scene" : project.name);
        addRenderableWidget(projectNameField);

        addButton("2D PNG", this.width - 132, top, 62, 20, this::open2DEditor);
        addButton("Gallery", this.width - 64, top, 54, 20, this::returnToParent);

        int buttonX = leftX() + 10;
        int buttonWidth = leftWidth() - 20;
        int y = 100;
        addButton("Place Block", buttonX, y, buttonWidth, 22, () -> openAssetPicker(false));
        addButton("Spawn Mob", buttonX, y + 28, buttonWidth, 22, () -> openAssetPicker(true));
        addButton("Add Cube", buttonX, y + 56, buttonWidth, 22, this::addCube);
        addButton("Import World", buttonX, y + 84, buttonWidth, 22, this::importNearbyWorld);

        int cameraY = 224;
        int smallW = 41;
        addButton("Cam <", buttonX, cameraY, smallW, 20, () -> moveCamera(-1, 0, 0));
        addButton("Cam >", buttonX + 44, cameraY, smallW, 20, () -> moveCamera(1, 0, 0));
        addButton("Cam ^", buttonX + 88, cameraY, smallW, 20, () -> moveCamera(0, 1, 0));
        addButton("Cam v", buttonX, cameraY + 25, smallW, 20, () -> moveCamera(0, -1, 0));
        addButton("Forward", buttonX + 44, cameraY + 25, smallW, 20,
                () -> moveCamera(0, 0, 1));
        addButton("Back", buttonX + 88, cameraY + 25, smallW, 20,
                () -> moveCamera(0, 0, -1));
        addButton("Reset", buttonX, cameraY + 52, buttonWidth, 20, this::resetCamera);
        addButton("Yaw-", buttonX, cameraY + 79, smallW, 20, () -> rotateCamera(-10.0F, 0.0F));
        addButton("Yaw+", buttonX + 44, cameraY + 79, smallW, 20, () -> rotateCamera(10.0F, 0.0F));
        addButton("Pit-", buttonX + 88, cameraY + 79, smallW, 20, () -> rotateCamera(0.0F, -10.0F));
        addButton("Pit+", buttonX, cameraY + 104, smallW, 20, () -> rotateCamera(0.0F, 10.0F));
        addButton("Zoom+", buttonX + 44, cameraY + 104, smallW, 20, () -> zoomCamera(0.1F));
        addButton("Zoom-", buttonX + 88, cameraY + 104, smallW, 20, () -> zoomCamera(-0.1F));
        addButton("AO: " + (project.ambientOcclusion ? "ON" : "OFF"), buttonX,
                cameraY + 132, buttonWidth, 20, this::toggleAmbientOcclusion);
        addButton("Light-", buttonX, cameraY + 160, smallW, 20, () -> adjustLightYaw(-10.0F));
        addButton("Light+", buttonX + 44, cameraY + 160, smallW, 20, () -> adjustLightYaw(10.0F));
        addButton("Amb-", buttonX + 88, cameraY + 160, smallW, 20, () -> adjustAmbientLight(-0.05F));
        addButton("Amb+", buttonX, cameraY + 185, smallW, 20, () -> adjustAmbientLight(0.05F));

        int mx = middleX() + 10;
        int pairW = 52;
        int pairGap = 4;
        SceneProject.SceneObject selected = selectedObject();

        addButton("X -", mx, 146, pairW, 20, () -> nudge(0, -MOVE_STEP));
        addButton("X +", mx + pairW + pairGap, 146, pairW, 20, () -> nudge(0, MOVE_STEP));
        addButton("Y -", mx + (pairW + pairGap) * 2, 146, pairW, 20,
                () -> nudge(1, -MOVE_STEP));
        addButton("Y +", mx + (pairW + pairGap) * 3, 146, pairW, 20,
                () -> nudge(1, MOVE_STEP));
        addButton("Z -", mx, 170, pairW, 20, () -> nudge(2, -MOVE_STEP));
        addButton("Z +", mx + pairW + pairGap, 170, pairW, 20, () -> nudge(2, MOVE_STEP));

        addButton("X -", mx, 202, pairW, 20, () -> rotate(0, -ROTATE_STEP));
        addButton("X +", mx + pairW + pairGap, 202, pairW, 20, () -> rotate(0, ROTATE_STEP));
        addButton("Y -", mx + (pairW + pairGap) * 2, 202, pairW, 20,
                () -> rotate(1, -ROTATE_STEP));
        addButton("Y +", mx + (pairW + pairGap) * 3, 202, pairW, 20,
                () -> rotate(1, ROTATE_STEP));
        addButton("Z -", mx, 226, pairW, 20, () -> rotate(2, -ROTATE_STEP));
        addButton("Z +", mx + pairW + pairGap, 226, pairW, 20, () -> rotate(2, ROTATE_STEP));

        int scaleButtonWidth = 36;
        addButton("X-", mx, 276, scaleButtonWidth, 20, () -> scaleAxis(0, 1.0F - SCALE_STEP));
        addButton("X+", mx + 38, 276, scaleButtonWidth, 20, () -> scaleAxis(0, 1.0F + SCALE_STEP));
        addButton("Y-", mx + 76, 276, scaleButtonWidth, 20, () -> scaleAxis(1, 1.0F - SCALE_STEP));
        addButton("Y+", mx + 114, 276, scaleButtonWidth, 20, () -> scaleAxis(1, 1.0F + SCALE_STEP));
        addButton("Z-", mx + 152, 276, scaleButtonWidth, 20, () -> scaleAxis(2, 1.0F - SCALE_STEP));
        addButton("Z+", mx + 190, 276, scaleButtonWidth, 20, () -> scaleAxis(2, 1.0F + SCALE_STEP));
        addButton("Uniform -", mx, 302, 78, 20, () -> scale(1.0F - SCALE_STEP));
        addButton("Uniform +", mx + 82, 302, 78, 20, () -> scale(1.0F + SCALE_STEP));
        addButton("Duplicate", mx, 334, 78, 20, this::duplicateSelected);
        addButton("Delete", mx + 82, 334, 78, 20, this::deleteSelected);
        addButton(selected != null && selected.visible ? "Hide" : "Show",
                mx, 360, 78, 20, this::toggleVisibility);
        addButton(selected != null && selected.locked ? "Unlock" : "Lock",
                mx + 82, 360, 78, 20, this::toggleLock);
        addButton("Undo", mx, 390, 78, 20, this::undo);
        addButton("Redo", mx + 82, 390, 78, 20, this::redo);
        addButton("Reset Transform", mx, 418, 160, 20, this::resetSelectedTransform);

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
                project.selectedObjectId = id;
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
        graphics.fill(0, 0, this.width, this.height, 0xFF090D13);
        graphics.fill(0, 0, this.width, 3, 0xFF36C8D9);
        graphics.fill(8, 8, this.width - 8, 38, 0xFF151E2A);
        graphics.fill(8, 39, this.width - 8, 62, 0xFF101720);
        graphics.fill(leftX(), panelTop(), leftX() + leftWidth(), panelBottom(), 0xFF151E29);
        graphics.fill(middleX(), panelTop(), middleX() + middleWidth(), panelBottom(), 0xFF111923);
        graphics.fill(rightX(), panelTop(), this.width - 12, panelBottom(), 0xFF151E29);
        graphics.fill(leftX(), panelTop(), leftX() + leftWidth(), panelTop() + 2, 0xFF36C8D9);
        graphics.fill(middleX(), panelTop(), middleX() + middleWidth(), panelTop() + 2, 0xFF8C78E8);
        graphics.fill(rightX(), panelTop(), this.width - 12, panelTop() + 2, 0xFF36C8D9);
        graphics.outline(leftX(), panelTop(), leftWidth(), panelBottom() - panelTop(), 0xFF2D3B4B);
        graphics.outline(middleX(), panelTop(), middleWidth(), panelBottom() - panelTop(), 0xFF2D3B4B);
        graphics.outline(rightX(), panelTop(), this.width - rightX() - 12,
                panelBottom() - panelTop(), 0xFF2D3B4B);
        graphics.fill(8, this.height - 23, this.width - 8, this.height - 8, 0xFF101720);
        graphics.fill(8, this.height - 23, this.width - 8, this.height - 22, 0xFF26384A);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "CRAFTSHOP", 16, 18, 0xFFECF7FF, true);
        graphics.text(this.font, "CREATIVE SCENE STUDIO", 112, 19, 0xFF6F879C, false);
        graphics.text(this.font, "3D WORKSPACE", 16, 49, 0xFF55DDEE, true);
        String pngLabel = "No PNG selected";
        if (project.canvasImage != null && !project.canvasImage.isBlank()) {
            int separator = Math.max(project.canvasImage.lastIndexOf('/'), project.canvasImage.lastIndexOf('\\'));
            pngLabel = separator >= 0 ? project.canvasImage.substring(separator + 1) : project.canvasImage;
        }
        graphics.text(this.font, "SOURCE  /  " + trim(pngLabel, 30), 16, 59, 0xFF9AA8B8, false);
        graphics.text(this.font, trim(status, 54),
                Math.max(180, this.width - this.font.width(trim(status, 54)) - 14), 48,
                0xFF9DDDAA, false);

        graphics.text(this.font, "01  /  ADD TO SCENE", leftX() + 10, 83, 0xFF55DDEE, true);
        graphics.text(this.font, "CAMERA CONTROLS", leftX() + 10, 204, 0xFF9AA8B8, true);
        graphics.fill(leftX() + 10, 218, leftX() + leftWidth() - 10, 219, 0xFF344354);

        graphics.text(this.font, "02  /  TRANSFORM", middleX() + 10, 83, 0xFFB8A7FF, true);
        graphics.fill(middleX() + 10, 126, middleX() + middleWidth() - 10, 127, 0xFF344354);
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
        graphics.text(this.font, "MOVE  /  0.25 BLOCK", middleX() + 10, 136,
                0xFF9AA8B8, false);
        graphics.text(this.font, "ROTATE  /  15 DEGREES", middleX() + 10, 188,
                0xFF9AA8B8, false);
        graphics.text(this.font, "AXIS SCALE  /  X  Y  Z", middleX() + 10, 262,
                0xFF9AA8B8, false);
        graphics.text(this.font, "UNIFORM SCALE", middleX() + 10, 290,
                0xFF9AA8B8, false);
        graphics.text(this.font, "OBJECT ACTIONS", middleX() + 10, 320,
                0xFF9AA8B8, false);
        graphics.text(this.font, "HISTORY", middleX() + 10, 376,
                0xFF9AA8B8, false);

        graphics.text(this.font, "03  /  HIERARCHY", rightX() + 10, 83, 0xFF55DDEE, true);
        graphics.text(this.font, project.objects.size() + " ITEMS",
                this.width - 74, 83, 0xFF8296AA, false);
        graphics.fill(rightX() + 10, 94, this.width - 22, 95, 0xFF344354);
        if (project.objects.isEmpty()) {
            graphics.fill(rightX() + 12, 112, this.width - 24, 176, 0xFF101923);
            graphics.outline(rightX() + 12, 112, this.width - rightX() - 36, 64, 0xFF2D3B4B);
            graphics.text(this.font, "SCENE IS EMPTY", rightX() + 22, 128, 0xFFE6F1FA, true);
            graphics.text(this.font, "Add a block, mob or cube", rightX() + 22, 147,
                    0xFF8296AA, false);
            graphics.text(this.font, "using the tools on the left.", rightX() + 22, 160,
                    0xFF8296AA, false);
        }
        graphics.text(this.font, "PROJECT  /  " + trim(project.name, 34),
                16, this.height - 13, 0xFF91A5B9, false);
        graphics.text(this.font, "CTRL+Z UNDO     CTRL+Y REDO     CTRL+D DUPLICATE",
                Math.max(250, this.width / 2 - 105), this.height - 13, 0xFF62788E, false);
        graphics.text(this.font, "CRAFTSHOP  •  " + project.objects.size() + " OBJECTS",
                this.width - 150, this.height - 13, 0xFF55DDEE, true);
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
            project.selectedObjectId = object.id;
            scenePage = Math.max(0, (project.objects.size() - 1) / visibleObjectRows());
            status = (asset.entity() ? "Spawned " : "Placed ") + asset.id();
            rebuild();
        }));
    }

    private void importNearbyWorld() {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            status = "Join a world before importing blocks";
            rebuild();
            return;
        }

        checkpoint();
        net.minecraft.core.BlockPos origin = minecraft.player.blockPosition();
        int imported = 0;
        for (int y = -2; y <= 3; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    net.minecraft.core.BlockPos position = origin.offset(x, y, z);
                    net.minecraft.world.level.block.state.BlockState state =
                            minecraft.level.getBlockState(position);
                    if (state.isAir()) continue;

                    net.minecraft.resources.Identifier blockId =
                            net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (blockId == null) continue;

                    SceneProject.SceneObject object = new SceneProject.SceneObject();
                    object.kind = "minecraft_block";
                    object.assetId = blockId.toString();
                    object.name = blockId.toString();
                    for (net.minecraft.world.level.block.state.properties.Property<?> property
                            : state.getProperties()) {
                        object.blockProperties.put(property.getName(),
                                propertyValueName(state, property));
                    }
                    object.transform.x = x;
                    object.transform.y = y;
                    object.transform.z = z;
                    project.objects.add(object);
                    imported++;
                }
            }
        }

        if (imported == 0) {
            undoHistory.pop();
            status = "No non-air blocks found nearby";
        } else {
            selectedObjectId = project.objects.get(project.objects.size() - 1).id;
            project.selectedObjectId = selectedObjectId;
            scenePage = Math.max(0, (project.objects.size() - 1) / visibleObjectRows());
            status = "Imported " + imported + " nearby blocks with block-state properties";
        }
        rebuild();
    }

    private static <T extends Comparable<T>> String propertyValueName(
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> property) {
        return property.getName(state.getValue(property));
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

    private void scaleAxis(int axis, float factor) {
        withSelected("Scaled axis", object -> {
            if (axis == 0) object.transform.scaleX = clamp(object.transform.scaleX * factor, 0.05F, 20.0F);
            else if (axis == 1) object.transform.scaleY = clamp(object.transform.scaleY * factor, 0.05F, 20.0F);
            else object.transform.scaleZ = clamp(object.transform.scaleZ * factor, 0.05F, 20.0F);
        });
    }

    private void resetSelectedTransform() {
        withSelected("Transform reset", object -> object.transform = new SceneProject.Transform());
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
        copy.blockProperties = source.blockProperties == null ? new java.util.LinkedHashMap<>()
                : new java.util.LinkedHashMap<>(source.blockProperties);
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
        project.selectedObjectId = copy.id;
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
        SceneAssetRenderer.forget(selected.id);
        selectedObjectId = null;
        project.selectedObjectId = "";
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
        camera.x += (float) (Math.cos(yaw) * side - Math.sin(yaw) * forward) * MOVE_STEP;
        camera.y += vertical * MOVE_STEP;
        camera.z += (float) (-Math.sin(yaw) * side - Math.cos(yaw) * forward) * MOVE_STEP;
        status = "Spectator camera position updated";
        rebuild();
    }

    private void rotateCamera(float yaw, float pitch) {
        checkpoint();
        project.camera.yaw += yaw;
        project.camera.pitch = clamp(project.camera.pitch + pitch, -89.0F, 89.0F);
        status = "Spectator camera orientation updated";
        rebuild();
    }

    private void zoomCamera(float amount) {
        checkpoint();
        project.camera.zoom = clamp(project.camera.zoom + amount, 0.2F, 4.0F);
        status = "Camera zoom: " + String.format(java.util.Locale.ROOT, "%.1f", project.camera.zoom);
        rebuild();
    }

    private void resetCamera() {
        checkpoint();
        project.camera = new SceneProject.Camera();
        status = "Spectator camera reset";
        rebuild();
    }

    private void toggleAmbientOcclusion() {
        checkpoint();
        project.ambientOcclusion = !project.ambientOcclusion;
        status = "Screen-space ambient occlusion " + (project.ambientOcclusion ? "enabled" : "disabled");
        rebuild();
    }

    private void adjustLightYaw(float amount) {
        checkpoint();
        project.lightYaw += amount;
        if (project.lightYaw > 360.0F) project.lightYaw -= 360.0F;
        if (project.lightYaw < -360.0F) project.lightYaw += 360.0F;
        status = "Studio light direction: " + Math.round(project.lightYaw) + "°";
        rebuild();
    }

    private void adjustAmbientLight(float amount) {
        checkpoint();
        project.ambientLight = clamp(project.ambientLight + amount, 0.15F, 0.95F);
        status = "Ambient light: " + Math.round(project.ambientLight * 100.0F) + "%";
        rebuild();
    }

    private void choosePng() {
        this.minecraft.gui.setScreen(new CanvasImagePickerScreen(this, selectedPath -> {
            try {
                checkpoint();
                project.canvasImage = ProjectManager.importCanvasImage(selectedPath);
                project.renderedImage = "";
                ProjectManager.save(project);
                status = "PNG linked to project";
            } catch (IOException exception) {
                CraftShop.LOGGER.error("Could not import project PNG", exception);
                status = "PNG import failed: " + exception.getMessage();
            }
            rebuild();
        }));
    }

    private void open2DEditor() {
        try {
            Path image = ProjectManager.resolveCanvasImage(project);
            if (image == null) {
                this.minecraft.gui.setScreen(new CanvasImagePickerScreen(this, selectedPath -> {
                    try {
                        checkpoint();
                        project.canvasImage = ProjectManager.importCanvasImage(selectedPath);
                        project.renderedImage = "";
                        ProjectManager.save(project);
                        openCanvasEditor(ProjectManager.resolveCanvasImage(project));
                    } catch (IOException exception) {
                        CraftShop.LOGGER.error("Could not prepare PNG for the 2D editor", exception);
                        status = "Could not open selected PNG";
                        rebuild();
                    }
                }));
                return;
            }
            openCanvasEditor(image);
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not resolve project PNG", exception);
            status = "Could not load project PNG";
            rebuild();
        }
    }

    private void openCanvasEditor(Path image) {
        this.minecraft.gui.setScreen(new Canvas2DScreen(this, image, exportedImage -> {
            try {
                project.canvasImage = ProjectManager.importCanvasImage(exportedImage);
                project.renderedImage = "";
                ProjectManager.save(project);
                GalleryTextureCache.invalidate(image);
                status = "Edited PNG linked to project";
            } catch (IOException exception) {
                CraftShop.LOGGER.error("Could not update project PNG after 2D export", exception);
                status = "Could not update project PNG";
            }
        }));
    }

    private void previewScene() {
        if (projectNameField != null) project.name = projectNameField.getValue();
        validateProject();
        status = "3D viewport open • click objects to select, drag to move, right-drag to orbit";
        this.minecraft.gui.setScreen(new SceneRenderScreen(project, this));
    }

    private void renderPng() {
        if (projectNameField != null) project.name = projectNameField.getValue();
        try {
            ProjectManager.save(project);
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not save project before rendering", exception);
            status = "Save before render failed";
            rebuild();
            return;
        }
        SceneExporter.beginExport(this.minecraft, this, project, message -> {
            status = message;
            rebuild();
        });
    }

    private void openProjectBrowser() {
        this.minecraft.gui.setScreen(new ProjectBrowserScreen(this, loaded -> {
            checkpoint();
            SceneAssetRenderer.clear();
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
        SceneAssetRenderer.clear();
        project = new SceneProject();
        selectedObjectId = null;
        scenePage = 0;
        if (projectNameField != null) projectNameField.setValue(project.name);
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
