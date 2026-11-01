package dev.modsfinder.craftshop;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class ProjectBrowserScreen extends Screen {
    private static final int PAGE_SIZE = 8;

    private final Screen parent;
    private final Consumer<SceneProject> onLoad;
    private Path[] projects = new Path[0];
    private int page;
    private String status = "";

    public ProjectBrowserScreen(Screen parent, Consumer<SceneProject> onLoad) {
        super(Component.literal("Open CraftShop Project"));
        this.parent = parent;
        this.onLoad = onLoad;
    }

    @Override
    protected void init() {
        refreshList();
        int left = Math.max(24, this.width / 2 - 190);
        int top = 66;
        int bottom = this.height - 38;

        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            if (index >= projects.length) break;
            Path file = projects[index];
            this.addRenderableWidget(Button.builder(
                    Component.literal(file.getFileName().toString()),
                    button -> open(file)
            ).bounds(left, top + i * 27, 360, 22).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Previous"), button -> {
            if (page > 0) {
                page--;
                rebuild();
            }
        }).bounds(left, bottom, 76, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Next"), button -> {
            if ((page + 1) * PAGE_SIZE < projects.length) {
                page++;
                rebuild();
            }
        }).bounds(left + 82, bottom, 62, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> returnToParent())
                .bounds(left + 290, bottom, 70, 20).build());
    }

    private void refreshList() {
        try {
            projects = ProjectManager.listProjects();
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not list CraftShop projects", exception);
            projects = new Path[0];
            status = "Could not read the projects folder.";
        }
        page = Math.min(page, Math.max(0, (projects.length - 1) / PAGE_SIZE));
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void open(Path file) {
        try {
            SceneProject loaded = ProjectManager.load(file);
            onLoad.accept(loaded);
            returnToParent();
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not open CraftShop project {}", file, exception);
            status = "Could not open this project.";
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(12, 10, this.width - 12, this.height - 10, 0xE9161922);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "OPEN CRAFTSHOP PROJECT", 24, 20, 0xFFFFFFFF, true);
        graphics.text(this.font, "Choose an editable .craftshop project", 24, 40, 0xFF9AA8B8, false);
        graphics.text(this.font, "Projects found: " + projects.length, 24, this.height - 62, 0xFFB5C0CD, false);
        if (!status.isEmpty()) graphics.text(this.font, status, 24, this.height - 82, 0xFFFF7777, false);
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
