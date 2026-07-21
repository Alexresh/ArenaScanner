package ru.obabok.client.util;

import fi.dy.masa.malilib.config.HudAlignment;
import fi.dy.masa.malilib.util.EntityUtils;
import fi.dy.masa.malilib.util.GuiUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.CommonColors;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import ru.obabok.client.Config;
import ru.obabok.client.Scan;

import java.util.*;

import static ru.obabok.common.References.LOGGER;


public class HudRender {


    private static final List<Component> lines = new ArrayList<>();
    private static final long ETA_UPDATE_MS = 1000;
    private static long scanStartMs = 0;
    private static long lastTotal = 0;
    private static long lastProcessed = 0;
    private static long lastEtaUpdateMs = 0;
    private static String lastEtaText = "--";
    private static final int CLUSTER_SIZE = 16;
    private static final Map<Long, BlockPos> clusters = new HashMap<>();

    private static final String[] hudAnimation = new String[]{
            "⠀⠀",
            "⠁⠀",
            "⠉⠀",
            "⠉⠁",
            "⠉⠉",
            "⠋⠉",
            "⠛⠉",
            "⠛⠋",
            "⠛⠛",
            "⠟⠛",
            "⠿⠛",
            "⠿⠟",
            "⠿⠿",
            "⡿⠿",
            "⣿⠿",
            "⣿⡿",
            "⣿⣿",
            "⣾⣿",
            "⣶⣿",
            "⣶⣾",
            "⣶⣶",
            "⣴⣶",
            "⣤⣶",
            "⣤⣴",
            "⣤⣤",
            "⣠⣤",
            "⣀⣤",
            "⣀⣠",
            "⣀⣀",
            "⢀⣀",
            "⠀⣀",
            "⠀⢀"
    };


