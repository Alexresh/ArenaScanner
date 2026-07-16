package ru.obabok.server.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.obabok.server.ServerScanManager;

@Mixin(Level.class)
public abstract class WorldSetBlockStateMixin {
    @Redirect(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;"
            )
    )
    private BlockState areaScannerOnSetBlockState(LevelChunk chunk, BlockPos pos, BlockState state, int moved) {
        BlockState oldState = chunk.getBlockState(pos);
        BlockState result = chunk.setBlockState(pos, state, moved);
        if (result == null) {
            return null;
        }
        Level world = (Level) (Object) this;
        if (!world.isClientSide() && world instanceof ServerLevel serverWorld) {
            if (!oldState.equals(state)) {
                ServerScanManager.getInstance().onBlockStateChange(serverWorld, pos, oldState, state);
            }
        }
        return result;
    }
}
