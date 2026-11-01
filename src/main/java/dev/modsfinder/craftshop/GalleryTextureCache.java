package dev.modsfinder.craftshop;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class GalleryTextureCache {
    private static final int MAX_PREVIEWS = 2;
    private static final int MAX_THUMBNAILS = 36;

    private static final Map<Path, TextureEntry> PREVIEWS = new LinkedHashMap<>(16, 0.75F, true);
    private static final Map<Path, TextureEntry> THUMBNAILS = new LinkedHashMap<>(48, 0.75F, true);

    private GalleryTextureCache() {}

    public static TextureEntry get(Path imagePath) {
        Path normalized = imagePath.toAbsolutePath().normalize();
        TextureEntry existing = PREVIEWS.get(normalized);
        if (existing != null) return existing;

        try {
            NativeImage image;
            try (var input = Files.newInputStream(normalized)) {
                image = NativeImage.read(input);
            }
            TextureEntry entry = register(normalized, "preview", image);
            PREVIEWS.put(normalized, entry);
            trim(PREVIEWS, MAX_PREVIEWS);
            return entry;
        } catch (IOException | RuntimeException exception) {
            CraftShop.LOGGER.error("Could not load gallery preview {}", normalized, exception);
            return null;
        }
    }

    public static TextureEntry getThumbnail(Path imagePath) {
        Path normalized = imagePath.toAbsolutePath().normalize();
        TextureEntry existing = THUMBNAILS.get(normalized);
        if (existing != null) return existing;

        try {
            BufferedImage source = ImageIO.read(normalized.toFile());
            if (source == null) return null;

            double scale = Math.min(128.0 / source.getWidth(), 96.0 / source.getHeight());
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaled.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.drawImage(source, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
                source.flush();
            }

            NativeImage image = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setPixel(x, y, scaled.getRGB(x, y));
                }
            }
            scaled.flush();

            TextureEntry entry = register(normalized, "thumbnail", image);
            THUMBNAILS.put(normalized, entry);
            trim(THUMBNAILS, MAX_THUMBNAILS);
            return entry;
        } catch (IOException | RuntimeException exception) {
            CraftShop.LOGGER.error("Could not load gallery thumbnail {}", normalized, exception);
            return null;
        }
    }

    private static TextureEntry register(Path path, String type, NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        String uniqueId = UUID.nameUUIDFromBytes(path.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString().replace("-", "");
        Identifier id = Identifier.fromNamespaceAndPath("craftshop", "gallery/" + type + "_" + uniqueId);
        DynamicTexture texture = new DynamicTexture(() -> "CraftShop " + type + " " + path.getFileName(), image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return new TextureEntry(id, width, height);
    }

    private static void trim(Map<Path, TextureEntry> cache, int maximumSize) {
        while (cache.size() > maximumSize) {
            Path eldestPath = cache.keySet().iterator().next();
            TextureEntry eldest = cache.remove(eldestPath);
            release(eldest);
        }
    }

    public static void invalidate(Path imagePath) {
        Path normalized = imagePath.toAbsolutePath().normalize();
        release(PREVIEWS.remove(normalized));
        release(THUMBNAILS.remove(normalized));
    }

    public static void clear() {
        PREVIEWS.values().forEach(GalleryTextureCache::release);
        THUMBNAILS.values().forEach(GalleryTextureCache::release);
        PREVIEWS.clear();
        THUMBNAILS.clear();
    }

    private static void release(TextureEntry entry) {
        if (entry != null) Minecraft.getInstance().getTextureManager().release(entry.id());
    }

    public record TextureEntry(Identifier id, int width, int height) {}
}
