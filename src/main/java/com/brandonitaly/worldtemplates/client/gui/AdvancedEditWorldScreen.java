package com.brandonitaly.worldtemplates.client.gui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.OptimizeWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.commons.io.FileUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AdvancedEditWorldScreen extends BaseEditScreen {
    private final LevelStorageSource.LevelStorageAccess levelAccess;
    private final BooleanConsumer callback;

    public AdvancedEditWorldScreen(Minecraft minecraft, LevelStorageSource.LevelStorageAccess levelAccess, BooleanConsumer callback) {
        super(Component.translatable("selectWorld.edit.title"));
        this.levelAccess = levelAccess;
        this.callback = callback;
        this.folderNameForTooltip = levelAccess.getLevelId();

        try {
            Path levelDatPath = this.getLevelDatPath();
            if (Files.exists(levelDatPath)) {
                CompoundTag rootTag = NbtIo.readCompressed(levelDatPath, NbtAccounter.unlimitedHeap());
                CompoundTag dataTag = rootTag.getCompound("Data").orElse(null);

                if (dataTag != null) {
                    this.worldName = dataTag.getString("LevelName").orElse(levelAccess.getLevelId());
                    this.gameMode = GameType.byId(dataTag.getInt("GameType").orElse(0));
                    this.difficulty = Difficulty.byId(dataTag.getByte("Difficulty").orElse((byte)2));
                    this.allowCommands = dataTag.getBoolean("allowCommands").orElse(false);

                    dataTag.getCompound("GameRules").ifPresent(rulesTag -> {
                        GameRules.codec(FeatureFlags.DEFAULT_FLAGS).parse(NbtOps.INSTANCE, rulesTag)
                            .resultOrPartial(System.err::println)
                            .ifPresent(rules -> this.gameRules = rules);
                    });
                }
            }
        } catch (Exception e) {
            this.worldName = levelAccess.getLevelId();
        }
    }

    @Override
    protected void addTabs(TabNavigationBar.Builder builder) {
        builder.addTabs(new SharedGameTab(), new WorldTab(), new SharedMoreTab());
    }

    @Override
    protected void addFooterButtons(LinearLayout footer) {
        footer.addChild(Button.builder(Component.translatable("selectWorld.edit.save"), btn -> this.onSave()).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, btn -> this.onCancel()).build());
    }

    @Override
    protected Path getLevelDatPath() {
        return this.levelAccess.getLevelPath(LevelResource.LEVEL_DATA_FILE);
    }

    @Override
    protected Path getDataPacksDir() {
        return this.levelAccess.getLevelPath(LevelResource.DATAPACK_DIR);
    }

    @Override
    protected Path getResourcePacksJsonPath() {
        return this.levelAccess.getLevelPath(LevelResource.ROOT).resolve("world_resource_packs.json");
    }

    @Override
    protected void onSave() {
        try {
            Path levelDatPath = this.getLevelDatPath();
            if (Files.exists(levelDatPath)) {
                CompoundTag rootTag = NbtIo.readCompressed(levelDatPath, NbtAccounter.unlimitedHeap());
                CompoundTag dataTag = rootTag.getCompound("Data").orElse(null);
                
                if (dataTag != null) {
                    dataTag.putString("LevelName", this.worldName);
                    dataTag.putInt("GameType", this.gameMode.getId());
                    dataTag.putByte("Difficulty", (byte) this.difficulty.getId());
                    dataTag.putBoolean("allowCommands", this.allowCommands);

                    GameRules.codec(FeatureFlags.DEFAULT_FLAGS).encodeStart(NbtOps.INSTANCE, this.gameRules)
                        .resultOrPartial(System.err::println)
                        .ifPresent(tag -> dataTag.put("GameRules", tag));
                    
                    if (this.dataPackRepository != null) {
                        List<String> enabledPacks = new ArrayList<>(this.dataPackRepository.getSelectedIds());
                        List<String> disabledPacks = this.dataPackRepository.getAvailableIds().stream()
                                .filter(id -> !enabledPacks.contains(id)).toList();

                        DataPackConfig.CODEC.encodeStart(NbtOps.INSTANCE, new DataPackConfig(enabledPacks, disabledPacks))
                                .resultOrPartial(System.err::println)
                                .ifPresent(tag -> dataTag.put("DataPacks", tag));
                    }
                    NbtIo.writeCompressed(rootTag, levelDatPath);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to save level.dat: " + e.getMessage());
        }

        if (this.resourcePackRepository != null) {
            Path jsonPath = this.getResourcePacksJsonPath();
            try {
                JsonArray jsonArray = new JsonArray();
                for (String packId : this.resourcePackRepository.getSelectedIds()) {
                    jsonArray.add(packId.startsWith("file/") ? packId.substring(5) : packId);
                }
                Files.writeString(jsonPath, new GsonBuilder().setPrettyPrinting().create().toJson(jsonArray));
            } catch (Exception e) {}
        }
        
        this.callback.accept(true);
    }

    @Override
    protected void onCancel() {
        this.callback.accept(false);
        this.minecraft.setScreen(new SelectWorldScreen(new TitleScreen()));
    }

    private class WorldTab extends GridLayoutTab {
        private WorldTab() {
            super(Component.translatable("createWorld.tab.world.title"));
            GridLayout.RowHelper helper = this.layout.rowSpacing(8).createRowHelper(1);

            helper.addChild(Button.builder(Component.translatable("selectWorld.edit.openFolder"), b -> Util.getPlatform().openPath(AdvancedEditWorldScreen.this.levelAccess.getLevelPath(LevelResource.ROOT))).width(210).build());

            helper.addChild(Button.builder(Component.translatable("selectWorld.edit.backup"), b -> EditWorldScreen.makeBackupAndShowToast(AdvancedEditWorldScreen.this.levelAccess)).width(210).build());

            helper.addChild(Button.builder(Component.translatable("selectWorld.edit.backupFolder"), b -> {
                Path path = AdvancedEditWorldScreen.this.minecraft.getLevelSource().getBackupPath();
                try { FileUtil.createDirectoriesSafe(path); } catch (Exception e) {}
                Util.getPlatform().openPath(path);
            }).width(210).build());

            helper.addChild(Button.builder(Component.translatable("selectWorld.edit.optimize"), b -> {
                AdvancedEditWorldScreen.this.minecraft.setScreen(new BackupConfirmScreen(
                    () -> AdvancedEditWorldScreen.this.minecraft.setScreen(AdvancedEditWorldScreen.this),
                    (backup, eraseCache) -> {
                        if (backup) EditWorldScreen.makeBackupAndShowToast(AdvancedEditWorldScreen.this.levelAccess);
                        AdvancedEditWorldScreen.this.minecraft.setScreen(OptimizeWorldScreen.create(AdvancedEditWorldScreen.this.minecraft, AdvancedEditWorldScreen.this.callback, AdvancedEditWorldScreen.this.minecraft.getFixerUpper(), AdvancedEditWorldScreen.this.levelAccess, eraseCache));
                    },
                    Component.translatable("optimizeWorld.confirm.title"),
                    Component.translatable("optimizeWorld.confirm.description"),
                    true
                ));
            }).width(210).build());

            Button resetIconBtn = helper.addChild(Button.builder(Component.translatable("selectWorld.edit.resetIcon"), b -> {
                AdvancedEditWorldScreen.this.levelAccess.getIconFile().ifPresent(p -> FileUtils.deleteQuietly(p.toFile()));
                b.active = false;
            }).width(210).build());
            resetIconBtn.active = AdvancedEditWorldScreen.this.levelAccess.getIconFile().filter(Files::isRegularFile).isPresent();
        }
    }
}