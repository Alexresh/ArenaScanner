package ru.obabok.client.util;

import fi.dy.masa.malilib.config.HudAlignment;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.CommonColors;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import ru.obabok.client.Config;
import ru.obabok.client.Scan;

import java.util.*;

import static fi.dy.masa.malilib.render.GuiContext.fromGuiGraphics;
import static ru.obabok.common.References.LOGGER;


public class HudRender {


    private static final List<String> lines = new ArrayList<>();
    private static final long ETA_UPDATE_MS = 1000;
    private static long scanStartMs = 0;
    private static long lastTotal = 0;
    private static long lastProcessed = 0;
    private static long lastEtaUpdateMs = 0;
    private static String lastEtaText = "--";
    private static final char[] SPINNER_FRAMES = new char[]{'\\', '|', '/', '-'};


    public static void render(GuiGraphicsExtractor guiGraphicsExtractor, DeltaTracker deltaTracker) {
        if (Config.Generic.MAIN_RENDER.getBooleanValue() && Config.Hud.HUD_ENABLE.getBooleanValue() && !Minecraft.getInstance().gui.hud.isHidden()) {
            lines.clear();
            if(!ChunkScheduler.getChunkQueue().isEmpty()){
                String processedChunksText = "ProcessedChunks: %d".formatted(ChunkScheduler.getChunkQueue().size());
                lines.add(processedChunksText);
            }

            addScanStatus();

            try {
                if(!Scan.selectedBlocks.isEmpty()){
                    BlockPos pos = Scan.selectedBlocks.iterator().next();
                    String selectedBlocksText = "Selected blocks: %d -> [%d, %d, %d]".formatted(Scan.selectedBlocks.size(), pos.getX(), pos.getY(), pos.getZ());
                    lines.add(selectedBlocksText);
                    if(Config.Generic.LOD2_HUD.getBooleanValue()){
                        renderClusteredDots(guiGraphicsExtractor);
                    }
                }
            }catch (Exception exception){
                LOGGER.error(exception.getMessage());
            }

            if(!Scan.unloadedChunks.isEmpty()){
                try {
                    Iterator<ChunkPos> iterator = Scan.unloadedChunks.iterator();
                    if(iterator.hasNext()){
                        String unloadedChunksText = "Unchecked chunks: %d -> %s".formatted(Scan.unloadedChunks.size(), iterator.next().toString());
                        lines.add(unloadedChunksText);
                    }
                }catch (Exception exception){
                    LOGGER.error(exception.getMessage());
                }

            }

            RenderUtils.renderText(fromGuiGraphics(guiGraphicsExtractor), Config.Hud.HUD_POS_X.getIntegerValue(), Config.Hud.HUD_POS_Y.getIntegerValue(), Config.Hud.HUD_SCALE.getFloatValue(), CommonColors.WHITE, CommonColors.BLACK, (HudAlignment)Config.Hud.HUD_ALIGNMENT.getOptionListValue(), false, false, lines);
        }
    }

    private static void renderClusteredDots(GuiGraphicsExtractor guiGraphicsExtractor){
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Camera camera = mc.gameRenderer.mainCamera();
        double camX = camera.position().x;
        double camY = camera.position().y;
        double camZ = camera.position().z;

        float fwdX = camera.forwardVector().x();
        float fwdY = camera.forwardVector().y();
        float fwdZ = camera.forwardVector().z();

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float scaleX = screenWidth * 0.5f;
        float scaleY = screenHeight * 0.5f;

        // Группируем блоки по кластерам
        Map<Long, BlockPos> clusters = new HashMap<>();

        int minDistance = Config.Generic.LOD2_HORIZON.getIntegerValue();


        for(int i = 0; i < RenderUtil.renderBlocksList.size(); i++){
            BlockPos pos = RenderUtil.renderBlocksList.get(i);

            double relX = pos.getX() + 0.5 - camX;
            double relZ = pos.getZ() + 0.5 - camZ;

            double distXZ = Math.sqrt(relX * relX + relZ * relZ);

            if(distXZ > minDistance) {
                long clusterKey = getClusterKey(pos.getX(), pos.getY(), pos.getZ(), CLUSTER_SIZE);
                clusters.putIfAbsent(clusterKey, pos);
            }
        }

        // Рендерим только центры кластеров
        for (BlockPos clusterCenter : clusters.values()) {
            double worldX = clusterCenter.getX() + 0.5;
            double worldY = clusterCenter.getY() + 0.5;
            double worldZ = clusterCenter.getZ() + 0.5;

            double dirX = worldX - camX;
            double dirY = worldY - camY;
            double dirZ = worldZ - camZ;

            if (dirX * fwdX + dirY * fwdY + dirZ * fwdZ <= 0) {
                continue;
            }

            // Создаем Vec3 напрямую
            Vec3 worldPos = new Vec3(worldX, worldY, worldZ);
            Vec3 screenPos = mc.gameRenderer.projectPointToScreen(worldPos);

            float screenX = (float)((screenPos.x + 1.0) * scaleX);
            float screenY = (float)((1.0 - screenPos.y) * scaleY);

            int ix = (int)screenX;
            int iy = (int)screenY;

            if (ix < -3 || ix > screenWidth + 3 || iy < -3 || iy > screenHeight + 3) {
                continue;
            }

            guiGraphicsExtractor.fill(ix - 3, iy - 3, ix + 4, iy + 4, 0xFFFF0000);
        }
    }

