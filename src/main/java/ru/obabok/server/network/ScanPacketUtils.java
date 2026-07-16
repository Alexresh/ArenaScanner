package ru.obabok.server.network;

import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public class ScanPacketUtils {

    public static void writeBlockBox(RegistryFriendlyByteBuf buf, BlockBox box) {
        buf.writeInt(box.min().getX());
        buf.writeInt(box.min().getY());
        buf.writeInt(box.min().getZ());
        buf.writeInt(box.max().getX());
        buf.writeInt(box.max().getY());
        buf.writeInt(box.max().getZ());
    }

    public static BlockBox readBlockBox(RegistryFriendlyByteBuf buf) {
        int minX = buf.readInt();
        int minY = buf.readInt();
        int minZ = buf.readInt();
        int maxX = buf.readInt();
        int maxY = buf.readInt();
        int maxZ = buf.readInt();
        return new BlockBox(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    public static void writeBlockPosList(RegistryFriendlyByteBuf buf, List<BlockPos> list) {
        buf.writeVarInt(list.size());
        for (BlockPos pos : list) {
            buf.writeBlockPos(pos);
        }
    }

    public static List<BlockPos> readBlockPosList(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<BlockPos> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buf.readBlockPos());
        }
        return list;
    }
}
