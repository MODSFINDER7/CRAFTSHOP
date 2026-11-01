package dev.modsfinder.craftshop;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class SceneAssetPickerScreen extends Screen {
    private final Screen parent;
    private final boolean entities;
    private final Consumer<SceneAsset> onPick;
    private EditBox search;
    private int page;
    private String filter = "";

    public record SceneAsset(String id, String displayName, boolean entity) {}

    public SceneAssetPickerScreen(Screen parent, boolean entities, Consumer<SceneAsset> onPick) {
        super(Component.literal(entities ? "Add Minecraft Entity" : "Add Minecraft Block"));
        this.parent = parent;
        this.entities = entities;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        search = new EditBox(font, 18, 38, Math.min(260, width - 36), 20, Component.literal("Search"));
        search.setMaxLength(80);
        search.setValue(filter);
        search.setHint(Component.literal("Search " + (entities ? "entities" : "blocks") + "..."));
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("Previous"), b -> {
            page = Math.max(0, page - 1);
            rebuild();
        }).bounds(18, height - 34, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"), b -> {
            page++;
            rebuild();
        }).bounds(100, height - 34, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> minecraft.gui.setScreen(parent))
                .bounds(width - 94, height - 34, 76, 20).build());
        addAssetButtons();
    }

    private void addAssetButtons() {
        List<SceneAsset> assets = collectAssets();
        int pageSize = 12;
        int start = page * pageSize;
        if (start >= assets.size() && page > 0) {
            page = Math.max(0, (assets.size() - 1) / pageSize);
            start = page * pageSize;
        }
        int end = Math.min(assets.size(), start + pageSize);
        int columns = width >= 520 ? 2 : 1;
        int columnWidth = (width - 44) / columns;
        for (int i = start; i < end; i++) {
            SceneAsset asset = assets.get(i);
            int index = i - start;
            int col = index % columns;
            int row = index / columns;
            int x = 18 + col * (columnWidth + 8);
            int y = 72 + row * 24;
            addRenderableWidget(Button.builder(Component.literal(asset.displayName()), button -> {
                onPick.accept(asset);
                minecraft.gui.setScreen(parent);
            }).bounds(x, y, columnWidth, 20).build());
        }
    }

    private List<SceneAsset> collectAssets() {
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        List<SceneAsset> assets = new ArrayList<>();
        if (entities) {
            for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
                if (type == null || id.getPath().equals("player") || id.getPath().equals("block_display")) continue;
                if (query.isEmpty() || id.toString().contains(query)) {
                    assets.add(new SceneAsset(id.toString(), id.toString(), true));
                }
            }
        } else {
            for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
                Block block = BuiltInRegistries.BLOCK.getValue(id);
                if (block == null || block.defaultBlockState().isAir()) continue;
                if (query.isEmpty() || id.toString().contains(query)) {
                    assets.add(new SceneAsset(id.toString(), id.toString(), false));
                }
            }
        }
        assets.sort(Comparator.comparing(SceneAsset::id));
        return assets;
    }

    private void rebuild() {
        boolean keepFocus = search != null && search.isFocused();
        clearWidgets();
        init();
        if (keepFocus) search.setFocused(true);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(0, 0, width, height, 0xF010141B);
        graphics.fill(10, 10, width - 10, 30, 0xFF202A36);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
        graphics.text(font, entities ? "VANILLA ENTITY REGISTRY" : "VANILLA BLOCK REGISTRY",
                18, 62, 0xFF55DDEE, true);
        graphics.text(font, "Page " + (page + 1), 190, height - 29, 0xFFB7C3D0, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (search != null && search.isFocused()) {
            boolean handled = search.keyPressed(event);
            if (handled) {
                filter = search.getValue();
                page = 0;
                rebuild();
                search.setFocused(true);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (search != null && search.isFocused() && search.charTyped(event)) {
            filter = search.getValue();
            page = 0;
            rebuild();
            search.setFocused(true);
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
