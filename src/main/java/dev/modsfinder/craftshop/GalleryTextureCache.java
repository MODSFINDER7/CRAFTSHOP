package dev.modsfinder.craftshop;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class GalleryTextureCache {
    private static final Map<Path, TextureEntry> TEXTURES = new HashMap<>();

    private GalleryTextureCache() {}

    public static TextureEntry get(Path imagePath) {
        Path normalized = imagePath.toAbsolutePath().normalize();
        TextureEntry existing = TEXTURES.get(normalized);
        if (existing != null) return existing;

        try {
            NativeImage image = NativeImage.read(normalized.toFile());
            int width = image.getWidth();
            int height = image.getHeight();
            Identifier id = Identifier.fromNamespaceAndPath(
                    "craftshop", "gallery/" + Integer.toUnsignedString(normalized.toString().hashCode(), 36)
            );
            DynamicTexture texture = new DynamicTexture(() -> "CraftShop gallery " + normalized.getFileName(), image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            TextureEntry entry = new TextureEntry(id, width, height);
            TEXTURES.put(normalized, entry);
            return entry;
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not load gallery image {}", normalized, exception);
            return null;
        }
    }

    public static void invalidate(Path imagePath) {
        Path normalized = imagePath.toAbsolutePath().normalize();
        TextureEntry entry = TEXTURES.remove(normalized);
        if (entry != null) Minecraft.getInstance().getTextureManager().release(entry.id());
    }

    public static void clear() {
        for (TextureEntry entry : TEXTURES.values()) {
            Minecraft.getInstance().getTextureManager().release(entry.id());
        }
        TEXTURES.clear();
    }

    public record TextureEntry(Identifier id, int width, int height) {}
}
