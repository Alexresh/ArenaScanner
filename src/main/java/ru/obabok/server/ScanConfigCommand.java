package ru.obabok.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

public class ScanConfigCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("scanset")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("key", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestKeys(builder))
                        .executes(ScanConfigCommand::showCurrentValue)
                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                .executes(ScanConfigCommand::setValue)))
                .then(Commands.literal("load").executes(ctx -> {
                    ServerScanConfig.load();
                    ctx.getSource().sendSuccess(() -> Component.literal("Config reloaded."), true);
                    return 1;
                }))
                .then(Commands.literal("save").executes(ctx -> {
                    ServerScanConfig.save();
                    ctx.getSource().sendSuccess(() -> Component.literal("Config saved."), true);
                    return 1;
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    ServerScanConfig.resetToDefaults();
                    ctx.getSource().sendSuccess(() -> Component.literal("Config reset to defaults."), true);
                    return 1;
                }))
        );
    }




    private static CompletableFuture<Suggestions> suggestKeys(SuggestionsBuilder builder) {
        for (String key : ServerScanConfig.KEYS) {
            builder.suggest(key);
        }
        return builder.buildFuture();
    }

    private static int showCurrentValue(CommandContext<CommandSourceStack> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        if (!ServerScanConfig.VALUES.containsKey(key)) {
            ctx.getSource().sendFailure(Component.literal("Unknown config key: " + key));
            return 0;
        }
        int value = ServerScanConfig.VALUES.get(key);
        ctx.getSource().sendSuccess(() -> Component.literal(key + " = " + value), false);
        return 1;
    }

    private static int setValue(CommandContext<CommandSourceStack> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        int value = IntegerArgumentType.getInteger(ctx, "value");

        if (!ServerScanConfig.VALUES.containsKey(key)) {
            ctx.getSource().sendFailure(Component.literal("Unknown config key: " + key));
            return 0;
        }

        int[] bounds = ServerScanConfig.BOUNDS.get(key);
        if (value < bounds[0] || value > bounds[1]) {
            ctx.getSource().sendFailure(Component.literal(
                    "Value for '" + key + "' must be between " + bounds[0] + " and " + bounds[1]
            ));
            return 0;
        }

        ServerScanConfig.VALUES.put(key, value);
        ctx.getSource().sendSuccess(() -> Component.literal("Set " + key + " = " + value), true);
        return 1;
    }

}
