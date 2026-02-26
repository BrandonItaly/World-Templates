package com.brandonitaly.worldtemplates.mixins;

import com.brandonitaly.worldtemplates.client.gui.AdvancedEditWorldScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class WorldListEntryMixin {

    @Shadow @Final Minecraft minecraft;
    @Shadow @Final LevelSummary summary;
    
    @Inject(method = "editWorld", at = @At("HEAD"), cancellable = true)
    private void openAdvancedEditScreen(CallbackInfo ci) {
        ci.cancel();
        String levelId = this.summary.getLevelId();
        
        SelectWorldScreen currentScreen = (this.minecraft.screen instanceof SelectWorldScreen s) ? s : null;

        try {
            LevelStorageSource.LevelStorageAccess access = this.minecraft.getLevelSource().createAccess(levelId);
            
            this.minecraft.setScreen(new AdvancedEditWorldScreen(this.minecraft, access, (saved) -> {
                access.safeClose();
                if (saved) {
                    // Reload the world list so name changes show up immediately
                    this.minecraft.setScreen(new SelectWorldScreen(new TitleScreen()));
                } else {
                    this.minecraft.setScreen(currentScreen);
                }
            }));
            
        } catch (Exception e) {
            SystemToast.onWorldAccessFailure(this.minecraft, levelId);
        }
    }
}