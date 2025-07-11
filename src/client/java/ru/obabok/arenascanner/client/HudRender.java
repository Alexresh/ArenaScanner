package ru.obabok.arenascanner.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import ru.obabok.arenascanner.client.util.ChunkScheduler;
import ru.obabok.arenascanner.client.util.RenderUtil;

import java.util.Iterator;

import static ru.obabok.arenascanner.client.ArenascannerClient.*;

public class HudRender {


    public static void render(DrawContext drawContext, RenderTickCounter renderTickCounter) {
        if (RenderUtil.render && CONFIG.hudRender) {
            int windowWidth = drawContext.getScaledWindowWidth();
            int windowHeight = drawContext.getScaledWindowHeight();
            int hudStartX = CONFIG.hudRenderPosX >= 0
                    ? CONFIG.hudRenderPosX
                    : windowWidth + CONFIG.hudRenderPosX;
            int hudStartY = CONFIG.hudRenderPosY >= 0
                    ? CONFIG.hudRenderPosY
                    : windowHeight + CONFIG.hudRenderPosY;
            try {
                if(!ScanCommand.selectedBlocks.isEmpty()){
                    BlockPos pos = ScanCommand.selectedBlocks.iterator().next();
                    String selectedBlocksText = "Selected blocks: %d -> [%d, %d, %d]".formatted(ScanCommand.selectedBlocks.size(), pos.getX(), pos.getY(), pos.getZ());
                    int textWidthSelected = MinecraftClient.getInstance().textRenderer.getWidth(selectedBlocksText);
                    int posX = hudStartX;
                    if (CONFIG.hudRenderPosX < 0) {
                        posX -= textWidthSelected;
                    }
                    drawContext.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(selectedBlocksText), posX, hudStartY, 0xFFFFFFFF);
                }
            }catch (Exception exception){
                LOGGER.error(exception.getMessage());
            }

            if(!ScanCommand.unloadedChunks.isEmpty()){
                try {
                    Iterator<ChunkPos> iterator = ScanCommand.unloadedChunks.iterator();
                    if(iterator.hasNext()){
                        String unloadedChunksText = "Unloaded chunks: %d -> %s".formatted(ScanCommand.unloadedChunks.size(), iterator.next().toString());
                        int textWidthUnloaded = MinecraftClient.getInstance().textRenderer.getWidth(unloadedChunksText);
                        int posX = hudStartX;
                        if (CONFIG.hudRenderPosX < 0) {
                            posX -= textWidthUnloaded;
                        }
                        drawContext.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(unloadedChunksText), posX, hudStartY + 10, 0xFFFFFFFF);
                    }
                }catch (Exception exception){
                    LOGGER.error(exception.getMessage());
                }

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
