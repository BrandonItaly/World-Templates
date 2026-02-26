package com.brandonitaly.worldtemplates.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
import net.minecraft.client.gui.screens.worldselection.ExperimentsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseEditScreen extends Screen {
    protected final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    protected final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    protected TabNavigationBar tabNavigationBar;

    protected String worldName = "";
    protected GameType gameMode = GameType.SURVIVAL;
    protected Difficulty difficulty = Difficulty.NORMAL;
    protected boolean allowCommands = false;
    protected GameRules gameRules = new GameRules(FeatureFlags.DEFAULT_FLAGS);
    protected String folderNameForTooltip = "";

    protected PackRepository dataPackRepository;
    protected PackRepository resourcePackRepository;

    protected BaseEditScreen(Component title) {
        super(title);
    }

    // --- Abstract Hooks for Child Classes ---
    protected abstract void addTabs(TabNavigationBar.Builder builder);
    protected abstract void addFooterButtons(LinearLayout footer);
    protected abstract Path getLevelDatPath();
    protected abstract Path getDataPacksDir();
    protected abstract Path getResourcePacksJsonPath();
    protected abstract void onSave();
    protected abstract void onCancel();

    @Override
    protected void init() {
        TabNavigationBar.Builder builder = TabNavigationBar.builder(this.tabManager, this.width);
        this.addTabs(builder);
        this.tabNavigationBar = builder.build();
        this.addRenderableWidget(this.tabNavigationBar);

        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        this.addFooterButtons(footer);

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

    protected void openGameRulesScreen() {
        this.minecraft.setScreen(new EditGameRulesScreen(
            this.gameRules.copy(FeatureFlags.DEFAULT_FLAGS),
            (optionalRules) -> {
                this.minecraft.setScreen(this);
                optionalRules.ifPresent(rules -> this.gameRules = rules);
            }
        ));
    }

    protected PackRepository getOrInitDataPackRepository() {
        if (this.dataPackRepository == null) {
            Path datapacksDir = this.getDataPacksDir();
            try { Files.createDirectories(datapacksDir); } catch (IOException ignored) {}

            this.dataPackRepository = ServerPacksSource.createPackRepository(datapacksDir, this.minecraft.directoryValidator());
            this.dataPackRepository.reload();

            List<String> enabledPacks = new ArrayList<>(WorldDataConfiguration.DEFAULT.dataPacks().getEnabled());

            try {
                Path levelDatPath = this.getLevelDatPath();
                if (Files.exists(levelDatPath)) {
                    CompoundTag rootTag = NbtIo.readCompressed(levelDatPath, NbtAccounter.unlimitedHeap());
                    CompoundTag dataTag = rootTag.getCompound("Data").orElse(null);
                    if (dataTag != null) {
                        dataTag.getCompound("DataPacks").ifPresent(dataPacksTag -> {
                            DataPackConfig.CODEC.parse(NbtOps.INSTANCE, dataPacksTag)
                                .resultOrPartial(System.err::println)
                                .ifPresent(packConfig -> {
                                    enabledPacks.clear();
                                    enabledPacks.addAll(packConfig.getEnabled());
                                });
                        });
                    }
                }
            } catch (Exception e) {}

            enabledPacks.retainAll(this.dataPackRepository.getAvailableIds());
            this.dataPackRepository.setSelected(enabledPacks);
        } else {
            this.dataPackRepository.reload();
        }
        return this.dataPackRepository;
    }

    protected void openExperimentsScreen() {
        this.minecraft.setScreen(new ExperimentsScreen(this, getOrInitDataPackRepository(), repository -> this.minecraft.setScreen(this)));
    }

    protected void openDataPackScreen() {
        this.minecraft.setScreen(new PackSelectionScreen(
            getOrInitDataPackRepository(),
            repository -> this.minecraft.setScreen(this),
            this.getDataPacksDir(),
            Component.translatable("selectWorld.dataPacks")
        ));
    }

    protected void openResourcePackScreen() {
        Path resourcePacksDir = this.minecraft.gameDirectory.toPath().resolve("resourcepacks");
        try { Files.createDirectories(resourcePacksDir); } catch (IOException ignored) {}

        if (this.resourcePackRepository == null) {
            this.resourcePackRepository = new PackRepository(
                new FolderRepositorySource(resourcePacksDir, PackType.CLIENT_RESOURCES, PackSource.DEFAULT, this.minecraft.directoryValidator())
            );
            this.resourcePackRepository.reload();

            Path jsonPath = this.getResourcePacksJsonPath();
            List<String> selectedIds = new ArrayList<>();

            if (Files.exists(jsonPath)) {
                try {
                    String jsonContent = Files.readString(jsonPath);
                    JsonArray jsonArray = JsonParser.parseString(jsonContent).getAsJsonArray();
                    for (JsonElement element : jsonArray) {
                        String packId = "file/" + element.getAsString();
                        if (this.resourcePackRepository.getAvailableIds().contains(packId)) {
                            selectedIds.add(packId);
                        }
                    }
                } catch (Exception e) {}
            }
            this.resourcePackRepository.setSelected(selectedIds);
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

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.tabNavigationBar != null && this.tabNavigationBar.keyPressed(event)) return true;
        if (super.keyPressed(event)) return true;
        if (event.isConfirmation()) {
            this.onSave();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        this.onCancel();
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

    // --- Shared UI Tabs ---

    protected class SharedGameTab extends GridLayoutTab {
        public SharedGameTab() {
            super(Component.translatable("createWorld.tab.game.title"));
            GridLayout.RowHelper helper = this.layout.rowSpacing(8).createRowHelper(1);
            LayoutSettings buttonSettings = helper.newCellSettings();

            EditBox nameEdit = new EditBox(BaseEditScreen.this.font, 208, 20, Component.translatable("selectWorld.enterName"));
            nameEdit.setValue(BaseEditScreen.this.worldName);
            nameEdit.setResponder(val -> BaseEditScreen.this.worldName = val);
            if (BaseEditScreen.this.folderNameForTooltip != null && !BaseEditScreen.this.folderNameForTooltip.isEmpty()) {
                nameEdit.setTooltip(Tooltip.create(Component.translatable("selectWorld.targetFolder", Component.literal(BaseEditScreen.this.folderNameForTooltip).withStyle(ChatFormatting.ITALIC))));
            }
            BaseEditScreen.this.setInitialFocus(nameEdit);
            helper.addChild(CommonLayouts.labeledElement(BaseEditScreen.this.font, nameEdit, Component.translatable("selectWorld.enterName")), helper.newCellSettings().alignHorizontallyCenter());

            helper.addChild(CycleButton.<GameType>builder(GameType::getShortDisplayName, BaseEditScreen.this.gameMode)
                .withValues(GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE)
                .withTooltip(value -> Tooltip.create(Component.translatable("selectWorld.gameMode." + value.getName() + ".info")))
                .create(0, 0, 210, 20, Component.translatable("selectWorld.gameMode"), (btn, val) -> BaseEditScreen.this.gameMode = val), buttonSettings);

            helper.addChild(CycleButton.<Difficulty>builder(Difficulty::getDisplayName, BaseEditScreen.this.difficulty)
                .withValues(Difficulty.values())
                .withTooltip(value -> Tooltip.create(value.getInfo()))
                .create(0, 0, 210, 20, Component.translatable("options.difficulty"), (btn, val) -> BaseEditScreen.this.difficulty = val), buttonSettings);

            helper.addChild(CycleButton.onOffBuilder(BaseEditScreen.this.allowCommands)
                .withTooltip(state -> Tooltip.create(Component.translatable("selectWorld.allowCommands.info")))
                .create(0, 0, 210, 20, Component.translatable("selectWorld.allowCommands"), (btn, val) -> BaseEditScreen.this.allowCommands = val), buttonSettings);
        }
    }

    protected class SharedMoreTab extends GridLayoutTab {
        public SharedMoreTab() {
            super(Component.translatable("createWorld.tab.more.title"));
            GridLayout.RowHelper helper = this.layout.rowSpacing(8).createRowHelper(1);

            helper.addChild(Button.builder(Component.translatable("selectWorld.gameRules"), b -> BaseEditScreen.this.openGameRulesScreen()).width(210).build());
            helper.addChild(Button.builder(Component.translatable("selectWorld.experiments"), b -> BaseEditScreen.this.openExperimentsScreen()).width(210).build());
            helper.addChild(Button.builder(Component.translatable("worldtemplates.screen.resourcePacks"), b -> BaseEditScreen.this.openResourcePackScreen()).width(210).build());
            helper.addChild(Button.builder(Component.translatable("selectWorld.dataPacks"), b -> BaseEditScreen.this.openDataPackScreen()).width(210).build());
        }
    }
}