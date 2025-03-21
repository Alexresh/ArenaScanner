package ru.obabok.arenascanner.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;
import ru.obabok.arenascanner.client.ScanCommand;

import java.util.Queue;
import java.util.concurrent.*;


public class ChunkScheduler {

    private static final Queue<ChunkPos> chunkQueue = new ConcurrentLinkedQueue<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(0);
    private static ScheduledFuture<?> scheduledFuture;
    private static long period = 30;

    public static void startProcessing() {
        scheduledFuture = scheduler.scheduleAtFixedRate(() -> {
            ChunkPos chunkPos = chunkQueue.poll();
            if (chunkPos != null) {
                ScanCommand.processChunk(MinecraftClient.getInstance().world, chunkPos);
                RenderUtil.clearRender();
                RenderUtil.addAllRenderBlocks(ScanCommand.selectedBlocks);
                RenderUtil.addAllRenderChunks(ScanCommand.unloadedChunks);
            }
        }, 0, period, TimeUnit.MILLISECONDS);
    }

    public static void updatePeriod(long newPeriod) {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        period = newPeriod;
        startProcessing();
    }
    public static Queue<ChunkPos> getChunkQueue(){
        return chunkQueue;
    }

    public static void addChunkToProcess(ChunkPos pos){
        chunkQueue.add(pos);
    }

}
