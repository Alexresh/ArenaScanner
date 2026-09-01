package ru.obabok.client.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.xpple.clientarguments.arguments.CBlockPosArgument;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.Nullable;
import ru.obabok.client.Scan;
import ru.obabok.client.gui.screens.ConfigGui;
import ru.obabok.common.model.BlockArea;
import ru.obabok.client.network.ClientNetwork;

import java.util.concurrent.CompletableFuture;


public class ScanCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("scan")
                .then(ClientCommands.literal("area")
                        .then(ClientCommands.literal("add")
                                .then(ClientCommands.argument("from", CBlockPosArgument.blockPos())
                                        .then(ClientCommands.argument("to", CBlockPosArgument.blockPos())
                                                .executes(ctx -> addRange(ctx,
                                                        CBlockPosArgument.getBlockPos(ctx, "from"),
                                                        CBlockPosArgument.getBlockPos(ctx, "to"))))))

                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("index", IntegerArgumentType.integer(1))
                                        .suggests(ScanCommand::suggestIndices)
                                        .executes(ctx -> removeRange(ctx, IntegerArgumentType.getInteger(ctx, "index")))))

                        .then(ClientCommands.literal("edit")
                                .then(ClientCommands.argument("index", IntegerArgumentType.integer(1))
                                        .suggests(ScanCommand::suggestIndices)
                                        .then(ClientCommands.argument("from", CBlockPosArgument.blockPos())
                                                .then(ClientCommands.argument("to", CBlockPosArgument.blockPos())
                                                        .executes(ctx -> editRange(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "index"),
                                                                CBlockPosArgument.getBlockPos(ctx, "from"),
                                                                CBlockPosArgument.getBlockPos(ctx, "to")))))))

                        .then(ClientCommands.literal("list")
                                .executes(ScanCommand::listRanges))

                        .then(ClientCommands.literal("clear")
                                .executes(ScanCommand::clearRanges))
                )

                .then(ClientCommands.literal("start")
                        .executes(ctx -> startScan(ctx, null, null))
                        .then(ClientCommands.argument("whitelist", StringArgumentType.string())
                                .suggests(new FileSuggestionProvider())
                                .executes(ctx -> startScan(ctx,
                                        StringArgumentType.getString(ctx, "whitelist"), null))
                                .then(ClientCommands.argument("share_name", StringArgumentType.string())
                                        .executes(ctx -> startScan(ctx,
                                                StringArgumentType.getString(ctx, "whitelist"),
                                                StringArgumentType.getString(ctx, "share_name"))))))

                .then(ClientCommands.literal("stop").executes(context -> {
                    if (Scan.isRemoteProcessing()) ClientNetwork.stopScan();
                    Scan.stopScan();
                    context.getSource().sendFeedback(Component.literal("Scan stopped"));
                    return 1;
                }))

                .then(ClientCommands.literal("whitelists")
                        .then(ClientCommands.literal("create")
                                .then(ClientCommands.argument("whitelist", StringArgumentType.string())
                                        .executes(context -> WhitelistManager.createWhitelist(
                                                StringArgumentType.getString(context, "whitelist")) ? 1 : 0)))
                        .then(ClientCommands.literal("delete")
                                .then(ClientCommands.argument("whitelist", StringArgumentType.string())
                                        .suggests(new FileSuggestionProvider())
                                        .executes(context -> WhitelistManager.deleteWhitelist(
                                                StringArgumentType.getString(context, "whitelist")) ? 1 : 0)))
                        .then(ClientCommands.literal("print")
                                .then(ClientCommands.argument("whitelist", StringArgumentType.string())
                                        .suggests(new FileSuggestionProvider())
                                        .executes(context -> WhitelistManager.printWhitelist(
                                                context.getSource().getPlayer(),
                                                StringArgumentType.getString(context, "whitelist"))))))
                .then(ClientCommands.literal("gui").executes(context -> {
                    if(Minecraft.getInstance().gui.screen() != null){
                        Minecraft.getInstance().execute(() -> {
                            Minecraft.getInstance().gui.setScreen(new ConfigGui());
                        });
                    }
                    return 1;
                }))
        );
    }

    private static int addRange(CommandContext<FabricClientCommandSource> ctx, BlockPos from, BlockPos to) {
        if (Scan.isProcessing() || Scan.isRemoteProcessing()) {
            ctx.getSource().sendError(Component.literal("Scan in progress. Use /scan stop first").withColor(TextColor.GRAY));
            return 0;
        }
        BlockArea area = getOrCreateRange();
        BlockBox box = BlockBox.of(from, to);
        area.add(box);

        ctx.getSource().sendFeedback(Component.literal("Added region #" + area.getBoxes().size() + ": " + formatBox(box)).withColor(TextColor.GREEN));
        return 1;
    }

    private static int removeRange(CommandContext<FabricClientCommandSource> ctx, int index) {
        BlockArea area = Scan.getArea();
        if (area == null || area.getBoxes().isEmpty()) {
            ctx.getSource().sendError(Component.literal("No regions defined").withColor(TextColor.GRAY));
            return 0;
        }
        if (index < 1 || index > area.getBoxes().size()) {
            ctx.getSource().sendError(Component.literal("Index out of area").withColor(TextColor.GRAY));
            return 0;
        }
        if (Scan.isProcessing() || Scan.isRemoteProcessing()) {
            ctx.getSource().sendError(Component.literal("Scan in progress. Use /scan stop first").withColor(TextColor.GRAY));
            return 0;
        }

        BlockBox removed = area.getBoxes().remove(index - 1);
        ctx.getSource().sendFeedback(Component.literal("Removed region #" + index + ": " + formatBox(removed)).withColor(TextColor.GRAY));
        return 1;
    }

    private static int editRange(CommandContext<FabricClientCommandSource> ctx, int index, BlockPos from, BlockPos to) {
        BlockArea area = Scan.getArea();
        if (area == null || area.getBoxes().isEmpty()) {
            ctx.getSource().sendError(Component.literal("No regions defined").withColor(TextColor.GRAY));
            return 0;
        }
        if (index < 1 || index > area.getBoxes().size()) {
            ctx.getSource().sendError(Component.literal("Index out of area").withColor(TextColor.GRAY));
            return 0;
        }
        if (Scan.isProcessing() || Scan.isRemoteProcessing()) {
            ctx.getSource().sendError(Component.literal("Scan in progress. Use /scan stop first").withColor(TextColor.GRAY));
            return 0;
        }

        BlockBox newBox = BlockBox.of(from, to);
        area.setBox(index - 1, newBox);

        ctx.getSource().sendFeedback(Component.literal("Edited region #" + index + ": " + formatBox(newBox)).withColor(TextColor.AQUA));
        return 1;
    }

    private static int listRanges(CommandContext<FabricClientCommandSource> ctx) {
        BlockArea area = Scan.getArea();
        if (area == null || area.getBoxes().isEmpty()) {
            ctx.getSource().sendFeedback(Component.literal("No regions defined").withColor(TextColor.GRAY));
            return 0;
        }

        ctx.getSource().sendFeedback(Component.literal("=== Scan Regions (" + area.getBoxes().size() + ") ===").withColor(TextColor.GOLD));
        for (int i = 0; i < area.getBoxes().size(); i++) {
            BlockBox box = area.getBoxes().get(i);
            ctx.getSource().sendFeedback(Component.literal("  #" + (i + 1) + ": " + formatBox(box)));
        }
        return 1;
    }

    private static int clearRanges(CommandContext<FabricClientCommandSource> ctx) {
        BlockArea area = Scan.getArea();
        if (area == null || area.getBoxes().isEmpty()) {
            ctx.getSource().sendFeedback(Component.literal("No regions to clear").withColor(TextColor.GRAY));
            return 0;
        }
        int count = area.getBoxes().size();
        area.clearAll();
        Scan.stopScan();
        ctx.getSource().sendFeedback(Component.literal("Cleared " + count + " region(s)").withColor(TextColor.GRAY));
        return 1;
    }

    private static int startScan(CommandContext<FabricClientCommandSource> ctx, @Nullable String whitelistName, @Nullable String shareName) {
        BlockArea area = Scan.getArea();
        if (area == null || area.getBoxes().isEmpty()) {
            ctx.getSource().sendError(Component.literal("No regions defined. Use /scan area add first").withColor(TextColor.GRAY));
            return 0;
        }
        if (Scan.isProcessing() || Scan.isRemoteProcessing()) {
            ctx.getSource().sendError(Component.literal("Scan already in progress. Use /scan stop first").withColor(TextColor.GRAY));
            return 0;
        }
        if (whitelistName == null || whitelistName.isEmpty()) {
            whitelistName = Scan.getCurrentFilename();
            if (whitelistName == null || whitelistName.isEmpty()) {
                ctx.getSource().sendError(Component.literal("No whitelist specified").withColor(TextColor.GRAY));
                return 0;
            }
        }

        if (shareName != null && !shareName.isEmpty()) {
            if (!ClientNetwork.requestScan(area, whitelistName, shareName)) {
                Scan.executeAsync(ctx.getSource().getLevel(), area, whitelistName);
            }
        } else {
            Scan.executeAsync(ctx.getSource().getLevel(), area, whitelistName);
        }

        ctx.getSource().sendFeedback(Component.literal("Scan started: " + area.getBoxes().size() + " region(s), whitelist: " + whitelistName).withColor(TextColor.GRAY));
        return 1;
    }

    private static BlockArea getOrCreateRange() {
        BlockArea area = Scan.getArea();
        if (area == null) {
            area = new BlockArea();
            Scan.setArea(area);
        }
        return area;
    }

    private static CompletableFuture<Suggestions> suggestIndices(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        BlockArea area = Scan.getArea();
        if (area != null) {
            for (int i = 1; i <= area.getBoxes().size(); i++) {
                builder.suggest(String.valueOf(i));
            }
        }
        return builder.buildFuture();
    }

    private static String formatBox(BlockBox box) {
        return String.format("(%d,%d,%d) → (%d,%d,%d)",
                box.min().getX(), box.min().getY(), box.min().getZ(),
                box.max().getX(), box.max().getY(), box.max().getZ());
    }
}
