package ru.obabok.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.obabok.client.Scan;
import ru.obabok.client.util.AreaScannerMalilibHelper;
import ru.obabok.client.util.ChunkScheduler;

@Environment(EnvType.CLIENT)
@Mixin(LevelChunk.class)
public class WorldChunkMixin {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void setBlock(BlockPos pos, BlockState state, int i, CallbackInfoReturnable<BlockState> cir){
        if(AreaScannerMalilibHelper.shouldUpdateRealtime()){
            BlockState oldState = cir.getReturnValue();
            if (oldState != null && oldState != state) {
                Level world = ((LevelChunk) (Object) this).getLevel();
                if (world instanceof ClientLevel) {
                    ChunkPos chunkPos = new ChunkPos(pos);
                    if(Scan.isProcessing() && !ChunkScheduler.getChunkQueue().contains(chunkPos))
                        ChunkScheduler.addChunkToProcess(chunkPos);
                }
            }
        }
    }

}
