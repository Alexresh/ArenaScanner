package ru.obabok.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import ru.obabok.client.Config;
import ru.obabok.client.Scan;
import ru.obabok.client.gui.screens.MaterialListScreen;
import ru.obabok.client.gui.screens.SharedScansScreen;
import ru.obabok.common.model.BlockArea;
import ru.obabok.client.util.WhitelistManager;
import ru.obabok.common.NetworkPackets;
import ru.obabok.common.References;
import ru.obabok.common.model.JobInfo;
import ru.obabok.common.model.Whitelist;
import ru.obabok.common.network.c2s.*;
import ru.obabok.common.network.s2c.*;
import ru.obabok.common.serializers.WhitelistCodec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static fi.dy.masa.malilib.gui.GuiBase.openGui;

public class ClientNetwork {
    private static long activeJobId = 0;
    private static List<JobInfo> jobList = List.of();
    private static final Map<Long, PendingStart> pendingStarts = new HashMap<>();
    private static boolean requested;
    private static SharedScansScreen sharedScansScreen;
    private static int debugServerPacketsQueue;

    public static void register(){

        if (!requested) {
            ClientNetwork.requestSharedList();
            requested = true;
        }
        NetworkPackets.registerPayloads();

        ClientPlayNetworking.registerGlobalReceiver(ServerVersionPayload.ID, (payload, context) -> {
            if(Config.Generic.JOIN_NOTIFICATION.getBooleanValue() && Minecraft.getInstance().player != null){
                if(FabricLoader.getInstance().getModContainer(References.MOD_ID).isPresent()){
                    if(!FabricLoader.getInstance().getModContainer(References.MOD_ID).get().getMetadata().getVersion().getFriendlyString().equals(payload.version())){
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("[" + References.MOD_ID + "] server version is: " + payload.version() + " but you in " + FabricLoader.getInstance().getModContainer(References.MOD_ID).get().getMetadata().getVersion().getFriendlyString() + ". Operation is not guaranteed"));
                    }else {
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("[" + References.MOD_ID + "] Server-side scanning is supported"));
                    }
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(ScanListResponsePayload.ID, (payload, context) -> {
            jobList = payload.scans();
            if(sharedScansScreen != null){
                sharedScansScreen.drawJobs();
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(ScanAcceptedPayload.ID, (payload, context) -> {
            PendingStart pending = pendingStarts.remove(payload.jobId());
            if (pending == null) {
                return;
            }
            activeJobId = payload.jobId();
            BlockArea area = new BlockArea(pending.range);
            Scan.startRemoteScan(area, pending.whitelistName, payload.totalChunks());
        });

        ClientPlayNetworking.registerGlobalReceiver(ScanRejectedPayload.ID, (payload, context) -> {
            pendingStarts.remove(payload.jobId());
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("Scan rejected: " + payload.reason()));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(ScanChunkSummaryPayload.ID, (payload, context) -> {
            if (payload.jobId() != activeJobId) return;
            Scan.markRemoteChunkProcessed(payload.chunkCount());
        });

        ClientPlayNetworking.registerGlobalReceiver(ScanFullCompletedPayload.ID, (payload, context) -> {
            if (payload.jobId() != activeJobId) return;
            activeJobId = 0;
            if(payload.restart()){
                BlockArea range = Scan.getArea();
                Scan.stopScan();
                Scan.setArea(range);
            }else {
                Scan.stopScan();
            }
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("Scan finished, cause: " + payload.cause()));
                Minecraft.getInstance().player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1, 1);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(ScanDeltaPayload.ID, (payload, context) -> {
            if (payload.jobId() != activeJobId) return;
            Scan.applyRemoteDelta(payload.positions(), payload.add());
        });

        ClientPlayNetworking.registerGlobalReceiver(DebugInfoPayload.ID, (payload, context) -> {
            debugServerPacketsQueue = payload.networkQueueSize();
        });

        ClientPlayNetworking.registerGlobalReceiver(MaterialListResponsePayload.ID, (payload, context) -> {
            if(payload.jobId() != MaterialListScreen.jobId) return;
            MaterialListScreen.clear();
            MaterialListScreen.updateList(payload.materials());
        });

    }

    public static void requestMaterialList(long jobId){
        ClientPlayNetworking.send(new MaterialListRequestPayload(jobId));
    }

    public static void openSharedScansScreen(Screen parent, int page){
        sharedScansScreen = new SharedScansScreen(page, parent);
        openGui(sharedScansScreen);
    }

    public static int getDebugServerPacketsQueue(){
        return debugServerPacketsQueue;
    }

    public static boolean requestScan(BlockArea range, String whitelistName, String shareName) {
        if (!canUseServerScan()) {
            return false;
        }
        long jobId = ThreadLocalRandom.current().nextLong();
        Whitelist whitelist = WhitelistManager.loadData(whitelistName);
        String json = WhitelistCodec.toJson(whitelist);
        pendingStarts.put(jobId, new PendingStart(range, whitelistName));
        ClientPlayNetworking.send(new ScanStartPayload(jobId, range, whitelistName, json, shareName));
        return true;
    }


    public static void stopScan() {
        if (activeJobId == 0) return;
        ClientPlayNetworking.send(new ScanStopPayload(activeJobId, "stopped"));
        activeJobId = 0;
    }

    public static long getActiveJobId(){
        return activeJobId;
    }

    public static void requestSharedList() {
        if (!ClientPlayNetworking.canSend(ScanListRequestPayload.ID)) {
            clearList();
            return;
        }
        ClientPlayNetworking.send(new ScanListRequestPayload());
    }

    public static void subscribeToScan(JobInfo info) {
        if (!ClientPlayNetworking.canSend(ScanSubscribePayload.ID)) {
            return;
        }
        BlockArea area = info.area();
        String whitelistName = info.whitelistName();
        pendingStarts.put(info.id(), new PendingStart(area, whitelistName));
        ClientPlayNetworking.send(new ScanSubscribePayload(info.id()));
    }

    public static void deleteScan(long jobId) {
        ClientPlayNetworking.send(new ScanStopPayload(jobId, "delete"));
    }

    public static void clearList() {
        jobList = List.of();
    }
    public static List<JobInfo> getJobList() {
        return new ArrayList<>(jobList);
    }

    public static boolean canUseServerScan() {
        return ClientPlayNetworking.canSend(ScanStartPayload.ID);
    }

    public static void unsubscribeFromScan(long jobId) {
        if(ClientPlayNetworking.canSend(ScanUnsubscribePayload.ID)){
            ClientPlayNetworking.send(new ScanUnsubscribePayload(jobId));
        }
    }

    private record PendingStart(BlockArea range, String whitelistName) {
    }
}
