package ru.obabok.client.serializes;

import com.google.gson.*;
import net.minecraft.world.level.ChunkPos;

import java.lang.reflect.Type;

public class ChunkPosSerializer implements JsonSerializer<ChunkPos>, JsonDeserializer<ChunkPos> {
    @Override
    public JsonElement serialize(ChunkPos src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", src.x());
        obj.addProperty("z", src.z());
        return obj;
    }

    @Override
    public ChunkPos deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException{
        JsonObject obj = json.getAsJsonObject();
        int x = obj.get("x").getAsInt();
        int z = obj.get("z").getAsInt();
        return new ChunkPos(x, z);
    }
}
