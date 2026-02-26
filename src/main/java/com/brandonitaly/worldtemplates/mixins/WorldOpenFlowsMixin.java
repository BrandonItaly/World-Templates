package com.brandonitaly.worldtemplates.mixins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Mixin(WorldOpenFlows.class)
public class WorldOpenFlowsMixin {

    @Unique
    private static final UUID VANILLA_WORLD_PACK_ID = UUID.fromString("640a6a92-b6cb-48a0-b391-831586500359");

    @Inject(method = "loadBundledResourcePack", at = @At("HEAD"), cancellable = true)
    private void injectCustomWorldPacks(DownloadedPackSource packSource, LevelStorageSource.LevelStorageAccess levelSourceAccess, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        boolean hasPacks = false;
        UUID lastPackIdToWaitFor = null;

        // 1. Check for Vanilla resources.zip
        Path mapResourceFile = levelSourceAccess.getLevelPath(LevelResource.MAP_RESOURCE_FILE);
        if (Files.exists(mapResourceFile) && !Files.isDirectory(mapResourceFile)) {
            hasPacks = true;
            lastPackIdToWaitFor = VANILLA_WORLD_PACK_ID;
        }

        // 2. Read Custom World Templates JSON
        List<Path> customPacks = new ArrayList<>();
        List<UUID> customPackIds = new ArrayList<>();
        Path jsonPath = levelSourceAccess.getLevelPath(LevelResource.ROOT).resolve("world_resource_packs.json");
        
        if (Files.exists(jsonPath)) {
            try {
                String jsonContent = Files.readString(jsonPath);
                JsonArray jsonArray = JsonParser.parseString(jsonContent).getAsJsonArray();
                Path globalPacksDir = Minecraft.getInstance().gameDirectory.toPath().resolve("resourcepacks");

                for (JsonElement element : jsonArray) {
                    String packName = element.getAsString();
                    Path packPath = globalPacksDir.resolve(packName);

                    if (Files.exists(packPath) && !Files.isDirectory(packPath)) {
                        customPacks.add(packPath);
                        UUID packId = UUID.nameUUIDFromBytes(packName.getBytes());
                        customPackIds.add(packId);
                        
                        // Keep updating this so we only wait for the absolute last pack in the list
                        lastPackIdToWaitFor = packId;
                        hasPacks = true;
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to load custom world resource packs from JSON: " + e.getMessage());
            }
        }

        // 3. Push all packs and wait for the last one to load
        if (hasPacks) {
            packSource.configureForLocalWorld();
            
            // Only create one listener future
            CompletableFuture<Void> future = packSource.waitForPackFeedback(lastPackIdToWaitFor);
            
            // Push vanilla map resources first (if they exist)
            if (Files.exists(mapResourceFile) && !Files.isDirectory(mapResourceFile)) {
                packSource.pushLocalPack(VANILLA_WORLD_PACK_ID, mapResourceFile);
            }
            
            // Push custom world template resources next
            for (int i = 0; i < customPacks.size(); i++) {
                packSource.pushLocalPack(customPackIds.get(i), customPacks.get(i));
            }
            
            cir.setReturnValue(future);
        } else {
            cir.setReturnValue(CompletableFuture.completedFuture(null));
        }
    }
}