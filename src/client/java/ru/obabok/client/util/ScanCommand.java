package ru.obabok.client.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.xpple.clientarguments.arguments.CBlockPosArgument;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.BlockBox;
import ru.obabok.client.Scan;
import ru.obabok.client.network.ClientNetwork;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;


public class ScanCommand {


    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("scan")
                .then(argument("from", CBlockPosArgument.blockPos())
                        .then(argument("to", CBlockPosArgument.blockPos())
                                .then(literal("whitelist")
                                        .then(argument("whitelist", StringArgumentType.string()).suggests(new FileSuggestionProvider())
                                                .executes(context -> {
                                                    BlockBox range = BlockBox.of(
                                                            CBlockPosArgument.getBlockPos(context, "from"),
                                                            CBlockPosArgument.getBlockPos(context, "to"));
                                                    String whitelistName = StringArgumentType.getString(context, "whitelist");
                                                    return Scan.executeAsync(context.getSource().getWorld(), range, whitelistName);
                                                }).then(argument("shared_name", StringArgumentType.string()).executes(context -> {
                                                    BlockBox range = BlockBox.of(
                                                            CBlockPosArgument.getBlockPos(context, "from"),
                                                            CBlockPosArgument.getBlockPos(context, "to"));
                                                    String whitelistName = StringArgumentType.getString(context, "whitelist");
                                                    if (!ClientNetwork.requestScan(range, whitelistName, StringArgumentType.getString(context, "shared_name"))) {
                                                        return Scan.executeAsync(context.getSource().getWorld(), range, whitelistName);
                                                    }
                                                    return 1;
                                                }))))))
                .then(literal("stop").executes(context -> {
                    if (Scan.isRemoteProcessing()) {
                        ClientNetwork.stopScan();
                        Scan.stopScan();
                        return 1;
                    }
                    Scan.stopScan();
                    return 1;
                }))
                .then(literal("whitelists")
                        .then(literal("create")
                                .then(argument("whitelist", StringArgumentType.string())
                                        .executes(context -> WhitelistManager.createWhitelist(StringArgumentType.getString(context, "whitelist")) ? 1 : 0)))
                        .then(literal("delete")
                                .then(argument("whitelist", StringArgumentType.string()).suggests(new FileSuggestionProvider())
                                        .executes(context -> WhitelistManager.deleteWhitelist(StringArgumentType.getString(context, "whitelist")) ? 1 : 0)))
                        .then(literal("print")
                                .then(argument("whitelist", StringArgumentType.string()).suggests(new FileSuggestionProvider())
                                        .executes(context -> WhitelistManager.printWhitelist(context.getSource().getPlayer(), StringArgumentType.getString(context, "whitelist")))))));

    }
}
