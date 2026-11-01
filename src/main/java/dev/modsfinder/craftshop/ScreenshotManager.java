package dev.modsfinder.craftshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class ScreenshotManager {
    private static final ScheduledExecutorService WATCHER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "CraftShop-Screenshot-Importer");
        thread.setDaemon(true);
        return thread;
    });
    private static final Set<String> KNOWN_FILES = ConcurrentHashMap.newKeySet();
    private static Path sourceDirectory;
    private static Path craftShopDirectory;

    private ScreenshotManager() {}

    public static void initialize() {
        Minecraft minecraft = Minecraft.getInstance();
        sourceDirectory = minecraft.gameDirectory.toPath().resolve("screenshots");
        craftShopDirectory = minecraft.gameDirectory.toPath().resolve("craftshop").resolve("screenshots");

        try {
            Files.createDirectories(sourceDirectory);
            Files.createDirectories(craftShopDirectory);
            try (Stream<Path> paths = Files.list(sourceDirectory)) {
                paths.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .forEach(KNOWN_FILES::add);
            }
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not initialize screenshot folders", exception);
        }

        WATCHER.scheduleWithFixedDelay(ScreenshotManager::importNewScreenshots, 500, 500, TimeUnit.MILLISECONDS);
    }

    public static void capture(Minecraft minecraft) {
        try {
            Files.createDirectories(sourceDirectory);
            Screenshot.grab(minecraft, false);
            if (minecraft.player != null) {
                CraftShop.LOGGER.info("CraftShop screenshot captured; importing it into the gallery.");
            }
        } catch (Exception exception) {
            CraftShop.LOGGER.error("Screenshot capture failed", exception);
            if (minecraft.player != null) {
                CraftShop.LOGGER.warn("CraftShop screenshot capture failed.");
            }
        }
    }

    private static void importNewScreenshots() {
        if (sourceDirectory == null || craftShopDirectory == null) return;

        try (Stream<Path> paths = Files.list(sourceDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".png"))
                    .sorted(Comparator.comparingLong(ScreenshotManager::lastModified))
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        if (KNOWN_FILES.contains(name)) return;

                        try {
                            long sizeBefore = Files.size(path);
                            if (sizeBefore <= 0) return;
                            Thread.sleep(250);
                            if (!Files.exists(path) || Files.size(path) != sizeBefore) return;

                            Path target = uniqueTarget(craftShopDirectory.resolve(name));
                            Path temporary = target.resolveSibling(target.getFileName() + ".part");
                            Files.copy(path, temporary, StandardCopyOption.REPLACE_EXISTING);
                            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                            KNOWN_FILES.add(name);
                            CraftShop.LOGGER.info("Imported screenshot into CraftShop gallery: {}", target.getFileName());
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        } catch (IOException exception) {
                            CraftShop.LOGGER.error("Could not import screenshot {}", name, exception);
                        }
                    });
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not scan Minecraft screenshot folder", exception);
        }
    }

    private static Path uniqueTarget(Path requested) {
        if (!Files.exists(requested)) return requested;
        String filename = requested.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String extension = dot > 0 ? filename.substring(dot) : "";
        int index = 2;
        Path candidate;
        do {
            candidate = requested.resolveSibling(base + " (" + index++ + ")" + extension);
        } while (Files.exists(candidate));
        return candidate;
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    public static Path getGalleryDirectory() {
        return craftShopDirectory;
    }

    public static Path[] getScreenshots() {
        if (craftShopDirectory == null || !Files.isDirectory(craftShopDirectory)) return new Path[0];
        try (Stream<Path> paths = Files.list(craftShopDirectory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".png"))
                    .sorted(Comparator.comparingLong(ScreenshotManager::lastModified).reversed())
                    .toArray(Path[]::new);
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not list CraftShop screenshots", exception);
            return new Path[0];
        }
    }

    public static boolean delete(Path screenshot) {
        if (!isGalleryFile(screenshot)) return false;
        try {
            boolean deleted = Files.deleteIfExists(screenshot);
            if (deleted) GalleryTextureCache.invalidate(screenshot);
            return deleted;
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not delete screenshot {}", screenshot, exception);
            return false;
        }
    }

    public static Path rename(Path screenshot, String requestedName) {
        if (!isGalleryFile(screenshot) || requestedName == null) return null;
        String safeName = requestedName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        safeName = safeName.replaceAll("[. ]+$", "");
        if (safeName.isBlank() || safeName.equals(".") || safeName.equals("..")) return null;
        if (safeName.toLowerCase().endsWith(".png")) safeName = safeName.substring(0, safeName.length() - 4);
        if (safeName.isBlank()) return null;

        Path target = craftShopDirectory.resolve(safeName + ".png").normalize();
        if (!target.getParent().equals(craftShopDirectory.normalize())) return null;
        try {
            if (Files.exists(target)) {
                if (Files.isSameFile(screenshot, target)) return screenshot;
                return null;
            }
            Path renamed = Files.move(screenshot, target);
            GalleryTextureCache.invalidate(screenshot);
            return renamed;
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not rename screenshot {}", screenshot, exception);
            return null;
        }
    }

    private static boolean isGalleryFile(Path screenshot) {
        if (screenshot == null || craftShopDirectory == null) return false;
        Path normalized = screenshot.toAbsolutePath().normalize();
        return normalized.startsWith(craftShopDirectory.toAbsolutePath().normalize())
                && normalized.getFileName().toString().toLowerCase().endsWith(".png");
    }
}
