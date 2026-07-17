package ru.obabok.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockBox;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ChunkPos;
import ru.obabok.client.network.ClientNetwork;
import ru.obabok.client.util.*;
import ru.obabok.common.References;

public class AreaScannerClient implements ClientModInitializer {
	public static boolean isMaliLibLoaded;
	@Override
	public void onInitializeClient() {
		isMaliLibLoaded = FabricLoader.getInstance().isModLoaded("malilib");
		if(isMaliLibLoaded){
			AreaScannerMalilibHelper.initMalilib();
			ClientNetwork.register();
			ClientCommandRegistrationCallback.EVENT.register((commandDispatcher, commandRegistryAccess) -> ScanCommand.register(commandDispatcher));
			ClientPlayerBlockBreakEvents.AFTER.register((clientWorld, clientPlayerEntity, blockPos, blockState) ->{
				if (Scan.isRemoteProcessing()) return;
				if(Scan.isProcessing()){
					ChunkScheduler.addChunkToProcess(new ChunkPos(blockPos));
				}
			});

			AttackBlockCallback.EVENT.register((playerEntity, world, hand, blockPos, direction) -> {
				if(!world.isClientSide()) return InteractionResult.PASS;
				if (Scan.isRemoteProcessing()) return InteractionResult.PASS;
				if(Scan.isProcessing()) {
					ChunkScheduler.addChunkToProcess(new ChunkPos(blockPos));
				}
				return InteractionResult.PASS;
			});

			UseBlockCallback.EVENT.register((playerEntity, world, hand, blockHitResult) -> {
				if(!world.isClientSide()) return InteractionResult.PASS;
				if (Scan.isRemoteProcessing()) return InteractionResult.PASS;
				if(Scan.isProcessing()) {
					ChunkScheduler.addChunkToProcess(new ChunkPos(blockHitResult.getBlockPos()));
				}
				return InteractionResult.PASS;
			});

			ClientChunkEvents.CHUNK_LOAD.register((clientWorld, worldChunk) -> {
				BlockBox range = Scan.getRange();
				if (range != null) {
					ChunkPos chunkPos = worldChunk.getPos();
					if (OldUtils.intersectsChunk(range, chunkPos)) {
						if (Scan.isRemoteProcessing()) return;
						if(Scan.isProcessing()) {
							ChunkScheduler.addChunkToProcess(chunkPos);
						}
					}
				}
			});

			HudElementRegistry.addLast(ResourceLocation.fromNamespaceAndPath(References.MOD_ID, "hud"), HudRender::render);
			WorldRenderEvents.BEFORE_TRANSLUCENT.register(RenderUtil::renderAll);
			ClientLifecycleEvents.CLIENT_STOPPING.register(ChunkScheduler::stopProcessing);
			ChunkScheduler.startProcessing();
		}
	}
}