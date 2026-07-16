package ru.obabok.common.network.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import ru.obabok.common.References;

public record ScanListRequestPayload() implements CustomPacketPayload {
    public static final Identifier shared_scan_list_request = Identifier.fromNamespaceAndPath(References.MOD_ID, "shared_scan_list_request");
    public static final CustomPacketPayload.Type<ScanListRequestPayload> ID = new CustomPacketPayload.Type<>(shared_scan_list_request);

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanListRequestPayload> CODEC =
            StreamCodec.ofMember(ScanListRequestPayload::write, ScanListRequestPayload::new);

    public ScanListRequestPayload(RegistryFriendlyByteBuf buf) {
        this();
    }

    private void write(RegistryFriendlyByteBuf buf) {

    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
