package ru.obabok.common.network.s2c;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.obabok.common.References;
import ru.obabok.common.model.JobInfo;

import java.util.ArrayList;
import java.util.List;

public record ScanListResponsePayload(List<JobInfo> scans) implements CustomPacketPayload {
    public static final ResourceLocation shared_scan_list = ResourceLocation.fromNamespaceAndPath(References.MOD_ID, "shared_scan_list");
    public static final CustomPacketPayload.Type<ScanListResponsePayload> ID = new CustomPacketPayload.Type<>(shared_scan_list);

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanListResponsePayload> CODEC =
            StreamCodec.ofMember(ScanListResponsePayload::write, ScanListResponsePayload::new);

    public ScanListResponsePayload(RegistryFriendlyByteBuf buf) {
        this(readList(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(scans.size());
        for (JobInfo info : scans) {
            info.write(buf);
        }
    }

    private static List<JobInfo> readList(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<JobInfo> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(JobInfo.read(buf));
        }
        return list;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
