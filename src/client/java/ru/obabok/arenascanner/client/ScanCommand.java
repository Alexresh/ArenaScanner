package ru.obabok.arenascanner.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.xpple.clientarguments.arguments.CBlockPosArgumentType;
import dev.xpple.clientarguments.arguments.CBlockStateArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import ru.obabok.arenascanner.client.util.ConfigurationManager;
import ru.obabok.arenascanner.client.util.FileSuggestionProvider;
import ru.obabok.arenascanner.client.util.RenderUtil;
import ru.obabok.arenascanner.client.util.ChunkScheduler;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.*;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;


public class ScanCommand {
    public static final HashSet<BlockPos> selectedBlocks = new HashSet<>();
    public static final HashSet<ChunkPos> unloadedChunks = new HashSet<>();
    public static ArrayList<Block> whitelist = new ArrayList<>();
    public static BlockBox range;
    private static boolean worldEaterMode = false;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess commandRegistryAccess) {

        dispatcher.register(literal("scan")
                .then(argument("from", CBlockPosArgumentType.blockPos())
                        .then(argument("to", CBlockPosArgumentType.blockPos())
                                .then(literal("whitelist")
                                        .then(argument("whitelist", StringArgumentType.string()).suggests(new FileSuggestionProvider())
                                                .executes(context -> {
                                                    worldEaterMode = false;
                                                    return executeAsync(context.getSource().getWorld(), context.getSource().getPlayer(), BlockBox.create(
                                                                    CBlockPosArgumentType.getCBlockPos(context, "from"),
                                                                    CBlockPosArgumentType.getCBlockPos(context, "to")),
                                                            StringArgumentType.getString(context, "whitelist"));
                                                })))
                                .then(literal("worldEaterMode")
                                        .executes(context -> {
                                            worldEaterMode = true;
                                            return executeAsync(context.getSource().getWorld(), context.getSource().getPlayer(), BlockBox.create(
                                                            CBlockPosArgumentType.getCBlockPos(context, "from"),
                                                            CBlockPosArgumentType.getCBlockPos(context, "to")),
                                                    "");
                                        }))))
                .then(literal("stop").executes(context -> {
                    stopScan();
                    return 1;
                }))
                .then(literal("whitelists")
                        .then(literal("create")
                                .then(argument("whitelist", StringArgumentType.string())
                                        .executes(context -> createWhitelist(context.getSource().getPlayer(), StringArgumentType.getString(context, "whitelist")))))
                        .then(literal("delete")
                                .then(argument("whitelist", StringArgumentType.string()).suggests(new FileSuggestionProvider())
                                        .executes(context -> deleteWhitelist(context.getSource().getPlayer(), StringArgumentType.getString(context, "whitelist")))))
                        .then(literal("add_block")
                                .then(argument("whitelist", StringArgumentType.string()).suggests(new FileSuggestionProvider())
                                        .then(argument("block", CBlockStateArgumentType.blockState(commandRegistryAccess))
                                                .executes(context -> addToWhitelist(context.getSource().getPlayer(), StringArgumentType.getString(context, "whitelist"), CBlockStateArgumentType.getCBlockState(context, "block").getBlock())))))
                        .then(literal("remove_block")
                                .then(argument("whitelist", StringArgumentType.string()).suggests(new FileSuggestionProvider())
                                        .then(argument("block", CBlockStateArgumentType.blockState(commandRegistryAccess))
                                                .executes(context -> removeFromWhitelist(context.getSource().getPlayer(), StringArgumentType.getString(context, "whitelist"), CBlockStateArgumentType.getCBlockState(context, "block").getBlock())))))
                        .then(literal("print")
                                .then(argument("whitelist", StringArgumentType.string()).suggests(new FileSuggestionProvider())
                                        .executes(context -> printWhitelist(context.getSource().getPlayer(), StringArgumentType.getString(context, "whitelist"))))))
                .then(literal("reload_config")
                        .executes(commandContext -> {
                            ArenascannerClient.CONFIG = ConfigurationManager.loadConfig();
                            commandContext.getSource().getPlayer().sendMessage(Text.literal("Reloaded"));
                            return 1;
                        }))
                .then(literal("toggle_render").executes(commandContext -> {
                    RenderUtil.toggleRender(commandContext.getSource().getPlayer());
                    return 1;
                })));

    }

    private static int executeAsync(ClientWorld world, ClientPlayerEntity player, BlockBox _range, String filename) throws CommandSyntaxException {
        stopScan();
        range = _range;
        if (world == null) return 0;
        whitelist = loadWhitelist(player, filename);
        if(whitelist == null) return 0;

        RenderUtil.render = true;
        int startChunkX = range.getMinX() >> 4;
        int startChunkZ = range.getMinZ() >> 4;
        int endChunkX = range.getMaxX() >> 4;
        int endChunkZ = range.getMaxZ() >> 4;

        for (int chunkX = startChunkX; chunkX <= endChunkX; chunkX++) {
            for (int chunkZ = startChunkZ; chunkZ <= endChunkZ; chunkZ++) {
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                if (world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                    ChunkScheduler.addChunkToProcess(chunkPos);
                }
                unloadedChunks.add(chunkPos);

            }
        }


        return 1;
    }

    private static void stopScan(){
        selectedBlocks.clear();
        unloadedChunks.clear();
        range = null;
        RenderUtil.render = false;
        RenderUtil.clearRender();
    }

    public static void processChunk(ClientWorld world, ChunkPos chunkPos){
        if(range == null || world == null || whitelist == null || chunkPos == null) return;
        if(!world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) return;
        if((chunkPos.x >= range.getMinX() >> 4) && (chunkPos.x <= range.getMaxX() >> 4) && (chunkPos.z >= range.getMinZ() >> 4) && (chunkPos.z <= range.getMaxZ() >> 4)){
            unloadedChunks.remove(chunkPos);
            updateChunk(chunkPos, world);
            for (int x = 0; x < 16; x++) {
                for (int y = range.getMinY(); y <= range.getMaxY(); y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockPos blockPos = new BlockPos(chunkPos.x * 16 + x, y, chunkPos.z * 16 + z);
                        processBlock(blockPos, world.getBlockState(blockPos));
                    }
                }
            }
        }
    }

    public static void updateChunk(ChunkPos chunkPos, ClientWorld world){
        Iterator<BlockPos> iterator = selectedBlocks.iterator();
        while (iterator.hasNext()) {
            BlockPos blockPos = iterator.next();
            if (blockPos.getX() >> 4 == chunkPos.x && blockPos.getZ() >> 4 == chunkPos.z) {
                if (worldEaterMode) {
                    if (!((getBlastResistance(world.getBlockState(blockPos), world.getBlockState(blockPos).getFluidState()).isPresent()
                            && getBlastResistance(world.getBlockState(blockPos), world.getBlockState(blockPos).getFluidState()).get() > 9)
                            && world.getBlockState(blockPos).getPistonBehavior() != PistonBehavior.DESTROY)) {
                        iterator.remove();
                    }
                } else if (!whitelist.contains(world.getBlockState(blockPos).getBlock())) {
                    iterator.remove();
                }
            }
        }
    }

    public static void processBlock(BlockPos blockPos, BlockState blockState){
        if(range == null || whitelist == null) return;
        if(blockPos.getX() <= range.getMaxX() && blockPos.getX() >= range.getMinX() &&
                blockPos.getY() <= range.getMaxY() && blockPos.getY() >= range.getMinY() &&
                blockPos.getZ() <= range.getMaxZ() && blockPos.getZ() >= range.getMinZ()){
            if (whitelist.contains(blockState.getBlock())) {
                selectedBlocks.add(blockPos);
            }

            if(worldEaterMode && (getBlastResistance(blockState, blockState.getFluidState()).isPresent() && getBlastResistance(blockState, blockState.getFluidState()).get() > 9)
                    && blockState.getPistonBehavior() != PistonBehavior.DESTROY){
                selectedBlocks.add(blockPos);
            }
        }
    }
    public static Optional<Float> getBlastResistance(BlockState blockState, FluidState fluidState) {
        return blockState.isAir() && fluidState.isEmpty() ? Optional.empty() : Optional.of(Math.max(blockState.getBlock().getBlastResistance(), fluidState.getBlastResistance()));
    }


    private static int addToWhitelist(ClientPlayerEntity player, String filename, Block block){
        ArrayList<Block> blocks = loadWhitelist(player, filename);
        if(blocks == null){
            player.sendMessage(Text.literal("blocks is null!"));
            return 0;
        }
        if(!blocks.contains(block)){
            blocks.add(block);
            saveWhitelist(player, blocks, filename);
            player.sendMessage(Text.literal("Added"));
        }else{
            player.sendMessage(Text.literal("Already in list"));
        }


        return 1;
    }
    private static int deleteWhitelist(ClientPlayerEntity player, String filename){
        Path toFile = Path.of(FileSuggestionProvider.pathToWhitelists, filename + ".txt");
        File file = toFile.toFile();
        file.delete();
        player.sendMessage(Text.literal("Deleted"));
        return 1;
    }
    private static int createWhitelist(ClientPlayerEntity player, String filename){
        Path toFile = Path.of(FileSuggestionProvider.pathToWhitelists, filename + ".txt");
        File file = toFile.toFile();
        Path.of(FileSuggestionProvider.pathToWhitelists).toFile().mkdirs();
        try {
            file.createNewFile();

        }catch (Exception ex){
            player.sendMessage(Text.literal("Exception during write file: " + ex.getMessage() + file));
            return 0;
        }
        player.sendMessage(Text.literal("Created"));
        return 1;
    }
    private static int removeFromWhitelist(ClientPlayerEntity player, String filename, Block block){
        ArrayList<Block> blocks = loadWhitelist(player, filename);
        if(blocks == null){
            player.sendMessage(Text.literal("blocks is null!"));
            return 0;
        }
        blocks.remove(block);
        saveWhitelist(player, blocks, filename);
        player.sendMessage(Text.literal("Removed"));
        return 1;
    }

    private static int printWhitelist(ClientPlayerEntity player, String filename){
        ArrayList<Block> blocks = loadWhitelist(player, filename);
        if(blocks == null){
            player.sendMessage(Text.literal("blocks is null!"));
            return 0;
        }
        for (Block block : blocks) {
            player.sendMessage(block.getName());
        }
        return 1;
    }

    private static void saveWhitelist(ClientPlayerEntity player, ArrayList<Block> values, String filename){
        Path toFile = Path.of(FileSuggestionProvider.pathToWhitelists, filename + ".txt");
        try {
            FileWriter writer = new FileWriter(toFile.toString());
            for (Block block : values) {
                writer.write(Registries.BLOCK.getId(block)+ "\n");
            }
            writer.close();
        }catch (Exception ex){
            player.sendMessage(Text.literal("Exception during write file: " + ex.getMessage()));

        }
    }
    private static ArrayList<Block> loadWhitelist(ClientPlayerEntity player, String filename){
        Path toFile = Path.of(FileSuggestionProvider.pathToWhitelists, filename  + ".txt");
        ArrayList<Block> whitelist = new ArrayList<>();

        if(filename.isEmpty()) return whitelist;

        try{
            FileReader reader = new FileReader(toFile.toString());
            Scanner scan = new Scanner(reader);
            while(scan.hasNextLine()){
                String fileString = scan.nextLine();
                Identifier blockId = new Identifier(fileString);
                Block block = Registries.BLOCK.get(blockId);
                if(Registries.BLOCK.getId(block).toString().equals(fileString)){
                    whitelist.add(block);
                }else {
                    player.sendMessage(Text.literal("Unknown block:" + fileString));
                }
            }
            reader.close();
            return whitelist;
        }catch (Exception ex){
            MinecraftClient.getInstance().player.sendMessage(Text.literal("Exception during read file: " + ex.getMessage()));
        }
        return null;
    }

}
