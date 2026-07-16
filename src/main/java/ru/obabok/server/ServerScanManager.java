package ru.obabok.server;

import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import ru.obabok.common.model.JobInfo;
import ru.obabok.common.model.Whitelist;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public class ServerScanManager {
    private static final ServerScanManager INSTANCE = new ServerScanManager();
    private final ArrayList<FastScanJob> jobs = new ArrayList<>();
    public static ServerScanManager getInstance() {
        return INSTANCE;
    }

    public void startJob(ServerPlayer player, long jobId, BlockBox range, String whitelistName, Whitelist whitelist, String sharedName) {
        for (int i = 0; i < jobs.size(); i++) {
            if(player.getUUID().equals(jobs.get(i).getOwner().getUUID()) && Objects.equals(jobs.get(i).getSharedName(), sharedName)){
                jobs.get(i).stop("Restarted", false);
                deleteJob(i);
            }
        }
        jobs.add(new FastScanJob(player, jobId, range, whitelist, sharedName, whitelistName));
    }

    public void stopJob(ServerPlayer player, long jobId, String cause){
        for (int i = 0; i < jobs.size(); i++) {
            FastScanJob job = jobs.get(i);

            if (job.getOwner().getUUID().equals(player.getUUID()) && job.getJobId() == jobId) {
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

    public void onBlockStateChange(ServerLevel world, BlockPos pos, BlockState oldState, BlockState newState){
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

    public String subscribe(ServerPlayer player, long jobId){
        for (int i = 0; i < jobs.size(); i++) {
            FastScanJob job = jobs.get(i);
            if(job.getWorld().dimension().registry() != player.level().dimension().registry()){
                return "[wrong world] jobWorld: " + job.getWorld().dimension().registry() + " ServerWorld: " + player.level().dimension().registry();
            }
            if (job.getJobId() == jobId) {
                job.subscribe(player);
                return null;
            }
        }
        return "wrong job id";
    }

    public void unsubscribe(ServerPlayer player, long jobId){
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
