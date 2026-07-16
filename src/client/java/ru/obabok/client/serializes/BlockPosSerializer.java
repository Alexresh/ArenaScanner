package ru.obabok.client.serializes;

import com.google.gson.*;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Type;

public class BlockPosSerializer implements JsonSerializer<BlockPos>, JsonDeserializer<BlockPos> {
    @Override
    public JsonElement serialize(BlockPos src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", src.getX());
        obj.addProperty("y", src.getY());
        obj.addProperty("z", src.getZ());
        return obj;
    }

    @Override
    public BlockPos deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        int x = obj.get("x").getAsInt();
        int y = obj.get("y").getAsInt();
        int z = obj.get("z").getAsInt();
        return new BlockPos(x, y, z);
    }
}
