package dev.modsfinder.craftshop;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public final class SceneExporter {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private SceneExporter() {}

    public static void exportViewport(Minecraft minecraft, int guiX, int guiY, int guiWidth, int guiHeight,
                                      Consumer<String> completion) {
        if (minecraft == null || minecraft.gameRenderer == null) {
            completion.accept("Export unavailable");
            return;
        }
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            NativeImage output = null;
            try {
                int guiScreenWidth = Math.max(1, minecraft.getWindow().getGuiScaledWidth());
                int guiScreenHeight = Math.max(1, minecraft.getWindow().getGuiScaledHeight());
                int x = Math.max(0, Math.round(guiX * image.getWidth() / (float) guiScreenWidth));
                int y = Math.max(0, Math.round(guiY * image.getHeight() / (float) guiScreenHeight));
                int width = Math.min(image.getWidth() - x,
                        Math.max(1, Math.round(guiWidth * image.getWidth() / (float) guiScreenWidth)));
                int height = Math.min(image.getHeight() - y,
                        Math.max(1, Math.round(guiHeight * image.getHeight() / (float) guiScreenHeight)));
                if (width <= 0 || height <= 0) throw new IOException("Viewport is outside the screenshot");
                output = new NativeImage(width, height, false);
                output.copyRect(image, x, y, 0, 0, width, height, false, false);

                Path directory = minecraft.gameDirectory.toPath().resolve("Pictures").resolve("CraftShop");
                Files.createDirectories(directory);
                Path destination = directory.resolve("CraftShop-" + FILE_TIME.format(LocalDateTime.now()) + ".png");
                output.writeToFile(destination);
                completion.accept("Exported: " + destination.toAbsolutePath());
            } catch (Exception exception) {
                CraftShop.LOGGER.error("Could not export CraftShop viewport", exception);
                completion.accept("Export failed: " + exception.getMessage());
            } finally {
                if (output != null) output.close();
                image.close();
            }
        });
    }
}
