package dev.modsfinder.craftshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

public final class ProjectManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProjectManager() {}

    public static Path getProjectsDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("craftshop").resolve("projects");
    }

    public static Path save(SceneProject project) throws IOException {
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

    public static SceneProject load(Path file) throws IOException {
        Path directory = getProjectsDirectory().toAbsolutePath().normalize();
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(directory) || !normalized.getFileName().toString().endsWith(".craftshop")) {
            throw new IOException("Invalid CraftShop project file");
        }

        try (Reader reader = Files.newBufferedReader(normalized)) {
            SceneProject project = GSON.fromJson(reader, SceneProject.class);
            if (project == null) throw new IOException("Project file is empty");
            if (project.camera == null) project.camera = new SceneProject.Camera();
            if (project.objects == null) project.objects = new java.util.ArrayList<>();
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
