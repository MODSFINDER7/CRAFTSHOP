package dev.modsfinder.craftshop;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class CanvasProjectPickerScreen extends Screen {
    private static final int PAGE_SIZE = 8;

    private final Screen parent;
    private final Consumer<Path> selection;
    private Path[] projects = new Path[0];
    private int page;

    public CanvasProjectPickerScreen(Screen parent, Consumer<Path> selection) {
        super(Component.literal("Open CraftShop Project"));
        this.parent = parent;
        this.selection = selection;
    }

    @Override
    protected void init() {
        projects = listProjects();
        int maxPage = Math.max(0, (projects.length - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, maxPage));

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = page * PAGE_SIZE + slot;
            if (index >= projects.length) break;
            Path project = projects[index];
            int y = 66 + slot * 42;
            addRenderableWidget(Button.builder(
                    Component.literal(project.getFileName().toString()),
                    button -> {
                        selection.accept(project);
                        this.minecraft.gui.setScreen(parent);
                    }
            ).bounds(28, y + 5, this.width - 56, 28).build());
        }

        int bottom = this.height - 34;
        addRenderableWidget(Button.builder(Component.literal("Previous"), button -> {
            if (page > 0) {
                page--;
                refresh();
            }
        }).bounds(24, bottom, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"), button -> {
            if ((page + 1) * PAGE_SIZE < projects.length) {
                page++;
                refresh();
            }
        }).bounds(108, bottom, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> returnToParent())
                .bounds(this.width - 92, bottom, 68, 20).build());
    }

    private Path[] listProjects() {
        Path directory = MinecraftDirectory.canvasDirectory();
        try {
            Files.createDirectories(directory);
            try (Stream<Path> files = Files.list(directory)) {
                return files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".craftshop2d"))
                        .sorted(Comparator.comparingLong(CanvasProjectPickerScreen::lastModified).reversed())
                        .toArray(Path[]::new);
            }
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not list CraftShop 2D projects", exception);
            return new Path[0];
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
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
        graphics.text(this.font, "OPEN 2D PROJECT", 24, 20, 0xFFFFFFFF, true);
        graphics.text(this.font, "Select a saved .craftshop2d project", 24, 40, 0xFF9BAAB9, false);

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = page * PAGE_SIZE + slot;
            int y = 66 + slot * 42;
            if (index >= projects.length) {
                graphics.text(this.font, "No saved project", 34, y + 14, 0xFF6F7C8B, false);
                continue;
            }
            Path project = projects[index];
            graphics.text(this.font, project.getFileName().toString(), 34, y + 15, 0xFFE1E7EE, false);
        }

        int pages = Math.max(1, (projects.length + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.text(this.font, "Page " + (page + 1) + " / " + pages + "  •  " + projects.length + " projects",
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

    private static final class MinecraftDirectory {
        private static Path canvasDirectory() {
            return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("craftshop").resolve("canvas");
        }
    }
}
