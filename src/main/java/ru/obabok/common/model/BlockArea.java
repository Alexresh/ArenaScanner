package ru.obabok.common.model;

import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class BlockArea {
    private List<BlockBox> ranges = new ArrayList<>();

    public BlockArea(){}

    public BlockArea(BlockArea area){
        ranges = area.getBoxes();
    }

    public BlockArea(BlockBox box){
        ranges.add(box);
    }

    public BlockArea(BlockPos pos1, BlockPos pos2){
        ranges.add(new BlockBox(pos1, pos2));
    }

    public BlockArea(List<BlockBox> area){
        ranges = new ArrayList<>(area);
    }

    public boolean isEmpty(){
        return ranges.isEmpty();
    }

    public void clearAll(){
        ranges.clear();
    }

    public void setBox(int index, BlockBox box) {
        ranges.set(index, box);
    }

    public void remove(int index){
        ranges.remove(index);
    }

    public BlockBox getArea(int i){
       return ranges.get(i);
    }

    public int size(){
        return ranges.size();
    }

    public void add(BlockBox box){
        ranges.add(box);
    }

    public List<BlockBox> getBoxes(){
        return ranges;
    }

    public HashSet<ChunkPos> getAffectedChunks() {
        HashSet<ChunkPos> chunks = new HashSet<>(); // LinkedHashSet сохраняет порядок добавления

        for (int i = 0; i < ranges.size(); i++) {
            int startX = ranges.get(i).min().getX() >> 4;
            int startZ = ranges.get(i).min().getZ() >> 4;
            int endX = ranges.get(i).max().getX() >> 4;
            int endZ = ranges.get(i).max().getZ() >> 4;

            for (int cx = startX; cx <= endX; cx++) {
                for (int cz = startZ; cz <= endZ; cz++) {
                    chunks.add(new ChunkPos(cx, cz));
                }
            }
        }
        return chunks;
    }

    public List<BlockBox> getBoxesIntersectingChunk(ChunkPos chunkPos) {
        int chunkMinX = chunkPos.x() << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunkPos.z() << 4;
        int chunkMaxZ = chunkMinZ + 15;

        List<BlockBox> result = new ArrayList<>();
        for (int i = 0; i < ranges.size(); i++) {
            boolean intersectsX = ranges.get(i).min().getX() <= chunkMaxX && ranges.get(i).max().getX() >= chunkMinX;
            boolean intersectsZ = ranges.get(i).min().getZ() <= chunkMaxZ && ranges.get(i).max().getZ() >= chunkMinZ;

            if (intersectsX && intersectsZ) {
                result.add(ranges.get(i));
            }
        }
        return result;
    }

    public boolean intersectsChunk(ChunkPos chunkPos) {
        int chunkMinX = chunkPos.x() << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunkPos.z() << 4;
        int chunkMaxZ = chunkMinZ + 15;

        for (int i = 0; i < ranges.size(); i++) {
            boolean intersectsX = ranges.get(i).min().getX() <= chunkMaxX && ranges.get(i).max().getX() >= chunkMinX;
            boolean intersectsZ = ranges.get(i).min().getZ() <= chunkMaxZ && ranges.get(i).max().getZ() >= chunkMinZ;
            if (intersectsX && intersectsZ) {
                return true;
            }
        }
        return false;
    }
}
