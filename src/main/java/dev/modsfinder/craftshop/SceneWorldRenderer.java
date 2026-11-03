package dev.modsfinder.craftshop;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Independent CPU scene renderer. It rasterizes scene geometry into its own
 * color/depth buffers and never reads or writes the active Minecraft level.
 */
public final class SceneWorldRenderer {
    private static final double NEAR = 0.08D;

    private SceneWorldRenderer() {}

    public static Frame render(SceneProject project, int width, int height) {
        int w = Math.max(64, Math.min(384, width));
        int h = Math.max(64, Math.min(256, height));
        int[] pixels = new int[w * h];
        double[] depth = new double[w * h];
        java.util.Arrays.fill(depth, Double.POSITIVE_INFINITY);
        Map<String, Bounds> bounds = new HashMap<>();

        SceneProject.Camera camera = project.camera == null ? new SceneProject.Camera() : project.camera;
        CameraBasis basis = new CameraBasis(camera);
        Raster raster = new Raster(w, h, pixels, depth, basis);

        if (project.objects != null) {
            for (SceneProject.SceneObject object : project.objects) {
                if (object == null || !object.visible || object.transform == null) continue;
                int startX = w, startY = h, endX = -1, endY = -1;
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
                        Bounds drawn = raster.quad(vertices[face[0]], vertices[face[1]],
                                vertices[face[2]], vertices[face[3]], faceColor, object.id);
                        if (drawn != null) {
                            startX = Math.min(startX, drawn.minX);
                            startY = Math.min(startY, drawn.minY);
                            endX = Math.max(endX, drawn.maxX);
                            endY = Math.max(endY, drawn.maxY);
                        }
                    }
                }
                if (endX >= startX && endY >= startY) {
                    bounds.put(object.id, new Bounds(startX, startY, endX, endY));
                    if (object.id.equals(project.selectedObjectId)) {
                        raster.selectionOutline(startX, startY, endX, endY);
                    }
                }
            }
        }
        return new Frame(w, h, pixels, bounds);
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
        if (frame == null) return null;
        String result = null;
        int bestArea = Integer.MAX_VALUE;
        for (Map.Entry<String, Bounds> entry : frame.bounds.entrySet()) {
            Bounds b = entry.getValue();
            if (x < b.minX - 3 || x > b.maxX + 3 || y < b.minY - 3 || y > b.maxY + 3) continue;
            int area = Math.max(1, (b.maxX - b.minX) * (b.maxY - b.minY));
            if (area < bestArea) {
                bestArea = area;
                result = entry.getKey();
            }
        }
        return result;
    }

    private static List<Box> objectBoxes(SceneProject.SceneObject object) {
        List<Box> boxes = new ArrayList<>();
        String id = object.assetId == null ? "" : object.assetId.toLowerCase(java.util.Locale.ROOT);
        if (!"minecraft_entity".equals(object.kind)) {
            boxes.add(new Box(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5));
            return boxes;
        }
        if (id.contains("cow") || id.contains("pig") || id.contains("sheep") || id.contains("wolf")
                || id.contains("cat") || id.contains("horse")) {
            boxes.add(new Box(-0.55, -0.15, -0.8, 0.55, 0.48, 0.55));
            boxes.add(new Box(-0.32, 0.05, 0.42, 0.32, 0.58, 0.88));
            boxes.add(new Box(-0.42, -0.5, -0.62, -0.18, -0.12, -0.38));
            boxes.add(new Box(0.18, -0.5, -0.62, 0.42, -0.12, -0.38));
            boxes.add(new Box(-0.42, -0.5, 0.28, -0.18, -0.12, 0.5));
            boxes.add(new Box(0.18, -0.5, 0.28, 0.42, -0.12, 0.5));
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

    private static int shade(int color, int face) {
        double[] factors = {0.78, 0.62, 0.70, 0.88, 1.0, 0.52};
        double factor = factors[face % factors.length];
        int a = color >>> 24;
        int r = (int) (((color >>> 16) & 255) * factor);
        int g = (int) (((color >>> 8) & 255) * factor);
        int b = (int) ((color & 255) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public record Frame(int width, int height, int[] pixels, Map<String, Bounds> bounds) {}
    public record Bounds(int minX, int minY, int maxX, int maxY) {}
    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    private record Vec3(double x, double y, double z) {}
    private record CameraPoint(double x, double y, double z) {}

    private static final class CameraBasis {
        final double rx, rz, ux, uy, uz, fx, fy, fz;
        final double x, y, z;
        CameraBasis(SceneProject.Camera c) {
            double yaw = Math.toRadians(c.yaw), pitch = Math.toRadians(c.pitch);
            double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
            rx = cy; rz = -sy;
            fx = -sy * cp; fy = -sp; fz = -cy * cp;
            ux = -sy * sp; uy = cp; uz = -cy * sp;
            x = c.x; y = c.y; z = c.z;
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
        final CameraBasis camera;
        final double focal;
        Raster(int width, int height, int[] pixels, double[] depth, CameraBasis camera) {
            this.width = width; this.height = height; this.pixels = pixels; this.depth = depth; this.camera = camera;
            this.focal = Math.min(width, height) * 0.92;
        }
        Bounds quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, String objectId) {
            CameraPoint ca = camera.transform(a), cb = camera.transform(b), cc = camera.transform(c), cd = camera.transform(d);
            if (ca.z <= NEAR || cb.z <= NEAR || cc.z <= NEAR || cd.z <= NEAR) return null;
            ScreenPoint pa = project(ca), pb = project(cb), pc = project(cc), pd = project(cd);
            Bounds first = triangle(pa, pb, pc, color);
            Bounds second = triangle(pa, pc, pd, color);
            if (first == null) return second;
            if (second == null) return first;
            return new Bounds(Math.min(first.minX, second.minX), Math.min(first.minY, second.minY),
                    Math.max(first.maxX, second.maxX), Math.max(first.maxY, second.maxY));
        }
        ScreenPoint project(CameraPoint p) {
            return new ScreenPoint(width * 0.5 + p.x * focal / p.z,
                    height * 0.5 - p.y * focal / p.z, p.z);
        }
        Bounds triangle(ScreenPoint a, ScreenPoint b, ScreenPoint c, int color) {
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
                drawn=true;
                actualMinX=Math.min(actualMinX,x); actualMinY=Math.min(actualMinY,y);
                actualMaxX=Math.max(actualMaxX,x); actualMaxY=Math.max(actualMaxY,y);
            }
            return drawn ? new Bounds(actualMinX,actualMinY,actualMaxX,actualMaxY) : null;
        }
        void selectionOutline(int minX,int minY,int maxX,int maxY) {
            int cyan=0xFF42E9F4;
            for(int x=minX;x<=maxX;x++) {
                set(x,minY,cyan); set(x,maxY,cyan);
            }
            for(int y=minY;y<=maxY;y++) {
                set(minX,y,cyan); set(maxX,y,cyan);
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
    private record ScreenPoint(double x,double y,double z) {}
}
