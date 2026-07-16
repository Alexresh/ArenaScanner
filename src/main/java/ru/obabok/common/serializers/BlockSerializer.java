package ru.obabok.common.serializers;


import com.google.gson.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Type;

public class BlockSerializer implements JsonSerializer<Block>, JsonDeserializer<Block> {
    @Override
    public JsonElement serialize(Block src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(BuiltInRegistries.BLOCK.getKey(src).toString());
    }

    @Override
    public Block deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        Identifier id = Identifier.parse(json.getAsString());
        var block = BuiltInRegistries.BLOCK.get(id);
        return block.map(Holder.Reference::value).orElse(Blocks.AIR);
    }
}
