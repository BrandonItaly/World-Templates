package com.brandonitaly.worldtemplates.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class WorldResourcePackManager {
    private static List<String> originalGlobalPacks = null;
    private static boolean packsModified = false;

    public static void registerEvents() {
        // Triggered the moment the player successfully joins a world
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // Ensure we are in a Singleplayer world
            if (client.isLocalServer() && client.getSingleplayerServer() != null) {
                
                // Get the path to the current world save directory
                Path saveDir = client.getSingleplayerServer().getWorldPath(LevelResource.LEVEL_DATA_FILE).getParent();
                if (saveDir == null) return;

                Path jsonPath = saveDir.resolve("world_resource_packs.json");

                if (Files.exists(jsonPath)) {
                    try {
                        String jsonContent = Files.readString(jsonPath);
                        JsonArray jsonArray = JsonParser.parseString(jsonContent).getAsJsonArray();

                        PackRepository repo = client.getResourcePackRepository();
                        
                        // 1. Backup the globally enabled packs
                        originalGlobalPacks = new ArrayList<>(repo.getSelectedIds());
                        List<String> newPacks = new ArrayList<>(originalGlobalPacks);

                        boolean needsReload = false;

                        // 2. Add the world-specific packs to the top of the stack
                        for (JsonElement element : jsonArray) {
                            String packId = "file/" + element.getAsString();
                            
                            // Only apply the pack if it exists in the folder AND isn't already enabled globally
                            if (repo.getAvailableIds().contains(packId) && !newPacks.contains(packId)) {
                                newPacks.add(packId);
                                needsReload = true;
                            }
                        }

                        // 3. Trigger a temporary client reload if new packs were added
                        if (needsReload) {
                            packsModified = true;
                            repo.setSelected(newPacks);
                            
                            // Note: We deliberately DO NOT call options.updateResourcePacks(repo)
                            // This ensures the packs aren't permanently saved to options.txt!
                            client.reloadResourcePacks();
                        }

                    } catch (Exception e) {
                        System.err.println("Failed to read world_resource_packs.json: " + e.getMessage());
                    }
                }
            }
        });

        // Triggered the moment the player leaves the world (Save & Quit)
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (packsModified && originalGlobalPacks != null) {
                PackRepository repo = client.getResourcePackRepository();
                
                // 4. Restore the original global pack list
                repo.setSelected(originalGlobalPacks);
                
                // Reset state
                packsModified = false;
                originalGlobalPacks = null;
                
                // Trigger the client reload to clear the world packs from memory
                client.reloadResourcePacks();
            }
        });
    }
}