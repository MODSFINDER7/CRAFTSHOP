package dev.modsfinder.craftshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;

public final class ScreenshotGalleryScreen extends Screen {
    private static final int PAGE_SIZE = 6;

    private Path[] screenshots = new Path[0];
    private Path selected;
    private int page;
    private EditBox renameField;

    public ScreenshotGalleryScreen() {
        super(Component.literal("CraftShop Gallery"));
    }

    @Override
    protected void init() {
        loadScreenshots();

        int left = 22;
        int listTop = 76;
        int rowHeight = 48;
        int panelRight = Math.max(520, this.width - 24);
        int previewX = Math.max(360, this.width / 2 + 35);
        int bottom = this.height - 38;

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = page * PAGE_SIZE + slot;
            if (index >= screenshots.length) break;

            Path path = screenshots[index];
            int y = listTop + slot * rowHeight;
            this.addRenderableWidget(Button.builder(
                    Component.literal(trim(path.getFileName().toString(), 30)),
                    button -> select(path)
            ).bounds(left + 58, y + 5, Math.max(175, previewX - left - 78), 24).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Previous"), button -> {
            if (page > 0) {
                page--;
                refresh();
            }
        }).bounds(left, bottom, 76, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Next"), button -> {
            if ((page + 1) * PAGE_SIZE < screenshots.length) {
                page++;
                refresh();
            }
        }).bounds(left + 82, bottom, 62, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> refresh())
                .bounds(left + 150, bottom, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Open Folder"), button -> openFolder())
                .bounds(left + 226, bottom, 94, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Delete"), button -> {
            if (ScreenshotManager.delete(selected)) {
                selected = null;
                refresh();
            }
        }).bounds(previewX, bottom, 64, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Rename"), button -> renameSelected())
                .bounds(previewX + 70, bottom, 72, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("3D Studio"), button ->
                this.minecraft.gui.setScreen(new StudioScreen(this)))
                .bounds(this.width - 190, 12, 88, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(this.width - 88, bottom, 66, 20).build());

        renameField = new EditBox(this.font, previewX, bottom - 31, 220, 20, Component.literal("New screenshot name"));
        renameField.setMaxLength(64);
        if (selected != null) renameField.setValue(removeExtension(selected.getFileName().toString()));
        this.addRenderableWidget(renameField);
    }

    private void loadScreenshots() {
        screenshots = ScreenshotManager.getScreenshots();
        if (selected != null && !selected.toFile().exists()) selected = null;
        int maxPage = Math.max(0, (screenshots.length - 1) / PAGE_SIZE);
        page = Math.min(page, maxPage);
    }

    private void select(Path path) {
        selected = path;
        if (renameField != null) renameField.setValue(removeExtension(path.getFileName().toString()));
    }

    private void renameSelected() {
        if (selected == null || renameField == null) return;
        Path renamed = ScreenshotManager.rename(selected, renameField.getValue());
        if (renamed != null) {
            selected = renamed;
            refresh();
        } else if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                    Component.literal("CraftShop: name is invalid or already exists."), true
            );
        }
    }

    private void refresh() {
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
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(12, 10, this.width - 12, this.height - 10, 0xE9161922);

        int listTop = 76;
        int rowHeight = 48;
        int previewX = Math.max(360, this.width / 2 + 35);
        int previewWidth = this.width - previewX - 28;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int y = listTop + slot * rowHeight;
            graphics.fill(18, y, previewX - 12, y + 42, 0xFF252B36);
        }
        graphics.fill(previewX - 8, 76, this.width - 22, this.height - 82, 0xFF202630);
        graphics.outline(previewX - 8, 76, previewWidth + 8, this.height - 158, 0xFF465363);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "CRAFTSHOP", 22, 18, 0xFFFFFFFF, true);
        graphics.text(this.font, "SCREENSHOT LIBRARY", 22, 36, 0xFF55DDEE, false);
        graphics.text(this.font, "Saved in .minecraft/craftshop/screenshots", 22, 54, 0xFFAAAAAA, false);
        graphics.text(this.font, "Total: " + screenshots.length, this.width - 105, 20, 0xFFCCCCCC, false);

        int listTop = 76;
        int rowHeight = 48;
        int previewX = Math.max(360, this.width / 2 + 35);
        int previewWidth = this.width - previewX - 28;

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = page * PAGE_SIZE + slot;
            int y = listTop + slot * rowHeight;
            if (index >= screenshots.length) {
                graphics.text(this.font, "Empty slot", 78, y + 15, 0xFF707987, false);
                continue;
            }

            Path path = screenshots[index];
            GalleryTextureCache.TextureEntry texture = GalleryTextureCache.getThumbnail(path);
            if (texture != null) {
                int thumbW = 48;
                int thumbH = 32;
                graphics.fill(24, y + 5, 24 + thumbW + 2, y + 5 + thumbH + 2, 0xFF080A0E);
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), 25, y + 6, 0.0F, 0.0F,
                        thumbW, thumbH, texture.width(), texture.height());
            }
            if (path.equals(selected)) {
                graphics.outline(18, y, previewX - 30, 42, 0xFF55DDEE);
            }
            graphics.text(this.font, " " + (index + 1) + ".", 78, y - 1, 0xFF8E9AAA, false);
        }

        graphics.text(this.font, "PREVIEW", previewX, 60, 0xFFFFFFFF, true);
        if (selected != null && selected.toFile().isFile()) {
            GalleryTextureCache.TextureEntry texture = GalleryTextureCache.get(selected);
            graphics.text(this.font, selected.getFileName().toString(), previewX, 84, 0xFF55DDEE, false);
            if (texture != null) {
                int areaX = previewX + 8;
                int areaY = 108;
                int areaW = Math.max(40, previewWidth - 32);
                int areaH = Math.max(40, this.height - 220);
                float scale = Math.min((float) areaW / texture.width(), (float) areaH / texture.height());
                int drawW = Math.max(1, Math.round(texture.width() * scale));
                int drawH = Math.max(1, Math.round(texture.height() * scale));
                int drawX = areaX + (areaW - drawW) / 2;
                int drawY = areaY + (areaH - drawH) / 2;
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), drawX, drawY, 0.0F, 0.0F,
                        drawW, drawH, texture.width(), texture.height());
                graphics.text(this.font, texture.width() + " x " + texture.height() + " px",
                        previewX, this.height - 99, 0xFFAAAAAA, false);
            } else {
                graphics.text(this.font, "Preview could not be loaded.", previewX, 112, 0xFFFF7777, false);
            }
        } else {
            graphics.text(this.font, "Select an image to preview it.", previewX, 96, 0xFFB7C0CC, false);
        }

        int pageCount = Math.max(1, (screenshots.length + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.text(this.font, "Page " + (page + 1) + " / " + pageCount,
                22, this.height - 54, 0xFFAAAAAA, false);
        graphics.text(this.font, "Z: Capture  |  O: Open gallery  |  ESC: Close",
                22, this.height - 70, 0xFFAAAAAA, false);
    }

    private static String removeExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    @Override
    public void onClose() {
        GalleryTextureCache.clear();
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
