package dev.modsfinder.craftshop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Project-owned scene world. It has its own chunk index and object storage view;
 * it never delegates world state, chunk access, or entity state to Minecraft's
 * currently loaded ClientLevel.
 */
public final class SceneWorld {
    public static final int CHUNK_SIZE = 16;
    public static final int SECTION_HEIGHT = 16;
    private static final int MAX_RENDER_CHUNK_DISTANCE = 64;
    private static final Map<SceneProject, CacheEntry> CACHE = new WeakHashMap<>();

    private final Map<ChunkPos, SceneChunk> chunks;
    private final List<SceneProject.SceneObject> objects;

    private SceneWorld(Map<ChunkPos, SceneChunk> chunks,
                      List<SceneProject.SceneObject> objects) {
        this.chunks = Map.copyOf(chunks);
        this.objects = List.copyOf(objects);
    }

    public static SceneWorld from(SceneProject project) {
        if (project == null || project.objects == null) {
            return new SceneWorld(Map.of(), List.of());
        }
        synchronized (CACHE) {
            CacheEntry cached = CACHE.get(project);
            if (cached != null && cached.matches(project.objects)) return cached.world;

            Map<ChunkPos, SceneChunk> chunks = new LinkedHashMap<>();
            List<SceneProject.SceneObject> objects = new ArrayList<>();
            for (SceneProject.SceneObject object : project.objects) {
                if (object == null || object.transform == null) continue;
                objects.add(object);
                ChunkPos position = new ChunkPos(
                        floorChunk(object.transform.x),
                        floorChunk(object.transform.y),
                        floorChunk(object.transform.z));
                chunks.computeIfAbsent(position, SceneChunk::new).add(object);
            }
            SceneWorld world = new SceneWorld(chunks, objects);
            CACHE.put(project, new CacheEntry(world, objects));
            return world;
        }
    }

    /** Drops the cached chunk index when a project is explicitly discarded. */
    public static void invalidate(SceneProject project) {
        if (project == null) return;
        synchronized (CACHE) {
            CACHE.remove(project);
        }
    }

    private static final class CacheEntry {
        private final SceneWorld world;
        private final SceneProject.SceneObject[] objects;
        private final String[] ids;
        private final String[] kinds;
        private final int[] chunkX;
        private final int[] sectionY;
        private final int[] chunkZ;

        private CacheEntry(SceneWorld world, List<SceneProject.SceneObject> source) {
            this.world = world;
            int size = source.size();
            objects = source.toArray(SceneProject.SceneObject[]::new);
            ids = new String[size];
            kinds = new String[size];
            chunkX = new int[size];
            sectionY = new int[size];
            chunkZ = new int[size];
            for (int i = 0; i < size; i++) {
                SceneProject.SceneObject object = objects[i];
                ids[i] = object.id;
                kinds[i] = object.kind;
                chunkX[i] = floorChunk(object.transform.x);
                sectionY[i] = floorChunk(object.transform.y);
                chunkZ[i] = floorChunk(object.transform.z);
            }
        }

        private boolean matches(List<SceneProject.SceneObject> source) {
            int index = 0;
            for (SceneProject.SceneObject object : source) {
                if (object == null || object.transform == null) continue;
                if (index >= objects.length || objects[index] != object
                        || !java.util.Objects.equals(ids[index], object.id)
                        || !java.util.Objects.equals(kinds[index], object.kind)
                        || chunkX[index] != floorChunk(object.transform.x)
                        || sectionY[index] != floorChunk(object.transform.y)
                        || chunkZ[index] != floorChunk(object.transform.z)) return false;
                index++;
            }
            return index == objects.length;
        }
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
        ordered.sort(Comparator.comparingLong((SceneChunk chunk) -> chunk.distanceSquared(centerX, centerY, centerZ)).reversed());

        List<SceneProject.SceneObject> visible = new ArrayList<>();
        for (SceneChunk chunk : ordered) {
            for (SceneProject.SceneObject object : chunk.objects.values()) {
                if (object.visible && insideCameraFrustum(object, camera)) visible.add(object);
            }
        }
        return visible;
    }

    private static boolean insideCameraFrustum(SceneProject.SceneObject object,
                                                SceneProject.Camera camera) {
        if (camera == null || object.transform == null) return true;
        double yaw = Math.toRadians(camera.yaw);
        double pitch = Math.toRadians(camera.pitch);
        double sinYaw = Math.sin(yaw), cosYaw = Math.cos(yaw);
        double sinPitch = Math.sin(pitch), cosPitch = Math.cos(pitch);
        double dx = object.transform.x - camera.x;
        double dy = object.transform.y - camera.y;
        double dz = object.transform.z - camera.z;

        double forward = dx * (-sinYaw * cosPitch)
                + dy * (-sinPitch) + dz * (-cosYaw * cosPitch);
        if (forward < -2.0D) return false;

        double right = dx * cosYaw - dz * sinYaw;
        double up = dx * (-sinYaw * sinPitch)
                + dy * cosPitch + dz * (-cosYaw * sinPitch);
        double scale = Math.max(0.05D, Math.max(object.transform.scaleX,
                Math.max(object.transform.scaleY, object.transform.scaleZ)));
        double radius = Math.min(32.0D, scale * 1.8D + 0.9D);
        double horizontalLimit = Math.max(4.0D, forward * 1.55D + radius);
        double verticalLimit = Math.max(4.0D, forward * 1.55D + radius);
        return Math.abs(right) <= horizontalLimit && Math.abs(up) <= verticalLimit;
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
