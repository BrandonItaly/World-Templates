package com.brandonitaly.worldtemplates;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldTemplates implements ModInitializer {
    private static final Logger logger = LoggerFactory.getLogger("worldtemplates");

    @Override
    public void onInitialize() {
        logger.info("Initializing World Templates Mod");
    }
}