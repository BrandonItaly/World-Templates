package com.brandonitaly.worldtemplates.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

//? if fabric {
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
//?}

//? if neoforge {
/*import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
*///?}

public class WorldResourcePackManager {
    public static List<String> pendingVanillaResourcePacks = null;

    public static void registerEvents() {
        //? if fabric {
        ServerLifecycleEvents.SERVER_STARTING.register(WorldResourcePackManager::onServerStarting);
        //?}
        
        //? if neoforge {
        /*NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> onServerStarting(event.getServer()));
        *///?}
    }

    private static void onServerStarting(MinecraftServer server) {
        if (pendingVanillaResourcePacks != null && !pendingVanillaResourcePacks.isEmpty()) {
            Path saveDir = server.getWorldPath(LevelResource.ROOT);
            if (saveDir != null) {
                Path jsonPath = saveDir.resolve("world_resource_packs.json");
                try {
                    JsonArray jsonArray = new JsonArray();
                    for (String packId : pendingVanillaResourcePacks) {
                        String fileName = packId.startsWith("file/") ? packId.substring(5) : packId;
                        jsonArray.add(fileName);
                    }
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    Files.writeString(jsonPath, gson.toJson(jsonArray));
                } catch (Exception e) {
                    System.err.println("Failed to write world_resource_packs.json: " + e.getMessage());
                }
            }
            pendingVanillaResourcePacks = null;
        }
    }
}