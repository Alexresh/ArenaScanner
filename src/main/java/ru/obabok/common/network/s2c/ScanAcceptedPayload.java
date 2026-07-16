package ru.obabok.common.network.s2c;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.obabok.common.References;

public record ScanAcceptedPayload(long jobId, long totalChunks) implements CustomPacketPayload {
    public static final ResourceLocation scan_accepted = ResourceLocation.fromNamespaceAndPath(References.MOD_ID, "scan_accepted");
    public static final CustomPacketPayload.Type<ScanAcceptedPayload> ID = new CustomPacketPayload.Type<>(scan_accepted);

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanAcceptedPayload> CODEC =
            StreamCodec.ofMember(ScanAcceptedPayload::write, ScanAcceptedPayload::new);

    public ScanAcceptedPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readLong(), buf.readLong());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeLong(jobId);
        buf.writeLong(totalChunks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
