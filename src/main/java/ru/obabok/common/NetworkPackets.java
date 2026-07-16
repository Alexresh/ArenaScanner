package ru.obabok.common;


import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import ru.obabok.common.network.c2s.*;
import ru.obabok.common.network.s2c.*;

public class NetworkPackets {
    private static boolean registered = false;
    public static void registerPayloads() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.clientboundPlay().register(ServerVersionPayload.ID, ServerVersionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ScanChunkSummaryPayload.ID, ScanChunkSummaryPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ScanAcceptedPayload.ID, ScanAcceptedPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ScanRejectedPayload.ID, ScanRejectedPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ScanCompletePayload.ID, ScanCompletePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ScanDeltaPayload.ID, ScanDeltaPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ScanListResponsePayload.ID, ScanListResponsePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ScanFullCompletedPayload.ID, ScanFullCompletedPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DebugInfoPayload.ID, DebugInfoPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MaterialListResponsePayload.ID, MaterialListResponsePayload.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(ScanStartPayload.ID, ScanStartPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ScanStopPayload.ID, ScanStopPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ScanUnsubscribePayload.ID, ScanUnsubscribePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ScanSubscribePayload.ID, ScanSubscribePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ScanListRequestPayload.ID, ScanListRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MaterialListRequestPayload.ID, MaterialListRequestPayload.CODEC);

    }

}
