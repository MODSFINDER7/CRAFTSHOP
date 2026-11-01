package dev.modsfinder.craftshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public final class SceneAssetRenderer {
    private static final Map<String, Entity> PREVIEWS = new HashMap<>();

    private SceneAssetRenderer() {}

    public static void extract(GuiGraphicsExtractor graphics, SceneProject.SceneObject object,
                               Minecraft minecraft, int x, int y, int size) {
        if (object == null || object.assetId == null || minecraft.level == null) return;
        Identifier id = Identifier.tryParse(object.assetId);
        if (id == null) return;

        Entity preview = PREVIEWS.get(object.id);
        boolean created = false;
        if ("minecraft_block".equals(object.kind)) {
            Block block = BuiltInRegistries.BLOCK.getValue(id);
            if (block == null) return;
            if (!(preview instanceof Display.BlockDisplay)) {
                preview = EntityType.BLOCK_DISPLAY.create(minecraft.level, EntitySpawnReason.LOAD);
                if (!(preview instanceof Display.BlockDisplay display)) return;
                display.setBlockState(block.defaultBlockState());
                PREVIEWS.put(object.id, display);
                preview = display;
                created = true;
            }
        } else if ("minecraft_entity".equals(object.kind)) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
            if (type == null || type == EntityType.PLAYER) return;
            if (preview == null || preview.getType() != type) {
                preview = type.create(minecraft.level, EntitySpawnReason.LOAD);
                if (preview == null) return;
                PREVIEWS.put(object.id, preview);
                created = true;
            }
        } else {
            return;
        }

        preview.setNoGravity(true);
        if (created) preview.tick();
        preview.setPos(0.0, 0.0, 0.0);
        preview.setYRot(object.transform.rotationY);
        preview.setXRot(object.transform.rotationX);
        var state = minecraft.getEntityRenderDispatcher().extractEntity(
                preview, minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        float scale = Math.max(0.25F, Math.min(2.0F,
                (object.transform.scaleX + object.transform.scaleY + object.transform.scaleZ) / 3.0F));
        Quaternionf rotation = new Quaternionf().rotationXYZ(
                (float) Math.toRadians(object.transform.rotationX),
                (float) Math.toRadians(object.transform.rotationY),
                (float) Math.toRadians(object.transform.rotationZ));
        graphics.entity(state, size * scale, new Vector3f(0.0F, 0.0F, 0.0F),
                rotation, null, x - size, y - size, x + size, y + size);
    }

    public static void forget(String objectId) {
        PREVIEWS.remove(objectId);
    }

    public static void clear() {
        PREVIEWS.clear();
    }
}
