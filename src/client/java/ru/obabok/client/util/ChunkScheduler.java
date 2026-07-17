package ru.obabok.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import ru.obabok.client.Scan;
import ru.obabok.common.References;

import java.util.Queue;
import java.util.concurrent.*;



public class ChunkScheduler {

    private static final Queue<ChunkPos> chunkQueue = new ConcurrentLinkedQueue<>();
    private static ScheduledExecutorService schedulerChunk = Executors.newScheduledThreadPool(0);
    private static ScheduledExecutorService schedulerRender = Executors.newScheduledThreadPool(0);
    private static ScheduledFuture<?> chunkScheduledFuture;
    private static ScheduledFuture<?> renderScheduledFuture;
    private static long period = 30;
    private static volatile boolean isRunning = false;
    public static void startProcessing() {

        isRunning = true;

        if (schedulerChunk == null || schedulerChunk.isShutdown()) {
            schedulerChunk = Executors.newScheduledThreadPool(1);
        }
        if (schedulerRender == null || schedulerRender.isShutdown()) {
            schedulerRender = Executors.newScheduledThreadPool(1);
        }


        chunkScheduledFuture = schedulerChunk.scheduleAtFixedRate(() -> {
            if (!isRunning) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) {
                    return;
                }
                ChunkPos chunkPos = chunkQueue.poll();
                if (chunkPos != null && Minecraft.getInstance().level != null && Minecraft.getInstance().level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false) != null) {
                    Scan.processChunk(Minecraft.getInstance().level, chunkPos);
                }
            }catch (Exception e){
                References.LOGGER.error(e.getMessage());
            }

        }, 0, period, TimeUnit.MILLISECONDS);


        renderScheduledFuture = schedulerRender.scheduleAtFixedRate(() -> {
            if (!isRunning) return;
            try {
                RenderUtil.clearRender();
                RenderUtil.addAllRenderBlocks(Scan.selectedBlocks);
                RenderUtil.addAllRenderChunks(Scan.unloadedChunks);
            }catch (Exception ignored){}

        }, 0, 1000, TimeUnit.MILLISECONDS);


    }

    public static void updatePeriod(long newPeriod) {
        stopProcessing(Minecraft.getInstance());
        period = newPeriod;
        startProcessing();
    }

    public static Queue<ChunkPos> getChunkQueue(){
        return chunkQueue;
    }
    public static void clearQueue(){
        chunkQueue.clear();
    }

    public static void addChunkToProcess(ChunkPos pos){
        if (!chunkQueue.contains(pos)) {
            chunkQueue.add(pos);
        }

    }

    public static void stopProcessing(Minecraft minecraft) {
        isRunning = false;

        if (chunkScheduledFuture != null) {
            chunkScheduledFuture.cancel(false);
        }
        if (renderScheduledFuture != null) {
            renderScheduledFuture.cancel(false);
        }

        if (schedulerChunk != null && !schedulerChunk.isShutdown()) {
            schedulerChunk.shutdownNow();
        }
        if (schedulerRender != null && !schedulerRender.isShutdown()) {
            schedulerRender.shutdownNow();
        }
    }
}
