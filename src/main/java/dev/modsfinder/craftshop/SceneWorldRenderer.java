package dev.modsfinder.craftshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

/**
 * Independent CPU scene renderer. It rasterizes scene geometry into its own
 * color/depth buffers and never reads or writes the active Minecraft level.
 */
public final class SceneWorldRenderer {
    private static final double NEAR = 0.08D;
    private static final Map<ResourceManager, Map<net.minecraft.resources.Identifier, BufferedImage>> SPRITE_IMAGES = new WeakHashMap<>();

    private SceneWorldRenderer() {}

    public static Frame render(SceneProject project, int width, int height) {
        return render(project, width, height, null);
    }

    public static Frame render(SceneProject project, int width, int height, Minecraft minecraft) {
        int w = Math.max(64, Math.min(1024, width));
        int h = Math.max(64, Math.min(1024, height));
        int[] pixels = new int[w * h];
        double[] depth = new double[w * h];
        String[] objectIds = new String[w * h];
        java.util.Arrays.fill(depth, Double.POSITIVE_INFINITY);
        Map<String, Bounds> bounds = new HashMap<>();

        SceneProject.Camera camera = project.camera == null ? new SceneProject.Camera() : project.camera;
        CameraBasis basis = new CameraBasis(camera);
        Raster raster = new Raster(w, h, pixels, depth, objectIds, basis);

        SceneWorld world = SceneWorld.from(project);
        for (SceneProject.SceneObject object : world.renderObjects(camera)) {
                if (object == null || !object.visible || object.transform == null) continue;
                int startX = w, startY = h, endX = -1, endY = -1;
                boolean renderedModel = false;
                if (minecraft != null && "minecraft_block".equals(object.kind)) {
                    renderedModel = renderBakedBlockModel(raster, object, minecraft,
                            new int[]{startX, startY, endX, endY});
                    Bounds modelBounds = raster.objectBounds(object.id);
                    if (modelBounds != null) {
                        startX = Math.min(startX, modelBounds.minX);
                        startY = Math.min(startY, modelBounds.minY);
                        endX = Math.max(endX, modelBounds.maxX);
                        endY = Math.max(endY, modelBounds.maxY);
                    }
                }
                if (!renderedModel) {
                    int color = materialColor(object.assetId);
                    List<Box> boxes = objectBoxes(object);
                    for (Box box : boxes) {
                        int[][] faces = {
                                {0, 1, 2, 3}, {5, 4, 7, 6},
                                {4, 0, 3, 7}, {1, 5, 6, 2},
                                {3, 2, 6, 7}, {4, 5, 1, 0}
                        };
                        Vec3[] vertices = boxVertices(box, object.transform);
                        int faceIndex = 0;
                        for (int[] face : faces) {
                            int faceColor = shade(color, faceIndex++);
                            raster.quad(vertices[face[0]], vertices[face[1]],
                                    vertices[face[2]], vertices[face[3]], faceColor, object.id);
                        }
                    }
                }
                Bounds objectBounds = raster.objectBounds(object.id);
                if (objectBounds != null) {
                    startX = Math.min(startX, objectBounds.minX);
                    startY = Math.min(startY, objectBounds.minY);
                    endX = Math.max(endX, objectBounds.maxX);
                    endY = Math.max(endY, objectBounds.maxY);
                }
                if (endX >= startX && endY >= startY) {
                    bounds.put(object.id, new Bounds(startX, startY, endX, endY));
                }
        }
        if (project.selectedObjectId != null && !project.selectedObjectId.isBlank()) {
            raster.selectionOutline(project.selectedObjectId);
        }
        return new Frame(w, h, pixels, objectIds, bounds);
    }

    public static void draw(GuiGraphicsExtractor graphics, Frame frame,
                            int x, int y, int width, int height) {
        if (frame == null) return;
        for (int row = 0; row < frame.height; row++) {
            int screenY0 = y + row * height / frame.height;
            int screenY1 = y + (row + 1) * height / frame.height;
            if (screenY1 <= screenY0) screenY1 = screenY0 + 1;
            int column = 0;
            while (column < frame.width) {
                int color = frame.pixels[row * frame.width + column];
                if ((color >>> 24) == 0) {
                    column++;
                    continue;
                }
                int runStart = column++;
                while (column < frame.width && frame.pixels[row * frame.width + column] == color) column++;
                int x0 = x + runStart * width / frame.width;
                int x1 = x + column * width / frame.width;
                if (x1 <= x0) x1 = x0 + 1;
                graphics.fill(x0, screenY0, x1, screenY1, color);
            }
        }
    }

