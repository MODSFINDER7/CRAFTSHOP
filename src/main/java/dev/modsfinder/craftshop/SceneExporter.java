package dev.modsfinder.craftshop;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

public final class SceneExporter {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static Pending pending;

    private SceneExporter() {}

    public static void beginExport(Minecraft minecraft, Screen returnScreen,
                                   SceneProject project, Consumer<String> completion) {
        if (pending != null) {
            completion.accept("A PNG render is already running");
            return;
        }
        if (minecraft == null || minecraft.gameRenderer == null || minecraft.level == null) {
            completion.accept("Open a Minecraft world before rendering");
            return;
        }

        try {
            Path image = ProjectManager.resolveCanvasImage(project);
            if (image == null) {
                completion.accept("Choose a PNG before rendering");
                return;
            }
            SceneRenderScreen renderScreen = new SceneRenderScreen(project, image);
            if (!renderScreen.isReady()) {
                completion.accept("Selected PNG could not be prepared for rendering");
                return;
            }
            pending = new Pending(returnScreen, renderScreen, image, project.name, completion, 1);
            minecraft.gui.setScreen(renderScreen);
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not prepare CraftShop scene render", exception);
            completion.accept("Render setup failed: " + exception.getMessage());
        }
    }

    public static void tick(Minecraft minecraft) {
        Pending current = pending;
        if (current == null) return;
        if (minecraft.gui.screen() != current.renderScreen) {
            pending = null;
            minecraft.gui.setScreen(current.returnScreen);
            current.completion.accept("Render cancelled");
            return;
        }
        if (current.ticksRemaining > 0) {
            current.ticksRemaining--;
            return;
        }

        pending = null;
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            String result;
            try {
                result = composeAndSave(minecraft, image, current);
            } catch (Exception exception) {
                CraftShop.LOGGER.error("Could not render CraftShop scene to PNG", exception);
                result = "PNG render failed: " + exception.getMessage();
            } finally {
                image.close();
            }
            String message = result;
            minecraft.execute(() -> {
                SceneAssetRenderer.clear();
                minecraft.gui.setScreen(current.returnScreen);
                current.completion.accept(message);
            });
        });
    }

    private static String composeAndSave(Minecraft minecraft, NativeImage screenshot, Pending current)
            throws IOException {
        BufferedImage original = ImageIO.read(current.imagePath.toFile());
        if (original == null) throw new IOException("Selected PNG could not be decoded");

        int guiWidth = Math.max(1, minecraft.getWindow().getGuiScaledWidth());
        int guiHeight = Math.max(1, minecraft.getWindow().getGuiScaledHeight());
        SceneRenderScreen.CanvasBounds bounds = SceneRenderScreen.canvasBounds(
                guiWidth, guiHeight, original.getWidth(), original.getHeight());

        double pixelScaleX = screenshot.getWidth() / (double) guiWidth;
        double pixelScaleY = screenshot.getHeight() / (double) guiHeight;
        int cropX = clamp((int) Math.round(bounds.x() * pixelScaleX), 0, screenshot.getWidth() - 1);
        int cropY = clamp((int) Math.round(bounds.y() * pixelScaleY), 0, screenshot.getHeight() - 1);
        int cropWidth = Math.min(screenshot.getWidth() - cropX,
                Math.max(1, (int) Math.round(bounds.width() * pixelScaleX)));
        int cropHeight = Math.min(screenshot.getHeight() - cropY,
                Math.max(1, (int) Math.round(bounds.height() * pixelScaleY)));

        BufferedImage captured = new BufferedImage(cropWidth, cropHeight, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < cropHeight; y++) {
            for (int x = 0; x < cropWidth; x++) {
                captured.setRGB(x, y, screenshot.getPixel(cropX + x, cropY + y));
            }
        }

        BufferedImage output = new BufferedImage(original.getWidth(), original.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(captured, 0, 0, output.getWidth(), output.getHeight(), null);
        } finally {
            graphics.dispose();
            original.flush();
            captured.flush();
        }

        Path directory = ScreenshotManager.getGalleryDirectory();
        if (directory == null) {
            directory = minecraft.gameDirectory.toPath().resolve("craftshop").resolve("screenshots");
        }
        Files.createDirectories(directory);
        String safeName = current.projectName == null ? "CraftShop" :
                current.projectName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeName.isBlank()) safeName = "CraftShop";
        String timestamp = FILE_TIME.format(LocalDateTime.now());
        Path destination = directory.resolve(safeName + "-render-" + timestamp + ".png");
        int suffix = 2;
        while (Files.exists(destination)) {
            destination = directory.resolve(safeName + "-render-" + timestamp + "-" + suffix++ + ".png");
        }
        if (!ImageIO.write(output, "png", destination.toFile())) {
            output.flush();
            throw new IOException("PNG encoder is unavailable");
        }
        output.flush();
        return "Rendered PNG added to gallery: " + destination.getFileName();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Pending {
        final Screen returnScreen;
        final Screen renderScreen;
        final Path imagePath;
        final String projectName;
        final Consumer<String> completion;
        int ticksRemaining;

        Pending(Screen returnScreen, Screen renderScreen, Path imagePath, String projectName,
                Consumer<String> completion, int ticksRemaining) {
            this.returnScreen = returnScreen;
            this.renderScreen = renderScreen;
            this.imagePath = imagePath;
            this.projectName = projectName;
            this.completion = completion;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
