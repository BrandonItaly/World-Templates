package com.brandonitaly.worldtemplates.client.gui;

import com.brandonitaly.worldtemplates.client.WorldTemplate;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.CommonLayouts;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.ExperimentsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class TemplateSettingsScreen extends Screen {
    private final Screen parent;
    private final WorldTemplate template;
    private final String safeFolderName;
    
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private TabNavigationBar tabNavigationBar;

    private String worldName;
    private GameType gameMode = GameType.SURVIVAL;
    private Difficulty difficulty = Difficulty.NORMAL;
    private boolean allowCommands = false;

    private PackRepository dataPackRepository;
    private PackRepository resourcePackRepository;

    public TemplateSettingsScreen(Screen parent, WorldTemplate template, String safeFolderName) {
        super(Component.translatable("selectWorld.create"));
        this.parent = parent;
        this.template = template;
        this.safeFolderName = safeFolderName;
        this.worldName = template.folderName();
    }

    @Override
    protected void init() {
        this.tabNavigationBar = TabNavigationBar.builder(this.tabManager, this.width)
            .addTabs(new GameTab(), new WorldTab(), new MoreTab())
            .build();
        this.addRenderableWidget(this.tabNavigationBar);

        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        
        footer.addChild(Button.builder(Component.translatable("selectWorld.create"), btn -> this.playWorld()).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, btn -> this.popScreen()).build());

        this.layout.visitWidgets(this::addRenderableWidget);
        this.tabNavigationBar.selectTab(0, false);
        this.repositionElements();
    }

    @Override
    public void repositionElements() {
        if (this.tabNavigationBar != null) {
            this.tabNavigationBar.setWidth(this.width);
            this.tabNavigationBar.arrangeElements();
            int tabAreaTop = this.tabNavigationBar.getRectangle().bottom();
            ScreenRectangle tabArea = new ScreenRectangle(0, tabAreaTop, this.width, this.height - this.layout.getFooterHeight() - tabAreaTop);
            this.tabManager.setTabArea(tabArea);
            this.layout.setHeaderHeight(tabAreaTop);
            this.layout.arrangeElements();
        }
    }

    // =====================================
    // PACK SCREEN LOGIC
    // =====================================

    private PackRepository getOrInitDataPackRepository() {
        if (this.dataPackRepository == null) {
            Path datapacksDir = this.minecraft.gameDirectory.toPath().resolve("saves").resolve(this.safeFolderName).resolve("datapacks");
            try { Files.createDirectories(datapacksDir); } catch (IOException ignored) {}
            this.dataPackRepository = ServerPacksSource.createPackRepository(datapacksDir, this.minecraft.directoryValidator());
        }
        this.dataPackRepository.reload();
        return this.dataPackRepository;
    }

    private void openExperimentsScreen() {
        PackRepository repo = getOrInitDataPackRepository();
        this.minecraft.setScreen(new ExperimentsScreen(
            this,
            repo,
            repository -> this.minecraft.setScreen(this)
        ));
    }

    private void openDataPackScreen() {
        PackRepository repo = getOrInitDataPackRepository();
        Path datapacksDir = this.minecraft.gameDirectory.toPath().resolve("saves").resolve(this.safeFolderName).resolve("datapacks");
        
        this.minecraft.setScreen(new PackSelectionScreen(
            repo,
            repository -> this.minecraft.setScreen(this),
            datapacksDir,
            Component.translatable("selectWorld.dataPacks")
        ));
    }

    private void openResourcePackScreen() {
        Path resourcePacksDir = this.minecraft.gameDirectory.toPath().resolve("resourcepacks");
        try { Files.createDirectories(resourcePacksDir); } catch (IOException ignored) {}

        if (this.resourcePackRepository == null) {
            this.resourcePackRepository = new PackRepository(
                new FolderRepositorySource(resourcePacksDir, PackType.CLIENT_RESOURCES, PackSource.DEFAULT, this.minecraft.directoryValidator())
            );
            this.resourcePackRepository.reload();

            Path jsonPath = this.minecraft.gameDirectory.toPath().resolve("saves").resolve(this.safeFolderName).resolve("world_resource_packs.json");
            
            if (Files.exists(jsonPath)) {
                try {
                    String jsonContent = Files.readString(jsonPath);
                    JsonArray jsonArray = JsonParser.parseString(jsonContent).getAsJsonArray();
                    
                    java.util.List<String> selectedIds = new java.util.ArrayList<>();
                    
                    for (com.google.gson.JsonElement element : jsonArray) {
                        String packId = "file/" + element.getAsString();
                        if (this.resourcePackRepository.getAvailableIds().contains(packId)) {
                            selectedIds.add(packId);
                        }
                    }
                    
                    this.resourcePackRepository.setSelected(selectedIds);
                } catch (Exception e) {
                    System.err.println("Failed to read existing world_resource_packs.json: " + e.getMessage());
                }
            }
        } else {
            this.resourcePackRepository.reload();
        }

        this.minecraft.setScreen(new PackSelectionScreen(
            this.resourcePackRepository,
            repository -> this.minecraft.setScreen(this),
            resourcePacksDir,
            Component.translatable("worldtemplates.screen.resourcePacks")
        ));
    }

    // =====================================
    // WORLD LAUNCH & CLEANUP
    // =====================================

    private void playWorld() {
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
                    
                    // Inject Data Packs & Experiments into level.dat
                    if (this.dataPackRepository != null) {
                        CompoundTag dataPacksTag = new CompoundTag();
                        
                        ListTag enabledList = new ListTag();
                        for (String id : this.dataPackRepository.getSelectedIds()) {
                            enabledList.add(StringTag.valueOf(id));
                        }
                        dataPacksTag.put("Enabled", enabledList);

                        ListTag disabledList = new ListTag();
                        for (String id : this.dataPackRepository.getAvailableIds()) {
                            if (!this.dataPackRepository.getSelectedIds().contains(id)) {
                                disabledList.add(StringTag.valueOf(id));
                            }
                        }
                        dataPacksTag.put("Disabled", disabledList);

                        dataTag.put("DataPacks", dataPacksTag);
                    }
                    
                    NbtIo.writeCompressed(rootTag, levelDatPath);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to modify template level.dat: " + e.getMessage());
            e.printStackTrace();
        }

        // Generate JSON array for resource packs
        if (this.resourcePackRepository != null) {
            Path jsonPath = this.minecraft.gameDirectory.toPath().resolve("saves").resolve(this.safeFolderName).resolve("world_resource_packs.json");
            try {
                JsonArray jsonArray = new JsonArray();
                
                for (String packId : this.resourcePackRepository.getSelectedIds()) {
                    String fileName = packId.startsWith("file/") ? packId.substring(5) : packId;
                    jsonArray.add(fileName);
                }

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Files.writeString(jsonPath, gson.toJson(jsonArray));
                
            } catch (Exception e) {
                System.err.println("Failed to write world_resource_packs.json: " + e.getMessage());
                e.printStackTrace();
            }
        }

        this.minecraft.createWorldOpenFlows().openWorld(
            this.safeFolderName, 
            () -> this.minecraft.setScreen(new SelectWorldScreen(new TitleScreen()))
        );
    }

    private void deleteExtractedWorld() {
        Path savesDir = this.minecraft.gameDirectory.toPath().resolve("saves").resolve(this.safeFolderName);
        if (Files.exists(savesDir)) {
            try (Stream<Path> walk = Files.walk(savesDir)) {
                walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {}
                    });
            } catch (Exception e) {
                System.err.println("Failed to delete cancelled template world: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.tabNavigationBar.keyPressed(event)) return true;
        if (super.keyPressed(event)) return true;
        if (event.isConfirmation()) {
            this.playWorld();
            return true;
        }
        return false;
    }

    public void popScreen() {
        this.deleteExtractedWorld();
        this.minecraft.setScreen(new SelectWorldScreen(new TitleScreen()));
    }

    @Override
    public void onClose() {
        this.popScreen();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.FOOTER_SEPARATOR, 0, this.height - this.layout.getFooterHeight() - 2, 0.0F, 0.0F, this.width, 2, 32, 2);
    }

    @Override
    protected void renderMenuBackground(GuiGraphics graphics) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, CreateWorldScreen.TAB_HEADER_BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.layout.getHeaderHeight(), 16, 16);
        this.renderMenuBackground(graphics, 0, this.layout.getHeaderHeight(), this.width, this.height);
    }

    // =====================================
    // TABS
    // =====================================

    private class GameTab extends GridLayoutTab {
        private GameTab() {
            super(Component.translatable("createWorld.tab.game.title"));
            GridLayout.RowHelper helper = this.layout.rowSpacing(8).createRowHelper(1);
            LayoutSettings buttonLayoutSettings = helper.newCellSettings();

            EditBox nameEdit = new EditBox(TemplateSettingsScreen.this.font, 208, 20, Component.translatable("selectWorld.enterName"));
            nameEdit.setValue(TemplateSettingsScreen.this.worldName);
            nameEdit.setResponder(val -> TemplateSettingsScreen.this.worldName = val);
            TemplateSettingsScreen.this.setInitialFocus(nameEdit);
            helper.addChild(CommonLayouts.labeledElement(TemplateSettingsScreen.this.font, nameEdit, Component.translatable("selectWorld.enterName")), helper.newCellSettings().alignHorizontallyCenter());

            helper.addChild(CycleButton.<GameType>builder(GameType::getShortDisplayName, TemplateSettingsScreen.this.gameMode)
                .withValues(GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE)
                .create(0, 0, 210, 20, Component.translatable("selectWorld.gameMode"), (btn, val) -> TemplateSettingsScreen.this.gameMode = val), 
                buttonLayoutSettings);

            helper.addChild(CycleButton.<Difficulty>builder(Difficulty::getDisplayName, TemplateSettingsScreen.this.difficulty)
                .withValues(Difficulty.values())
                .create(0, 0, 210, 20, Component.translatable("options.difficulty"), (btn, val) -> TemplateSettingsScreen.this.difficulty = val), 
                buttonLayoutSettings);

            helper.addChild(CycleButton.onOffBuilder(TemplateSettingsScreen.this.allowCommands)
                .create(0, 0, 210, 20, Component.translatable("selectWorld.allowCommands"), (btn, val) -> TemplateSettingsScreen.this.allowCommands = val), 
                buttonLayoutSettings);
        }
    }

    private class WorldTab extends GridLayoutTab {
        private WorldTab() {
            super(Component.translatable("createWorld.tab.world.title"));
            GridLayout.RowHelper helper = this.layout.rowSpacing(8).createRowHelper(1);

            Button mapTypeBtn = helper.addChild(Button.builder(Component.translatable("selectWorld.mapType"), b -> {}).width(210).build());
            mapTypeBtn.active = false;
            
            Button customizeBtn = helper.addChild(Button.builder(Component.translatable("selectWorld.customizeType"), b -> {}).width(210).build());
            customizeBtn.active = false;

            Button mapFeaturesBtn = helper.addChild(Button.builder(Component.translatable("selectWorld.mapFeatures"), b -> {}).width(210).build());
            mapFeaturesBtn.active = false;

            Button bonusItemsBtn = helper.addChild(Button.builder(Component.translatable("selectWorld.bonusItems"), b -> {}).width(210).build());
            bonusItemsBtn.active = false;
        }
    }

    private class MoreTab extends GridLayoutTab {
        private MoreTab() {
            super(Component.translatable("createWorld.tab.more.title"));
            GridLayout.RowHelper helper = this.layout.rowSpacing(8).createRowHelper(1);

            Button gameRulesBtn = helper.addChild(Button.builder(Component.translatable("selectWorld.gameRules"), b -> {}).width(210).build());
            gameRulesBtn.active = false;

            Button experimentsBtn = helper.addChild(Button.builder(Component.translatable("selectWorld.experiments"), b -> TemplateSettingsScreen.this.openExperimentsScreen()).width(210).build());
            experimentsBtn.active = true;

            Button resourcePacksBtn = helper.addChild(Button.builder(Component.translatable("worldtemplates.screen.resourcePacks"), b -> TemplateSettingsScreen.this.openResourcePackScreen()).width(210).build());
            resourcePacksBtn.active = true;

            Button dataPacksBtn = helper.addChild(Button.builder(Component.translatable("selectWorld.dataPacks"), b -> TemplateSettingsScreen.this.openDataPackScreen()).width(210).build());
            dataPacksBtn.active = true;
        }
    }
}