package com.brandonitaly.worldtemplates.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

public record WorldTemplate(Component buttonMessage, Identifier icon, String templateLocation, String folderName, Optional<String> downloadURI) {
    
    public static final Codec<WorldTemplate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("buttonMessage").forGetter(WorldTemplate::buttonMessage),
            Identifier.CODEC.optionalFieldOf("icon", Identifier.parse("minecraft:textures/misc/unknown_server.png")).forGetter(WorldTemplate::icon),
            Codec.STRING.fieldOf("templateLocation").forGetter(WorldTemplate::templateLocation),
            Codec.STRING.fieldOf("folderName").forGetter(WorldTemplate::folderName),
            Codec.STRING.optionalFieldOf("downloadURI").forGetter(WorldTemplate::downloadURI)
    ).apply(instance, WorldTemplate::new));

    public static final Codec<List<WorldTemplate>> LIST_CODEC = CODEC.listOf();
}