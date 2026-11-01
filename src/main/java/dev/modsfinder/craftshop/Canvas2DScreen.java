package dev.modsfinder.craftshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class Canvas2DScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int[] PALETTE = {
            0xFF111111, 0xFFFFFFFF, 0xFFE53935, 0xFFFB8C00,
            0xFFFDD835, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5,
            0xFF5E35B1, 0xFFD81B60
    };

    private final Screen parent;
    private CanvasDocument document = new CanvasDocument();
    private String tool = "Brush";
    private int activeColor = PALETTE[0];
    private int brushSize = 5;
    private int activeLayer;
    private int layerPage;
    private final Deque<String> undoHistory = new ArrayDeque<>();
    private final Deque<String> redoHistory = new ArrayDeque<>();
    private boolean drawing;
    private boolean cropping;
    private float lastX;
    private float lastY;
    private int cropStartX;
    private int cropStartY;
    private int cropEndX;
    private int cropEndY;
    private EditBox textField;
    private String status = "Ready";

    public Canvas2DScreen(Screen parent) {
        super(Component.literal("CraftShop 2D Editor"));
        this.parent = parent;
    }

    private int canvasLeft() { return 112; }
    private int canvasTop() { return 76; }
    private int canvasRight() { return Math.max(canvasLeft() + 180, this.width - 190); }
    private int canvasBottom() { return this.height - 54; }

    @Override
    protected void init() {
        int x = 12;
        int y = 70;
        addRenderableWidget(toolButton("Brush", x, y));
        addRenderableWidget(toolButton("Eraser", x, y + 24));
        addRenderableWidget(toolButton("Text", x, y + 48));
        addRenderableWidget(toolButton("Crop", x, y + 72));

        addRenderableWidget(Button.builder(Component.literal("Size -"), b -> brushSize = Math.max(1, brushSize - 2))
                .bounds(x, y + 104, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Size +"), b -> brushSize = Math.min(40, brushSize + 2))
                .bounds(x, y + 128, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Undo"), b -> undo())
                .bounds(x, y + 160, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Redo"), b -> redo())
                .bounds(x, y + 184, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> clearLayer())
                .bounds(x, y + 208, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Layer On/Off"), b -> toggleLayer())
                .bounds(x, y + 232, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset Crop"), b -> resetCrop())
                .bounds(x, y + 256, 78, 20).build());

        addRenderableWidget(Button.builder(Component.literal("New"), b -> newDocument())
                .bounds(172, 12, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Open"), b -> openLatest())
                .bounds(224, 12, 52, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveDocument())
                .bounds(280, 12, 52, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Add Layer"), b -> addLayer())
                .bounds(338, 12, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Delete Layer"), b -> deleteLayer())
                .bounds(418, 12, 88, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Bright +"), b -> adjustBrightness(12))
                .bounds(510, 12, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Bright -"), b -> adjustBrightness(-12))
                .bounds(586, 12, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Import Image"), b -> openImagePicker())
                .bounds(this.width - 178, 310, 158, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Grayscale"), b -> applyImageFilter("grayscale"))
                .bounds(this.width - 178, 336, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Invert"), b -> applyImageFilter("invert"))
                .bounds(this.width - 98, 336, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Sepia"), b -> applyImageFilter("sepia"))
                .bounds(this.width - 178, 360, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset Image"), b -> {
            checkpoint();
            document.backgroundImage = "";
            status = "Background image cleared";
        }).bounds(this.width - 98, 360, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Export PNG"), b -> exportCanvas())
                .bounds(662, 12, 82, 20).build());
        addRenderableWidget(Button.builder(Component.literal("3D Studio"), b -> returnToParent())
                .bounds(this.width - 100, 12, 88, 20).build());

        textField = new EditBox(this.font, this.width - 178, 104, 158, 20, Component.literal("Text to add"));
        textField.setMaxLength(120);
        textField.setValue("CraftShop");
        addRenderableWidget(textField);
        addRenderableWidget(Button.builder(Component.literal("Add Text"), b -> addText())
                .bounds(this.width - 178, 128, 158, 20).build());

        for (int i = 0; i < PALETTE.length; i++) {
            final int color = PALETTE[i];
            int col = i % 2;
            int row = i / 2;
            addRenderableWidget(Button.builder(Component.literal("   "), b -> activeColor = color)
                    .bounds(this.width - 174 + col * 78, 174 + row * 24, 70, 20).build());
        }

        int layerY = this.height - 46;
        int layerPages = Math.max(1, (document.layers.size() + 4) / 5);
        layerPage = Math.max(0, Math.min(layerPage, layerPages - 1));
        if (layerPages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                layerPage = Math.max(0, layerPage - 1);
                refreshWidgets();
            }).bounds(8, layerY, 24, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                layerPage = Math.min(layerPages - 1, layerPage + 1);
                refreshWidgets();
            }).bounds(this.width - 34, layerY, 24, 20).build());
        }
        int firstLayer = layerPage * 5;
        for (int slot = 0; slot < 5 && firstLayer + slot < document.layers.size(); slot++) {
            int index = firstLayer + slot;
            addRenderableWidget(Button.builder(Component.literal((index == activeLayer ? "> " : "")
                    + (document.layers.get(index).visible ? "[on] " : "[off] ") + document.layers.get(index).name),
                    b -> { activeLayer = index; refreshWidgets(); })
                    .bounds(38 + slot * Math.max(108, (this.width - 76) / 5), layerY,
                            Math.max(96, (this.width - 90) / 5), 20).build());
        }
    }

    private Button toolButton(String name, int x, int y) {
        return Button.builder(Component.literal((tool.equals(name) ? "> " : "") + name), b -> {
            tool = name;
            status = name + " tool";
            refreshWidgets();
        }).bounds(x, y, 78, 20).build();
    }

    private void refreshWidgets() {
        clearWidgets();
        init();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(0, 0, width, height, 0xFF101319);
        graphics.fill(8, 8, width - 8, 36, 0xFF1D232D);
        graphics.fill(8, 42, width - 8, height - 8, 0xFF171C24);
        graphics.fill(canvasLeft(), canvasTop(), canvasRight(), canvasBottom(), 0xFFFFFFFF);
        graphics.outline(canvasLeft() - 1, canvasTop() - 1,
                canvasRight() - canvasLeft() + 2, canvasBottom() - canvasTop() + 2, 0xFF596879);
        for (int i = 0; i < PALETTE.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int sx = this.width - 174 + col * 78;
            int sy = 170 + row * 22;
            graphics.fill(sx + 2, sy + 2, sx + 68, sy + 18, PALETTE[i]);
            if (activeColor == PALETTE[i]) graphics.outline(sx + 1, sy + 1, 68, 18, 0xFF00D8FF);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = this.width - 174 + (i % 2) * 78;
            int sy = 170 + (i / 2) * 22;
            graphics.fill(sx + 2, sy + 2, sx + 68, sy + 18, PALETTE[i]);
            if (activeColor == PALETTE[i]) graphics.outline(sx + 1, sy + 1, 68, 18, 0xFF00D8FF);
        }
        graphics.text(font, "CRAFTSHOP", 18, 14, 0xFFFFFFFF, true);
        graphics.text(font, "2D IMAGE EDITOR", 104, 14, 0xFF55DDEE, true);
        graphics.text(font, "TOOLS", 16, 54, 0xFF9AA8B8, false);
        graphics.text(font, "TEXT", width - 174, 82, 0xFF55DDEE, true);
        graphics.text(font, "COLOURS", width - 174, 154, 0xFF55DDEE, true);
        graphics.text(font, "Active layer: " + activeLayerName(), width - 178, height - 66, 0xFFB5C0CD, false);
        graphics.text(font, "Brush: " + brushSize + " px", 14, 282, 0xFFB5C0CD, false);
        graphics.text(font, "Tool: " + tool, 14, 298, 0xFFB5C0CD, false);
        graphics.text(font, "Ink brightness: " + document.brightness, width - 178, 284, 0xFFB5C0CD, false);

        if (document.backgroundImage != null && !document.backgroundImage.isBlank()) {
            try {
                Path source = resolveBackgroundPath();
                if (source != null && Files.isRegularFile(source)) {
                    GalleryTextureCache.TextureEntry texture = GalleryTextureCache.get(source);
                    if (texture != null) {
                        float scale = Math.min((float) (canvasRight() - canvasLeft()) / texture.width(),
                                (float) (canvasBottom() - canvasTop()) / texture.height());
                        int drawW = Math.max(1, Math.round(texture.width() * scale));
                        int drawH = Math.max(1, Math.round(texture.height() * scale));
                        int drawX = canvasLeft() + ((canvasRight() - canvasLeft()) - drawW) / 2;
                        int drawY = canvasTop() + ((canvasBottom() - canvasTop()) - drawH) / 2;
                        graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), drawX, drawY, 0.0F, 0.0F,
                                drawW, drawH, texture.width(), texture.height());
                    }
                }
            } catch (RuntimeException exception) {
                status = "Imported image unavailable";
            }
        }

        for (Layer layer : document.layers) {
            if (!layer.visible) continue;
            for (Stroke stroke : layer.strokes) {
                if (stroke.points == null || stroke.points.isEmpty()) continue;
                int color = stroke.eraser ? 0xFFFFFFFF : adjustColor(stroke.color, document.brightness);
                Point previous = null;
                for (Point point : stroke.points) {
                    if (!insideCrop(point.x, point.y)) { previous = point; continue; }
                    if (previous == null || !insideCrop(previous.x, previous.y)) {
                        drawDot(graphics, point.x, point.y, stroke.size, color);
                    } else {
                        drawSegment(graphics, previous.x, previous.y, point.x, point.y, stroke.size, color);
                    }
                    previous = point;
                }
            }
            for (TextItem item : layer.texts) {
                if (insideCrop(item.x, item.y)) {
                    graphics.text(font, item.text, Math.round(item.x), Math.round(item.y),
                            adjustColor(item.color, document.brightness), false);
                }
            }
        }

        if (document.cropWidth > 0 && document.cropHeight > 0) {
            int cx = Math.round(document.cropX);
            int cy = Math.round(document.cropY);
            int cw = Math.round(document.cropWidth);
            int ch = Math.round(document.cropHeight);
            graphics.fill(canvasLeft(), canvasTop(), canvasRight(), cy, 0x66000000);
            graphics.fill(canvasLeft(), cy + ch, canvasRight(), canvasBottom(), 0x66000000);
            graphics.fill(canvasLeft(), cy, cx, cy + ch, 0x66000000);
            graphics.fill(cx + cw, cy, canvasRight(), cy + ch, 0x66000000);
            graphics.outline(cx, cy, cw, ch, 0xFF00A6D8);
        }
        if (cropping) {
            graphics.outline(Math.min(cropStartX, cropEndX), Math.min(cropStartY, cropEndY),
                    Math.abs(cropEndX - cropStartX), Math.abs(cropEndY - cropStartY), 0xFFFF9800);
        }
        graphics.fill(0, height - 8, width, height, 0xFF0B0E13);
        graphics.text(font, "CRAFTSHOP 2D  •  " + document.name + "  •  " + document.layers.size() + " layers",
                10, height - 7, 0xFF7F8D9C, false);
        graphics.text(font, status, Math.max(280, width - 250), height - 7, 0xFF9DDDAA, false);
    }

    private String activeLayerName() {
        if (document.layers.isEmpty()) return "None";
        activeLayer = Math.max(0, Math.min(activeLayer, document.layers.size() - 1));
        return document.layers.get(activeLayer).name;
    }

    private boolean insideCanvas(double x, double y) {
        return x >= canvasLeft() && x < canvasRight() && y >= canvasTop() && y < canvasBottom();
    }

    private boolean insideCrop(float x, float y) {
        if (document.cropWidth <= 0 || document.cropHeight <= 0) return true;
        return x >= document.cropX && y >= document.cropY
                && x <= document.cropX + document.cropWidth && y <= document.cropY + document.cropHeight;
    }

    private void drawDot(GuiGraphicsExtractor graphics, float x, float y, int size, int color) {
        int radius = Math.max(1, size / 2);
        graphics.fill(Math.round(x) - radius, Math.round(y) - radius,
                Math.round(x) + radius + 1, Math.round(y) + radius + 1, color);
    }

    private void drawSegment(GuiGraphicsExtractor graphics, float x1, float y1, float x2, float y2,
                             int size, int color) {
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(x2 - x1, y2 - y1)));
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            drawDot(graphics, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, size, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && insideCanvas(mouseX, mouseY)) {
            if ("Crop".equals(tool)) {
                cropping = true;
                cropStartX = cropEndX = (int) mouseX;
                cropStartY = cropEndY = (int) mouseY;
                return true;
            }
            if ("Brush".equals(tool) || "Eraser".equals(tool)) {
                Layer layer = currentLayer();
                if (layer == null) return true;
                checkpoint();
                Stroke stroke = new Stroke();
                stroke.color = activeColor;
                stroke.size = brushSize;
                stroke.eraser = "Eraser".equals(tool);
                stroke.points.add(new Point((float) mouseX, (float) mouseY));
                layer.strokes.add(stroke);
                drawing = true;
                lastX = (float) mouseX;
                lastY = (float) mouseY;
                return true;
            }
            if ("Text".equals(tool)) {
                textAnchorX = (float) mouseX;
                textAnchorY = (float) mouseY;
                status = "Text position selected; enter text on the right";
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private float textAnchorX = 0;
    private float textAnchorY = 0;

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        if (cropping) {
            cropEndX = clamp((int) mouseX, canvasLeft(), canvasRight());
            cropEndY = clamp((int) mouseY, canvasTop(), canvasBottom());
            return true;
        }
        if (drawing) {
            float x = (float) Math.max(canvasLeft(), Math.min(canvasRight() - 1, mouseX));
            float y = (float) Math.max(canvasTop(), Math.min(canvasBottom() - 1, mouseY));
            currentLayer().strokes.get(currentLayer().strokes.size() - 1).points.add(new Point(x, y));
            lastX = x;
            lastY = y;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && drawing) {
            drawing = false;
            status = "Stroke added";
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && cropping) {
            cropping = false;
            int x = Math.min(cropStartX, cropEndX);
            int y = Math.min(cropStartY, cropEndY);
            int w = Math.abs(cropEndX - cropStartX);
            int h = Math.abs(cropEndY - cropStartY);
            if (w > 8 && h > 8) {
                checkpoint();
                document.cropX = x;
                document.cropY = y;
                document.cropWidth = w;
                document.cropHeight = h;
                status = "Crop bounds updated";
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private Layer currentLayer() {
        if (document.layers.isEmpty()) document.layers.add(new Layer("Layer 1"));
        activeLayer = Math.max(0, Math.min(activeLayer, document.layers.size() - 1));
        return document.layers.get(activeLayer);
    }

    private void openImagePicker() {
        this.minecraft.gui.setScreen(new CanvasImagePickerScreen(this, path -> {
            try {
                Path assets = directory().resolve("assets");
                Files.createDirectories(assets);
                String safeName = path.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
                Path imported = assets.resolve("import-" + System.currentTimeMillis() + "-" + safeName);
                Files.copy(path, imported);
                checkpoint();
                document.backgroundImage = "assets/" + imported.getFileName();
                status = "Imported " + path.getFileName();
            } catch (IOException exception) {
                CraftShop.LOGGER.error("Could not copy image into CraftShop project assets", exception);
                status = "Image import failed";
            }
        }));
    }

    private Path resolveBackgroundPath() {
        if (document.backgroundImage == null || document.backgroundImage.isBlank()) return null;
        Path stored = Path.of(document.backgroundImage);
        return stored.isAbsolute() ? stored : directory().resolve(stored).normalize();
    }

    private void applyImageFilter(String filter) {
        if (document.backgroundImage == null || document.backgroundImage.isBlank()) {
            status = "Import an image first";
            return;
        }
        Path sourcePath = resolveBackgroundPath();
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) { status = "Imported image is missing"; return; }
        try {
            BufferedImage source = ImageIO.read(sourcePath.toFile());
            if (source == null) { status = "Image could not be decoded"; return; }
            BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int argb = source.getRGB(x, y);
                    int a = (argb >>> 24) & 255;
                    int red = (argb >>> 16) & 255;
                    int green = (argb >>> 8) & 255;
                    int blue = argb & 255;
                    int nr = red, ng = green, nb = blue;
                    switch (filter) {
                        case "grayscale" -> {
                            int gray = (int) (0.2126 * red + 0.7152 * green + 0.0722 * blue);
                            nr = ng = nb = gray;
                        }
                        case "invert" -> { nr = 255 - red; ng = 255 - green; nb = 255 - blue; }
                        case "sepia" -> {
                            nr = Math.min(255, (int) (0.393 * red + 0.769 * green + 0.189 * blue));
                            ng = Math.min(255, (int) (0.349 * red + 0.686 * green + 0.168 * blue));
                            nb = Math.min(255, (int) (0.272 * red + 0.534 * green + 0.131 * blue));
                        }
                        default -> { }
                    }
                    output.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
                }
            }
            Path assets = directory().resolve("assets");
            Files.createDirectories(assets);
            Path filtered = assets.resolve("filtered-" + filter + "-" + System.currentTimeMillis() + ".png");
            ImageIO.write(output, "png", filtered.toFile());
            source.flush();
            output.flush();
            checkpoint();
            document.backgroundImage = "assets/" + filtered.getFileName();
            status = filter + " filter applied";
        } catch (IOException | RuntimeException exception) {
            CraftShop.LOGGER.error("Could not apply image filter {}", filter, exception);
            status = "Image filter failed";
        }
    }

    private void addText() {
        String value = textField == null ? "" : textField.getValue().trim();
        if (value.isEmpty()) return;
        Layer layer = currentLayer();
        float x = textAnchorX == 0 ? canvasLeft() + 24 : textAnchorX;
        float y = textAnchorY == 0 ? canvasTop() + 30 : textAnchorY;
        checkpoint();
        layer.texts.add(new TextItem(value, x, y, activeColor));
        status = "Text added to " + layer.name;
    }

    private void addLayer() {
        checkpoint();
        document.layers.add(new Layer("Layer " + (document.layers.size() + 1)));
        activeLayer = document.layers.size() - 1;
        layerPage = activeLayer / 5;
        refreshWidgets();
        status = "Layer added";
    }

    private void deleteLayer() {
        if (document.layers.size() <= 1) { status = "Keep at least one layer"; return; }
        checkpoint();
        document.layers.remove(activeLayer);
        activeLayer = Math.max(0, activeLayer - 1);
        layerPage = activeLayer / 5;
        refreshWidgets();
        status = "Layer deleted";
    }

    private void toggleLayer() {
        checkpoint();
        Layer layer = currentLayer();
        layer.visible = !layer.visible;
        status = layer.name + (layer.visible ? " visible" : " hidden");
        refreshWidgets();
    }

    private void resetCrop() {
        checkpoint();
        document.cropX = 0;
        document.cropY = 0;
        document.cropWidth = 0;
        document.cropHeight = 0;
        status = "Crop reset";
    }

    private void clearLayer() {
        checkpoint();
        Layer layer = currentLayer();
        layer.strokes.clear();
        layer.texts.clear();
        status = "Active layer cleared";
    }

    private void checkpoint() {
        undoHistory.push(GSON.toJson(document));
        while (undoHistory.size() > 20) undoHistory.removeLast();
        redoHistory.clear();
    }

    private void undo() {
        if (undoHistory.isEmpty()) { status = "Nothing to undo"; return; }
        redoHistory.push(GSON.toJson(document));
        restoreDocument(undoHistory.pop());
        status = "Undo";
        refreshWidgets();
    }

    private void redo() {
        if (redoHistory.isEmpty()) { status = "Nothing to redo"; return; }
        undoHistory.push(GSON.toJson(document));
        restoreDocument(redoHistory.pop());
        status = "Redo";
        refreshWidgets();
    }

    private void restoreDocument(String json) {
        CanvasDocument restored = GSON.fromJson(json, CanvasDocument.class);
        if (restored != null && restored.layers != null && !restored.layers.isEmpty()) {
            document = restored;
            activeLayer = Math.max(0, Math.min(activeLayer, document.layers.size() - 1));
            layerPage = activeLayer / 5;
        }
    }

    private void adjustBrightness(int amount) {
        checkpoint();
        document.brightness = clamp(document.brightness + amount, -100, 100);
        status = "Brightness: " + document.brightness;
    }

    private void newDocument() {
        checkpoint();
        document = new CanvasDocument();
        activeLayer = 0;
        layerPage = 0;
        status = "New canvas";
        refreshWidgets();
    }

    private Path directory() {
        return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                .resolve("craftshop").resolve("canvas");
    }

    private void saveDocument() {
        try {
            Files.createDirectories(directory());
            String safe = document.name.replaceAll("[^A-Za-z0-9._-]", "_");
            Path file = directory().resolve(safe + ".craftshop2d");
            Files.writeString(file, GSON.toJson(document));
            status = "Saved " + file.getFileName();
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not save CraftShop 2D document", exception);
            status = "Save failed";
        }
    }

    private void openLatest() {
        try {
            Files.createDirectories(directory());
            try (var files = Files.list(directory())) {
                Path latest = files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".craftshop2d"))
                        .max(java.util.Comparator.comparingLong(path -> path.toFile().lastModified()))
                        .orElse(null);
                if (latest == null) { status = "No saved canvas found"; return; }
                CanvasDocument loaded = GSON.fromJson(Files.readString(latest), CanvasDocument.class);
                if (loaded == null || loaded.layers == null || loaded.layers.isEmpty()) {
                    status = "Invalid canvas file";
                    return;
                }
                document = loaded;
                undoHistory.clear();
                redoHistory.clear();
                activeLayer = 0;
                layerPage = 0;
                status = "Opened " + latest.getFileName();
                refreshWidgets();
            }
        } catch (Exception exception) {
            CraftShop.LOGGER.error("Could not open CraftShop 2D document", exception);
            status = "Open failed";
        }
    }

    private void exportCanvas() {
        status = "Exporting canvas...";
        int x = document.cropWidth > 0 ? Math.round(document.cropX) : canvasLeft();
        int y = document.cropHeight > 0 ? Math.round(document.cropY) : canvasTop();
        int w = document.cropWidth > 0 ? Math.round(document.cropWidth) : canvasRight() - canvasLeft();
        int h = document.cropHeight > 0 ? Math.round(document.cropHeight) : canvasBottom() - canvasTop();
        SceneExporter.exportViewport(this.minecraft, x, y, w, h, message -> status = message);
    }

    private void returnToParent() {
        this.minecraft.gui.setScreen(parent);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int adjustColor(int color, int brightness) {
        int a = (color >>> 24) & 255;
        int r = clamp(((color >>> 16) & 255) + brightness, 0, 255);
        int g = clamp(((color >>> 8) & 255) + brightness, 0, 255);
        int b = clamp((color & 255) + brightness, 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (textField != null && textField.isFocused()) return super.keyPressed(keyCode, scanCode, modifiers);
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_Z) {
            undo();
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_S) {
            saveDocument();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            returnToParent();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() { returnToParent(); }

    @Override
    public boolean isPauseScreen() { return false; }

    private static final class CanvasDocument {
        String name = "Untitled Canvas";
        int brightness = 0;
        String backgroundImage = "";
        float cropX;
        float cropY;
        float cropWidth;
        float cropHeight;
        List<Layer> layers = new ArrayList<>(List.of(new Layer("Layer 1")));
    }

    private static final class Layer {
        String name;
        boolean visible = true;
        List<Stroke> strokes = new ArrayList<>();
        List<TextItem> texts = new ArrayList<>();
        Layer(String name) { this.name = name; }
    }

    private static final class Stroke {
        int color;
        int size;
        boolean eraser;
        List<Point> points = new ArrayList<>();
    }

    private static final class Point {
        float x;
        float y;
        Point(float x, float y) { this.x = x; this.y = y; }
    }

    private static final class TextItem {
        String text;
        float x;
        float y;
        int color;
        TextItem(String text, float x, float y, int color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }
}
