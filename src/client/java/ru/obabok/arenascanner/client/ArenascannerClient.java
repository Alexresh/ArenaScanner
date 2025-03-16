package ru.obabok.arenascanner.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.obabok.arenascanner.client.Models.Config;
import ru.obabok.arenascanner.client.mixin.WorldRendererAccessor;
import ru.obabok.arenascanner.client.util.ConfigurationManager;
import ru.obabok.arenascanner.client.util.RenderUtil;




public class ArenascannerClient implements ClientModInitializer {
    public static final String MOD_ID = "arenascanner";
    public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static KeyBinding renderKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.arena_scanner.render", GLFW.GLFW_KEY_R, "category.arena_scanner"));
    public static boolean render = false;
    public static Config CONFIG;

    @Override
    public void onInitializeClient() {
        CONFIG = ConfigurationManager.loadConfig();
        ClientCommandRegistrationCallback.EVENT.register(ScanCommand::register);
        ClientPlayerBlockBreakEvents.AFTER.register((clientWorld, clientPlayerEntity, blockPos, blockState) -> {
            ScanCommand.updateChunk(new ChunkPos(blockPos), clientWorld);
        });

        AttackBlockCallback.EVENT.register((playerEntity, world, hand, blockPos, direction) -> {
            if(!world.isClient) return ActionResult.PASS;
            ScanCommand.updateChunk(new ChunkPos(blockPos), (ClientWorld) world);
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((playerEntity, world, hand, blockHitResult) -> {
            if(!world.isClient) return ActionResult.PASS;
            ScanCommand.updateChunk(new ChunkPos(blockHitResult.getBlockPos()), (ClientWorld) world);
            return ActionResult.PASS;
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
            if(render && (!ScanCommand.unloadedChunks.isEmpty() || !ScanCommand.selectedBlocks.isEmpty())){
                try {

                    WorldRendererAccessor worldRenderer = (WorldRendererAccessor) context.worldRenderer();
                    Vec3d pos = context.camera().getPos();
                    context.matrixStack().push();
                    context.matrixStack().translate(-pos.x, -pos.y, -pos.z);
                    for (ChunkPos unloadedPos : ScanCommand.unloadedChunks){
                        if(context.camera().getPos().distanceTo(new Vec3d(unloadedPos.getCenterX(), context.camera().getBlockPos().getY(), unloadedPos.getCenterZ())  ) < CONFIG.unloadedChunkViewDistance) {
                            RenderUtil.renderBlock(unloadedPos.getCenterAtY(context.camera().getBlockPos().getY() + CONFIG.unloadedChunkY), context.matrixStack(), worldRenderer.getBufferBuilders().getOutlineVertexConsumers(), CONFIG.unloadedChunkColor, CONFIG.unloadedChunkScale);
                        }

                    }
                    for (BlockPos block : ScanCommand.selectedBlocks){
                        float scale = (float) Math.min(1, pos.squaredDistanceTo(block.toCenterPos()) / 500);
                        scale = Math.max(scale, 0.05f);
                        RenderUtil.renderBlock(block, context.matrixStack(), worldRenderer.getBufferBuilders().getOutlineVertexConsumers(),CONFIG.selectedBlocksColor, scale);
                    }
                    context.matrixStack().pop();
                }catch (Exception ignored){

                }

            }
        });

    }
}
