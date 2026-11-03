package dev.modsfinder.craftshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Arrays;
import java.util.Comparator;

public final class ProjectManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProjectManager() {}

    public static Path getProjectsDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("craftshop").resolve("projects");
    }

    public static Path save(SceneProject project) throws IOException {
        if (project == null) throw new IOException("Project is missing");
        normalizeProject(project);
        Files.createDirectories(getProjectsDirectory());
        String safeName = sanitize(project.name);
        if (safeName.isBlank()) safeName = "Untitled Scene";
        project.name = safeName;
        Path destination = getProjectsDirectory().resolve(safeName + ".craftshop").normalize();
        if (!destination.getParent().equals(getProjectsDirectory().normalize())) {
            throw new IOException("Invalid project path");
        }

        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary)) {
            GSON.toJson(project, writer);
        }
        try {
            Files.move(temporary, destination,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return destination;
    }

    public static String importCanvasImage(Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("The selected PNG does not exist");
        }
        String original = source.getFileName().toString();
        String safe = original.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!safe.toLowerCase(Locale.ROOT).endsWith(".png")) {
            safe += ".png";
        }
        Path assets = getProjectsDirectory().resolve("assets").normalize();
        Files.createDirectories(assets);
        Path destination = assets.resolve(System.currentTimeMillis() + "-" + safe).normalize();
        if (!destination.startsWith(assets)) throw new IOException("Invalid PNG asset path");
        int suffix = 2;
        while (Files.exists(destination)) {
            destination = assets.resolve(System.currentTimeMillis() + "-" + suffix++ + "-" + safe).normalize();
        }
        validatePng(source);
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        try {
            Files.copy(source, temporary);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return "assets/" + destination.getFileName();
    }

    public static Path resolveCanvasImage(SceneProject project) throws IOException {
        if (project == null || project.canvasImage == null || project.canvasImage.isBlank()) return null;
        Path root = getProjectsDirectory().toAbsolutePath().normalize();
        Path stored = Path.of(project.canvasImage);
        Path resolved = (stored.isAbsolute() ? stored : root.resolve(stored)).toAbsolutePath().normalize();
        if (!stored.isAbsolute() && !resolved.startsWith(root)) {
            throw new IOException("Project PNG path is outside the CraftShop project folder");
        }
        return Files.isRegularFile(resolved) ? resolved : null;
    }

    public static SceneProject load(Path file) throws IOException {
        Path directory = getProjectsDirectory().toAbsolutePath().normalize();
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(directory) || !normalized.getFileName().toString().endsWith(".craftshop")) {
            throw new IOException("Invalid CraftShop project file");
        }

        try (Reader reader = Files.newBufferedReader(normalized)) {
            SceneProject project = GSON.fromJson(reader, SceneProject.class);
            if (project == null) throw new IOException("Project file is empty");
            normalizeProject(project);
            if (project.name == null || project.name.isBlank()) {
                project.name = normalized.getFileName().toString().replaceFirst("\\.craftshop$", "");
            }
            return project;
        } catch (JsonParseException exception) {
            throw new IOException("Project file is not valid CraftShop JSON", exception);
        }
    }

    public static Path[] listProjects() throws IOException {
        Files.createDirectories(getProjectsDirectory());
        try (var paths = Files.list(getProjectsDirectory())) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".craftshop"))
                    .sorted(Comparator.comparingLong(ProjectManager::lastModified).reversed())
                    .toArray(Path[]::new);
        }
    }


    private static void normalizeProject(SceneProject project) throws IOException {
        if (project.camera == null) project.camera = new SceneProject.Camera();
        if (project.objects == null) project.objects = new java.util.ArrayList<>();
        if (project.name == null || project.name.isBlank()) project.name = "Untitled Scene";
        if (project.canvasImage == null) project.canvasImage = "";
        if (project.renderedImage == null) project.renderedImage = "";
        if (project.selectedObjectId == null) project.selectedObjectId = "";

        SceneProject.Camera camera = project.camera;
        camera.x = finite(camera.x, 0.0F, -100000.0F, 100000.0F);
        camera.y = finite(camera.y, 2.0F, -100000.0F, 100000.0F);
        camera.z = finite(camera.z, 8.0F, -100000.0F, 100000.0F);
        camera.yaw = finite(camera.yaw, 0.0F, -360000.0F, 360000.0F);
        camera.pitch = finite(camera.pitch, 12.0F, -89.0F, 89.0F);
        camera.zoom = finite(camera.zoom, 1.0F, 0.2F, 4.0F);
        camera.targetX = finite(camera.targetX, 0.0F, -100000.0F, 100000.0F);
        camera.targetY = finite(camera.targetY, 0.0F, -100000.0F, 100000.0F);
        camera.targetZ = finite(camera.targetZ, 0.0F, -100000.0F, 100000.0F);

        java.util.HashSet<String> ids = new java.util.HashSet<>();
        project.objects.removeIf(java.util.Objects::isNull);
        for (SceneProject.SceneObject object : project.objects) {
            if (object.id == null || object.id.isBlank() || !ids.add(object.id)) {
                object.id = java.util.UUID.randomUUID().toString();
                ids.add(object.id);
            }
            if (object.name == null || object.name.isBlank()) object.name = "Object";
            if (object.kind == null || object.kind.isBlank()) object.kind = "mesh_cube";
            if (object.assetId == null || object.assetId.isBlank()) object.assetId = "minecraft:stone";
            if (object.transform == null) object.transform = new SceneProject.Transform();
            SceneProject.Transform t = object.transform;
            t.x = finite(t.x, 0.0F, -100000.0F, 100000.0F);
            t.y = finite(t.y, 0.0F, -100000.0F, 100000.0F);
            t.z = finite(t.z, 0.0F, -100000.0F, 100000.0F);
            t.rotationX = finite(t.rotationX, 0.0F, -360000.0F, 360000.0F);
            t.rotationY = finite(t.rotationY, 0.0F, -360000.0F, 360000.0F);
            t.rotationZ = finite(t.rotationZ, 0.0F, -360000.0F, 360000.0F);
            t.scaleX = finite(t.scaleX, 1.0F, 0.05F, 20.0F);
            t.scaleY = finite(t.scaleY, 1.0F, 0.05F, 20.0F);
            t.scaleZ = finite(t.scaleZ, 1.0F, 0.05F, 20.0F);
        }
        if (!project.selectedObjectId.isBlank()
                && project.objects.stream().noneMatch(object -> project.selectedObjectId.equals(object.id))) {
            project.selectedObjectId = "";
        }
        project.formatVersion = Math.max(1, project.formatVersion);
    }

    private static float finite(float value, float fallback, float minimum, float maximum) {
        if (!Float.isFinite(value)) return fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void validatePng(Path source) throws IOException {
        byte[] header = new byte[24];
        try (InputStream input = Files.newInputStream(source)) {
            int offset = 0;
            while (offset < header.length) {
                int count = input.read(header, offset, header.length - offset);
                if (count < 0) throw new IOException("The selected file is not a valid PNG");
                offset += count;
            }
        }
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) throw new IOException("The selected file is not a PNG image");
        }
        ByteBuffer dimensions = ByteBuffer.wrap(header, 16, 8).order(ByteOrder.BIG_ENDIAN);
        int width = dimensions.getInt();
        int height = dimensions.getInt();
        if (width < 1 || height < 1 || width > 16384 || height > 16384
                || (long) width * height > 100_000_000L) {
            throw new IOException("PNG dimensions are outside the supported limit");
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static String sanitize(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("[. ]+$", "");
    }
}
