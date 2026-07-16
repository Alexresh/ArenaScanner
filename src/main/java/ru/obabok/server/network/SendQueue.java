package ru.obabok.server.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import ru.obabok.server.ServerScanConfig;

import java.util.ArrayDeque;

public class SendQueue {
    private static final ArrayDeque<Packet> queue = new ArrayDeque<>();
    private static int sentCount = 0;

    public static void addPacket(ServerPlayer player, CustomPacketPayload payload){
        queue.addLast(new Packet(player, payload));
    }

    public static void tick(){
        while (!queue.isEmpty() && sentCount < ServerScanConfig.getMaxPacketsPerTick() && queue.peekFirst() != null) {
            ServerPlayNetworking.send(queue.peekFirst().player, queue.peekFirst().payload);
            queue.pollFirst();
            sentCount++;
        }
        sentCount = 0;
    }

    public static int getQueueSize(){
        return queue.size();
    }

    private record Packet(ServerPlayer player, CustomPacketPayload payload){}
}
