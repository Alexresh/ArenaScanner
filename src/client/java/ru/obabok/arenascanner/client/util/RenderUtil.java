package ru.obabok.arenascanner.client.util;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import ru.obabok.arenascanner.client.ArenascannerClient;

import java.util.EnumSet;

public class RenderUtil {
    private static final ModelPart.Cuboid CUBE = new ModelPart.Cuboid(0, 0, 0, 0, 0, 16, 16, 16, 0, 0, 0, false, 0, 0, EnumSet.allOf(Direction.class));
    private static final RenderLayer RENDER_LAYER = RenderLayer.getOutline(Identifier.of(ArenascannerClient.MOD_ID, "none1.png"));

    public static void renderBlock(BlockPos pos, MatrixStack matrices, OutlineVertexConsumerProvider vertexConsumers, int color, float scale){
        matrices.push();
        matrices.translate(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        matrices.scale(scale, scale, scale);
        {
            matrices.push();
            matrices.translate(-0.5, -0.5, -0.5);
            CUBE.renderCuboid(matrices.peek(), setOutlineColor(color, vertexConsumers), 0, OverlayTexture.DEFAULT_UV, 0, 0, 0, 0);
            matrices.pop();
        }
        matrices.pop();
    }
    private static VertexConsumer setOutlineColor(int color, OutlineVertexConsumerProvider vertexConsumers) {
        vertexConsumers.setColor((color >> 16) & 0xff, (color >> 8) & 0xff, color & 0xff, 255);
        return vertexConsumers.getBuffer(RENDER_LAYER);
    }
}
