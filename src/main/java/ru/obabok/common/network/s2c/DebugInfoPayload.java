package ru.obabok.common.network.s2c;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import ru.obabok.common.References;

public record DebugInfoPayload(int networkQueueSize) implements CustomPacketPayload {
    public static final Identifier debug_info = Identifier.fromNamespaceAndPath(References.MOD_ID, "debug_info");
    public static final CustomPacketPayload.Type<DebugInfoPayload> ID = new CustomPacketPayload.Type<>(debug_info);

    public static final StreamCodec<RegistryFriendlyByteBuf, DebugInfoPayload> CODEC =
            StreamCodec.ofMember(DebugInfoPayload::write, DebugInfoPayload::new);

    public DebugInfoPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(networkQueueSize);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}