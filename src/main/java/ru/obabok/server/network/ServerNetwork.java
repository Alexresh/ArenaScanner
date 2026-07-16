package ru.obabok.server.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockBox;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import ru.obabok.common.NetworkPackets;
import ru.obabok.common.References;
import ru.obabok.common.model.JobInfo;
import ru.obabok.common.model.Whitelist;
import ru.obabok.common.network.c2s.*;
import ru.obabok.common.network.s2c.*;
import ru.obabok.common.serializers.WhitelistCodec;
import ru.obabok.server.ServerScanManager;


public class ServerNetwork {

    public static void register(){
        NetworkPackets.registerPayloads();

        ServerPlayConnectionEvents.JOIN.register((serverPlayNetworkHandler, packetSender, minecraftServer) -> {
            if(FabricLoader.getInstance().getModContainer(References.MOD_ID).isPresent()){
                packetSender.sendPacket(new ServerVersionPayload(FabricLoader.getInstance().getModContainer(References.MOD_ID).get().getMetadata().getVersion().getFriendlyString()));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(ScanStopPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            if(player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)){
                ServerScanManager.getInstance().stopOPJob(payload.jobId());
            }else {
                ServerScanManager.getInstance().stopJob(player, payload.jobId(), payload.cause());
            }

            //new
            ServerPlayNetworking.send(player, new ScanListResponsePayload(ServerScanManager.getInstance().getJobs()));
            //SharedScanManager.getInstance().stopSubscription(context.player(), payload.jobId());
        });

        ServerPlayNetworking.registerGlobalReceiver(ScanStartPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            handleStart(player, payload);
        });



        ServerPlayNetworking.registerGlobalReceiver(ScanListRequestPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            ServerPlayNetworking.send(player, new ScanListResponsePayload(ServerScanManager.getInstance().getJobs()));
            ServerPlayNetworking.send(player, new DebugInfoPayload(SendQueue.getQueueSize()));
        });

        ServerPlayNetworking.registerGlobalReceiver(ScanSubscribePayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            String reason = ServerScanManager.getInstance().subscribe(player, payload.jobId());
            if (reason != null) {
                ServerPlayNetworking.send(player, new ScanRejectedPayload(payload.jobId(), reason));
                return;
            }
            JobInfo info = ServerScanManager.getInstance().getJobInfo(payload.jobId());
            if (info == null) {
                ServerPlayNetworking.send(player, new ScanRejectedPayload(payload.jobId(), "Shared scan not found"));
                return;
            }
            long totalChunks = info.totalChunks();
            ServerPlayNetworking.send(player, new ScanAcceptedPayload(payload.jobId(), totalChunks));
        });

        ServerPlayNetworking.registerGlobalReceiver(ScanUnsubscribePayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            ServerScanManager.getInstance().unsubscribe(player, payload.jobId());
            ServerPlayNetworking.send(player, new ScanFullCompletedPayload(payload.jobId(), "Unsubscribed", false));
        });

        ServerPlayNetworking.registerGlobalReceiver(MaterialListRequestPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            ServerScanManager.getInstance().getMaterialList(payload.jobId());
            ServerPlayNetworking.send(player, new MaterialListResponsePayload(payload.jobId(), ServerScanManager.getInstance().getMaterialList(payload.jobId())));
        });
    }

    private static void handleStart(ServerPlayer player, ScanStartPayload payload) {
        Whitelist whitelist = WhitelistCodec.fromJson(payload.whitelistJson());
        if (whitelist == null) {
            ServerPlayNetworking.send(player, new ScanRejectedPayload(payload.jobId(), "Whitelist not found"));
            return;
        }
        BlockBox range = payload.range();
        long totalChunks = getTotalChunks(range);
        if (totalChunks <= 0) {
            ServerPlayNetworking.send(player, new ScanRejectedPayload(payload.jobId(), "Invalid range"));
            return;
        }

        if (payload.shareName() != null && !payload.shareName().isEmpty() && payload.whitelistName() != null) {
            ServerScanManager.getInstance().startJob(player, payload.jobId(), range, payload.whitelistName(), whitelist, payload.shareName());
        }else{
            ServerPlayNetworking.send(player, new ScanRejectedPayload(payload.jobId(), "Share name is empty"));
        }
        ServerPlayNetworking.send(player, new ScanAcceptedPayload(payload.jobId(), totalChunks));
    }

    private static long getTotalChunks(BlockBox range) {
        int startChunkX = range.min().getX() >> 4;
        int startChunkZ = range.min().getZ() >> 4;
        int endChunkX = range.max().getX() >> 4;
        int endChunkZ = range.max().getZ() >> 4;
        long xCount = (long) endChunkX - startChunkX + 1;
        long zCount = (long) endChunkZ - startChunkZ + 1;
        return xCount * zCount;
    }
}
