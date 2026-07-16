package ru.obabok.common.network.s2c;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.obabok.common.References;

public record ScanRejectedPayload(long jobId, String reason) implements CustomPacketPayload {
    public static final ResourceLocation scan_rejected = ResourceLocation.fromNamespaceAndPath(References.MOD_ID, "scan_rejected");
    public static final CustomPacketPayload.Type<ScanRejectedPayload> ID = new CustomPacketPayload.Type<>(scan_rejected);

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanRejectedPayload> CODEC =
            StreamCodec.ofMember(ScanRejectedPayload::write, ScanRejectedPayload::new);

    public ScanRejectedPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readLong(), buf.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeLong(jobId);
        buf.writeUtf(reason);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
