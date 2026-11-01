package dev.modsfinder.craftshop;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class GalleryTextureCache {
    private static final Map<Path, ResourceLocation> TEXTURES = new HashMap<>();

    private GalleryTextureCache() {}

    public static ResourceLocation get(Path imagePath) {
        Path normalized = imagePath.toAbsolutePath().normalize();
        ResourceLocation existing = TEXTURES.get(normalized);
        if (existing != null) return existing;

        Minecraft client = Minecraft.getInstance();
        try {
            NativeImage image = NativeImage.read(normalized.toFile());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    "craftshop", "gallery/" + Integer.toUnsignedString(normalized.toString().hashCode(), 36)
            );
            DynamicTexture texture = new DynamicTexture(() -> "CraftShop gallery " + normalized.getFileName(), image);
            client.getTextureManager().register(id, texture);
            TEXTURES.put(normalized, id);
            return id;
        } catch (IOException exception) {
            CraftShop.LOGGER.error("Could not load gallery image {}", normalized, exception);
            return null;
        }
    }

    public static void invalidate(Path imagePath) {
        Path normalized = imagePath.toAbsolutePath().normalize();
        ResourceLocation id = TEXTURES.remove(normalized);
        if (id != null) {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }

    public static void clear() {
        for (ResourceLocation id : TEXTURES.values()) {
            Minecraft.getInstance().getTextureManager().release(id);
        }
        TEXTURES.clear();
    }
}
