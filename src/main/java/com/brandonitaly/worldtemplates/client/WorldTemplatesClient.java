package com.brandonitaly.worldtemplates.client;

//? if fabric {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;
//?}

//? if neoforge {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.resources.Identifier;
*///?}

//? if neoforge {
/*@EventBusSubscriber(modid = "worldtemplates", value = Dist.CLIENT)
*///?}
public class WorldTemplatesClient /*? if fabric {*/ implements ClientModInitializer /*?}*/ {
    
    //? if fabric {
    @Override
    public void onInitializeClient() {
        // Register manager to reload whenever client resources reload
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new WorldTemplateManager());
        WorldResourcePackManager.registerEvents();
    }
    //?}

    //? if neoforge {
    /*@SubscribeEvent
    public static void onClientReloadListeners(AddClientReloadListenersEvent event) {
        // NeoForge now requires all resource listeners to be explicitly named during registration!
        Identifier listenerId = Identifier.fromNamespaceAndPath("worldtemplates", "world_template_listener");
        event.addListener(listenerId, new WorldTemplateManager());
        
        WorldResourcePackManager.registerEvents();
    }
    *///?}
}