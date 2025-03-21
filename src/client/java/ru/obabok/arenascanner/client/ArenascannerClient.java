package ru.obabok.arenascanner.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
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

import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;


public class ArenascannerClient implements ClientModInitializer {
    public static final String MOD_ID = "arenascanner";
    public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static KeyBinding renderKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.arena_scanner.render", GLFW.GLFW_KEY_UNKNOWN, "category.arena_scanner"));
    public static boolean render = false;
    public static Config CONFIG;

    public static final Queue<ChunkPos> chunkQueue = new ConcurrentLinkedQueue<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final List<BlockPos> renderBlocksList = new CopyOnWriteArrayList<>();
    private static final List<ChunkPos> renderChunksList = new CopyOnWriteArrayList<>();

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

//        ClientChunkEvents.CHUNK_LOAD.register((clientWorld, worldChunk) -> {
//            ScanCommand.processChunk(clientWorld, worldChunk.getPos());
//        });

        ClientChunkEvents.CHUNK_LOAD.register((clientWorld, worldChunk) -> {
            if(ScanCommand.range != null)
                chunkQueue.add(worldChunk.getPos());
        });

        scheduler.scheduleAtFixedRate(() -> {
            ChunkPos chunkPos = chunkQueue.poll();
            if (chunkPos != null) {
                ScanCommand.processChunk(MinecraftClient.getInstance().world, chunkPos);
                renderBlocksList.clear();
                renderChunksList.clear();
                renderBlocksList.addAll(ScanCommand.selectedBlocks);
                renderChunksList.addAll(ScanCommand.unloadedChunks);
            }

        }, 0, 30, TimeUnit.MILLISECONDS);



        ClientTickEvents.END_CLIENT_TICK.register(minecraftClient -> {
            if(renderKey.wasPressed()){
                toggleRender(minecraftClient.player);
            }
        });


        HudRenderCallback.EVENT.register((drawContext, v) -> {
            if (render && CONFIG.hudRender) {
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
                if(!chunkQueue.isEmpty()){
                    String processedChunksText = "ProcessedChunks: %d".formatted(chunkQueue.size());
                    int textWidthUnloaded = MinecraftClient.getInstance().textRenderer.getWidth(processedChunksText);
                    int posX = hudStartX;
                    if (CONFIG.hudRenderPosX < 0) {
                        posX -= textWidthUnloaded;
                    }
                    drawContext.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(processedChunksText), posX, hudStartY + 20, 0xFFFFFFFF);
                }
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if(render && (!renderChunksList.isEmpty() || !renderBlocksList.isEmpty())){
                try {

                    WorldRendererAccessor worldRenderer = (WorldRendererAccessor) context.worldRenderer();
                    Vec3d pos = context.camera().getPos();
                    context.matrixStack().push();
                    context.matrixStack().translate(-pos.x, -pos.y, -pos.z);
                    for (ChunkPos unloadedPos : renderChunksList){
                        if(context.camera().getPos().distanceTo(new Vec3d(unloadedPos.getCenterX(), context.camera().getBlockPos().getY(), unloadedPos.getCenterZ())  ) < CONFIG.unloadedChunkViewDistance) {
                            RenderUtil.renderBlock(unloadedPos.getCenterAtY(context.camera().getBlockPos().getY() + CONFIG.unloadedChunkY), context.matrixStack(), worldRenderer.getBufferBuilders().getOutlineVertexConsumers(), CONFIG.unloadedChunkColor, CONFIG.unloadedChunkScale);
                        }
                    }
                    for (BlockPos block : renderBlocksList){
                        float scale = (float) Math.min(1, pos.squaredDistanceTo(block.toCenterPos()) / 500);
                        scale = Math.max(scale, 0.05f);
                        if(pos.distanceTo(block.toCenterPos()) < CONFIG.selectedBlocksViewDistance || CONFIG.selectedBlocksViewDistance == -1){
                            RenderUtil.renderBlock(block, context.matrixStack(), worldRenderer.getBufferBuilders().getOutlineVertexConsumers(),CONFIG.selectedBlocksColor, scale);
                        }

                    }
                    context.matrixStack().pop();
                }catch (Exception ignored){

                }

            }
        });

    }
    public static void toggleRender(ClientPlayerEntity player){
        render = !render;
        if(player != null)
            player.sendMessage(Text.literal("Render whitelisted blocks: " + render));
    }
}
