package dev.modsfinder.craftshop;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class CanvasImagePickerScreen extends Screen {
    private static final int PAGE_SIZE = 8;

    private final Screen parent;
    private final Consumer<Path> selection;
    private Path[] images = new Path[0];
    private int page;

    public CanvasImagePickerScreen(Screen parent, Consumer<Path> selection) {
        super(Component.literal("CraftShop Image Picker"));
        this.parent = parent;
        this.selection = selection;
    }

    @Override
    protected void init() {
        Path importDirectory = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                .resolve("craftshop").resolve("canvas").resolve("imports");
        Path[] importedPngs;
        try {
            Files.createDirectories(importDirectory);
            try (Stream<Path> files = Files.list(importDirectory)) {
                importedPngs = files.filter(path -> Files.isRegularFile(path)
                                && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png"))
                        .sorted().toArray(Path[]::new);
            }
        } catch (java.io.IOException exception) {
            CraftShop.LOGGER.warn("Could not scan CraftShop PNG import folder", exception);
            importedPngs = new Path[0];
        }
        Path[] screenshots = ScreenshotManager.getScreenshots();
        images = Stream.concat(Arrays.stream(screenshots), Arrays.stream(importedPngs))
                .distinct().toArray(Path[]::new);
        int maxPage = Math.max(0, (images.length - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, maxPage));

        int left = 24;
        int top = 66;
        int rowHeight = 42;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = page * PAGE_SIZE + slot;
            if (index >= images.length) break;
            Path path = images[index];
            int y = top + slot * rowHeight;
            addRenderableWidget(Button.builder(
                    Component.literal(path.getFileName().toString()),
                    button -> {
                        selection.accept(path);
                        this.minecraft.gui.setScreen(parent);
                    }
            ).bounds(left + 54, y + 5, Math.max(180, this.width - 110), 28).build());
        }

        int bottom = this.height - 34;
        addRenderableWidget(Button.builder(Component.literal("Previous"), button -> {
            if (page > 0) {
                page--;
                refresh();
            }
        }).bounds(24, bottom, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"), button -> {
            if ((page + 1) * PAGE_SIZE < images.length) {
                page++;
                refresh();
            }
        }).bounds(108, bottom, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> returnToParent())
                .bounds(this.width - 92, bottom, 68, 20).build());
    }

    private void refresh() {
        clearWidgets();
        init();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(12, 10, this.width - 12, this.height - 10, 0xEE171C24);
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int y = 66 + slot * 42;
            graphics.fill(18, y, this.width - 18, y + 38, 0xFF252B36);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "IMPORT IMAGE", 24, 20, 0xFFFFFFFF, true);
        graphics.text(this.font, "Choose a gallery screenshot or PNG from craftshop/canvas/imports", 24, 40, 0xFF9BAAB9, false);

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = page * PAGE_SIZE + slot;
            int y = 66 + slot * 42;
            if (index >= images.length) {
                graphics.text(this.font, "No image", 34, y + 14, 0xFF6F7C8B, false);
                continue;
            }
            Path path = images[index];
            GalleryTextureCache.TextureEntry texture = GalleryTextureCache.getThumbnail(path);
            if (texture != null) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), 25, y + 4,
                        0.0F, 0.0F, 44, 30, texture.width(), texture.height());
            }
            graphics.text(this.font, path.getFileName().toString(), 78, y + 15, 0xFFE1E7EE, false);
        }

        int pages = Math.max(1, (images.length + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.text(this.font, "Page " + (page + 1) + " / " + pages + "  •  " + images.length + " images",
                194, this.height - 28, 0xFF9BAAB9, false);
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
