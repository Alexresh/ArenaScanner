package ru.obabok.common.network.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.obabok.common.References;


public record ScanSubscribePayload(long jobId) implements CustomPacketPayload {
    public static final ResourceLocation scan_subscribe = ResourceLocation.fromNamespaceAndPath(References.MOD_ID, "scan_subscribe");
    public static final CustomPacketPayload.Type<ScanSubscribePayload> ID = new CustomPacketPayload.Type<>(scan_subscribe);

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanSubscribePayload> CODEC =
            StreamCodec.ofMember(ScanSubscribePayload::write, ScanSubscribePayload::new);

    public ScanSubscribePayload(RegistryFriendlyByteBuf buf) {
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
