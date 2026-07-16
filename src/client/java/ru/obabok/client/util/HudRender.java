package ru.obabok.client.util;

import fi.dy.masa.malilib.config.HudAlignment;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.CommonColors;
import net.minecraft.world.level.ChunkPos;
import ru.obabok.client.Config;
import ru.obabok.client.Scan;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
