package ru.obabok.client.serializes;

import com.google.gson.*;
import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Type;

public class BlockBoxSerializer implements JsonSerializer<BlockBox>, JsonDeserializer<BlockBox> {
    @Override
    public JsonElement serialize(BlockBox src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("minX", src.min().getX());
        obj.addProperty("minY", src.min().getY());
        obj.addProperty("minZ", src.min().getZ());
        obj.addProperty("maxX", src.max().getX());
        obj.addProperty("maxY", src.max().getY());
        obj.addProperty("maxZ", src.max().getZ());
        return obj;
    }


    @Override
    public BlockBox deserialize(JsonElement json, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException{
        JsonObject obj = json.getAsJsonObject();
        return new BlockBox(
            new BlockPos(
                obj.get("minX").getAsInt(),
                obj.get("minY").getAsInt(),
                obj.get("minZ").getAsInt()),
            new BlockPos(
                obj.get("maxX").getAsInt(),
                obj.get("maxY").getAsInt(),
                obj.get("maxZ").getAsInt())
        );
    }
}
