package ru.obabok.arenascanner.client.util;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import ru.obabok.arenascanner.client.ArenascannerClient;
import ru.obabok.arenascanner.client.mixin.WorldRendererAccessor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static ru.obabok.arenascanner.client.ArenascannerClient.CONFIG;

public class RenderUtil {
    private static final ModelPart.Cuboid CUBE = new ModelPart.Cuboid(0, 0, 0, 0, 0, 16, 16, 16, 0, 0, 0, false, 0, 0, EnumSet.allOf(Direction.class));
    private static final RenderLayer RENDER_LAYER = RenderLayer.getOutline(Identifier.of(ArenascannerClient.MOD_ID, "none1.png"));
    private static final List<BlockPos> renderBlocksList = new CopyOnWriteArrayList<>();
    private static final List<ChunkPos> renderChunksList = new CopyOnWriteArrayList<>();
    public static boolean render = false;

    public static void renderAll(WorldRenderContext context) {
        if(render && (!renderChunksList.isEmpty() || !renderBlocksList.isEmpty())){
            try {
                context.matrixStack().push();
                context.matrixStack().translate(-context.camera().getPos().x, -context.camera().getPos().y, -context.camera().getPos().z);
                for (ChunkPos unloadedPos : renderChunksList){
                    if(unloadedPos != null && context.camera().getPos().distanceTo(new Vec3d(unloadedPos.getCenterX(), context.camera().getBlockPos().getY(), unloadedPos.getCenterZ())) < CONFIG.unloadedChunkViewDistance) {
                        RenderUtil.renderBlock(unloadedPos.getCenterAtY(context.camera().getBlockPos().getY() + CONFIG.unloadedChunkY), context.matrixStack(), ((WorldRendererAccessor)context.worldRenderer()).getBufferBuilders().getOutlineVertexConsumers(), CONFIG.unloadedChunkColor, CONFIG.unloadedChunkScale);
                    }
                }
                for (BlockPos block : renderBlocksList){
                    float scale = (float) Math.min(1, context.camera().getPos().squaredDistanceTo(block.toCenterPos()) / 500);
                    scale = Math.max(scale, 0.05f);
                    if(context.camera().getPos().distanceTo(block.toCenterPos()) < CONFIG.selectedBlocksViewDistance || CONFIG.selectedBlocksViewDistance == -1){
                        RenderUtil.renderBlock(block, context.matrixStack(), ((WorldRendererAccessor)context.worldRenderer()).getBufferBuilders().getOutlineVertexConsumers(),CONFIG.selectedBlocksColor, scale);
                    }

                }
                context.matrixStack().pop();
            }catch (Exception ignored){

            }

        }
    }
    public static void toggleRender(ClientPlayerEntity player){
        render = !render;
        if(player != null)
            player.sendMessage(Text.literal("Render whitelisted blocks: " + render));
    }

    public static void clearRender(){
        renderBlocksList.clear();
        renderChunksList.clear();
    }

    public static void renderBlock(BlockPos pos, MatrixStack matrices, OutlineVertexConsumerProvider vertexConsumers, String color, float scale){
        matrices.push();
        matrices.translate(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        matrices.scale(scale, scale, scale);
        {
            matrices.push();
            matrices.translate(-0.5, -0.5, -0.5);
            CUBE.renderCuboid(matrices.peek(), setColorFromHex(vertexConsumers, color), 0, OverlayTexture.DEFAULT_UV, 0, 0, 0, 0);
            matrices.pop();
        }
        matrices.pop();
    }

    public static VertexConsumer setColorFromHex(OutlineVertexConsumerProvider vertexConsumers, String hexColor) {
        if (hexColor.startsWith("#")) {
            hexColor = hexColor.substring(1);
        }
        if (hexColor.length() == 6) {
            hexColor += "FF";
        }
        int color = (int) Long.parseLong(hexColor, 16);
        int r = (color >> 24) & 0xff;
        int g = (color >> 16) & 0xff;
        int b = (color >> 8) & 0xff;
        int a = color & 0xff;
        vertexConsumers.setColor(r, g, b, a);
        return vertexConsumers.getBuffer(RENDER_LAYER);
    }


    public static void addAllRenderBlocks(HashSet<BlockPos> blocks) {
        renderBlocksList.addAll(blocks);
    }
    public static void addAllRenderChunks(HashSet<ChunkPos> chunkPos) {
        renderChunksList.addAll(chunkPos);
    }
}
