package ru.obabok.common.network.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.obabok.common.References;

public record ScanDeltaPayload(long jobId, boolean add, long[] positions) implements CustomPacketPayload {
    public static final ResourceLocation scan_delta = ResourceLocation.fromNamespaceAndPath(References.MOD_ID, "scan_delta");
    public static final CustomPacketPayload.Type<ScanDeltaPayload> ID = new CustomPacketPayload.Type<>(scan_delta);

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanDeltaPayload> CODEC =
            StreamCodec.ofMember(ScanDeltaPayload::write, ScanDeltaPayload::new);


    public ScanDeltaPayload(RegistryFriendlyByteBuf buf) {
        this(
            buf.readLong(),
            buf.readBoolean(),
            buf.readLongArray()
        );
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeLong(jobId);
        buf.writeBoolean(add);
        buf.writeLongArray(positions);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
