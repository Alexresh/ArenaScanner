package ru.obabok.common.network.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.obabok.common.References;

public record ScanStopPayload(long jobId, String cause) implements CustomPacketPayload {
    public static final ResourceLocation scanStop = ResourceLocation.fromNamespaceAndPath(References.MOD_ID, "scan_stop");
    public static final CustomPacketPayload.Type<ScanStopPayload> ID = new CustomPacketPayload.Type<>(scanStop);

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanStopPayload> CODEC =
            StreamCodec.ofMember(ScanStopPayload::write, ScanStopPayload::new);

    public ScanStopPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readLong(), buf.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeLong(jobId);
        buf.writeUtf(cause);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
