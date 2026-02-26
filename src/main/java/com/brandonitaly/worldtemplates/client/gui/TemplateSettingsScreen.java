package com.brandonitaly.worldtemplates.client.gui;

import com.brandonitaly.worldtemplates.client.WorldTemplate;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class TemplateSettingsScreen extends BaseEditScreen {
    private final Screen parent;
    private final WorldTemplate template;
    private final String safeFolderName;

    public TemplateSettingsScreen(Screen parent, WorldTemplate template, String safeFolderName) {
        super(Component.translatable("selectWorld.create"));
        this.parent = parent;
        this.template = template;
        this.safeFolderName = safeFolderName;
        this.worldName = template.folderName();
        this.folderNameForTooltip = safeFolderName;
    }

    @Override
    protected void init() {
        super.init();
        
        // Disable the World Tab (index 1)
        if (this.tabNavigationBar != null) {
            this.tabNavigationBar.setTabActiveState(1, false);
        }
    }

    @Override
    protected void addTabs(TabNavigationBar.Builder builder) {
        builder.addTabs(new SharedGameTab(), new WorldTab(), new SharedMoreTab());
    }

    @Override
    protected void addFooterButtons(LinearLayout footer) {
        footer.addChild(Button.builder(Component.translatable("selectWorld.create"), btn -> this.onSave()).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, btn -> this.onCancel()).build());
    }

    @Override
    protected Path getLevelDatPath() {
        return this.minecraft.gameDirectory.toPath().resolve("saves").resolve(this.safeFolderName).resolve("level.dat");
    }

    @Override
    protected Path getDataPacksDir() {
        return this.minecraft.gameDirectory.toPath().resolve("saves").resolve(this.safeFolderName).resolve("datapacks");
    }

    @Override
    protected Path getResourcePacksJsonPath() {
        return this.minecraft.gameDirectory.toPath().resolve("saves").resolve(this.safeFolderName).resolve("world_resource_packs.json");
    }

    @Override
    protected void onSave() {
        try (LevelStorageSource.LevelStorageAccess access = this.minecraft.getLevelSource().createAccess(this.safeFolderName)) {
            Path levelDatPath = access.getLevelPath(LevelResource.LEVEL_DATA_FILE);
            
            if (Files.exists(levelDatPath)) {
                CompoundTag rootTag = NbtIo.readCompressed(levelDatPath, NbtAccounter.unlimitedHeap());
                CompoundTag dataTag = rootTag.getCompound("Data").orElse(null);
                
                if (dataTag != null) {
                    dataTag.putString("LevelName", this.worldName);
                    dataTag.putInt("GameType", this.gameMode.getId());
                    dataTag.putByte("Difficulty", (byte) this.difficulty.getId());
                    dataTag.putBoolean("allowCommands", this.allowCommands);

                    GameRules.codec(FeatureFlags.DEFAULT_FLAGS).encodeStart(NbtOps.INSTANCE, this.gameRules)
                        .resultOrPartial(error -> System.err.println("Failed to encode GameRules: " + error))
                        .ifPresent(tag -> dataTag.put("GameRules", tag));
                    
                    if (this.dataPackRepository != null) {
                        List<String> enabledPacks = new ArrayList<>(this.dataPackRepository.getSelectedIds());
                        List<String> disabledPacks = this.dataPackRepository.getAvailableIds().stream()
                                .filter(id -> !enabledPacks.contains(id))
                                .toList();

                        DataPackConfig packConfig = new DataPackConfig(enabledPacks, disabledPacks);
                        DataPackConfig.CODEC.encodeStart(NbtOps.INSTANCE, packConfig)
                                .resultOrPartial(error -> System.err.println("Failed to encode DataPacks: " + error))
                                .ifPresent(tag -> dataTag.put("DataPacks", tag));
                    }
                    
                    NbtIo.writeCompressed(rootTag, levelDatPath);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to modify template level.dat: " + e.getMessage());
        }

        if (this.resourcePackRepository != null) {
            Path jsonPath = getResourcePacksJsonPath();
            try {
                JsonArray jsonArray = new JsonArray();
                for (String packId : this.resourcePackRepository.getSelectedIds()) {
                    String fileName = packId.startsWith("file/") ? packId.substring(5) : packId;
                    jsonArray.add(fileName);
                }
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Files.writeString(jsonPath, gson.toJson(jsonArray));
            } catch (Exception e) {}
        }

        this.minecraft.createWorldOpenFlows().openWorld(
            this.safeFolderName, 
            () -> this.minecraft.setScreen(new SelectWorldScreen(new TitleScreen()))
        );
    }

    @Override
    protected void onCancel() {
        Path savesDir = this.minecraft.gameDirectory.toPath().resolve("saves").resolve(this.safeFolderName);
        if (Files.exists(savesDir)) {
            try (Stream<Path> walk = Files.walk(savesDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.delete(path); } catch (IOException e) {}
                });
            } catch (Exception e) {}
        }
        this.minecraft.setScreen(new SelectWorldScreen(new TitleScreen()));
    }

    // A simple empty tab for the disabled "World" layout
    private class WorldTab extends GridLayoutTab {
        private WorldTab() {
            super(Component.translatable("createWorld.tab.world.title"));
        }
    }
}