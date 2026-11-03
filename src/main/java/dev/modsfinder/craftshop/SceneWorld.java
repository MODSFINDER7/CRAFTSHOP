package dev.modsfinder.craftshop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Project-owned scene world. It has its own chunk index and object storage view;
 * it never delegates world state, chunk access, or entity state to Minecraft's
 * currently loaded ClientLevel.
 */
public final class SceneWorld {
    public static final int CHUNK_SIZE = 16;
    public static final int SECTION_HEIGHT = 16;
    private static final int MAX_RENDER_CHUNK_DISTANCE = 64;

    private final Map<ChunkPos, SceneChunk> chunks;
    private final List<SceneProject.SceneObject> objects;

    private SceneWorld(Map<ChunkPos, SceneChunk> chunks,
                      List<SceneProject.SceneObject> objects) {
        this.chunks = Map.copyOf(chunks);
        this.objects = List.copyOf(objects);
    }

    public static SceneWorld from(SceneProject project) {
        Map<ChunkPos, SceneChunk> chunks = new LinkedHashMap<>();
        List<SceneProject.SceneObject> objects = new ArrayList<>();
        if (project == null || project.objects == null) {
            return new SceneWorld(chunks, objects);
        }

        for (SceneProject.SceneObject object : project.objects) {
            if (object == null || object.transform == null) continue;
            objects.add(object);
            int chunkX = floorChunk(object.transform.x);
            int chunkZ = floorChunk(object.transform.z);
            int sectionY = floorChunk(object.transform.y);
            ChunkPos position = new ChunkPos(chunkX, sectionY, chunkZ);
            SceneChunk chunk = chunks.computeIfAbsent(position, SceneChunk::new);
            chunk.add(object);
        }
        return new SceneWorld(chunks, objects);
    }

    /**
     * Returns visible objects in stable near-to-far chunk order. Individual
     * object visibility is applied here so hidden objects never enter rendering
     * or hit-test buffers.
     */
    public List<SceneProject.SceneObject> renderObjects(SceneProject.Camera camera) {
        if (chunks.isEmpty()) return List.of();
        double cameraX = camera == null ? 0.0D : camera.x;
        double cameraY = camera == null ? 0.0D : camera.y;
        double cameraZ = camera == null ? 0.0D : camera.z;
        int centerX = floorChunk(cameraX);
        int centerY = floorChunk(cameraY);
        int centerZ = floorChunk(cameraZ);

        List<SceneChunk> ordered = new ArrayList<>();
        for (SceneChunk chunk : chunks.values()) {
            int dx = chunk.position.x - centerX;
            int dy = chunk.position.y - centerY;
            int dz = chunk.position.z - centerZ;
            if (Math.abs(dx) > MAX_RENDER_CHUNK_DISTANCE
                    || Math.abs(dy) > MAX_RENDER_CHUNK_DISTANCE
                    || Math.abs(dz) > MAX_RENDER_CHUNK_DISTANCE) continue;
            ordered.add(chunk);
        }
        ordered.sort(Comparator.comparingLong(chunk -> chunk.distanceSquared(centerX, centerY, centerZ)));

        List<SceneProject.SceneObject> visible = new ArrayList<>();
        for (SceneChunk chunk : ordered) {
            for (SceneProject.SceneObject object : chunk.objects.values()) {
                if (object.visible) visible.add(object);
            }
        }
        return visible;
    }

    public int chunkCount() {
        return chunks.size();
    }

    public int objectCount() {
        return objects.size();
    }

    public int blockCount() {
        int count = 0;
        for (SceneChunk chunk : chunks.values()) count += chunk.blocks.size();
        return count;
    }

    public int entityCount() {
        int count = 0;
        for (SceneChunk chunk : chunks.values()) count += chunk.entities.size();
        return count;
    }

    public SceneChunkView chunkAt(int blockX, int blockY, int blockZ) {
        SceneChunk chunk = chunks.get(new ChunkPos(
                Math.floorDiv(blockX, CHUNK_SIZE),
                Math.floorDiv(blockY, SECTION_HEIGHT),
                Math.floorDiv(blockZ, CHUNK_SIZE)));
        return chunk == null ? null : new SceneChunkView(chunk.position,
                List.copyOf(chunk.objects.values()), chunk.blocks.size(), chunk.entities.size());
    }

    private static int floorChunk(double coordinate) {
        if (!Double.isFinite(coordinate)) return 0;
        return (int) Math.floor(coordinate / CHUNK_SIZE);
    }

    public record ChunkPos(int x, int y, int z) {}

    public record SceneChunkView(ChunkPos position,
                                 List<SceneProject.SceneObject> objects,
                                 int blockCount, int entityCount) {}

    private static final class SceneChunk {
        private final ChunkPos position;
        private final Map<String, SceneProject.SceneObject> objects = new LinkedHashMap<>();
        private final Map<String, SceneProject.SceneObject> blocks = new HashMap<>();
        private final Map<String, SceneProject.SceneObject> entities = new HashMap<>();

        private SceneChunk(ChunkPos position) {
            this.position = position;
        }

        private void add(SceneProject.SceneObject object) {
            String id = object.id == null ? Integer.toHexString(System.identityHashCode(object)) : object.id;
            objects.put(id, object);
            if ("minecraft_entity".equals(object.kind)) entities.put(id, object);
            else blocks.put(id, object);
        }

        private long distanceSquared(int x, int y, int z) {
            long dx = position.x - x;
            long dy = position.y - y;
            long dz = position.z - z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
