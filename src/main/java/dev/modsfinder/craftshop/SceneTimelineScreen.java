package dev.modsfinder.craftshop;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import net.minecraft.network.chat.Component;

public final class SceneTimelineScreen extends Screen {
    private final Screen parent;
    private final SceneProject project;
    private boolean playing;
    private int ticks;
    private boolean draggingPlayhead;

    public SceneTimelineScreen(Screen parent, SceneProject project) {
        super(Component.literal("CraftShop Keyframe Timeline"));
        this.parent = parent;
        this.project = project;
    }

    @Override
    protected void init() {
        int center = width / 2;
        add("Frame -10", center - 190, 48, 72, () -> setFrame(project.timelineFrame - 10));
        add("Frame -1", center - 112, 48, 58, () -> setFrame(project.timelineFrame - 1));
        add("Frame +1", center - 50, 48, 58, () -> setFrame(project.timelineFrame + 1));
        add("Frame +10", center + 12, 48, 72, () -> setFrame(project.timelineFrame + 10));
        add(playing ? "Pause" : "Play", center + 90, 48, 58, () -> { playing = !playing; rebuild(); });
        add("Add Key", center - 190, 82, 82, this::addKey);
        add("Delete Key", center - 102, 82, 82, this::deleteKey);
        add("Previous", center - 14, 82, 72, () -> jumpKey(-1));
        add("Next", center + 64, 82, 72, () -> jumpKey(1));
        add("Back to Studio", center + 142, 82, 112, this::closeTimeline);
    }

    private void add(String label, int x, int y, int w, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(x, y, w, 20).build());
    }
    private void rebuild() { clearWidgets(); init(); }
    private String selectedId() { return project.selectedObjectId == null ? "" : project.selectedObjectId; }

    private void setFrame(int frame) {
        project.timelineFrame = Math.max(0, Math.min(2400, frame));
        SceneTimeline.applyFrame(project, project.timelineFrame);
        rebuild();
    }
    private void addKey() {
        if (selectedId().isBlank()) return;
        SceneTimeline.setKeyframe(project, selectedId(), project.timelineFrame);
        rebuild();
    }
    private void deleteKey() {
        SceneTimeline.removeKeyframe(project, selectedId(), project.timelineFrame);
        SceneTimeline.applyFrame(project, project.timelineFrame);
        rebuild();
    }
    private void jumpKey(int direction) {
        var keys = project.keyframes.stream().filter(k -> selectedId().equals(k.objectId))
                .sorted(java.util.Comparator.comparingInt(k -> k.frame)).toList();
        if (direction < 0) {
            for (int i = keys.size() - 1; i >= 0; i--) if (keys.get(i).frame < project.timelineFrame) {
                setFrame(keys.get(i).frame); return;
            }
        } else {
            for (var key : keys) if (key.frame > project.timelineFrame) { setFrame(key.frame); return; }
        }
    }
    private void closeTimeline() { playing = false; minecraft.gui.setScreen(parent); }


    private void scrub(double mouseX) {
        int left = 100, right = width - 42;
        double ratio = Math.max(0.0D, Math.min(1.0D, (mouseX - left) / Math.max(1.0D, right - left)));
        setFrame((int)Math.round(ratio * 2400.0D));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.y() >= 202 && event.y() <= 238
                && event.x() >= 100 && event.x() <= width - 42) {
            playing = false;
            draggingPlayhead = true;
            scrub(event.x());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingPlayhead && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            scrub(event.x());
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) draggingPlayhead = false;
        return super.mouseReleased(event);
    }

    @Override
    public void tick() {
        super.tick();
        if (playing && ++ticks % 3 == 0) {
            if (project.timelineFrame >= 2400) project.timelineFrame = 0;
            else project.timelineFrame++;
            SceneTimeline.applyFrame(project, project.timelineFrame);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(0, 0, width, height, 0xFF090D13);
        graphics.fill(0, 0, width, 3, 0xFF36C8D9);
        graphics.fill(18, 28, width - 18, height - 28, 0xFF151E29);
        graphics.outline(18, 28, width - 36, height - 56, 0xFF344354);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(font, "CRAFTSHOP  /  KEYFRAME TIMELINE", 30, 16, 0xFFECF7FF, true);
        graphics.text(font, "OBJECT  " + (selectedId().isBlank() ? "NONE SELECTED" : selectedId().substring(0, Math.min(8, selectedId().length()))),
                30, 118, 0xFF9AA8B8, false);
        graphics.text(font, "FRAME  " + project.timelineFrame + " / 2400", 30, 146, 0xFF55DDEE, true);
        graphics.text(font, "Tracks", 30, 180, 0xFFB8A7FF, true);
        int x0 = 100, x1 = width - 42, y = 220;
        graphics.fill(x0, y, x1, y + 2, 0xFF526477);
        for (int f = 0; f <= 2400; f += 100) {
            int x = x0 + (int)((x1 - x0) * (f / 2400.0));
            graphics.fill(x, y - 5, x + 1, y + 7, 0xFF71869B);
            if (f % 200 == 0) graphics.text(font, Integer.toString(f), x - 8, y + 12, 0xFF8296AA, false);
        }
        int playhead = x0 + (int)((x1 - x0) * (project.timelineFrame / 2400.0));
        graphics.fill(playhead - 1, y - 18, playhead + 2, y + 42, 0xFF36C8D9);
        if (!selectedId().isBlank()) {
            for (SceneProject.Keyframe key : project.keyframes) if (selectedId().equals(key.objectId)) {
                int x = x0 + (int)((x1 - x0) * (key.frame / 2400.0));
                graphics.fill(x - 4, y - 4, x + 5, y + 5, 0xFFFFC66D);
            }
        }
        graphics.text(font, "Yellow markers = selected object's transform keyframes • interpolation: linear position/scale + shortest-path rotation",
                30, y + 58, 0xFF9AA8B8, false);
        graphics.text(font, playing ? "PLAYING" : "PAUSED", 30, height - 48,
                playing ? 0xFF9DDDAA : 0xFF8296AA, true);
        graphics.text(font, "Add Key stores position, rotation and scale for the selected object at the current frame.",
                30, height - 32, 0xFF8296AA, false);
    }

    @Override public void onClose() { closeTimeline(); }
    @Override public boolean isPauseScreen() { return false; }
}
