package com.brandonitaly.worldtemplates.mixins;

import com.brandonitaly.worldtemplates.client.gui.WorldTemplateScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.GridLayout.RowHelper;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList.WorldListEntry;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen {

    @Shadow private HeaderAndFooterLayout layout;
    @Shadow private Button selectButton;
    @Shadow private Button renameButton;
    @Shadow private Button deleteButton;
    @Shadow private Button copyButton;
    @Shadow protected Screen lastScreen;

    protected SelectWorldScreenMixin(Component title) { 
        super(title); 
    }

    @Inject(method = "createFooterButtons", at = @At("HEAD"), cancellable = true)
    private void injectWorldTemplateButton(Consumer<WorldListEntry> joinWorld, WorldSelectionList list, CallbackInfo ci) {
        // Cancel the vanilla footer generation
        ci.cancel();

        // Create replacement GridLayout
        GridLayout footer = this.layout.addToFooter(new GridLayout().columnSpacing(8).rowSpacing(4));
        footer.defaultCellSetting().alignHorizontallyCenter();
        
        // Use 12 columns to cleanly fit a row of 3 and a row of 4
        RowHelper rowHelper = footer.createRowHelper(12);

        // ROW 1 (3 Buttons, Span 4 columns each)
        this.selectButton = rowHelper.addChild(
            Button.builder(LevelSummary.PLAY_WORLD, button -> list.getSelectedOpt().ifPresent(joinWorld)).width(120).build(), 4
        );
        rowHelper.addChild(
            Button.builder(Component.translatable("selectWorld.create"), button -> CreateWorldScreen.openFresh(this.minecraft, list::returnToScreen)).width(120).build(), 4
        );
        
        rowHelper.addChild(
            Button.builder(Component.translatable("worldtemplates.selectWorldTemplate"), btn -> this.minecraft.setScreen(new WorldTemplateScreen(this, list::returnToScreen))).width(120).build(), 4
        );

        // ROW 2 (4 Buttons, Span 3 columns each)
        this.renameButton = rowHelper.addChild(
            Button.builder(Component.translatable("selectWorld.edit"), button -> list.getSelectedOpt().ifPresent(WorldListEntry::editWorld)).width(88).build(), 3
        );
        this.deleteButton = rowHelper.addChild(
            Button.builder(Component.translatable("selectWorld.delete"), button -> list.getSelectedOpt().ifPresent(WorldListEntry::deleteWorld)).width(88).build(), 3
        );
        this.copyButton = rowHelper.addChild(
            Button.builder(Component.translatable("selectWorld.recreate"), button -> list.getSelectedOpt().ifPresent(WorldListEntry::recreateWorld)).width(88).build(), 3
        );
        rowHelper.addChild(
            Button.builder(CommonComponents.GUI_BACK, button -> this.minecraft.setScreen(this.lastScreen)).width(88).build(), 3
        );
    }
}