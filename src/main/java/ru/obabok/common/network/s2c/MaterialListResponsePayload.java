package ru.obabok.common.network.s2c;


import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import ru.obabok.common.References;

import java.util.Map;

public record MaterialListResponsePayload(long jobId, Map<Block, Integer> materials) implements CustomPacketPayload {
    public static final ResourceLocation material_list_response = ResourceLocation.fromNamespaceAndPath(References.MOD_ID, "material_list_response");
    public static final CustomPacketPayload.Type<MaterialListResponsePayload> ID = new CustomPacketPayload.Type<>(material_list_response);

    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialListResponsePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, MaterialListResponsePayload::jobId,
            ByteBufCodecs.map(
                    Object2IntOpenHashMap::new,
                    ByteBufCodecs.registry(Registries.BLOCK),
                    ByteBufCodecs.VAR_INT
            ),
            MaterialListResponsePayload::materials,
            MaterialListResponsePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}