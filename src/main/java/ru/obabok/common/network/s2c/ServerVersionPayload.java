package ru.obabok.common.network.s2c;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import ru.obabok.common.References;

public record ServerVersionPayload(String version) implements CustomPacketPayload {
    public static final Identifier server_hello = Identifier.fromNamespaceAndPath(References.MOD_ID, "server_hello");
    public static final CustomPacketPayload.Type<ServerVersionPayload> ID = new CustomPacketPayload.Type<>(server_hello);

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerVersionPayload> CODEC =
            StreamCodec.ofMember(ServerVersionPayload::write, ServerVersionPayload::new);

    public ServerVersionPayload(RegistryFriendlyByteBuf buf){
        this(buf.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(version);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
