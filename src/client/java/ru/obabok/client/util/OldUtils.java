package ru.obabok.client.util;

import net.minecraft.core.BlockBox;
import net.minecraft.world.level.ChunkPos;

public class OldUtils {
    public static boolean intersectsChunk(BlockBox box, ChunkPos chunkPos) {
        return box.max().getX() >= chunkPos.getMinBlockX()
                && box.min().getX() <= chunkPos.getMaxBlockX()
                && box.max().getZ() >= chunkPos.getMinBlockZ()
                && box.min().getZ() <= chunkPos.getMaxBlockZ();
    }
}
