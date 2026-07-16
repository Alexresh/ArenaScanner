package ru.obabok.common.model;


import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class WhitelistItem {
    public Block block;
    public String waterlogged;
    public String blastResistance;
    public String pistonBehavior;


    public WhitelistItem(@Nullable Block _block, @Nullable String _waterlogged, @Nullable String _blastResistance, @Nullable String _pistonBehavior){
        if(_block != null){
            this.block = _block;
        }
        if(_waterlogged != null){
            this.waterlogged = _waterlogged;
        }
        if(_blastResistance != null){
            this.blastResistance = _blastResistance;
        }
        if(_pistonBehavior != null){
            this.pistonBehavior = _pistonBehavior;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("[");
        if(block != null){
            builder.append("block:").append(block);
        }
        builder.append(" and ");
        if(waterlogged != null){
            builder.append("waterlogged:").append(waterlogged);
        }
        builder.append(" and ");
        if(blastResistance != null){
            builder.append("blastResistance:").append(blastResistance);
        }
        builder.append(" and ");
        if(pistonBehavior != null){
            builder.append("pistonBehavior:").append(pistonBehavior);
        }
        builder.append("]");
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WhitelistItem that = (WhitelistItem) o;
        return Objects.equals(block, that.block) &&
                Objects.equals(waterlogged, that.waterlogged) &&
                Objects.equals(blastResistance, that.blastResistance) &&
                Objects.equals(pistonBehavior, that.pistonBehavior);
    }
}
