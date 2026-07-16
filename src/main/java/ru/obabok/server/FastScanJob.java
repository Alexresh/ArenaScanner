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
import ru.obabok.common.model.JobInfo;
import ru.obabok.common.model.Whitelist;
import ru.obabok.common.network.s2c.ScanChunkSummaryPayload;
import ru.obabok.common.network.s2c.ScanDeltaPayload;
import ru.obabok.common.network.s2c.ScanFullCompletedPayload;
import ru.obabok.server.network.SendQueue;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class FastScanJob {
    private final long jobId;
    private final ServerPlayer owner;
    private final ArrayList<ServerPlayer> subscribers;
    private final BlockBox range;
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

    public FastScanJob(ServerPlayer player, long jobId, BlockBox range, Whitelist whitelist, String sharedName, String whitelistName){
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
        return new JobInfo(jobId, sharedName, owner.getName().getString(), owner.getUUID(), world.dimension().registry().getPath(), range, whitelistName, chunkCursor.size(), processedChunks, selectedBlocks.size(), scanCompleted);
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
                    stop("Chunk " + new ChunkPos(pos) + " missing", true);
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
                CompletableFuture<Optional<CompoundTag>> future = storage.loadAsync(new ChunkPos(pos));
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
            ChunkPos chunkPos = new ChunkPos(pos);
            ChunkAccess chunk = world.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY, true);
            if (!(chunk instanceof ProtoChunk worldChunk)) {
                owner.sendSystemMessage(Component.literal(References.MOD_ID + " chunk " + new ChunkPos(pos) + " is not world chunk! Cancelling"));
                stop("Chunk " + new ChunkPos(pos) + " is not world chunk! Cancelling", true);
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
            scannedChunks.add(ChunkPos.asLong(chunkPos.x, chunkPos.z));
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
            if(subscribers.get(i).level().dimension().registry() != world.dimension().registry()){
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
        output.clear();
        int minX = Math.max(chunkPos.getMinBlockX(), range.min().getX());
        int maxX = Math.min(chunkPos.getMaxBlockX(), range.max().getX());
        int minZ = Math.max(chunkPos.getMinBlockZ(), range.min().getZ());
        int maxZ = Math.min(chunkPos.getMaxBlockZ(), range.max().getZ());
        int minY = Math.max(range.min().getY(), world.getMinY());
        int maxY = Math.min(range.max().getY(), world.getMaxY());
        if (minY > maxY) {
            return;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    if (BlockMatcher.matches(whitelist, chunk.getBlockState(pos), world, pos)) {
                        long p = pos.asLong();
                        output.add(p);
                        materialList.addTo(chunk.getBlockState(pos).getBlock(), 1);
                    }
                }
            }
        }
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
        if (!scanCompleted && !scannedChunks.contains(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4))) return;

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
        return pos.getX()     >= range.min().getX()
                && pos.getX() <= range.max().getX()
                && pos.getY() >= range.min().getY()
                && pos.getY() <= range.max().getY()
                && pos.getZ() >= range.min().getZ()
                && pos.getZ() <= range.max().getZ();
    }


    private static class ChunkCursor {
        private final int startX;
        private final int startZ;
        private final int width;
        private final int total;
        private int current = 0;

        public ChunkCursor(BlockBox range) {
            this.startX = range.min().getX() >> 4;
            this.startZ = range.min().getZ() >> 4;
            int endX = range.max().getX() >> 4;
            int endZ = range.max().getZ() >> 4;
            this.width = (endX - startX + 1);
            this.total = width * (endZ - startZ + 1);
        }

        public int size() {
            return total;
        }

        public boolean hasNext() {
            return current < total;
        }

        public long nextLong() {
            int cz = current / width;
            int cx = current % width;
            current++;
            return ChunkPos.asLong(startX + cx, startZ + cz);
        }
    }

}
