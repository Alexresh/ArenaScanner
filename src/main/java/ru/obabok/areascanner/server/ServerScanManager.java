package ru.obabok.areascanner.server;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import ru.obabok.areascanner.common.model.JobInfo;
import ru.obabok.areascanner.common.model.Whitelist;

import java.util.*;

public class ServerScanManager {
    private static final ServerScanManager INSTANCE = new ServerScanManager();
    private final ArrayList<FastScanJob> jobs = new ArrayList<>();
    public static ServerScanManager getInstance() {
        return INSTANCE;
    }

    public void startJob(ServerPlayerEntity player, long jobId, BlockBox range, String whitelistName, Whitelist whitelist, String sharedName) {
        for (int i = 0; i < jobs.size(); i++) {
            if(player.getUuid().equals(jobs.get(i).getOwner().getUuid()) && Objects.equals(jobs.get(i).getSharedName(), sharedName)){
                jobs.get(i).stop("Restarted", false);
                deleteJob(i);
            }
        }
        jobs.add(new FastScanJob(player, jobId, range, whitelist, sharedName, whitelistName));
    }

    public void stopJob(ServerPlayerEntity player, long jobId, String cause){
        for (int i = 0; i < jobs.size(); i++) {
            FastScanJob job = jobs.get(i);

            if (job.getOwner().getUuid().equals(player.getUuid()) && job.getJobId() == jobId) {
                job.stop(cause, true);
                deleteJob(i);
                return;
            }
        }
    }

    public void stopOPJob(long jobId){
        for (int i = 0; i < jobs.size(); i++) {
            FastScanJob job = jobs.get(i);
            if (job.getJobId() == jobId) {
                job.stop("The Admin said so", true);
                deleteJob(i);
                return;
            }
        }
    }

    public void onBlockStateChange(ServerWorld world, BlockPos pos, BlockState oldState, BlockState newState){
        for (int i = 0; i < jobs.size(); i++) {
            jobs.get(i).onBlockStateChange(world, pos, oldState, newState);
        }
    }

    public ArrayList<JobInfo> getJobs(){
        ArrayList<JobInfo> jobInfos = new ArrayList<>(jobs.size());
        for (int i = 0; i < jobs.size(); i++) {
            jobInfos.add(jobs.get(i).getInfo());
        }
        return jobInfos;
    }

    public Map<Block, Integer> getMaterialList(long jobId){
        for (int i = 0; i < jobs.size(); i++) {
            if (jobs.get(i).getJobId() == jobId) {
                return jobs.get(i).getMaterialList();
            }
        }
        return null;
    }

    public JobInfo getJobInfo(long jobId){
        for (int i = 0; i < jobs.size(); i++) {
            if (jobs.get(i).getJobId() == jobId) {
                return jobs.get(i).getInfo();
            }
        }
        return null;
    }

    public String subscribe(ServerPlayerEntity player, long jobId){
        for (int i = 0; i < jobs.size(); i++) {
            FastScanJob job = jobs.get(i);
            if(job.getWorld().getRegistryKey().getValue() != player.getServerWorld().getRegistryKey().getValue()){
                return "[wrong world] jobWorld: " + job.getWorld().getRegistryKey().getValue() + " ServerWorld: " + player.getServerWorld().getRegistryKey().getValue();
            }
            if (job.getJobId() == jobId) {
                job.subscribe(player);
                return null;
            }
        }
        return "wrong job id";
    }

    public void unsubscribe(ServerPlayerEntity player, long jobId){
        for (int i = 0; i < jobs.size(); i++) {
            if (jobs.get(i).getJobId() == jobId) {
                jobs.get(i).unsubscribe(player);
                return;
            }
        }
    }

    private void deleteJob(int id){
        jobs.set(id, jobs.get(jobs.size() - 1));
        jobs.remove(jobs.size() - 1);
    }

    public void tick() {
        for (int i = jobs.size() - 1; i >= 0; i--) {
            FastScanJob job = jobs.get(i);
            job.tick();

            if (job.isFullComplete()) {
                deleteJob(i);
            }
        }
    }
}
