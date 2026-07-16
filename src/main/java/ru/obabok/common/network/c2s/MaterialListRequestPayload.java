package ru.obabok.common.network.c2s;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import ru.obabok.common.References;

public record MaterialListRequestPayload(long jobId) implements CustomPacketPayload {
    public static final Identifier material_list_request = Identifier.fromNamespaceAndPath(References.MOD_ID, "material_list_request");
    public static final CustomPacketPayload.Type<MaterialListRequestPayload> ID = new CustomPacketPayload.Type<>(material_list_request);

    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialListRequestPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.LONG, MaterialListRequestPayload::jobId, MaterialListRequestPayload::new);


    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
