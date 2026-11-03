package dev.modsfinder.craftshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.imageio.ImageIO;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

public final class Canvas2DScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int[] PALETTE = {
            0xFF111111, 0xFFFFFFFF, 0xFFE53935, 0xFFFB8C00,
            0xFFFDD835, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5,
            0xFF5E35B1, 0xFFD81B60
    };

    private final Screen parent;
    private final Consumer<Path> exportCallback;
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
    private boolean shaping;
    private boolean selecting;
    private boolean movingSelection;
    private boolean scalingSelection;
    private boolean hasSelection;
    private float selectionStartX, selectionStartY, selectionEndX, selectionEndY;
    private float selectionLeft, selectionTop, selectionRight, selectionBottom;
    private float selectionLastX, selectionLastY;
    private float shapeStartX;
    private float shapeStartY;
    private float shapeEndX;
    private float shapeEndY;
    private float lastX;
    private float lastY;
    private int cropStartX;
    private int cropStartY;
    private int cropEndX;
    private int cropEndY;
    private EditBox textField;
    private EditBox projectNameField;
    private String status = "Ready";

    public Canvas2DScreen(Screen parent) {
        this(parent, null, null);
    }

    public Canvas2DScreen(Screen parent, Path initialImage, Consumer<Path> exportCallback) {
        super(Component.literal("CraftShop 2D Editor"));
        this.parent = parent;
        this.exportCallback = exportCallback;
        if (initialImage != null && Files.isRegularFile(initialImage)) {
            document.backgroundImage = initialImage.toAbsolutePath().normalize().toString();
            String filename = initialImage.getFileName().toString();
            int dot = filename.lastIndexOf('.');
            document.name = dot > 0 ? filename.substring(0, dot) : filename;
        }
    }

    private int canvasLeft() { return 112; }
    private int canvasTop() { return 76; }
    private int canvasRight() { return Math.max(canvasLeft() + 180, this.width - 190); }
    private int canvasBottom() { return this.height - 54; }

    @Override
    protected void init() {
        int x = 12;
        int y = 70;
        int toolStep = this.height < 600 ? 18 : 24;
        int controlStep = this.height < 480 ? 18 : this.height < 600 ? 20 : 24;
        int controlsTop = y + toolStep * 8 + 8;
        addRenderableWidget(toolButton("Brush", x, y));
        addRenderableWidget(toolButton("Eraser", x, y + toolStep));
        addRenderableWidget(toolButton("Text", x, y + toolStep * 2));
        addRenderableWidget(toolButton("Crop", x, y + toolStep * 3));
        addRenderableWidget(toolButton("Line", x, y + toolStep * 4));
        addRenderableWidget(toolButton("Rectangle", x, y + toolStep * 5));
        addRenderableWidget(toolButton("Ellipse", x, y + toolStep * 6));
        addRenderableWidget(toolButton("Select", x, y + toolStep * 7));

        addRenderableWidget(Button.builder(Component.literal("Size -"), b -> brushSize = Math.max(1, brushSize - 2))
                .bounds(x, controlsTop, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Size +"), b -> brushSize = Math.min(40, brushSize + 2))
                .bounds(x, controlsTop + controlStep, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Undo"), b -> undo())
                .bounds(x, controlsTop + controlStep * 2, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Redo"), b -> redo())
                .bounds(x, controlsTop + controlStep * 3, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> clearLayer())
                .bounds(x, controlsTop + controlStep * 4, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Layer On/Off"), b -> toggleLayer())
                .bounds(x, controlsTop + controlStep * 5, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset Crop"), b -> resetCrop())
                .bounds(x, controlsTop + controlStep * 6, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Opacity +"), b -> adjustLayerOpacity(0.1F))
                .bounds(x, controlsTop + controlStep * 7, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Opacity -"), b -> adjustLayerOpacity(-0.1F))
                .bounds(x, controlsTop + controlStep * 8, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Layer Up"), b -> moveLayer(-1))
                .bounds(x, controlsTop + controlStep * 9, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Layer Down"), b -> moveLayer(1))
                .bounds(x, controlsTop + controlStep * 10, 78, 20).build());

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
        addRenderableWidget(Button.builder(Component.literal("Export PNG"), b -> exportCanvas())
                .bounds(662, 12, 82, 20).build());
        addRenderableWidget(Button.builder(Component.literal("3D Studio"), b -> returnToParent())
                .bounds(this.width - 100, 12, 88, 20).build());

        projectNameField = new EditBox(this.font, 116, 44, 190, 20, Component.literal("Project name"));
        projectNameField.setMaxLength(48);
        projectNameField.setValue(document.name == null ? "Untitled Canvas" : document.name);
        addRenderableWidget(projectNameField);

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
        graphics.text(font, "PROJECT", 16, 50, 0xFF9AA8B8, false);
        graphics.text(font, "2D IMAGE EDITOR", 104, 14, 0xFF55DDEE, true);
        graphics.text(font, "TOOLS", 16, 54, 0xFF9AA8B8, false);
        graphics.text(font, "TEXT", width - 174, 82, 0xFF55DDEE, true);
        graphics.text(font, "COLOURS", width - 174, 154, 0xFF55DDEE, true);
        graphics.text(font, "Active layer: " + activeLayerName(), width - 178, height - 66, 0xFFB5C0CD, false);
        graphics.text(font, "Brush: " + brushSize + " px", 14, 282, 0xFFB5C0CD, false);
        graphics.text(font, "Tool: " + tool, 14, 298, 0xFFB5C0CD, false);
        graphics.text(font, "Ink brightness: " + document.brightness, width - 178, 284, 0xFFB5C0CD, false);
        graphics.text(font, "Layer opacity: " + Math.round(currentLayer().opacity * 100) + "%", 14, 382, 0xFFB5C0CD, false);

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
                int color = withOpacity(stroke.eraser ? 0xFFFFFFFF : adjustColor(stroke.color, document.brightness), layer.opacity);
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
            for (ShapeItem item : layer.shapes) {
                if (insideCrop(item.x1, item.y1) || insideCrop(item.x2, item.y2)) {
                    drawCanvasShape(graphics, item.type, item.x1, item.y1, item.x2, item.y2,
                            item.size, withOpacity(adjustColor(item.color, document.brightness), layer.opacity));
                }
            }
            for (TextItem item : layer.texts) {
                if (insideCrop(item.x, item.y)) {
                    graphics.text(font, item.text, Math.round(item.x), Math.round(item.y),
                            withOpacity(adjustColor(item.color, document.brightness), layer.opacity), false);
                }
            }
        }

        if (shaping) {
            drawCanvasShape(graphics, tool, shapeStartX, shapeStartY, shapeEndX, shapeEndY,
                    brushSize, activeColor);
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
        if ("Select".equals(tool) && (selecting || hasSelection)) {
            float left = selecting ? Math.min(selectionStartX, selectionEndX) : selectionLeft;
            float top = selecting ? Math.min(selectionStartY, selectionEndY) : selectionTop;
            float right = selecting ? Math.max(selectionStartX, selectionEndX) : selectionRight;
            float bottom = selecting ? Math.max(selectionStartY, selectionEndY) : selectionBottom;
            int sx = Math.round(left);
            int sy = Math.round(top);
            int sw = Math.max(1, Math.round(right - left));
            int sh = Math.max(1, Math.round(bottom - top));
            graphics.outline(sx, sy, sw, sh, 0xFF00A6D8);
            if (hasSelection && !selecting) {
                graphics.fill(Math.round(right) - 3, Math.round(bottom) - 3,
                        Math.round(right) + 4, Math.round(bottom) + 4, 0xFF00D8FF);
            }
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

    private void drawCanvasShape(GuiGraphicsExtractor graphics, String type, float x1, float y1,
                                 float x2, float y2, int size, int color) {
        if ("Line".equals(type)) {
            drawSegment(graphics, x1, y1, x2, y2, size, color);
            return;
        }
        float left = Math.min(x1, x2);
        float top = Math.min(y1, y2);
        float right = Math.max(x1, x2);
        float bottom = Math.max(y1, y2);
        if ("Rectangle".equals(type)) {
            drawSegment(graphics, left, top, right, top, size, color);
            drawSegment(graphics, right, top, right, bottom, size, color);
            drawSegment(graphics, right, bottom, left, bottom, size, color);
            drawSegment(graphics, left, bottom, left, top, size, color);
            return;
        }
        float centerX = (left + right) * 0.5F;
        float centerY = (top + bottom) * 0.5F;
        float radiusX = Math.max(0.5F, (right - left) * 0.5F);
        float radiusY = Math.max(0.5F, (bottom - top) * 0.5F);
        float previousX = centerX + radiusX;
        float previousY = centerY;
        for (int i = 1; i <= 64; i++) {
            double angle = Math.PI * 2.0 * i / 64.0;
            float nextX = centerX + (float) Math.cos(angle) * radiusX;
            float nextY = centerY + (float) Math.sin(angle) * radiusY;
            drawSegment(graphics, previousX, previousY, nextX, nextY, size, color);
            previousX = nextX;
            previousY = nextY;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && insideCanvas(mouseX, mouseY)) {
            if ("Select".equals(tool)) {
                float px = (float) mouseX;
                float py = (float) mouseY;
                if (hasSelection && Math.abs(px - selectionRight) <= 8.0F
                        && Math.abs(py - selectionBottom) <= 8.0F) {
                    scalingSelection = true;
                    selectionLastX = px;
                    selectionLastY = py;
                    checkpoint();
                } else if (hasSelection && insideSelection(px, py)) {
                    movingSelection = true;
                    selectionLastX = px;
                    selectionLastY = py;
                    checkpoint();
                } else {
                    selecting = true;
                    hasSelection = false;
                    selectionStartX = selectionEndX = px;
                    selectionStartY = selectionEndY = py;
                }
                return true;
            }
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
                stroke.eraseBackground = stroke.eraser && document.backgroundImage != null
                        && !document.backgroundImage.isBlank();
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
            if ("Line".equals(tool) || "Rectangle".equals(tool) || "Ellipse".equals(tool)) {
                shaping = true;
                shapeStartX = shapeEndX = (float) mouseX;
                shapeStartY = shapeEndY = (float) mouseY;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private float textAnchorX = 0;
    private float textAnchorY = 0;

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseDragged(event, dragX, dragY);
        if (selecting) {
            selectionEndX = Math.max(canvasLeft(), Math.min(canvasRight() - 1, (float) mouseX));
            selectionEndY = Math.max(canvasTop(), Math.min(canvasBottom() - 1, (float) mouseY));
            return true;
        }
        if (movingSelection) {
            float x = (float) mouseX;
            float y = (float) mouseY;
            translateSelection(x - selectionLastX, y - selectionLastY);
            selectionLastX = x;
            selectionLastY = y;
            return true;
        }
        if (scalingSelection) {
            scaleSelectionTo((float) mouseX, (float) mouseY);
            return true;
        }
        if (cropping) {
            cropEndX = clamp((int) mouseX, canvasLeft(), canvasRight());
            cropEndY = clamp((int) mouseY, canvasTop(), canvasBottom());
            return true;
        }
        if (shaping) {
            shapeEndX = (float) Math.max(canvasLeft(), Math.min(canvasRight() - 1, mouseX));
            shapeEndY = (float) Math.max(canvasTop(), Math.min(canvasBottom() - 1, mouseY));
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
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int button = event.button();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && selecting) {
            selecting = false;
            selectionLeft = Math.min(selectionStartX, selectionEndX);
            selectionTop = Math.min(selectionStartY, selectionEndY);
            selectionRight = Math.max(selectionStartX, selectionEndX);
            selectionBottom = Math.max(selectionStartY, selectionEndY);
            hasSelection = selectionRight - selectionLeft >= 6.0F
                    && selectionBottom - selectionTop >= 6.0F;
            status = hasSelection ? "Selection created on " + currentLayer().name : "Selection cancelled";
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && movingSelection) {
            movingSelection = false;
            status = "Selection moved";
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && scalingSelection) {
            scalingSelection = false;
            status = "Selection resized";
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && shaping) {
            shaping = false;
            if (Math.hypot(shapeEndX - shapeStartX, shapeEndY - shapeStartY) > 2.0) {
                checkpoint();
                currentLayer().shapes.add(new ShapeItem(tool, shapeStartX, shapeStartY,
                        shapeEndX, shapeEndY, activeColor, brushSize));
                status = tool + " added";
            }
            return true;
        }
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
        return super.mouseReleased(event);
    }

    private boolean insideSelection(float x, float y) {
        return x >= selectionLeft && x <= selectionRight
                && y >= selectionTop && y <= selectionBottom;
    }

    private void translateSelection(float dx, float dy) {
        if (!hasSelection || (dx == 0.0F && dy == 0.0F)) return;
        dx = Math.max(canvasLeft() - selectionLeft,
                Math.min(canvasRight() - selectionRight, dx));
        dy = Math.max(canvasTop() - selectionTop,
                Math.min(canvasBottom() - selectionBottom, dy));
        if (dx == 0.0F && dy == 0.0F) return;

        Layer layer = currentLayer();
        for (Stroke stroke : layer.strokes) {
            for (Point point : stroke.points) {
                if (insideSelection(point.x, point.y)) {
                    point.x += dx;
                    point.y += dy;
                }
            }
        }
        for (ShapeItem item : layer.shapes) {
            if (insideSelection(item.x1, item.y1) && insideSelection(item.x2, item.y2)) {
                item.x1 += dx; item.x2 += dx;
                item.y1 += dy; item.y2 += dy;
            }
        }
        for (TextItem item : layer.texts) {
            if (insideSelection(item.x, item.y)) {
                item.x += dx;
                item.y += dy;
            }
        }
        selectionLeft += dx; selectionRight += dx;
        selectionTop += dy; selectionBottom += dy;
    }

    private void scaleSelectionTo(float mouseX, float mouseY) {
        if (!hasSelection) return;
        float oldWidth = Math.max(1.0F, selectionRight - selectionLeft);
        float oldHeight = Math.max(1.0F, selectionBottom - selectionTop);
        float newRight = Math.max(selectionLeft + 6.0F,
                Math.min(canvasRight() - 1.0F, mouseX));
        float newBottom = Math.max(selectionTop + 6.0F,
                Math.min(canvasBottom() - 1.0F, mouseY));
        float scaleX = (newRight - selectionLeft) / oldWidth;
        float scaleY = (newBottom - selectionTop) / oldHeight;
        if (Math.abs(scaleX - 1.0F) < 0.0001F && Math.abs(scaleY - 1.0F) < 0.0001F) return;

        Layer layer = currentLayer();
        for (Stroke stroke : layer.strokes) {
            for (Point point : stroke.points) {
                if (insideSelection(point.x, point.y)) {
                    point.x = selectionLeft + (point.x - selectionLeft) * scaleX;
                    point.y = selectionTop + (point.y - selectionTop) * scaleY;
                }
            }
        }
        for (ShapeItem item : layer.shapes) {
            if (insideSelection(item.x1, item.y1) && insideSelection(item.x2, item.y2)) {
                item.x1 = selectionLeft + (item.x1 - selectionLeft) * scaleX;
                item.x2 = selectionLeft + (item.x2 - selectionLeft) * scaleX;
                item.y1 = selectionTop + (item.y1 - selectionTop) * scaleY;
                item.y2 = selectionTop + (item.y2 - selectionTop) * scaleY;
            }
        }
        for (TextItem item : layer.texts) {
            if (insideSelection(item.x, item.y)) {
                item.x = selectionLeft + (item.x - selectionLeft) * scaleX;
                item.y = selectionTop + (item.y - selectionTop) * scaleY;
            }
        }
        selectionRight = newRight;
        selectionBottom = newBottom;
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
        try {
            Path stored = Path.of(document.backgroundImage);
            if (stored.isAbsolute()) return stored.normalize();
            Path root = directory().toAbsolutePath().normalize();
            Path resolved = root.resolve(stored).normalize();
            return resolved.startsWith(root) ? resolved : null;
        } catch (RuntimeException exception) {
            return null;
        }
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

    private void adjustLayerOpacity(float amount) {
        checkpoint();
        Layer layer = currentLayer();
        layer.opacity = Math.max(0.05F, Math.min(1.0F, layer.opacity + amount));
        status = "Layer opacity: " + Math.round(layer.opacity * 100) + "%";
    }

    private void moveLayer(int direction) {
        int target = activeLayer + direction;
        if (target < 0 || target >= document.layers.size()) return;
        checkpoint();
        java.util.Collections.swap(document.layers, activeLayer, target);
        activeLayer = target;
        layerPage = activeLayer / 5;
        refreshWidgets();
        status = "Layer order changed";
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
        layer.shapes.clear();
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
            normalizeDocument(restored);
            document = restored;
            activeLayer = Math.max(0, Math.min(activeLayer, document.layers.size() - 1));
            layerPage = activeLayer / 5;
            if (projectNameField != null) projectNameField.setValue(document.name);
        }
    }

    private void normalizeDocument(CanvasDocument candidate) {
        if (candidate.name == null || candidate.name.isBlank()) candidate.name = "Untitled Canvas";
        if (candidate.backgroundImage == null) candidate.backgroundImage = "";
        if (candidate.layers == null) candidate.layers = new ArrayList<>();
        for (Layer layer : candidate.layers) {
            if (layer == null) continue;
            if (layer.name == null) layer.name = "Layer";
            if (layer.opacity <= 0.0F) layer.opacity = 1.0F;
            layer.opacity = Math.max(0.05F, Math.min(1.0F, layer.opacity));
            if (layer.strokes == null) layer.strokes = new ArrayList<>();
            if (layer.texts == null) layer.texts = new ArrayList<>();
            if (layer.shapes == null) layer.shapes = new ArrayList<>();
            for (Stroke stroke : layer.strokes) if (stroke.points == null) stroke.points = new ArrayList<>();
        }
        candidate.layers.removeIf(java.util.Objects::isNull);
        if (candidate.layers.isEmpty()) candidate.layers.add(new Layer("Layer 1"));
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
        if (projectNameField != null) projectNameField.setValue(document.name);
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
            String requestedName = projectNameField == null ? document.name : projectNameField.getValue().trim();
            if (requestedName == null || requestedName.isBlank()) requestedName = "Untitled Canvas";
            document.name = requestedName;
            String safe = document.name.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^[.]+", "");
            if (safe.isBlank()) safe = "Untitled_Canvas";
            Path root = directory().toAbsolutePath().normalize();
            Path file = root.resolve(safe + ".craftshop2d").normalize();
            if (!file.getParent().equals(root)) throw new IOException("Invalid canvas project path");
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try {
                Files.writeString(temporary, GSON.toJson(document));
                try {
                    Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            if (projectNameField != null) projectNameField.setValue(document.name);
            status = "Saved " + file.getFileName();
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not save CraftShop 2D document", exception);
            status = "Save failed";
        }
    }

    private void openProjectPicker() {
        this.minecraft.gui.setScreen(new CanvasProjectPickerScreen(this, this::loadDocument));
    }

    private void loadDocument(Path selectedFile) {
        try {
            Path root = directory().toAbsolutePath().normalize();
            Path normalized = selectedFile.toAbsolutePath().normalize();
            if (!normalized.startsWith(root) || !normalized.getFileName().toString().endsWith(".craftshop2d")) {
                throw new IOException("Invalid CraftShop 2D project path");
            }
            CanvasDocument loaded = GSON.fromJson(Files.readString(normalized), CanvasDocument.class);
            if (loaded == null || loaded.layers == null || loaded.layers.isEmpty()) {
                status = "Invalid canvas file";
                return;
            }
            normalizeDocument(loaded);
            document = loaded;
            undoHistory.clear();
            redoHistory.clear();
            activeLayer = 0;
            layerPage = 0;
            if (projectNameField != null) projectNameField.setValue(document.name);
            status = "Opened " + selectedFile.getFileName();
            refreshWidgets();
        } catch (Exception exception) {
            CraftShop.LOGGER.error("Could not open CraftShop 2D document {}", selectedFile, exception);
            status = "Open failed";
        }
    }

    private void exportCanvas() {
        status = "Exporting full-resolution PNG...";
        BufferedImage source = null;
        BufferedImage output = null;
        Graphics2D graphics = null;
        try {
            int outputWidth = 1280;
            int outputHeight = 720;
            Path sourcePath = resolveBackgroundPath();
            if (sourcePath != null && Files.isRegularFile(sourcePath)) {
                source = ImageIO.read(sourcePath.toFile());
                if (source == null) throw new IOException("The selected PNG could not be decoded");
                outputWidth = source.getWidth();
                outputHeight = source.getHeight();
            }

            output = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB);
            graphics = output.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, outputWidth, outputHeight);

            float displayX = canvasLeft();
            float displayY = canvasTop();
            float displayWidth = canvasRight() - canvasLeft();
            float displayHeight = canvasBottom() - canvasTop();
            if (source != null) {
                float fit = Math.min(displayWidth / source.getWidth(), displayHeight / source.getHeight());
                displayWidth = source.getWidth() * fit;
                displayHeight = source.getHeight() * fit;
                displayX += ((canvasRight() - canvasLeft()) - displayWidth) / 2.0F;
                displayY += ((canvasBottom() - canvasTop()) - displayHeight) / 2.0F;
                graphics.drawImage(source, 0, 0, outputWidth, outputHeight, null);
            }

            float sx = outputWidth / displayWidth;
            float sy = outputHeight / displayHeight;

            // Apply image eraser strokes directly to the imported raster before compositing paint layers.
            for (Layer layer : document.layers) {
                if (!layer.visible) continue;
                for (Stroke stroke : layer.strokes) {
                    if (!stroke.eraseBackground || stroke.points == null || stroke.points.isEmpty()) continue;
                    graphics.setComposite(AlphaComposite.Clear);
                    graphics.setStroke(new BasicStroke(Math.max(1.0F, stroke.size * (sx + sy) * 0.5F),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    Path2D.Float erasePath = new Path2D.Float();
                    Point first = stroke.points.get(0);
                    erasePath.moveTo((first.x - displayX) * sx, (first.y - displayY) * sy);
                    for (int i = 1; i < stroke.points.size(); i++) {
                        Point point = stroke.points.get(i);
                        erasePath.lineTo((point.x - displayX) * sx, (point.y - displayY) * sy);
                    }
                    if (stroke.points.size() == 1) {
                        Point point = stroke.points.get(0);
                        float px = (point.x - displayX) * sx;
                        float py = (point.y - displayY) * sy;
                        graphics.draw(new java.awt.geom.Line2D.Float(px, py, px + 0.01F, py + 0.01F));
                    } else {
                        graphics.draw(erasePath);
                    }
                }
            }
            graphics.setComposite(AlphaComposite.SrcOver);

            for (Layer layer : document.layers) {
                if (!layer.visible) continue;
                BufferedImage layerImage = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D layerGraphics = layerImage.createGraphics();
                layerGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                for (Stroke stroke : layer.strokes) {
                    if (stroke.eraseBackground || stroke.points == null || stroke.points.isEmpty()) continue;
                    if (stroke.eraser) {
                        layerGraphics.setComposite(AlphaComposite.Clear);
                    } else {
                        layerGraphics.setComposite(AlphaComposite.SrcOver);
                        layerGraphics.setColor(new Color(adjustColor(stroke.color, document.brightness), true));
                    }
                    layerGraphics.setStroke(new BasicStroke(Math.max(1.0F, stroke.size * (sx + sy) * 0.5F),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    Path2D.Float path = new Path2D.Float();
                    Point first = stroke.points.get(0);
                    path.moveTo((first.x - displayX) * sx, (first.y - displayY) * sy);
                    for (int i = 1; i < stroke.points.size(); i++) {
                        Point point = stroke.points.get(i);
                        path.lineTo((point.x - displayX) * sx, (point.y - displayY) * sy);
                    }
                    if (stroke.points.size() == 1) {
                        Point point = stroke.points.get(0);
                        float px = (point.x - displayX) * sx;
                        float py = (point.y - displayY) * sy;
                        layerGraphics.draw(new java.awt.geom.Line2D.Float(px, py, px + 0.01F, py + 0.01F));
                    } else {
                        layerGraphics.draw(path);
                    }
                }
                layerGraphics.setComposite(AlphaComposite.SrcOver);
                layerGraphics.setColor(Color.WHITE);
                layerGraphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(12, Math.round(16 * sx))));
                for (ShapeItem item : layer.shapes) {
                    layerGraphics.setColor(new Color(adjustColor(item.color, document.brightness), true));
                    layerGraphics.setStroke(new BasicStroke(Math.max(1.0F, item.size * (sx + sy) * 0.5F),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    float x1 = (item.x1 - displayX) * sx;
                    float y1 = (item.y1 - displayY) * sy;
                    float x2 = (item.x2 - displayX) * sx;
                    float y2 = (item.y2 - displayY) * sy;
                    float left = Math.min(x1, x2);
                    float top = Math.min(y1, y2);
                    float width = Math.abs(x2 - x1);
                    float height = Math.abs(y2 - y1);
                    switch (item.type) {
                        case "Line" -> layerGraphics.draw(new java.awt.geom.Line2D.Float(x1, y1, x2, y2));
                        case "Rectangle" -> layerGraphics.draw(new java.awt.geom.Rectangle2D.Float(left, top, width, height));
                        case "Ellipse" -> layerGraphics.draw(new java.awt.geom.Ellipse2D.Float(left, top, width, height));
                        default -> { }
                    }
                }
                for (TextItem item : layer.texts) {
                    layerGraphics.setColor(new Color(adjustColor(item.color, document.brightness), true));
                    layerGraphics.drawString(item.text, (item.x - displayX) * sx, (item.y - displayY) * sy);
                }
                layerGraphics.dispose();
                graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0.0F, Math.min(1.0F, layer.opacity))));
                graphics.drawImage(layerImage, 0, 0, null);
                graphics.setComposite(AlphaComposite.SrcOver);
                layerImage.flush();
            }
            int cropX = 0, cropY = 0, cropW = outputWidth, cropH = outputHeight;
            if (document.cropWidth > 0 && document.cropHeight > 0) {
                cropX = Math.max(0, Math.round((document.cropX - displayX) * sx));
                cropY = Math.max(0, Math.round((document.cropY - displayY) * sy));
                cropW = Math.min(outputWidth - cropX, Math.round(document.cropWidth * sx));
                cropH = Math.min(outputHeight - cropY, Math.round(document.cropHeight * sy));
            }
            if (cropW <= 0 || cropH <= 0) throw new IOException("Crop area is outside the image");
            BufferedImage cropped = output.getSubimage(cropX, cropY, cropW, cropH);
            Path directory = directory().resolve("exports");
            Files.createDirectories(directory);
            String safeName = document.name == null ? "CraftShop" : document.name.replaceAll("[^A-Za-z0-9._-]", "_");
            if (safeName.isBlank()) safeName = "CraftShop";
            Path destination = directory.resolve(safeName + "-" + System.currentTimeMillis() + ".png");
            if (!ImageIO.write(cropped, "png", destination.toFile())) {
                throw new IOException("PNG encoder is unavailable");
            }
            status = "PNG exported: " + destination.getFileName();
            if (exportCallback != null) {
                exportCallback.accept(destination);
            } else {
                Path gallery = ScreenshotManager.getGalleryDirectory();
                if (gallery != null) {
                    Files.createDirectories(gallery);
                    Path galleryFile = gallery.resolve(safeName + "-edited-" + System.currentTimeMillis() + ".png");
                    Files.copy(destination, galleryFile);
                    status = "PNG exported to gallery: " + galleryFile.getFileName();
                }
            }
        } catch (Exception exception) {
            CraftShop.LOGGER.error("Could not export CraftShop PNG", exception);
            status = "PNG export failed: " + exception.getMessage();
        } finally {
            if (graphics != null) graphics.dispose();
            if (source != null) source.flush();
            if (output != null) output.flush();
        }
    }
    private void returnToParent() {
        this.minecraft.gui.setScreen(parent);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int withOpacity(int color, float opacity) {
        int alpha = (color >>> 24) & 255;
        int adjustedAlpha = Math.max(0, Math.min(255, Math.round(alpha * opacity)));
        return (color & 0x00FFFFFF) | (adjustedAlpha << 24);
    }

    private static int adjustColor(int color, int brightness) {
        int a = (color >>> 24) & 255;
        int r = clamp(((color >>> 16) & 255) + brightness, 0, 255);
        int g = clamp(((color >>> 8) & 255) + brightness, 0, 255);
        int b = clamp((color & 255) + brightness, 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.input();
        if ((textField != null && textField.isFocused())
                || (projectNameField != null && projectNameField.isFocused())) {
            return super.keyPressed(event);
        }
        if (event.hasControlDown() && keyCode == 'z') {
            undo();
            return true;
        }
        if (event.hasControlDown() && keyCode == 'y') {
            redo();
            return true;
        }
        if (event.hasControlDown() && keyCode == 's') {
            saveDocument();
            return true;
        }
        if (event.isEscape()) {
            returnToParent();
            return true;
        }
        return super.keyPressed(event);
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
        float opacity = 1.0F;
        List<Stroke> strokes = new ArrayList<>();
        List<TextItem> texts = new ArrayList<>();
        List<ShapeItem> shapes = new ArrayList<>();
        Layer(String name) { this.name = name; }
    }

    private static final class Stroke {
        int color;
        int size;
        boolean eraser;
        boolean eraseBackground;
        List<Point> points = new ArrayList<>();
    }

    private static final class ShapeItem {
        String type;
        float x1;
        float y1;
        float x2;
        float y2;
        int color;
        int size;
        ShapeItem(String type, float x1, float y1, float x2, float y2, int color, int size) {
            this.type = type;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.color = color;
            this.size = size;
        }
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
