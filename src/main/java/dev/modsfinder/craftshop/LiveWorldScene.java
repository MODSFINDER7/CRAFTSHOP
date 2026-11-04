package dev.modsfinder.craftshop;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Read-only live block-state snapshot used only by the scene viewport. */
public final class LiveWorldScene {
    private static final int HORIZONTAL_RADIUS = 8;
    private static final int VERTICAL_RADIUS = 8;

    private LiveWorldScene() {}

    public static Snapshot capture(SceneProject source, Minecraft minecraft) {
        SceneProject preview = copySettings(source);
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return new Snapshot(preview, 0, 0);
        }

        BlockPos origin = minecraft.player.blockPosition();
        double yaw = Math.toRadians(minecraft.player.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        int captured = 0;
        int unloaded = 0;

        for (int y = -VERTICAL_RADIUS; y < VERTICAL_RADIUS; y++) {
            for (int x = -HORIZONTAL_RADIUS; x < HORIZONTAL_RADIUS; x++) {
                for (int z = -HORIZONTAL_RADIUS; z < HORIZONTAL_RADIUS; z++) {
                    BlockPos position = origin.offset(x, y, z);
                    if (!minecraft.level.hasChunkAt(position)) {
                        unloaded++;
                        continue;
                    }
                    BlockState state = minecraft.level.getBlockState(position);
                    if (state.isAir()) continue;
                    Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (blockId == null) continue;

                    SceneProject.SceneObject object = new SceneProject.SceneObject();
                    object.id = "live-world:" + position.getX() + ":" + position.getY() + ":" + position.getZ();
                    object.name = "Live World / " + blockId;
                    object.kind = "minecraft_block";
                    object.assetId = blockId.toString();
                    object.locked = true;
                    for (Property<?> property : state.getProperties()) {
                        object.blockProperties.put(property.getName(), propertyValueName(state, property));
                    }

                    double worldX = x + 0.5D;
                    double worldZ = z + 0.5D;
                    object.transform.x = (float) (worldX * cos + worldZ * sin);
                    object.transform.y = y + 0.5F;
                    object.transform.z = (float) (worldX * sin - worldZ * cos);
                    preview.objects.add(object);
                    captured++;
                }
            }
        }
        return new Snapshot(preview, captured, unloaded);
    }

    private static SceneProject copySettings(SceneProject source) {
        SceneProject copy = new SceneProject();
        if (source == null) return copy;
        copy.name = source.name;
        copy.canvasImage = source.canvasImage;
        copy.renderedImage = source.renderedImage;
        copy.selectedObjectId = source.selectedObjectId;
        copy.ambientOcclusion = source.ambientOcclusion;
        copy.lightYaw = source.lightYaw;
        copy.lightPitch = source.lightPitch;
        copy.ambientLight = source.ambientLight;
        if (source.camera != null) {
            copy.camera.x = source.camera.x;
            copy.camera.y = source.camera.y;
            copy.camera.z = source.camera.z;
            copy.camera.yaw = source.camera.yaw;
            copy.camera.pitch = source.camera.pitch;
            copy.camera.zoom = source.camera.zoom;
            copy.camera.targetX = source.camera.targetX;
            copy.camera.targetY = source.camera.targetY;
            copy.camera.targetZ = source.camera.targetZ;
        }
        if (source.objects != null) copy.objects.addAll(source.objects);
        if (source.keyframes != null) copy.keyframes.addAll(source.keyframes);
        copy.timelineFrame = source.timelineFrame;
        return copy;
    }

    private static <T extends Comparable<T>> String propertyValueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    public record Snapshot(SceneProject project, int capturedBlocks, int unloadedPositions) {}
}
