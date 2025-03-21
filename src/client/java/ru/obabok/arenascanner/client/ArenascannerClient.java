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
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.ChunkPos;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.obabok.arenascanner.client.Models.Config;
import ru.obabok.arenascanner.client.util.ConfigurationManager;
import ru.obabok.arenascanner.client.util.RenderUtil;
import ru.obabok.arenascanner.client.util.ChunkScheduler;


public class ArenascannerClient implements ClientModInitializer {
    public static final String MOD_ID = "arenascanner";
    public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static KeyBinding renderKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.arena_scanner.render", GLFW.GLFW_KEY_UNKNOWN, "category.arena_scanner"));

    public static Config CONFIG;

    @Override
    public void onInitializeClient() {
        CONFIG = ConfigurationManager.loadConfig();
        ClientCommandRegistrationCallback.EVENT.register(ScanCommand::register);

        ClientPlayerBlockBreakEvents.AFTER.register((clientWorld, clientPlayerEntity, blockPos, blockState) -> {
            ChunkScheduler.addChunkToProcess(new ChunkPos(blockPos));
        });

        AttackBlockCallback.EVENT.register((playerEntity, world, hand, blockPos, direction) -> {
            if(!world.isClient) return ActionResult.PASS;
            ChunkScheduler.addChunkToProcess(new ChunkPos(blockPos));
            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((playerEntity, world, hand, blockHitResult) -> {
            if(!world.isClient) return ActionResult.PASS;
            ChunkScheduler.addChunkToProcess(new ChunkPos(blockHitResult.getBlockPos()));
            return ActionResult.PASS;
        });

        ClientChunkEvents.CHUNK_LOAD.register((clientWorld, worldChunk) -> {
            if(ScanCommand.range != null && ScanCommand.unloadedChunks.contains(worldChunk.getPos()))
                ChunkScheduler.addChunkToProcess(worldChunk.getPos());
        });

        ClientTickEvents.END_CLIENT_TICK.register(minecraftClient -> {
            if(renderKey.wasPressed()){
                RenderUtil.toggleRender(minecraftClient.player);
            }
        });

        HudRenderCallback.EVENT.register(HudRender::render);

        WorldRenderEvents.AFTER_ENTITIES.register(RenderUtil::renderAll);

        ChunkScheduler.startProcessing();

    }

}
