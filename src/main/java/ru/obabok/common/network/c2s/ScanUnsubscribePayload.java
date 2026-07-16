package ru.obabok.common.network.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import ru.obabok.common.References;

public record ScanUnsubscribePayload(long jobId) implements CustomPacketPayload {
    public static final Identifier scan_unsubscribe = Identifier.fromNamespaceAndPath(References.MOD_ID, "scan_unsubscribe");
    public static final CustomPacketPayload.Type<ScanUnsubscribePayload> ID = new CustomPacketPayload.Type<>(scan_unsubscribe);

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanUnsubscribePayload> CODEC =
            StreamCodec.ofMember(ScanUnsubscribePayload::write, ScanUnsubscribePayload::new);

    public ScanUnsubscribePayload(RegistryFriendlyByteBuf buf) {
        this(buf.readLong());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeLong(jobId);

    }
    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
