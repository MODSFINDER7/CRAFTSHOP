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
            minecraft.player.displayClientMessage(Component.literal("CraftShop: screenshot captured. Importing to gallery..."), true);
        } catch (Exception exception) {
            CraftShop.LOGGER.error("Screenshot capture failed", exception);
            minecraft.player.displayClientMessage(Component.literal("CraftShop: screenshot capture failed."), false);
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
                        if (KNOWN_FILES.add(name)) {
                            try {
                                Path target = craftShopDirectory.resolve(name);
                                Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                                CraftShop.LOGGER.info("Imported screenshot into CraftShop gallery: {}", name);
                            } catch (IOException exception) {
                                CraftShop.LOGGER.error("Could not import screenshot {}", name, exception);
                            }
                        }
                    });
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not scan Minecraft screenshot folder", exception);
        }
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
        if (screenshot == null || craftShopDirectory == null || !screenshot.normalize().startsWith(craftShopDirectory.normalize())) return false;
        try {
            return Files.deleteIfExists(screenshot);
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not delete screenshot {}", screenshot, exception);
            return false;
        }
    }
}
