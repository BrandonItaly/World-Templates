package com.brandonitaly.worldtemplates.client;

import com.brandonitaly.worldtemplates.client.WorldTemplateManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;

public class WorldTemplatesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register manager to reload whenever client resources reload
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new WorldTemplateManager());
        WorldResourcePackManager.registerEvents();
    }
}