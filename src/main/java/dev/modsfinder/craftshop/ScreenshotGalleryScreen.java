package dev.modsfinder.craftshop;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;

public final class ScreenshotGalleryScreen extends Screen {
    private Path[] screenshots = new Path[0];
    private Path selected;
    private int page;

    public ScreenshotGalleryScreen() {
        super(Component.literal("CraftShop Gallery"));
    }

    @Override
    protected void init() {
        refresh();
        int left = Math.max(12, this.width / 2 - 190);
        int top = 42;

        this.addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> refresh())
                .bounds(left, this.height - 34, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Open Folder"), button -> openFolder())
                .bounds(left + 86, this.height - 34, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Delete Selected"), button -> {
            if (ScreenshotManager.delete(selected)) {
                selected = null;
                refresh();
            }
        }).bounds(left + 192, this.height - 34, 120, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left + 318, this.height - 34, 62, 20).build());

        int visible = Math.min(8, screenshots.length);
        for (int i = 0; i < visible; i++) {
            final Path path = screenshots[i];
            int y = top + i * 25;
            this.addRenderableWidget(Button.builder(Component.literal(trim(path.getFileName().toString(), 38)), button -> selected = path)
                    .bounds(left, y, 250, 20).build());
        }
    }

    private void refresh() {
        screenshots = ScreenshotManager.getScreenshots();
        if (selected != null && !selected.toFile().exists()) selected = null;
        clearWidgets();
        init();
    }

    private void openFolder() {
        Path folder = ScreenshotManager.getGalleryDirectory();
        if (folder == null) return;
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(folder.toFile());
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not open gallery folder", exception);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "CRAFTSHOP  /  SCREENSHOT GALLERY", 14, 14, 0xFFFFFFFF, true);
        graphics.text(this.font, "Saved in .minecraft/craftshop/screenshots", 14, 28, 0xFFAAAAAA, false);
        graphics.text(this.font, "Screenshots: " + screenshots.length, this.width - 125, 14, 0xFFAAAAAA, false);

        int previewX = this.width / 2 + 80;
        graphics.text(this.font, "PREVIEW", previewX, 48, 0xFFFFFFFF, true);
        if (selected != null) {
            graphics.text(this.font, selected.getFileName().toString(), previewX, 68, 0xFF55FFFF, false);
            graphics.text(this.font, "Open Folder to view the full-size image.", previewX, 88, 0xFFBBBBBB, false);
        } else {
            graphics.text(this.font, "Select a screenshot from the list.", previewX, 68, 0xFFBBBBBB, false);
        }
        graphics.text(this.font, "Press O to open this gallery. Press Z in-world to capture.", 14, this.height - 52, 0xFFAAAAAA, false);
    }

    private static String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(null);
    }
}
