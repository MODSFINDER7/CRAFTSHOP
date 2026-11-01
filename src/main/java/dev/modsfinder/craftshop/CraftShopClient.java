package dev.modsfinder.craftshop;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class CraftShopClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("craftshop", "controls")
    );

    private static final KeyMapping CAPTURE_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.craftshop.capture", InputConstants.Type.KEYSYM, InputConstants.KEY_Z, CATEGORY)
    );

    private static final KeyMapping GALLERY_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.craftshop.gallery", InputConstants.Type.KEYSYM, InputConstants.KEY_O, CATEGORY)
    );

    @Override
    public void onInitializeClient() {
        ScreenshotManager.initialize();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (CAPTURE_KEY.consumeClick()) {
                if (client.player != null && client.level != null) {
                    ScreenshotManager.capture(client);
                }
            }

            while (GALLERY_KEY.consumeClick()) {
                if (client.player != null) {
                    client.gui.setScreen(new ScreenshotGalleryScreen());
                }
            }
        });
    }
}