    public static String hitTest(Frame frame, int x, int y) {
        if (frame == null || x < 0 || y < 0 || x >= frame.width || y >= frame.height) return null;
        return frame.objectIds[y * frame.width + x];
    }

    public static boolean hasVisiblePixels(Frame frame, String objectId) {
        if (frame == null || objectId == null || objectId.isBlank()) return false;
        Bounds bounds = frame.bounds.get(objectId);
        if (bounds == null) return false;
        int minX = Math.max(0, bounds.minX);
        int minY = Math.max(0, bounds.minY);
        int maxX = Math.min(frame.width - 1, bounds.maxX);
        int maxY = Math.min(frame.height - 1, bounds.maxY);
        for (int y = minY; y <= maxY; y++) {
            int offset = y * frame.width;
            for (int x = minX; x <= maxX; x++) {
                if (objectId.equals(frame.objectIds[offset + x])) return true;
            }
        }
        return false;
    }

    private static boolean renderBakedBlockModel(Raster raster, SceneProject.SceneObject object,
                                                   Minecraft minecraft, int[] ignoredBounds) {
        try {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(object.assetId);
            if (id == null) return false;
            Block block = BuiltInRegistries.BLOCK.getValue(id);
            if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) return false;
            BlockState state = block.defaultBlockState();
            BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(RandomSource.create(0x435241465453484FL), parts);
            boolean rendered = false;
            for (BlockStateModelPart part : parts) {
                List<BakedQuad> quads = new ArrayList<>(part.getQuads(null));
                for (Direction direction : Direction.values()) quads.addAll(part.getQuads(direction));
                for (BakedQuad quad : quads) {
                    Vec3[] vertices = new Vec3[4];
                    float[][] uv = new float[4][2];
                    for (int i = 0; i < 4; i++) {
                        var p = quad.position(i);
                        vertices[i] = transformModelVertex(p.x() - 0.5D, p.y() - 0.5D,
                                p.z() - 0.5D, object.transform);
                        long packedUv = quad.packedUV(i);
                        uv[i][0] = Float.intBitsToFloat((int) packedUv);
                        uv[i][1] = Float.intBitsToFloat((int) (packedUv >>> 32));
                    }
                    TextureAtlasSprite sprite = quad.materialInfo().sprite();
                    int fallbackColor = shade(materialColor(object.assetId), quad.direction().ordinal());
                    raster.texturedQuad(vertices[0], vertices[1], vertices[2], vertices[3],
                            sprite, spriteImage(minecraft, sprite), uv,
                            faceBrightness(quad.direction().ordinal()), fallbackColor, object.id);
                    rendered = true;
                }
            }
            return rendered;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static BufferedImage spriteImage(Minecraft minecraft, TextureAtlasSprite sprite) {
        if (minecraft == null || sprite == null) return null;
        try {
            ResourceManager manager = minecraft.getResourceManager();
            net.minecraft.resources.Identifier spriteId = sprite.contents().name();
            net.minecraft.resources.Identifier fileId = net.minecraft.resources.Identifier.tryParse(
                    spriteId.getNamespace() + ":textures/" + spriteId.getPath() + ".png");
            if (fileId == null) return null;
            Map<net.minecraft.resources.Identifier, BufferedImage> cache =
                    SPRITE_IMAGES.computeIfAbsent(manager, ignored -> new HashMap<>());
            BufferedImage cached = cache.get(fileId);
            if (cached != null) return cached;
            var resource = manager.getResource(fileId).orElse(null);
            if (resource == null) return null;
            try (InputStream input = resource.open()) {
                BufferedImage loaded = ImageIO.read(input);
                if (loaded != null) cache.put(fileId, loaded);
                return loaded;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Vec3 transformModelVertex(double x, double y, double z, SceneProject.Transform t) {
        double rx = Math.toRadians(t.rotationX), ry = Math.toRadians(t.rotationY), rz = Math.toRadians(t.rotationZ);
        double sx = Math.max(0.05, Math.min(20.0, t.scaleX));
        double sy = Math.max(0.05, Math.min(20.0, t.scaleY));
        double sz = Math.max(0.05, Math.min(20.0, t.scaleZ));
        x *= sx; y *= sy; z *= sz;
        double y1 = y * Math.cos(rx) - z * Math.sin(rx);
        double z1 = y * Math.sin(rx) + z * Math.cos(rx);
        double x2 = x * Math.cos(ry) + z1 * Math.sin(ry);
        double z2 = -x * Math.sin(ry) + z1 * Math.cos(ry);
        double x3 = x2 * Math.cos(rz) - y1 * Math.sin(rz);
        double y3 = x2 * Math.sin(rz) + y1 * Math.cos(rz);
        return new Vec3(x3 + t.x, y3 + t.y, z2 + t.z);
    }

    private static List<Box> objectBoxes(SceneProject.SceneObject object) {
        List<Box> boxes = new ArrayList<>();
        String id = object.assetId == null ? "" : object.assetId.toLowerCase(java.util.Locale.ROOT);
        if (!"minecraft_entity".equals(object.kind)) {
            if (id.contains("slab")) {
                boxes.add(new Box(-0.5, -0.5, -0.5, 0.5, 0.0, 0.5));
            } else if (id.contains("carpet") || id.contains("pressure_plate")) {
                boxes.add(new Box(-0.5, -0.5, -0.5, 0.5, -0.40, 0.5));
            } else if (id.contains("stairs")) {
                boxes.add(new Box(-0.5, -0.5, -0.5, 0.5, 0.0, 0.5));
                boxes.add(new Box(-0.5, 0.0, 0.0, 0.5, 0.5, 0.5));
            } else if (id.contains("fence") || id.contains("wall")) {
                boxes.add(new Box(-0.09, -0.5, -0.09, 0.09, 0.5, 0.09));
                boxes.add(new Box(-0.5, -0.22, -0.09, 0.5, -0.08, 0.09));
                boxes.add(new Box(-0.5, 0.12, -0.09, 0.5, 0.26, 0.09));
                boxes.add(new Box(-0.09, -0.22, -0.5, 0.09, -0.08, 0.5));
                boxes.add(new Box(-0.09, 0.12, -0.5, 0.09, 0.26, 0.5));
            } else if (id.contains("torch") || id.contains("candle")) {
                boxes.add(new Box(-0.055, -0.5, -0.055, 0.055, 0.24, 0.055));
                boxes.add(new Box(-0.10, 0.24, -0.10, 0.10, 0.42, 0.10));
            } else if (id.contains("lantern")) {
                boxes.add(new Box(-0.24, -0.5, -0.24, 0.24, -0.38, 0.24));
                boxes.add(new Box(-0.19, -0.38, -0.19, 0.19, 0.24, 0.19));
                boxes.add(new Box(-0.25, 0.24, -0.25, 0.25, 0.34, 0.25));
                boxes.add(new Box(-0.07, 0.34, -0.07, 0.07, 0.46, 0.07));
            } else if (id.contains("chest")) {
                boxes.add(new Box(-0.5, -0.5, -0.5, 0.5, 0.25, 0.5));
                boxes.add(new Box(-0.5, 0.25, -0.5, 0.5, 0.5, 0.5));
                boxes.add(new Box(-0.055, -0.12, -0.515, 0.055, 0.12, -0.48));
            } else if (id.contains("crafting_table")) {
                boxes.add(new Box(-0.5, -0.5, -0.5, 0.5, 0.35, 0.5));
                boxes.add(new Box(-0.43, 0.35, -0.43, 0.43, 0.51, 0.43));
            } else if (id.contains("bed")) {
                boxes.add(new Box(-0.5, -0.5, -0.5, 0.5, -0.22, 0.5));
                boxes.add(new Box(-0.5, -0.22, -0.5, 0.5, -0.08, 0.5));
            } else if (id.contains("door") || id.contains("trapdoor")) {
                boxes.add(id.contains("trapdoor")
                        ? new Box(-0.5, 0.34, -0.5, 0.5, 0.5, 0.5)
                        : new Box(-0.5, -0.5, -0.045, 0.5, 0.5, 0.045));
            } else if (id.contains("flower") || id.contains("sapling") || id.contains("tulip")
                    || id.contains("dandelion") || id.contains("poppy") || id.contains("orchid")) {
                boxes.add(new Box(-0.045, -0.5, -0.045, 0.045, 0.12, 0.045));
                boxes.add(new Box(-0.20, 0.12, -0.08, 0.20, 0.42, 0.08));
            } else {
                boxes.add(new Box(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5));
            }
            return boxes;
        }
        if (id.contains("spider") || id.contains("cave_spider")) {
            boxes.add(new Box(-0.58, -0.12, -0.72, 0.58, 0.28, 0.58));
            boxes.add(new Box(-0.32, -0.08, 0.42, 0.32, 0.34, 0.82));
            for (int side : new int[]{-1, 1}) {
                for (int leg = 0; leg < 4; leg++) {
                    double z = -0.58 + leg * 0.34;
                    double x = side * (0.62 + (leg % 2) * 0.12);
                    boxes.add(new Box(Math.min(side * 0.34, x) - 0.055, -0.20,
                            z - 0.06, Math.max(side * 0.34, x) + 0.055, -0.08, z + 0.06));
                }
            }
        } else if (id.contains("creeper")) {
            boxes.add(new Box(-0.28, -0.38, -0.20, 0.28, 0.42, 0.20));
            boxes.add(new Box(-0.30, 0.42, -0.22, 0.30, 0.94, 0.22));
            boxes.add(new Box(-0.28, -0.82, -0.20, -0.04, -0.38, 0.20));
            boxes.add(new Box(0.04, -0.82, -0.20, 0.28, -0.38, 0.20));
        } else if (id.contains("ghast")) {
            boxes.add(new Box(-0.62, -0.55, -0.62, 0.62, 0.62, 0.62));
            for (int i = 0; i < 9; i++) {
                double x = -0.42 + (i % 3) * 0.42;
                double z = -0.42 + (i / 3) * 0.42;
                double length = 0.30 + ((i * 7) % 5) * 0.10;
                boxes.add(new Box(x - 0.055, -0.55 - length, z - 0.055,
                        x + 0.055, -0.55, z + 0.055));
            }
        } else if (id.contains("slime") || id.contains("magma_cube")) {
            boxes.add(new Box(-0.48, -0.48, -0.48, 0.48, 0.48, 0.48));
        } else if (id.contains("cow") || id.contains("pig") || id.contains("sheep") || id.contains("wolf")
                || id.contains("cat") || id.contains("horse") || id.contains("goat") || id.contains("llama")) {
            boxes.add(new Box(-0.55, -0.15, -0.8, 0.55, 0.48, 0.55));
            boxes.add(new Box(-0.32, 0.05, 0.42, 0.32, 0.58, 0.88));
            boxes.add(new Box(-0.42, -0.5, -0.62, -0.18, -0.12, -0.38));
            boxes.add(new Box(0.18, -0.5, -0.62, 0.42, -0.12, -0.38));
            boxes.add(new Box(-0.42, -0.5, 0.28, -0.18, -0.12, 0.5));
            boxes.add(new Box(0.18, -0.5, 0.28, 0.42, -0.12, 0.5));
        } else if (id.contains("enderman")) {
            boxes.add(new Box(-0.20, -0.95, -0.14, 0.20, 0.45, 0.14));
            boxes.add(new Box(-0.22, 0.45, -0.16, 0.22, 0.90, 0.16));
            boxes.add(new Box(-0.38, -0.90, -0.10, -0.22, 0.42, 0.10));
            boxes.add(new Box(0.22, -0.90, -0.10, 0.38, 0.42, 0.10));
            boxes.add(new Box(-0.19, -1.55, -0.11, -0.02, -0.95, 0.11));
            boxes.add(new Box(0.02, -1.55, -0.11, 0.19, -0.95, 0.11));
        } else {
            boxes.add(new Box(-0.28, -0.18, -0.18, 0.28, 0.58, 0.18));
            boxes.add(new Box(-0.22, 0.58, -0.22, 0.22, 1.02, 0.22));
            boxes.add(new Box(-0.48, -0.12, -0.14, -0.28, 0.5, 0.14));
            boxes.add(new Box(0.28, -0.12, -0.14, 0.48, 0.5, 0.14));
            boxes.add(new Box(-0.24, -0.62, -0.14, -0.04, -0.18, 0.14));
            boxes.add(new Box(0.04, -0.62, -0.14, 0.24, -0.18, 0.14));
        }
        return boxes;
    }

    private static Vec3[] boxVertices(Box b, SceneProject.Transform t) {
        double[][] points = {
                {b.minX,b.minY,b.minZ},{b.maxX,b.minY,b.minZ},
                {b.maxX,b.maxY,b.minZ},{b.minX,b.maxY,b.minZ},
                {b.minX,b.minY,b.maxZ},{b.maxX,b.minY,b.maxZ},
                {b.maxX,b.maxY,b.maxZ},{b.minX,b.maxY,b.maxZ}
        };
        double rx = Math.toRadians(t.rotationX), ry = Math.toRadians(t.rotationY), rz = Math.toRadians(t.rotationZ);
        double sx = Math.max(0.05, Math.min(20.0, t.scaleX));
        double sy = Math.max(0.05, Math.min(20.0, t.scaleY));
        double sz = Math.max(0.05, Math.min(20.0, t.scaleZ));
        double cx = Math.cos(rx), sxr = Math.sin(rx), cy = Math.cos(ry), syr = Math.sin(ry);
        double cz = Math.cos(rz), szr = Math.sin(rz);
        Vec3[] result = new Vec3[8];
        for (int i = 0; i < points.length; i++) {
            double x = points[i][0] * sx, y = points[i][1] * sy, z = points[i][2] * sz;
            double y1 = y * cx - z * sxr, z1 = y * sxr + z * cx;
            double x2 = x * cy + z1 * syr, z2 = -x * syr + z1 * cy;
            double x3 = x2 * cz - y1 * szr, y3 = x2 * szr + y1 * cz;
            result[i] = new Vec3(x3 + t.x, y3 + t.y, z2 + t.z);
        }
        return result;
    }

    private static int materialColor(String assetId) {
        String id = assetId == null ? "" : assetId.toLowerCase(java.util.Locale.ROOT);
        if (id.contains("grass") || id.contains("leaves") || id.contains("moss")) return 0xFF69A84F;
        if (id.contains("dirt") || id.contains("mud") || id.contains("coarse")) return 0xFF94633E;
        if (id.contains("oak") || id.contains("wood") || id.contains("plank") || id.contains("log")) return 0xFFB4874F;
        if (id.contains("diamond") || id.contains("prismarine") || id.contains("ice")) return 0xFF58C7C7;
        if (id.contains("gold")) return 0xFFE6C34A;
        if (id.contains("iron") || id.contains("stone") || id.contains("andesite")) return 0xFF96999B;
        if (id.contains("redstone") || id.contains("nether")) return 0xFFB94D42;
        if (id.contains("sand") || id.contains("sandstone")) return 0xFFD9C58A;
        if (id.contains("water")) return 0xFF4D8DD1;
        if (id.contains("lava") || id.contains("magma")) return 0xFFE66B32;
        if (id.contains("creeper")) return 0xFF68A84A;
        if (id.contains("skeleton")) return 0xFFD8D6C8;
        if (id.contains("zombie")) return 0xFF548E59;
        return 0xFF8C9AA7;
    }

    private static float faceBrightness(int face) {
        return switch (face) {
            case 0 -> 0.52F;
            case 1 -> 1.0F;
            case 2 -> 0.70F;
            case 3 -> 0.88F;
            case 4 -> 0.96F;
            case 5 -> 0.62F;
            default -> 0.82F;
        };
    }

    private static int shade(int color, int face) {
        double[] factors = {0.78, 0.62, 0.70, 0.88, 1.0, 0.52};
        double factor = factors[face % factors.length];
        int a = color >>> 24;
        int r = (int) (((color >>> 16) & 255) * factor);
        int g = (int) (((color >>> 8) & 255) * factor);
        int b = (int) ((color & 255) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public record Frame(int width, int height, int[] pixels, String[] objectIds, Map<String, Bounds> bounds) {}
    public record Bounds(int minX, int minY, int maxX, int maxY) {}
    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    private record Vec3(double x, double y, double z) {}
    private record CameraPoint(double x, double y, double z) {}

    private static final class CameraBasis {
        final double rx, rz, ux, uy, uz, fx, fy, fz;
        final double x, y, z;
        final double zoom;
        CameraBasis(SceneProject.Camera c) {
            double yaw = Math.toRadians(c.yaw), pitch = Math.toRadians(c.pitch);
            double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
            rx = cy; rz = -sy;
            fx = -sy * cp; fy = -sp; fz = -cy * cp;
            ux = -sy * sp; uy = cp; uz = -cy * sp;
            x = c.x; y = c.y; z = c.z;
            zoom = Math.max(0.2D, Math.min(4.0D, c.zoom));
        }
        CameraPoint transform(Vec3 p) {
            double dx = p.x - x, dy = p.y - y, dz = p.z - z;
            return new CameraPoint(dx * rx + dz * rz, dx * ux + dy * uy + dz * uz,
                    dx * fx + dy * fy + dz * fz);
        }
    }

    private static final class Raster {
        final int width, height;
        final int[] pixels;
        final double[] depth;
        final String[] objectIds;
        final CameraBasis camera;
        final double focal;
        final Map<String, MutableBounds> trackedBounds = new HashMap<>();
        Raster(int width, int height, int[] pixels, double[] depth, String[] objectIds, CameraBasis camera) {
            this.width = width; this.height = height; this.pixels = pixels; this.depth = depth;
            this.objectIds = objectIds; this.camera = camera;
            this.focal = Math.min(width, height) * 0.92 * camera.zoom;
        }
        void texturedQuad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, TextureAtlasSprite sprite,
                          BufferedImage texture, float[][] uv, float brightness,
                          int fallbackColor, String objectId) {
            if (sprite == null || texture == null) {
                quad(a, b, c, d, fallbackColor, objectId);
                return;
            }

            List<TexturedPoint> input = List.of(
                    new TexturedPoint(camera.transform(a), uv[0][0], uv[0][1]),
                    new TexturedPoint(camera.transform(b), uv[1][0], uv[1][1]),
                    new TexturedPoint(camera.transform(c), uv[2][0], uv[2][1]),
                    new TexturedPoint(camera.transform(d), uv[3][0], uv[3][1]));
            List<TexturedPoint> clipped = new ArrayList<>(6);
            TexturedPoint previous = input.get(input.size() - 1);
            boolean previousInside = previous.position.z > NEAR;
            for (TexturedPoint current : input) {
                boolean currentInside = current.position.z > NEAR;
                if (currentInside != previousInside) {
                    double t = (NEAR - previous.position.z)
                            / (current.position.z - previous.position.z);
                    CameraPoint p = new CameraPoint(
                            previous.position.x + (current.position.x - previous.position.x) * t,
                            previous.position.y + (current.position.y - previous.position.y) * t,
                            NEAR + 0.00001D);
                    clipped.add(new TexturedPoint(p,
                            (float) (previous.u + (current.u - previous.u) * t),
                            (float) (previous.v + (current.v - previous.v) * t)));
                }
                if (currentInside) clipped.add(current);
                previous = current;
                previousInside = currentInside;
            }
            if (clipped.size() < 3) return;

            TexturedPoint origin = clipped.get(0);
            ScreenPoint screenOrigin = project(origin.position);
            for (int i = 1; i < clipped.size() - 1; i++) {
                TexturedPoint second = clipped.get(i);
                TexturedPoint third = clipped.get(i + 1);
                texturedTriangle(screenOrigin, project(second.position), project(third.position),
                        new float[]{origin.u, origin.v},
                        new float[]{second.u, second.v},
                        new float[]{third.u, third.v},
                        sprite, texture, brightness, objectId);
            }
        }
        void texturedTriangle(ScreenPoint a, ScreenPoint b, ScreenPoint c,
                              float[] uvA, float[] uvB, float[] uvC,
                              TextureAtlasSprite sprite, BufferedImage texture,
                              float brightness, String objectId) {
            double area = edge(a.x,a.y,b.x,b.y,c.x,c.y);
            if (Math.abs(area) < 0.0001) return;
            int minX = clamp((int)Math.floor(Math.min(a.x,Math.min(b.x,c.x))),0,width-1);
            int maxX = clamp((int)Math.ceil(Math.max(a.x,Math.max(b.x,c.x))),0,width-1);
            int minY = clamp((int)Math.floor(Math.min(a.y,Math.min(b.y,c.y))),0,height-1);
            int maxY = clamp((int)Math.ceil(Math.max(a.y,Math.max(b.y,c.y))),0,height-1);
            int spriteWidth = Math.max(1, texture.getWidth());
            int spriteHeight = Math.max(1, texture.getHeight());
            float uRange = sprite.getU1() - sprite.getU0();
            float vRange = sprite.getV1() - sprite.getV0();
            for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5, py = y + 0.5;
                double wa = edge(b.x,b.y,c.x,c.y,px,py) / area;
                double wb = edge(c.x,c.y,a.x,a.y,px,py) / area;
                double wc = 1.0 - wa - wb;
                if (wa < -0.0001 || wb < -0.0001 || wc < -0.0001) continue;
                double reciprocalDepth = wa / a.z + wb / b.z + wc / c.z;
                double z = 1.0 / reciprocalDepth;
                int index = y * width + x;
                if (z >= depth[index]) continue;
                double u = (wa * uvA[0] / a.z + wb * uvB[0] / b.z + wc * uvC[0] / c.z) / reciprocalDepth;
                double v = (wa * uvA[1] / a.z + wb * uvB[1] / b.z + wc * uvC[1] / c.z) / reciprocalDepth;
                double localU = uRange == 0.0F ? 0.0D : (u - sprite.getU0()) / uRange;
                double localV = vRange == 0.0F ? 0.0D : (v - sprite.getV0()) / vRange;
                int tx = clamp((int) (Math.max(0.0D, Math.min(0.999999D, localU)) * spriteWidth), 0, spriteWidth - 1);
                int ty = clamp((int) (Math.max(0.0D, Math.min(0.999999D, localV)) * spriteHeight), 0, spriteHeight - 1);
                int sample = texture.getRGB(tx, ty);
                int alpha = (sample >>> 24) & 255;
                if (alpha < 8) continue;
                int red = (sample >>> 16) & 255;
                int green = (sample >>> 8) & 255;
                int blue = sample & 255;
                red = clamp((int) (red * brightness), 0, 255);
                green = clamp((int) (green * brightness), 0, 255);
                blue = clamp((int) (blue * brightness), 0, 255);
                pixels[index] = (alpha << 24) | (red << 16) | (green << 8) | blue;
                depth[index] = z;
                objectIds[index] = objectId;
                trackedBounds.computeIfAbsent(objectId, ignored -> new MutableBounds()).include(x, y);
            }
        }
        Bounds quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, String objectId) {
            CameraPoint ca = camera.transform(a), cb = camera.transform(b), cc = camera.transform(c), cd = camera.transform(d);
            Bounds first = clippedTriangle(ca, cb, cc, color, objectId);
            Bounds second = clippedTriangle(ca, cc, cd, color, objectId);
            if (first == null) return second;
            if (second == null) return first;
            return new Bounds(Math.min(first.minX, second.minX), Math.min(first.minY, second.minY),
                    Math.max(first.maxX, second.maxX), Math.max(first.maxY, second.maxY));
        }
        Bounds clippedTriangle(CameraPoint a, CameraPoint b, CameraPoint c, int color, String objectId) {
            List<CameraPoint> input = List.of(a, b, c);
            List<CameraPoint> clipped = new ArrayList<>(4);
            CameraPoint previous = input.get(input.size() - 1);
            boolean previousInside = previous.z > NEAR;
            for (CameraPoint current : input) {
                boolean currentInside = current.z > NEAR;
                if (currentInside != previousInside) {
                    double t = (NEAR - previous.z) / (current.z - previous.z);
                    clipped.add(new CameraPoint(previous.x + (current.x - previous.x) * t,
                            previous.y + (current.y - previous.y) * t, NEAR + 0.00001D));
                }
                if (currentInside) clipped.add(current);
                previous = current;
                previousInside = currentInside;
            }
            if (clipped.size() < 3) return null;
            ScreenPoint origin = project(clipped.get(0));
            Bounds combined = null;
            for (int i = 1; i < clipped.size() - 1; i++) {
                Bounds part = triangle(origin, project(clipped.get(i)), project(clipped.get(i + 1)), color, objectId);
                if (part == null) continue;
                combined = combined == null ? part : new Bounds(
                        Math.min(combined.minX, part.minX), Math.min(combined.minY, part.minY),
                        Math.max(combined.maxX, part.maxX), Math.max(combined.maxY, part.maxY));
            }
            return combined;
        }
        ScreenPoint project(CameraPoint p) {
            return new ScreenPoint(width * 0.5 + p.x * focal / p.z,
                    height * 0.5 - p.y * focal / p.z, p.z);
        }
        Bounds triangle(ScreenPoint a, ScreenPoint b, ScreenPoint c, int color, String objectId) {
            double area = edge(a.x,a.y,b.x,b.y,c.x,c.y);
            if (Math.abs(area) < 0.0001) return null;
            int minX = clamp((int)Math.floor(Math.min(a.x,Math.min(b.x,c.x))),0,width-1);
            int maxX = clamp((int)Math.ceil(Math.max(a.x,Math.max(b.x,c.x))),0,width-1);
            int minY = clamp((int)Math.floor(Math.min(a.y,Math.min(b.y,c.y))),0,height-1);
            int maxY = clamp((int)Math.ceil(Math.max(a.y,Math.max(b.y,c.y))),0,height-1);
            boolean drawn = false;
            int actualMinX=width, actualMinY=height, actualMaxX=-1, actualMaxY=-1;
            for(int y=minY;y<=maxY;y++) for(int x=minX;x<=maxX;x++) {
                double px=x+0.5, py=y+0.5;
                double wa=edge(b.x,b.y,c.x,c.y,px,py)/area;
                double wb=edge(c.x,c.y,a.x,a.y,px,py)/area;
                double wc=1.0-wa-wb;
                if(wa < -0.0001 || wb < -0.0001 || wc < -0.0001) continue;
                double z=1.0/(wa/a.z+wb/b.z+wc/c.z);
                int index=y*width+x;
                if(z>=depth[index]) continue;
                depth[index]=z;
                pixels[index]=color;
                objectIds[index]=objectId;
                trackedBounds.computeIfAbsent(objectId, ignored -> new MutableBounds()).include(x, y);
                drawn=true;
                actualMinX=Math.min(actualMinX,x); actualMinY=Math.min(actualMinY,y);
                actualMaxX=Math.max(actualMaxX,x); actualMaxY=Math.max(actualMaxY,y);
            }
            return drawn ? new Bounds(actualMinX,actualMinY,actualMaxX,actualMaxY) : null;
        }
        Bounds objectBounds(String id) {
            MutableBounds bounds = trackedBounds.get(id);
            return bounds == null ? null : bounds.toBounds();
        }
        void selectionOutline(String selectedId) {
            int cyan = 0xFF42E9F4;
            boolean[] edgePixels = new boolean[objectIds.length];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = y * width + x;
                    if (!selectedId.equals(objectIds[index])) continue;
                    if (x == 0 || x == width - 1 || y == 0 || y == height - 1
                            || !selectedId.equals(objectIds[index - 1])
                            || !selectedId.equals(objectIds[index + 1])
                            || !selectedId.equals(objectIds[index - width])
                            || !selectedId.equals(objectIds[index + width])) {
                        edgePixels[index] = true;
                    }
                }
            }
            for (int i = 0; i < edgePixels.length; i++) {
                if (edgePixels[i]) pixels[i] = cyan;
            }
        }
        void set(int x,int y,int color) {
            if(x>=0&&x<width&&y>=0&&y<height) pixels[y*width+x]=color;
        }
        static double edge(double ax,double ay,double bx,double by,double px,double py) {
            return (px-ax)*(by-ay)-(py-ay)*(bx-ax);
        }
        static int clamp(int v,int min,int max) { return Math.max(min,Math.min(max,v)); }
    }
    private static final class MutableBounds {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        void include(int x, int y) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        Bounds toBounds() {
            return maxX < minX ? null : new Bounds(minX, minY, maxX, maxY);
        }
    }
    private record ScreenPoint(double x,double y,double z) {}\n    private record TexturedPoint(CameraPoint position, float u, float v) {}
}