    private static final int CLUSTER_SIZE = 16;

    private static long getClusterKey(int x, int y, int z, int clusterSize) {
        int cx = Math.floorDiv(x, clusterSize);
        int cy = Math.floorDiv(y, clusterSize);
        int cz = Math.floorDiv(z, clusterSize);

        return ((long)cx << 42) | ((long)cy << 21) | (cz & 0x1FFFFF);
    }


    private static String getSpinnerFrame() {
        int index = (int) ((System.currentTimeMillis() / 100) % SPINNER_FRAMES.length);
        return String.valueOf(SPINNER_FRAMES[index]);
    }

    private static void addScanStatus() {
        if (!Scan.isProcessing() && !Scan.isRemoteProcessing()) {
            resetScanTiming();
            return;
        }
        long total = Scan.getAllChunksCounter();
        if (total <= 0) {
            lines.add("Scan: waiting for range");
            resetScanTiming();
            return;
        }
        long processed = Scan.getChunkProcessedCounter();
        if(total == processed) return;
        updateScanTiming(processed, total);
        double percent = Math.min(100.0, (processed * 100.0) / total);
        long remaining = Math.max(0L, total - processed);
        String eta = Scan.isRemoteProcessing() ? formatEta(remaining, processed) : "-";
        String mode = Scan.isRemoteProcessing() ? "server" : "client";

        lines.add(getSpinnerFrame() + " Scan (" + mode + "): " + processed + "/" + total + " (" + String.format("%.1f", percent) + "%) ETA " + eta);
    }

    private static void updateScanTiming(long processed, long total) {
        if (scanStartMs == 0 || total != lastTotal || processed < lastProcessed) {
            scanStartMs = System.currentTimeMillis();
        }
        lastTotal = total;
        lastProcessed = processed;
    }

    private static void resetScanTiming() {
        scanStartMs = 0;
        lastTotal = 0;
        lastProcessed = 0;
        lastEtaUpdateMs = 0;
        lastEtaText = "--";
    }

    private static String formatEta(long remaining, long processed) {
        long now = System.currentTimeMillis();
        if (lastEtaUpdateMs != 0 && now - lastEtaUpdateMs < ETA_UPDATE_MS) {
            return lastEtaText;
        }
        if (remaining <= 0) {
            lastEtaText = "0s";
            lastEtaUpdateMs = now;
            return lastEtaText;
        }
        if (scanStartMs == 0 || processed <= 0) {
            lastEtaText = "--";
            lastEtaUpdateMs = now;
            return lastEtaText;
        }
        long elapsedMs = now - scanStartMs;
        if (elapsedMs <= 0) {
            lastEtaText = "--";
            lastEtaUpdateMs = now;
            return lastEtaText;
        }
        double rate = processed / (elapsedMs / 1000.0);
        if (rate <= 0.001) {
            lastEtaText = "--";
            lastEtaUpdateMs = now;
            return lastEtaText;
        }
        long seconds = Math.round(remaining / rate);
        lastEtaText = formatSeconds(seconds);
        lastEtaUpdateMs = now;
        return lastEtaText;
    }

    private static String formatSeconds(long seconds) {
        long s = Math.max(0L, seconds);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) {
            return h + "h " + m + "m";
        }
        if (m > 0) {
            return m + "m " + sec + "s";
        }
        return sec + "s";
    }
}
