package ru.obabok.server.network;

import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import ru.obabok.common.model.BlockArea;

import java.util.ArrayList;
import java.util.List;

public class ScanPacketUtils {

    public static void writeBlockArea(RegistryFriendlyByteBuf buf, BlockArea area) {
        if (area == null || area.getBoxes().isEmpty()) {
            buf.writeInt(0);
            return;
        }

        List<BlockBox> boxes = area.getBoxes();
        buf.writeInt(boxes.size());
        for (BlockBox box : boxes) {
            buf.writeInt(box.min().getX());
            buf.writeInt(box.min().getY());
            buf.writeInt(box.min().getZ());
            buf.writeInt(box.max().getX());
            buf.writeInt(box.max().getY());
            buf.writeInt(box.max().getZ());
        }
    }

    public static BlockArea readBlockArea(RegistryFriendlyByteBuf buf) {
        int count = buf.readInt();
        if (count <= 0) {
            return new BlockArea(); // Пустой конструктор!
        }

        List<BlockBox> boxes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int minX = buf.readInt();
            int minY = buf.readInt();
            int minZ = buf.readInt();
            int maxX = buf.readInt();
            int maxY = buf.readInt();
            int maxZ = buf.readInt();
            boxes.add(new BlockBox(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ)));
        }
        return new BlockArea(boxes);
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
