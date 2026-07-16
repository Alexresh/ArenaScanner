package ru.obabok.common.network.s2c;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.obabok.common.References;

public record ScanFullCompletedPayload(long jobId, String cause, boolean restart) implements CustomPacketPayload {
    public static final ResourceLocation scan_full_complete = ResourceLocation.fromNamespaceAndPath(References.MOD_ID, "scan_full_complete");
    public static final CustomPacketPayload.Type<ScanFullCompletedPayload> ID = new CustomPacketPayload.Type<>(scan_full_complete);

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanFullCompletedPayload> CODEC =
            StreamCodec.ofMember(ScanFullCompletedPayload::write, ScanFullCompletedPayload::new);

    public ScanFullCompletedPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readLong(), buf.readUtf(), buf.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeLong(jobId);
        buf.writeUtf(cause);
        buf.writeBoolean(restart);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
