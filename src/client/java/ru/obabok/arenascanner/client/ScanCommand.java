package ru.obabok.arenascanner.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.xpple.clientarguments.arguments.CBlockPosArgumentType;
import dev.xpple.clientarguments.arguments.CBlockStateArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.registry.Registries;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import ru.obabok.arenascanner.client.util.ConfigurationManager;
import ru.obabok.arenascanner.client.util.FileSuggestionProvider;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.*;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;


public class ScanCommand {
    public static final ArrayList<BlockPos> selectedBlocks = new ArrayList<>();
    public static final ArrayList<ChunkPos> unloadedChunks = new ArrayList<>();
    public static ArrayList<Block> whitelist = new ArrayList<>();
    public static BlockBox range;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess commandRegistryAccess) {

        dispatcher.register(literal("scan")
                .then(argument("from", CBlockPosArgumentType.blockPos())
                        .then(argument("to", CBlockPosArgumentType.blockPos()).then(argument("whitelist", StringArgumentType.string()).suggests(new FileSuggestionProvider())
                                .executes(context ->
                                execute(context.getSource().getWorld(), context.getSource().getPlayer(),
                                        BlockBox.create(
                                                CBlockPosArgumentType.getCBlockPos(context,"from"),
                                                CBlockPosArgumentType.getCBlockPos(context, "to")), StringArgumentType.getString(context, "whitelist"))))))
                .then(literal("stop").executes(context -> { stopScan(context.getSource().getPlayer()); return 1;}))
                .then(literal("whitelists")
                        .then(literal("create")
                                .then(argument("whitelist", StringArgumentType.string())
                                        .executes(context -> createWhitelist(context.getSource().getPlayer(), StringArgumentType.getString(context,"whitelist")))))
                        .then(literal("delete")
                                .then(argument("whitelist", StringArgumentType.string()).suggests(new FileSuggestionProvider())
                                        .executes(context -> deleteWhitelist(context.getSource().getPlayer(), StringArgumentType.getString(context,"whitelist")))))
                        .then(literal("add_block")
                                .then(argument("whitelist", StringArgumentType.string()).suggests(new FileSuggestionProvider())
                                        .then(argument("block", CBlockStateArgumentType.blockState(commandRegistryAccess))
                                                .executes(context -> addToWhitelist(context.getSource().getPlayer(), StringArgumentType.getString(context, "whitelist"), CBlockStateArgumentType.getCBlockState(context,"block").getBlock())))))
                        .then(literal("remove_block")
                                .then(argument("whitelist", StringArgumentType.string()).suggests(new FileSuggestionProvider())
                                        .then(argument("block", CBlockStateArgumentType.blockState(commandRegistryAccess))
                                                .executes(context -> removeFromWhitelist(context.getSource().getPlayer(), StringArgumentType.getString(context, "whitelist"), CBlockStateArgumentType.getCBlockState(context,"block").getBlock())))))
                        .then(literal("print")
                                .then(argument("whitelist", StringArgumentType.string()).suggests(new FileSuggestionProvider())
                                        .executes(context -> printWhitelist(context.getSource().getPlayer(), StringArgumentType.getString(context, "whitelist"))))))
                .then(literal("reload_config")
                        .executes(commandContext -> {
                            ArenascannerClient.CONFIG = ConfigurationManager.loadConfig();
                            commandContext.getSource().getPlayer().sendMessage(Text.literal("Reloaded"));
                            return 1;
                }))
                .then(literal("toggle_render").executes(commandContext -> {ArenascannerClient.toggleRender(commandContext.getSource().getPlayer()); return 1;})));

    }



    private static int execute(ClientWorld world, ClientPlayerEntity player, BlockBox _range, String filename) throws CommandSyntaxException {
        unloadedChunks.clear();
        range = _range;
        if (world == null) return 0;
        whitelist = loadWhitelist(player, filename);
        if(whitelist == null) return 0;

        ArenascannerClient.render = true;
        int startChunkX = range.getMinX() >> 4;
        int startChunkZ = range.getMinZ() >> 4;
        int endChunkX = range.getMaxX() >> 4;
        int endChunkZ = range.getMaxZ() >> 4;

        for (int chunkX = startChunkX; chunkX <= endChunkX; chunkX++) {
            for (int chunkZ = startChunkZ; chunkZ <= endChunkZ; chunkZ++) {
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                if (world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                    processChunk(player, world, chunkPos);
                }else{
                    unloadedChunks.add(chunkPos);
                }
            }
        }
        return 1;
    }
    private static void stopScan(ClientPlayerEntity player){
        selectedBlocks.clear();
        unloadedChunks.clear();
        range = null;
        player.sendMessage(Text.literal("Cleared!"));
    }

    public static void processChunk(ClientPlayerEntity player, ClientWorld world, ChunkPos chunkPos){
        if(range == null || world == null || whitelist == null || whitelist.isEmpty() || chunkPos == null) return;

        if((chunkPos.x >= range.getMinX() >> 4) && (chunkPos.x <= range.getMaxX() >> 4) && (chunkPos.z >= range.getMinZ() >> 4) && (chunkPos.z <= range.getMaxZ() >> 4)){
            unloadedChunks.remove(chunkPos);
            updateChunk(chunkPos, world);
            for (int x = 0; x < 16; x++) {
                for (int y = range.getMinY(); y <= range.getMaxY(); y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockPos blockPos = new BlockPos(chunkPos.x * 16 + x, y, chunkPos.z * 16 + z);
                        Block block = world.getBlockState(blockPos).getBlock();
                        processBlock(blockPos, block);
                    }
                }
            }

        }
        if(!unloadedChunks.isEmpty()){
            player.sendMessage(Text.literal("%d blocks found. Unloaded chunks: %d [%d,%d] [%d,%d]".formatted(selectedBlocks.size(), unloadedChunks.size(), unloadedChunks.get(0).getCenterX(), unloadedChunks.get(0).getCenterZ(), unloadedChunks.get(unloadedChunks.size() - 1).getCenterX(), unloadedChunks.get(unloadedChunks.size() - 1).getCenterZ())).setStyle(Style.EMPTY.withColor(Formatting.AQUA)), true);
        }else{
            player.sendMessage(Text.literal("%d blocks found.".formatted(selectedBlocks.size())).setStyle(Style.EMPTY.withColor(Formatting.AQUA)), true);
        }
    }

    public static void updateChunk(ChunkPos chunkPos, ClientWorld world){
        int size = selectedBlocks.size();
        for (int i = 0; i < size; i++) {
            if(selectedBlocks.get(i).getX() >> 4 == chunkPos.x && selectedBlocks.get(i).getZ() >> 4 == chunkPos.z && !whitelist.contains(world.getBlockState(selectedBlocks.get(i)).getBlock())){
                selectedBlocks.remove(selectedBlocks.get(i));
                i--;
                size--;
            }
        }
    }

    public static void processBlock(BlockPos blockPos, Block block){
        if(range == null || whitelist == null || whitelist.isEmpty()) return;
        if(blockPos.getX() <= range.getMaxX() && blockPos.getX() >= range.getMinX() &&
                blockPos.getY() <= range.getMaxY() && blockPos.getY() >= range.getMinY() &&
                blockPos.getZ() <= range.getMaxZ() && blockPos.getZ() >= range.getMinZ()){
            if (whitelist.contains(block)) {
                if(!selectedBlocks.contains(blockPos)){
                    selectedBlocks.add(blockPos);
                }
            }
        }
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
        try {
            file.createNewFile();

        }catch (Exception ex){
            player.sendMessage(Text.literal("Exception during write file: " + ex.getMessage()));
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
