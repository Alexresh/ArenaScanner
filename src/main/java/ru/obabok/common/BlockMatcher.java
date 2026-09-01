package ru.obabok.common;


import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import ru.obabok.common.model.Whitelist;
import ru.obabok.common.model.WhitelistItem;

import java.util.List;
import java.util.Optional;

import static net.minecraft.world.level.block.piston.PistonBaseBlock.EXTENDED;

public class BlockMatcher {
    public static final List<String> COMPARISON_OPERATORS = List.of("=", "≠", ">", "<", "≥", "≤");
    public static final List<String> EQUALS_OPERATORS = List.of("=", "≠");

    public enum PistonBehavior {
        NORMAL,
        IMMOVABLE,
        DESTROY
    }

    public static boolean matches(Whitelist whitelist, BlockState blockState, Level world, BlockPos pos) {
        if (whitelist == null || whitelist.whitelist == null) return false;
        boolean meet = false;
        for (WhitelistItem whitelistItem : whitelist.whitelist) {
            boolean insideMeet = true;
            if (whitelistItem.block != null && whitelistItem.block != blockState.getBlock()) {
                insideMeet = false;
            }

            if (whitelistItem.waterlogged != null) {
                if(blockState.hasProperty(BlockStateProperties.WATERLOGGED)){
                    boolean waterlogged = blockState.getValue(BlockStateProperties.WATERLOGGED);
                    if (Boolean.parseBoolean(whitelistItem.waterlogged) != waterlogged) {
                        insideMeet = false;
                    }
                }else{
                    insideMeet = false;
                }
            }

            if (whitelistItem.blastResistance != null) {
                Optional<Float> resistanceOpt = getBlastResistance(blockState, blockState.getFluidState());
                if (resistanceOpt.isPresent()) {
                    String input = whitelistItem.blastResistance.trim();
                    String operator = null;
                    String numberPart = null;
                    for (String op : COMPARISON_OPERATORS) {
                        if (input.startsWith(op)) {
                            operator = op;
                            numberPart = input.substring(op.length()).trim();
                            break;
                        }
                    }
                    if (operator == null || numberPart.isEmpty()) {
                        References.LOGGER.warn("invalid blastResistance format: {}", whitelistItem.blastResistance);
                        return false;
                    }
                    if (!isParsableToInt(numberPart)) {
                        References.LOGGER.warn("invalid number in blastResistance: {}", numberPart);
                        return false;
                    }
                    int threshold = Integer.parseInt(numberPart);

                    float actualResistance = resistanceOpt.get();
                    boolean matches = switch (operator) {
                        case "=" -> actualResistance == threshold;
                        case "≠" -> actualResistance != threshold;
                        case ">" -> actualResistance > threshold;
                        case "<" -> actualResistance < threshold;
                        case "≥" -> actualResistance >= threshold;
                        case "≤" -> actualResistance <= threshold;
                        default -> false;
                    };
                    if (!matches) {
                        insideMeet = false;
                    }
                } else {
                    insideMeet = false;
                }
            }

            if (whitelistItem.pistonBehavior != null) {
                String input = whitelistItem.pistonBehavior.trim();
                String operator = null;
                String behaviorPart = null;
                for (String op : EQUALS_OPERATORS) {
                    if (input.startsWith(op)) {
                        operator = op;
                        behaviorPart = input.substring(op.length()).trim();
                        break;
                    }
                }
                if (operator == null || behaviorPart.isEmpty()) {
                    References.LOGGER.warn("invalid pistonBehavior format: {}", whitelistItem.pistonBehavior);
                    insideMeet = false;
                }
                try {
                    PistonBehavior behavior = PistonBehavior.valueOf(behaviorPart);
                    PistonBehavior actual = getPistonBehavior(blockState, world, pos);
                    boolean matches = switch (operator) {
                        case "=" -> behavior == actual;
                        case "≠" -> behavior != actual;
                        default -> false;
                    };
                    if (!matches) {
                        insideMeet = false;
                    }
                } catch (Exception e) {
                    References.LOGGER.error("PistonBehavior is corrupted");
                    insideMeet = false;
                }
            }
            meet = meet || insideMeet;
        }
        return meet;
    }

    public static Optional<Float> getBlastResistance(BlockState blockState, FluidState fluidState) {
        return blockState.isAir() && fluidState.isEmpty()
                ? Optional.empty()
                : Optional.of(Math.max(blockState.getBlock().getExplosionResistance(), fluidState.getExplosionResistance()));
    }

    public static PistonBehavior getPistonBehavior(BlockState state, Level world, BlockPos pos) {
        if (state.isAir()) {
            return PistonBehavior.NORMAL;
        } else if (!state.is(Blocks.OBSIDIAN)
                && !state.is(Blocks.CRYING_OBSIDIAN)
                && !state.is(Blocks.RESPAWN_ANCHOR)
                && !state.is(Blocks.REINFORCED_DEEPSLATE)) {

            if (!state.is(Blocks.PISTON) && !state.is(Blocks.STICKY_PISTON)) {
                if (state.getDestroySpeed(world, pos) == -1.0F) {
                    return PistonBehavior.IMMOVABLE;
                }

                switch (state.getPistonPushReaction()) {
                    case BLOCK -> {
                        return PistonBehavior.IMMOVABLE;
                    }
                    case DESTROY -> {
                        return PistonBehavior.DESTROY;
                    }
                    case PUSH_ONLY -> {
                        return PistonBehavior.NORMAL;
                    }
                }
            } else if (state.getValue(EXTENDED)) {
                return PistonBehavior.IMMOVABLE;
            }

            return (state.hasBlockEntity() ? PistonBehavior.IMMOVABLE : PistonBehavior.NORMAL);

        } else {
            return PistonBehavior.IMMOVABLE;
        }
    }

    private static boolean isParsableToInt(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
