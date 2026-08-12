package ru.obabok.common.model;

import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import ru.obabok.server.network.ScanPacketUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record JobInfo(
        long id,
        String name,
        String owner,
        UUID ownerUUID,
        String dimension,
        BlockArea area,
        String whitelistName,
        long totalChunks,
        long processedChunks,
        int selectedBlocks,
        boolean completedScan
) {
    public static JobInfo read(RegistryFriendlyByteBuf buf) {
        long id = buf.readLong();
        String name = buf.readUtf();
        String owner = buf.readUtf();
        UUID ownerUUID = buf.readUUID();
        String dimension = buf.readUtf();
        BlockArea range = ScanPacketUtils.readBlockArea(buf);
        String whitelistName = buf.readUtf();
        long totalChunks = buf.readLong();
        long processedChunks = buf.readLong();
        int selectedBlocks = buf.readInt();
        boolean completeScan = buf.readBoolean();
        return new JobInfo(id, name, owner, ownerUUID, dimension, range, whitelistName, totalChunks, processedChunks, selectedBlocks, completeScan);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeLong(id);
        buf.writeUtf(name == null ? "" : name);
        buf.writeUtf(owner == null ? "" : owner);
        buf.writeUUID(ownerUUID);
        buf.writeUtf(dimension == null ? "" : dimension);
        ScanPacketUtils.writeBlockArea(buf, area);
        buf.writeUtf(whitelistName == null ? "" : whitelistName);
        buf.writeLong(totalChunks);
        buf.writeLong(processedChunks);
        buf.writeInt(selectedBlocks);
        buf.writeBoolean(completedScan);
    }

//    private static void writeBlockBox(RegistryFriendlyByteBuf buf, BlockArea box) {
//        buf.writeInt(box.min().getX());
//        buf.writeInt(box.min().getY());
//        buf.writeInt(box.min().getZ());
//        buf.writeInt(box.max().getX());
//        buf.writeInt(box.max().getY());
//        buf.writeInt(box.max().getZ());
//    }


//    private static BlockArea readBlockBox(RegistryFriendlyByteBuf buf) {
//        int minX = buf.readInt();
//        int minY = buf.readInt();
//        int minZ = buf.readInt();
//        int maxX = buf.readInt();
//        int maxY = buf.readInt();
//        int maxZ = buf.readInt();
//        return new BlockArea(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
//    }
}
