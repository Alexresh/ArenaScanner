package ru.obabok.common.network.s2c;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.obabok.common.References;

public record ScanCompletePayload(long jobId) implements CustomPacketPayload {
    public static final ResourceLocation scan_complete = ResourceLocation.fromNamespaceAndPath(References.MOD_ID, "scan_complete");
    public static final CustomPacketPayload.Type<ScanCompletePayload> ID = new CustomPacketPayload.Type<>(scan_complete);

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanCompletePayload> CODEC =
            StreamCodec.ofMember(ScanCompletePayload::write, ScanCompletePayload::new);

    public ScanCompletePayload(RegistryFriendlyByteBuf buf) {
        this(buf.readLong());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeLong(jobId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
