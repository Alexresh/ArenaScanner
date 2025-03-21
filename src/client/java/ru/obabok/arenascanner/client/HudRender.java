package ru.obabok.arenascanner.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import ru.obabok.arenascanner.client.util.ChunkScheduler;
import ru.obabok.arenascanner.client.util.RenderUtil;

import static ru.obabok.arenascanner.client.ArenascannerClient.*;

public class HudRender {


    public static void render(DrawContext drawContext, float v) {
        if (RenderUtil.render && CONFIG.hudRender) {
            int windowWidth = drawContext.getScaledWindowWidth();
            int windowHeight = drawContext.getScaledWindowHeight();
            int hudStartX = CONFIG.hudRenderPosX >= 0
                    ? CONFIG.hudRenderPosX
                    : windowWidth + CONFIG.hudRenderPosX;
            int hudStartY = CONFIG.hudRenderPosY >= 0
                    ? CONFIG.hudRenderPosY
                    : windowHeight + CONFIG.hudRenderPosY;
            if(!ScanCommand.selectedBlocks.isEmpty()){
                BlockPos pos = ScanCommand.selectedBlocks.get(0);
                String selectedBlocksText = "Selected blocks: %d -> [%d, %d, %d]".formatted(ScanCommand.selectedBlocks.size(), pos.getX(), pos.getY(), pos.getZ());
                int textWidthSelected = MinecraftClient.getInstance().textRenderer.getWidth(selectedBlocksText);
                int posX = hudStartX;
                if (CONFIG.hudRenderPosX < 0) {
                    posX -= textWidthSelected;
                }
                drawContext.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(selectedBlocksText), posX, hudStartY, 0xFFFFFFFF);
            }
            if(!ScanCommand.unloadedChunks.isEmpty()){
                String unloadedChunksText = "Unloaded chunks: %d -> %s".formatted(ScanCommand.unloadedChunks.size(), ScanCommand.unloadedChunks.get(0).toString());
                int textWidthUnloaded = MinecraftClient.getInstance().textRenderer.getWidth(unloadedChunksText);
                int posX = hudStartX;
                if (CONFIG.hudRenderPosX < 0) {
                    posX -= textWidthUnloaded;
                }
                drawContext.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(unloadedChunksText), posX, hudStartY + 10, 0xFFFFFFFF);
            }
            if(!ChunkScheduler.getChunkQueue().isEmpty()){
                String processedChunksText = "ProcessedChunks: %d".formatted(ChunkScheduler.getChunkQueue().size());
                int textWidthUnloaded = MinecraftClient.getInstance().textRenderer.getWidth(processedChunksText);
                int posX = hudStartX;
                if (CONFIG.hudRenderPosX < 0) {
                    posX -= textWidthUnloaded;
                }
                drawContext.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(processedChunksText), posX, hudStartY + 20, 0xFFFFFFFF);
            }
        }
    }
}
