package com.brandonitaly.worldtemplates.client.gui;

import com.brandonitaly.worldtemplates.client.TemplateDownloader;
import com.brandonitaly.worldtemplates.client.WorldTemplate;
import com.brandonitaly.worldtemplates.client.WorldTemplateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

public class WorldTemplateScreen extends Screen {
    private final Screen parent;
    private final Runnable onCloseAction;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 33, 60);
    private TemplateList list;
    private Button selectButton;
    private boolean isDownloading = false;

    public WorldTemplateScreen(Screen parent, Runnable onCloseAction) {
        super(Component.translatable("worldtemplates.screen.select_template"));
        this.parent = parent;
        this.onCloseAction = onCloseAction;
    }

    @Override
    protected void init() {
        LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(this.title, this.font));

        this.list = this.layout.addToContents(new TemplateList(this.minecraft, this.width, this.height, 33, 36));
        
        for (WorldTemplate template : WorldTemplateManager.TEMPLATES) {
            this.list.addTemplate(new TemplateEntry(template));
        }

        GridLayout footer = this.layout.addToFooter(new GridLayout().columnSpacing(8).rowSpacing(4));
        footer.defaultCellSetting().alignHorizontallyCenter();
        GridLayout.RowHelper rowHelper = footer.createRowHelper(2);

        this.selectButton = rowHelper.addChild(Button.builder(Component.translatable("selectWorld.select"), btn -> {
            TemplateEntry selected = this.list.getSelected();
            if (selected != null) {
                downloadTemplate(selected.template);
            }
        }).width(150).build());
        this.selectButton.active = false; 

        rowHelper.addChild(Button.builder(CommonComponents.GUI_BACK, btn -> this.popScreen()).width(150).build());

        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        if (this.list != null) {
            this.list.updateSize(this.width, this.layout);
        }
        this.layout.arrangeElements();
    }

    private void downloadTemplate(WorldTemplate template) {
        if (isDownloading) return;
        isDownloading = true;
        this.selectButton.setMessage(Component.translatable("worldtemplates.status.downloading"));
        this.selectButton.active = false;

        CompletableFuture.runAsync(() -> {
            TemplateDownloader.loadAndExtractTemplate(
                template,
                (safeFolderName) -> this.minecraft.execute(() -> this.minecraft.setScreen(new TemplateSettingsScreen(this.parent, template, safeFolderName))),
                () -> this.minecraft.execute(() -> {
                    this.selectButton.setMessage(Component.translatable("worldtemplates.status.failed"));
                    isDownloading = false;
                })
            );
        });
    }

    public void updateSelectButton(boolean active) {
        if (!isDownloading && this.selectButton != null) {
            this.selectButton.active = active;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (isDownloading) {
            // guiGraphics.drawCenteredString(this.font, Component.translatable("worldtemplates.status.extracting"), this.width / 2, 20, 0xFFFFFF55);
        }
    }

    public void popScreen() {
        this.onCloseAction.run();
    }

    @Override
    public void onClose() {
        this.popScreen();
    }

    class TemplateList extends ObjectSelectionList<TemplateEntry> {
        public TemplateList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return 250; 
        }

        protected int getScrollbarPosition() {
            return this.width / 2 + 170 + 8;
        }

        public void addTemplate(TemplateEntry entry) {
            super.addEntry(entry);
        }

        @Override
        public void setSelected(TemplateEntry entry) {
            super.setSelected(entry);
            WorldTemplateScreen.this.updateSelectButton(entry != null);
        }
    }

    class TemplateEntry extends ObjectSelectionList.Entry<TemplateEntry> {
        final WorldTemplate template;

        public TemplateEntry(WorldTemplate template) {
            this.template = template;
        }

        @Override
        public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            int index = WorldTemplateScreen.this.list.children().indexOf(this);
            if (index == -1) return;

            int x = WorldTemplateScreen.this.list.getRowLeft();
            int y = WorldTemplateScreen.this.list.getRowTop(index);

            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.template.icon(), x + 2, y + 2, 32, 32);
            guiGraphics.drawString(minecraft.font, this.template.buttonMessage(), x + 37, y + 3, 0xFFFFFFFF, false);
            guiGraphics.drawString(minecraft.font, this.template.folderName(), x + 37, y + 14, 0xFF808080, false);
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select", this.template.buttonMessage());
        }
    }
}