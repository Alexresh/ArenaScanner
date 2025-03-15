package ru.obabok.arenascanner.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import ru.obabok.arenascanner.client.mixin.WorldRendererAccessor;
import ru.obabok.arenascanner.client.util.RenderUtil;

public class ArenascannerClient implements ClientModInitializer {
    public static final String MOD_ID = "arena_scanner";
    public static KeyBinding renderKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.arena_scanner.render", GLFW.GLFW_KEY_R, "category.arena_scanner"));
    private static boolean render = false;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(ScanCommand::register);
        ClientPlayerBlockBreakEvents.AFTER.register((clientWorld, clientPlayerEntity, blockPos, blockState) -> {
            //ScanCommand.selectedBlocks.remove(blockPos);
            ScanCommand.processChunk(clientPlayerEntity, clientWorld, new ChunkPos(blockPos));
        });
        ClientChunkEvents.CHUNK_LOAD.register((clientWorld, worldChunk) -> {
            ScanCommand.processChunk(MinecraftClient.getInstance().player, clientWorld, worldChunk.getPos());
        });

        ClientTickEvents.END_CLIENT_TICK.register(minecraftClient -> {
            if(renderKey.wasPressed()){
                render = !render;
                if(minecraftClient.player != null)
                    minecraftClient.player.sendMessage(Text.literal("Render whitelisted blocks: " + render));
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if(render){
                try {

                    WorldRendererAccessor worldRenderer = (WorldRendererAccessor) context.worldRenderer();
                    Vec3d pos = context.camera().getPos();

                    context.matrixStack().push();
                    context.matrixStack().translate(-pos.x, -pos.y, -pos.z);
                    for (ChunkPos unloadedPos : ScanCommand.unloadedChunks){
                        if(context.camera().getPos().distanceTo(new Vec3d(unloadedPos.getCenterX(), context.camera().getBlockPos().getY(), unloadedPos.getCenterZ())  ) < 300) {
                            RenderUtil.renderBlock(unloadedPos.getCenterAtY(context.camera().getBlockPos().getY() - 50), context.matrixStack(), worldRenderer.getBufferBuilders().getOutlineVertexConsumers(), 51200, 2);
                        }

                    }
                    for (BlockPos block : ScanCommand.selectedBlocks)
                        RenderUtil.renderBlock(block, context.matrixStack(), worldRenderer.getBufferBuilders().getOutlineVertexConsumers(),14423100, 0.5f);
                    context.matrixStack().pop();
                }catch (Exception ignored){

                }

            }
        });

    }
}
