package ru.obabok.client.util.Lavobsidian;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

public class ObsidianScanner {
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    public static void scan(Level world, AABB range, Consumer<BlockPos> obsidianMarker, Consumer<Integer> progressCallback) {
        int minX = (int) Math.floor(range.minX);
        int minY = (int) Math.floor(range.minY);
        int minZ = (int) Math.floor(range.minZ);
        int maxX = (int) Math.floor(range.maxX);
        int maxY = (int) Math.floor(range.maxY);
        int maxZ = (int) Math.floor(range.maxZ);

        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;

        // Глобальная матрица уровня воды, как в Python: layer_matrix[x][z]
        short[] layerMatrix = new short[width * depth];

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        // Идем сверху вниз
        for (int y = maxY; y >= minY; y--) {
            Deque<int[]> sources = new ArrayDeque<>(); // x, z
            Deque<int[]> flows = new ArrayDeque<>();   // x, z
            LongOpenHashSet markedLavaThisLayer = new LongOpenHashSet();

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    BlockState state = world.getBlockState(mutable);
                    FluidState fluid = state.getFluidState();

                    int localX = x - minX;
                    int localZ = z - minZ;
                    int index = localX * depth + localZ;

                    boolean isWaterSource = fluid.getType() == Fluids.WATER && fluid.isSource();
                    boolean isAir = state.isAir();
                    boolean isLavaSource = fluid.getType() == Fluids.LAVA && fluid.isSource();
                    boolean hasWaterAbove = layerMatrix[index] > 0;

                    if (isWaterSource) {
                        sources.add(new int[]{x, z});
                    } else if (isAir && hasWaterAbove) {
                        flows.add(new int[]{x, z});
                    } else if (isLavaSource && hasWaterAbove) {
                        // Mark lava logic
                        if (!markedLavaThisLayer.contains(index)) {
                            markLavaComponent(world, x, y, z, minX, minZ, maxX, maxZ, markedLavaThisLayer);
                            obsidianMarker.accept(mutable.immutable());
                        }
                    }

                    // Сброс матрицы для текущего слоя, как в Python
                    layerMatrix[index] = 0;
                }
            }

            // 2. Симуляция источников (Inflation, check_collisions = false)
            for (int[] pos : sources) {
                simulateWater(world, pos[0], y, pos[1], minX, minY, minZ, maxX, maxY, maxZ, layerMatrix, 8, false, depth);
            }

            // 3. Симуляция потоков (Check collisions = true)
            for (int[] pos : flows) {
                simulateWater(world, pos[0], y, pos[1], minX, minY, minZ, maxX, maxY, maxZ, layerMatrix, 8, true, depth);
            }
            if (progressCallback != null) {
                progressCallback.accept(y);
            }
        }
    }

    private static void simulateWater(Level world, int startX, int startY, int startZ,
                                      int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                      short[] layerMatrix, int waterLevel, boolean checkCollisions, int depth) {

        Deque<FlowNode> queue = new ArrayDeque<>();
        queue.add(new FlowNode(startX, startZ, waterLevel));

        int terminalWaterLevel = 1;

        while (!queue.isEmpty()) {
            FlowNode node = queue.poll();

            // Проверка границ
            if (node.x < minX || node.x > maxX || node.z < minZ || node.z > maxZ) continue;

            int localX = node.x - minX;
            int localZ = node.z - minZ;
            int index = localX * depth + localZ;

            // Условия выхода из Python:
            // if (water_level < terminal_water_level or ... or water_level <= layer_matrix[x, z] ...)
            if (node.level < terminalWaterLevel || node.level <= layerMatrix[index]) continue;

            // Проверка коллизий
            if (checkCollisions) {
                BlockPos pos = new BlockPos(node.x, startY, node.z);
                BlockState state = world.getBlockState(pos);
                // В Python: region[x, y, z].id != AIR
                // То есть вода может течь ТОЛЬКО в воздух.
                if (!state.isAir()) {
                    continue;
                }
            }

            // Обновляем матрицу
            layerMatrix[index] = (short) node.level;

            // Логика растекания в стороны
            // Python: if y > 0 and (water_level >= 8 or region[x, y - 1, z].id not in (AIR, WATER))
            boolean canFlowSideways = false;
            if (startY > minY) {
                BlockPos belowPos = new BlockPos(node.x, startY - 1, node.z);
                BlockState belowState = world.getBlockState(belowPos);
                FluidState belowFluid = belowState.getFluidState();

                // not in (AIR, WATER) means it's solid or lava
                boolean isSupport = !belowState.isAir() && belowFluid.getType() != Fluids.WATER;

                if (node.level >= 8 || isSupport) {
                    canFlowSideways = true;
                }
            }

            if (canFlowSideways) {
                for (Direction dir : HORIZONTAL) {
                    queue.add(new FlowNode(node.x + dir.getStepX(), node.z + dir.getStepZ(), node.level - 1));
                }
            } else {
                // Python: terminal_water_level = water_level
                terminalWaterLevel = node.level;
            }
        }
    }

    private static void markLavaComponent(Level world, int startX, int startY, int startZ,
                                          int minX, int minZ, int maxX, int maxZ,
                                          LongOpenHashSet cache) {
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startZ});

        int depth = maxZ - minZ + 1;

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int x = pos[0];
            int z = pos[1];

            if (x < minX || x > maxX || z < minZ || z > maxZ) continue;

            int localX = x - minX;
            int localZ = z - minZ;
            int index = localX * depth + localZ;

            if (cache.contains(index)) continue;

            BlockPos blockPos = new BlockPos(x, startY, z);
            FluidState fluid = world.getFluidState(blockPos);

            if (fluid.getType() == Fluids.LAVA && fluid.isSource()) {
                cache.add(index);
                queue.add(new int[]{x + 1, z});
                queue.add(new int[]{x - 1, z});
                queue.add(new int[]{x, z + 1});
                queue.add(new int[]{x, z - 1});
            }
        }
    }

    private static final class FlowNode {
        final int x, z, level;
        FlowNode(int x, int z, int level) {
            this.x = x; this.z = z; this.level = level;
        }
    }

}