    public static void render(GuiGraphicsExtractor guiGraphicsExtractor, DeltaTracker deltaTracker) {
        if (Config.Generic.MAIN_RENDER.getBooleanValue() && Config.Hud.HUD_ENABLE.getBooleanValue() && !Minecraft.getInstance().gui.hud.isHidden()) {
            lines.clear();
            if(!ChunkScheduler.getChunkQueue().isEmpty()){
                lines.add(Component.literal("ProcessedChunks: %d".formatted(ChunkScheduler.getChunkQueue().size())));
            }

            addScanStatus();

            try {
                if(!Scan.selectedBlocks.isEmpty()){
                    BlockPos pos = Scan.selectedBlocks.iterator().next();
                    lines.add(Component.literal("Selected blocks: %d -> [%d, %d, %d]".formatted(Scan.selectedBlocks.size(), pos.getX(), pos.getY(), pos.getZ())));
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
                        lines.add(Component.literal("Unchecked chunks: %d -> %s".formatted(Scan.unloadedChunks.size(), iterator.next().toString())));
                    }
                }catch (Exception exception){
                    LOGGER.error(exception.getMessage());
                }

            }

            renderText(guiGraphicsExtractor,
                    Config.Hud.HUD_POS_X.getIntegerValue(),
                    Config.Hud.HUD_POS_Y.getIntegerValue(),
                    Config.Hud.HUD_SCALE.getFloatValue(),
                    CommonColors.WHITE,
                    (HudAlignment)Config.Hud.HUD_ALIGNMENT.getOptionListValue(),
                    lines);

        }
    }

    public static int renderText(GuiGraphicsExtractor ctx,
                                 int xOff, int yOff, double scale,
                                 int textColor, HudAlignment alignment,
                                 List<Component> lines)
    {
        Font fontRenderer = Minecraft.getInstance().font;
        final int scaledWidth = GuiUtils.getScaledWindowWidth();
        final int lineHeight = fontRenderer.lineHeight + 2;
        final int contentHeight = lines.size() * lineHeight - 2;
        final int bgMargin = 2;

        if (scale < 0.0125)
        {
            return 0;
        }

        boolean scaled = scale != 1.0;

        if (scaled)
        {
            ctx.pose().pushMatrix();
            ctx.pose().scale((float) scale, (float) scale);
        }

        double posX = xOff + bgMargin;
        double posY = yOff + bgMargin;

        posY = getHudPosY((int) posY, yOff, contentHeight, scale, alignment);


        posY += getHudOffsetForPotions(alignment, scale, Minecraft.getInstance().player);


        for (Component line : lines)
        {
            final int width = fontRenderer.width(line);

            switch (alignment)
            {
                case TOP_RIGHT:
                case BOTTOM_RIGHT:
                    posX = (scaledWidth / scale) - width - xOff - bgMargin;
                    break;
                case CENTER:
                    posX = (scaledWidth / scale / 2) - ((double) width / 2) - xOff;
                    break;
                default:
            }

            final int x = (int) posX;
            final int y = (int) posY;
            posY += lineHeight;

            ctx.text(fontRenderer, line, x, y, textColor, false);
        }

        if (scaled)
        {
            ctx.pose().popMatrix();
        }

        return contentHeight + bgMargin * 2;
    }

    public static int getHudOffsetForPotions(HudAlignment alignment, double scale, Player player)
    {
        if (alignment == HudAlignment.TOP_RIGHT)
        {
            if (scale == 0d)
            {
                return 0;
            }

            Collection<MobEffectInstance> effects = player.getActiveEffects();
            boolean hasTurtleHelmet = EntityUtils.hasTurtleHelmetEquipped(player);

            if (effects.isEmpty() == false)
            {
                int y1 = 0;
                int y2 = 0;

                for (MobEffectInstance effectInstance : effects)
                {
                    MobEffect effect = effectInstance.getEffect().value();

                    if (effectInstance.isVisible() && effectInstance.showIcon())
                    {
                        if (effect.isBeneficial())
                        {
                            y1 = 26;
                        }
                        else
                        {
                            y2 = 52;
                            break;
                        }
                    }
                }

                if (hasTurtleHelmet && y1 == 0)
                {
                    y1 = 26;
                }

                return (int) (Math.max(y1, y2) / scale);
            }
            else if (hasTurtleHelmet)
            {
                return (int) ((int) 26 / scale);
            }
        }

        return 0;
    }

    public static int getHudPosY(int yOrig, int yOffset, int contentHeight, double scale, HudAlignment alignment)
    {
        int scaledHeight = GuiUtils.getScaledWindowHeight();
        int posY = yOrig;

        switch (alignment)
        {
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT:
                posY = (int) ((scaledHeight / scale) - contentHeight - yOffset);
                break;
            case CENTER:
                posY = (int) ((scaledHeight / scale / 2.0d) - (contentHeight / 2.0d) + yOffset);
                break;
            default:
        }

        return posY;
    }


    private static void renderClusteredDots(GuiGraphicsExtractor guiGraphicsExtractor){
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        clusters.clear();

        Camera camera = mc.gameRenderer.mainCamera();
        double camX = camera.position().x;
        double camY = camera.position().y;
        double camZ = camera.position().z;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        for(int i = 0; i < RenderUtil.renderBlocksList.size(); i++){
            BlockPos pos = RenderUtil.renderBlocksList.get(i);
            double distXZ = Math.sqrt((pos.getX() + 0.5 - camX) * (pos.getX() + 0.5 - camX) + (pos.getZ() + 0.5 - camZ) * (pos.getZ() + 0.5 - camZ));

            if(distXZ > Config.Generic.LOD2_HORIZON.getIntegerValue()) {
                long clusterKey = getClusterKey(pos.getX(), pos.getY(), pos.getZ());
                clusters.putIfAbsent(clusterKey, pos);
            }
        }

        for (BlockPos clusterCenter : clusters.values()) {
            double worldX = clusterCenter.getX() + 0.5;
            double worldY = clusterCenter.getY() + 0.5;
            double worldZ = clusterCenter.getZ() + 0.5;

            double dirX = worldX - camX;
            double dirY = worldY - camY;
            double dirZ = worldZ - camZ;

            if (dirX * camera.forwardVector().x() + dirY * camera.forwardVector().y() + dirZ * camera.forwardVector().z() <= 0) {
                continue;
            }

            Vec3 screenPos = mc.gameRenderer.projectPointToScreen(new Vec3(worldX, worldY, worldZ));

            float screenX = (float)((screenPos.x + 1.0) * screenWidth * 0.5f);
            float screenY = (float)((1.0 - screenPos.y) * screenHeight * 0.5f);


            if (screenX < -3 || screenX > screenWidth + 3 || screenY < -3 || screenY > screenHeight + 3) {
                continue;
            }

            guiGraphicsExtractor.fill((int)screenX - 3, (int)screenY - 3, (int)screenX + 4, (int)screenY + 4, 0xFFFF0000);
        }
    }



    private static long getClusterKey(int x, int y, int z) {
        int cx = Math.floorDiv(x, HudRender.CLUSTER_SIZE);
        int cy = Math.floorDiv(y, HudRender.CLUSTER_SIZE);
        int cz = Math.floorDiv(z, HudRender.CLUSTER_SIZE);

        return ((long)cx << 42) | ((long)cy << 21) | (cz & 0x1FFFFF);
    }

    private static MutableComponent getSpinnerFrame() {

        int indexA = (int) ((System.currentTimeMillis() / 50) % hudAnimation.length);
        return Component.literal( hudAnimation[indexA]).withStyle(ChatFormatting.GOLD);
    }

    private static void addScanStatus() {
        if (!Scan.isProcessing() && !Scan.isRemoteProcessing()) {
            resetScanTiming();
            return;
        }
        long total = Scan.getAllChunksCounter();
        if (total <= 0) {
            lines.add(Component.literal("Scan: waiting for range"));
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

        lines.add(getSpinnerFrame().append(Component.literal(" Scan (" + mode + "): " + processed + "/" + total + " (" + String.format("%.1f", percent) + "%) ETA " + eta).withStyle(ChatFormatting.WHITE)));
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
