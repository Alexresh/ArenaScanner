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
    private static final ScheduledExecutorService schedulerChunk = Executors.newScheduledThreadPool(0);
    private static final ScheduledExecutorService schedulerRender = Executors.newScheduledThreadPool(0);
    private static ScheduledFuture<?> chunkScheduledFuture;
    private static ScheduledFuture<?> renderScheduledFuture;
    private static long period = 30;

    public static void startProcessing() {
        chunkScheduledFuture = schedulerChunk.scheduleAtFixedRate(() -> {
            try {
                boolean processing = true;
                while (processing){
                    ChunkPos chunkPos = chunkQueue.poll();
                    if (chunkPos != null && Minecraft.getInstance().level != null && Minecraft.getInstance().level.getChunkSource().getChunk(chunkPos.x(), chunkPos.z(), ChunkStatus.FULL, false) != null) {
                        processing = false;
                        Scan.processChunk(Minecraft.getInstance().level, chunkPos);
                    }
                }

            }catch (Exception e){
                References.LOGGER.error(e.getMessage());
            }

        }, 0, period, TimeUnit.MILLISECONDS);


        renderScheduledFuture = schedulerRender.scheduleAtFixedRate(() -> {
            try {
                RenderUtil.clearRender();
                RenderUtil.addAllRenderBlocks(Scan.selectedBlocks);
                RenderUtil.addAllRenderChunks(Scan.unloadedChunks);
            }catch (Exception ignored){}

        }, 0, 1000, TimeUnit.MILLISECONDS);


    }

    public static void updatePeriod(long newPeriod) {
        if (chunkScheduledFuture != null) {
            chunkScheduledFuture.cancel(false);
        }
        if(renderScheduledFuture != null){
            renderScheduledFuture.cancel(false);
        }
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

}
