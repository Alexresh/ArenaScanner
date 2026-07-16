package ru.obabok.common.network.s2c;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import ru.obabok.common.References;

public record ScanChunkSummaryPayload(long jobId, long chunkCount) implements CustomPacketPayload {
    public static final Identifier scan_chunk_summary = Identifier.fromNamespaceAndPath(References.MOD_ID, "scan_chunk_summary");
    public static final CustomPacketPayload.Type<ScanChunkSummaryPayload> ID = new CustomPacketPayload.Type<>(scan_chunk_summary);

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanChunkSummaryPayload> CODEC =
            StreamCodec.ofMember(ScanChunkSummaryPayload::write, ScanChunkSummaryPayload::new);

    public ScanChunkSummaryPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readLong(), buf.readLong());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeLong(jobId);
        buf.writeLong(chunkCount);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
