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
        if ("minecraft_block".equals(object.kind) || "mesh_cube".equals(object.kind)) {
            Block block = BuiltInRegistries.BLOCK.getValue(id);
            if (block == null) return;
            if (!(preview instanceof Display.BlockDisplay)) {
                preview = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("block_display"))
                        .create(minecraft.level, EntitySpawnReason.LOAD);
                if (!(preview instanceof Display.BlockDisplay blockDisplay)) return;
                PREVIEWS.put(object.id, blockDisplay);
                preview = blockDisplay;
                created = true;
            }
            ((Display.BlockDisplay) preview).setBlockState(block.defaultBlockState());
        } else if ("minecraft_entity".equals(object.kind)) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
            if (type == null || id.getPath().equals("player")) return;
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
        preview.setYRot(0.0F);
        preview.setXRot(0.0F);
        var state = minecraft.getEntityRenderDispatcher().extractEntity(
                preview, minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        float scale = Math.max(0.05F, Math.min(4.0F,
                (object.transform.scaleX + object.transform.scaleY + object.transform.scaleZ) / 3.0F));
        Quaternionf rotation = new Quaternionf().rotationXYZ(
                (float) Math.toRadians(object.transform.rotationX),
                (float) Math.toRadians(object.transform.rotationY),
                (float) Math.toRadians(object.transform.rotationZ));
        int renderSize = Math.max(4, Math.round(size * scale));
        graphics.entity(state, renderSize, new Vector3f(0.0F, 0.0F, 0.0F),
                rotation, null, x - renderSize, y - renderSize, x + renderSize, y + renderSize);
    }

    public static void forget(String objectId) {
        PREVIEWS.remove(objectId);
    }

    public static void clear() {
        PREVIEWS.clear();
    }
}
