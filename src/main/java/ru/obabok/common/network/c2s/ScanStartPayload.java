package ru.obabok.common.network.c2s;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import ru.obabok.common.References;
import ru.obabok.common.model.BlockArea;
import ru.obabok.server.network.ScanPacketUtils;

public record ScanStartPayload(
        long jobId,
        BlockArea range,
        String whitelistName,
        String whitelistJson,
        String shareName
) implements CustomPacketPayload {
    public static final Identifier scan_start = Identifier.fromNamespaceAndPath(References.MOD_ID, "scan_start");
    public static final CustomPacketPayload.Type<ScanStartPayload> ID = new CustomPacketPayload.Type<>(scan_start);

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanStartPayload> CODEC =
            StreamCodec.ofMember(ScanStartPayload::write, ScanStartPayload::new);


    public ScanStartPayload(RegistryFriendlyByteBuf buf) {
        this(
            buf.readLong(),
            ScanPacketUtils.readBlockArea(buf),
            buf.readUtf(),
            buf.readUtf(),
            buf.readUtf()
        );
    }


    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeLong(jobId);
        ScanPacketUtils.writeBlockArea(buf, range);
        buf.writeUtf(whitelistName);
        buf.writeUtf(whitelistJson);
        buf.writeUtf(shareName == null ? "" : shareName);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
