package com.brandonitaly.worldtemplates.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

//? if fabric {
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
//?}

public class WorldTemplateManager implements ResourceManagerReloadListener /*? if fabric {*/ , IdentifiableResourceReloadListener /*?}*/ {
    private static final Logger LOGGER = LoggerFactory.getLogger("worldtemplates");
    
    public static final List<WorldTemplate> TEMPLATES = new ArrayList<>();
    
    //? if fabric {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("worldtemplates", "world_template_listener");

    @Override
    public Identifier getFabricId() {
        return ID;
    }
    //?}

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        TEMPLATES.clear();
        
        // Scan every loaded namespace
        resourceManager.getNamespaces().forEach(namespace -> {
            Identifier location = Identifier.fromNamespaceAndPath(namespace, "world_templates.json");
            
            // If the file exists in this namespace, parse it
            resourceManager.getResource(location).ifPresent(resource -> {
                try (BufferedReader reader = resource.openAsReader()) {
                    WorldTemplate.LIST_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                            .resultOrPartial(LOGGER::warn)
                            .ifPresent(TEMPLATES::addAll);
                } catch (Exception e) {
                    LOGGER.error("Failed to load world templates from {}", location, e);
                }
            });
        });
        
        LOGGER.info("Loaded {} world templates.", TEMPLATES.size());
    }
}