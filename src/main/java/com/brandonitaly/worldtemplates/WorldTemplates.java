package com.brandonitaly.worldtemplates;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? if fabric {
import net.fabricmc.api.ModInitializer;
//?}

//? if neoforge {
/*import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
*///?}

//? if neoforge {
/*@Mod("worldtemplates")
*///?}
public class WorldTemplates /*? if fabric {*/ implements ModInitializer /*?}*/ {
    private static final Logger logger = LoggerFactory.getLogger("worldtemplates");

    //? if fabric {
    @Override
    public void onInitialize() {
        logger.info("Initializing World Templates Mod");
    }
    //?}

    //? if neoforge {
    /*public WorldTemplates(IEventBus modEventBus) {
        logger.info("Initializing World Templates Mod");
    }
    *///?}
}