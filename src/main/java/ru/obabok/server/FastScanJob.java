package ru.obabok.server;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.chunk.storage.IOWorker;
import ru.obabok.common.BlockMatcher;
import ru.obabok.common.References;
import ru.obabok.common.model.BlockArea;
import ru.obabok.common.model.JobInfo;
import ru.obabok.common.model.Whitelist;
import ru.obabok.common.network.s2c.ScanChunkSummaryPayload;
import ru.obabok.common.network.s2c.ScanDeltaPayload;
import ru.obabok.common.network.s2c.ScanFullCompletedPayload;
import ru.obabok.server.network.SendQueue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class FastScanJob {
    private final long jobId;
    private final ServerPlayer owner;
    private final ArrayList<ServerPlayer> subscribers;
    private final BlockArea range;
    private final Whitelist whitelist;
    private final ServerLevel world;
    private boolean scanCompleted;
    private final Long2ObjectOpenHashMap<CompletableFuture<Optional<CompoundTag>>> pendingNbt;
    private final LongArrayFIFOQueue readyChunks;
    private long processedChunks;
    private final LongOpenHashSet scannedChunks;
    private boolean fullComplete;
    private final LongArrayFIFOQueue pendingDeltaPos = new LongArrayFIFOQueue();
    private final IntArrayFIFOQueue pendingDeltaTypes = new IntArrayFIFOQueue();
    public LongOpenHashSet selectedBlocks = new LongOpenHashSet();
    private final String sharedName;
    private final String whitelistName;
    private final Object2IntOpenHashMap<Block> materialList = new Object2IntOpenHashMap<>();
    private final LongArrayList deltaBuffer = new LongArrayList();
    private final LongArrayList activeNbtReads = new LongArrayList();
    private ChunkScanAccess scannableIOWorker;
    private final ChunkCursor chunkCursor;
    private int sendCooldown = 1;

    public FastScanJob(ServerPlayer player, long jobId, BlockArea range, Whitelist whitelist, String sharedName, String whitelistName){
        this.jobId = jobId;
        this.owner = player;
        this.range = range;
        this.sharedName = sharedName;
        this.whitelist = whitelist;
        this.world = player.level();
        chunkCursor = new ChunkCursor(range);
        this.pendingNbt = new Long2ObjectOpenHashMap<>();
        this.readyChunks = new LongArrayFIFOQueue();
        this.scannedChunks = new LongOpenHashSet();
        this.whitelistName = whitelistName;
        this.subscribers = new ArrayList<>();
        subscribers.add(owner);
    }

    public ServerLevel getWorld(){
        return world;
    }
    public String getSharedName(){
        return sharedName;
    }

    public long getJobId(){
        return jobId;
    }

    public ServerPlayer getOwner(){
        return owner;
    }

    public JobInfo getInfo(){
        return new JobInfo(jobId, sharedName, owner.getName().getString(), owner.getUUID(), world.dimension().identifier().getPath(), range, whitelistName, chunkCursor.size(), processedChunks, selectedBlocks.size(), scanCompleted);
    }

    public void tick(){
        sendDeltaDataPackets();

        if (!scanCompleted) {
            processCompletedNbtChecks();
            scanReadyChunks();

            scheduleNbtChecks();

            if (processedChunks >= chunkCursor.size() && pendingNbt.isEmpty() && readyChunks.isEmpty()) {
                scanCompleted = true;
                cleanup();
                for (int sub = 0; sub < subscribers.size(); sub++) {
                    sendSelectedBlocks(subscribers.get(sub));
                }
            }
        }
        sendCooldown--;
        if(sendCooldown <= 0){
            updateChunkProcess();
            sendCooldown = 20;
        }
        if (scanCompleted) {
            if(selectedBlocks.isEmpty()){
                stop("no blocks found!", false);
            }
        }
    }

    private void sendSelectedBlocks(ServerPlayer player){
        if(scanCompleted){
            LongArrayList blockList = new LongArrayList(selectedBlocks);
            int max = ServerScanConfig.getMaxDeltaPositionsPerPacket();
            for (int i = 0; i < blockList.size(); i += max) {
                int end = Math.min(i + max, blockList.size());
                LongList batch = blockList.subList(i, end);
                SendQueue.addPacket(player, new ScanDeltaPayload(jobId, true, batch.toLongArray()));
            }
        }
    }

    private void processCompletedNbtChecks() {
        if (activeNbtReads.isEmpty()) return;

        int completed = 0;
        for (int i = activeNbtReads.size() - 1; i >= 0; i--) {
            if (completed >= ServerScanConfig.getMaxNbtReadsPerTick()) break;

            long pos = activeNbtReads.getLong(i);
            CompletableFuture<Optional<CompoundTag>> future = pendingNbt.get(pos);

            if (future == null || !future.isDone()) continue;

            try {
                Optional<CompoundTag> nbt = future.getNow(Optional.empty());
                pendingNbt.remove(pos);
                activeNbtReads.removeLong(i);
                completed++;

                if (nbt.isPresent()) {
                    readyChunks.enqueue(pos);
                } else {
                    stop("Chunk " + ChunkPos.fromSectionNode(pos) + " missing", true);
                    return;
                }
            } catch (Exception e) {
                stop("NBT Read Error", true);
                return;
            }
        }
    }

    public Object2IntOpenHashMap<Block> getMaterialList(){
        return materialList;
    }

    private void scheduleNbtChecks() {
        if (pendingNbt.size() >= ServerScanConfig.getMaxPendingNbt()) return;

        IOWorker storage = getStorageIoWorker();
        if (storage == null) return;

        int scheduled = 0;
        while (chunkCursor.hasNext()
                && scheduled < ServerScanConfig.getMaxNbtReadsPerTick()
                && pendingNbt.size() < ServerScanConfig.getMaxPendingNbt()) {
                long pos = chunkCursor.nextLong();
                CompletableFuture<Optional<CompoundTag>> future = storage.loadAsync(ChunkPos.unpack(pos));
                pendingNbt.put(pos, future);
                activeNbtReads.add(pos); // Запоминаем, что этот чанк в работе
                scheduled++;
        }
    }

    private void scanReadyChunks() {
        long startNs = System.nanoTime();
        while (!readyChunks.isEmpty()) {
            if (timeExceeded(startNs, ServerScanConfig.getBudgetMs() * 1_000_000L)) {
                break;
            }
            long pos = readyChunks.dequeueLong();
            ChunkPos chunkPos = ChunkPos.unpack(pos);
            ChunkAccess chunk = world.getChunk(chunkPos.x(), chunkPos.z(), ChunkStatus.EMPTY, true);
            if (!(chunk instanceof ProtoChunk worldChunk)) {
                owner.sendSystemMessage(Component.literal(References.MOD_ID + " chunk " + ChunkPos.unpack(pos) + " is not world chunk! Cancelling"));
                stop("Chunk " + ChunkPos.unpack(pos) + " is not world chunk! Cancelling", true);
                return;
            }
            scanChunk(worldChunk, chunkPos, deltaBuffer);
            selectedBlocks.addAll(deltaBuffer);
            for (int i = 0; i < deltaBuffer.size(); i++) {
                pendingDeltaTypes.enqueue(1);
                pendingDeltaPos.enqueue(deltaBuffer.getLong(i));
            }


            if(selectedBlocks.size() > ServerScanConfig.getMaxSelectedBlocks()){
                stop("too many selected blocks", true);
            }

            processedChunks++;
            scannedChunks.add(ChunkPos.pack(chunkPos.x(), chunkPos.z()));
        }
    }

    private void sendDeltaDataPackets() {
        while (!pendingDeltaTypes.isEmpty()) {
            // Берем первый тип и позицию
            int currentType = pendingDeltaTypes.dequeueInt();
            boolean isAdd = (currentType == 1);

            deltaBuffer.clear();
            deltaBuffer.add(pendingDeltaPos.dequeueLong());

            // Собираем пачку, пока типы совпадают
            while (!pendingDeltaTypes.isEmpty() && deltaBuffer.size() < ServerScanConfig.getMaxDeltaPositionsPerPacket()) {
                // "Подсматриваем" тип следующего элемента
                int nextType = pendingDeltaTypes.firstInt();
                if (nextType != currentType) break;

                // Если тип такой же, забираем его из обеих очередей
                pendingDeltaTypes.dequeueInt();
                deltaBuffer.add(pendingDeltaPos.dequeueLong());
            }

            ScanDeltaPayload payload = new ScanDeltaPayload(jobId, isAdd, deltaBuffer.toLongArray());

            for (int i = 0; i < subscribers.size(); i++) {
                SendQueue.addPacket(subscribers.get(i), payload);
            }
        }
    }

    private void updateChunkProcess(){
        for (int i = 0; i < subscribers.size(); i++) {
            if(subscribers.get(i).level().dimension().identifier() != world.dimension().identifier()){
                ServerPlayNetworking.send(subscribers.get(i), new ScanFullCompletedPayload(jobId, "Wrong world", false));
                unsubscribe(subscribers.get(i));
                return;
            }
            SendQueue.addPacket(subscribers.get(i), new ScanChunkSummaryPayload(jobId, processedChunks));
        }
    }

    public void unsubscribe(ServerPlayer playerEntity){
        subscribers.remove(playerEntity);
    }

    public void subscribe(ServerPlayer player){
        unsubscribe(player);
        subscribers.add(player);
        if(scanCompleted){
            sendSelectedBlocks(player);
        }
    }

    private void scanChunk(ProtoChunk chunk, ChunkPos chunkPos, LongArrayList output) {
        List<BlockBox> relevantBoxes = getRelevantBoxes(chunkPos);
        if (relevantBoxes.isEmpty()) return;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int chunkMinX = chunkPos.getMinBlockX();
        int chunkMaxX = chunkPos.getMaxBlockX();
        int chunkMinZ = chunkPos.getMinBlockZ();
        int chunkMaxZ = chunkPos.getMaxBlockZ();

        for (int i = 0; i < relevantBoxes.size(); i++) {
            int minX = Math.max(chunkMinX, relevantBoxes.get(i).min().getX());
            int maxX = Math.min(chunkMaxX, relevantBoxes.get(i).max().getX());
            int minZ = Math.max(chunkMinZ, relevantBoxes.get(i).min().getZ());
            int maxZ = Math.min(chunkMaxZ, relevantBoxes.get(i).max().getZ());
            int minY = Math.max(relevantBoxes.get(i).min().getY(), world.getMinY());
            int maxY = Math.min(relevantBoxes.get(i).max().getY(), world.getMaxY());

            if (minX > maxX || minZ > maxZ || minY > maxY) continue;

            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        pos.set(x, y, z);
                        long p = pos.asLong();

                        if (output.contains(p)) continue;

                        BlockState state = chunk.getBlockState(pos);
                        if (BlockMatcher.matches(whitelist, state, world, pos)) {
                            output.add(p);
                            materialList.addTo(state.getBlock(), 1);
                        }
                    }
                }
            }
        }
    }

    private List<BlockBox> getRelevantBoxes(ChunkPos chunkPos) {
        int chunkMinX = chunkPos.getMinBlockX();
        int chunkMaxX = chunkPos.getMaxBlockX();
        int chunkMinZ = chunkPos.getMinBlockZ();
        int chunkMaxZ = chunkPos.getMaxBlockZ();

        List<BlockBox> result = new ArrayList<>();
        for (int i = 0; i < range.size(); i++) {
            boolean intersectsX = range.getArea(i).min().getX() <= chunkMaxX && range.getArea(i).max().getX() >= chunkMinX;
            boolean intersectsZ = range.getArea(i).min().getZ() <= chunkMaxZ && range.getArea(i).max().getZ() >= chunkMinZ;
            if (intersectsX && intersectsZ) {
                result.add(range.getArea(i));
            }
        }
        return result;
    }

    public void stop(String cause, boolean restart){
        ScanFullCompletedPayload payload = new ScanFullCompletedPayload(jobId, cause, restart);
        for (int i = 0; i < subscribers.size(); i++) {
            SendQueue.addPacket(subscribers.get(i), payload);
        }
        fullComplete = true;
    }

    private void cleanup(){
        readyChunks.clear();
        readyChunks.trim();
        pendingNbt.clear();
        pendingNbt.trim();
        activeNbtReads.clear();
        activeNbtReads.trim();
        deltaBuffer.clear();
    }

    private IOWorker getStorageIoWorker() {
        if(scannableIOWorker == null){
            scannableIOWorker = world.getChunkSource().chunkScanner();
        }
        if (scannableIOWorker instanceof IOWorker storage) {
            return storage;
        }
        return null;
    }

    public boolean isFullComplete() {
        return fullComplete;
    }

    private static boolean timeExceeded(long startNs, long budgetNs) {
        return System.nanoTime() - startNs > budgetNs;
    }

    public void onBlockStateChange(ServerLevel eventWorld, BlockPos pos, BlockState oldState, BlockState newState) {
        if (fullComplete) return;
        if (eventWorld != world) return;
        if (!isInRange(pos)) return;
        if (!scanCompleted && !scannedChunks.contains(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4))) return;

        boolean oldMatch = BlockMatcher.matches(whitelist, oldState, world, pos);
        boolean newMatch = BlockMatcher.matches(whitelist, newState, world, pos);
        if (oldMatch == newMatch) return;
        queueDelta(pos, newMatch, oldState, newState);
    }

    private void queueDelta(BlockPos pos, boolean add, BlockState oldState, BlockState newState) {
        long posLong = pos.asLong();
        if (add) {
            selectedBlocks.add(posLong);
            materialList.addTo(newState.getBlock(), 1);
        } else {
            selectedBlocks.remove(posLong);
            materialList.addTo(oldState.getBlock(), -1);
        }

        // Записываем тип (1 или 0) и координаты
        pendingDeltaTypes.enqueue(add ? 1 : 0);
        pendingDeltaPos.enqueue(posLong);
    }

    private boolean isInRange(BlockPos pos) {
        for (int i = 0; i < range.size(); i++) {
            if (pos.getX() >= range.getArea(i).min().getX() && pos.getX() <= range.getArea(i).max().getX()
                    && pos.getY() >= range.getArea(i).min().getY() && pos.getY() <= range.getArea(i).max().getY()
                    && pos.getZ() >= range.getArea(i).min().getZ() && pos.getZ() <= range.getArea(i).max().getZ()) {
                return true;
            }
        }
        return false;
    }


    private static class ChunkCursor {
        private final List<ChunkPos> chunks;
        private int current = 0;

        public ChunkCursor(BlockArea area) {
            // Собираем уникальные чанки из всех боксов
            LinkedHashSet<ChunkPos> uniqueChunks = new LinkedHashSet<>();
            for (BlockBox box : area.getBoxes()) {
                int startX = box.min().getX() >> 4;
                int startZ = box.min().getZ() >> 4;
                int endX = box.max().getX() >> 4;
                int endZ = box.max().getZ() >> 4;
                for (int cx = startX; cx <= endX; cx++) {
                    for (int cz = startZ; cz <= endZ; cz++) {
                        uniqueChunks.add(new ChunkPos(cx, cz));
                    }
                }
            }
            this.chunks = new ArrayList<>(uniqueChunks);
        }

        public int size() {
            return chunks.size();
        }

        public boolean hasNext() {
            return current < chunks.size();
        }

        public long nextLong() {
            ChunkPos pos = chunks.get(current++);
            return ChunkPos.pack(pos.x(), pos.z());
        }
    }

}
