package com.buildwand.utils;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class AreaSelection {
    private BlockPos firstPosition;
    private BlockPos secondPosition;
    private World world;

    public BlockPos getFirstPosition() {
        return firstPosition;
    }

    public void setFirstPosition(BlockPos firstPosition, World world) {
        this.firstPosition = firstPosition;
        if (this.world == null) {
            this.world = world;
        }
    }

    public BlockPos getSecondPosition() {
        return secondPosition;
    }

    public void setSecondPosition(BlockPos secondPosition, World world) {
        this.secondPosition = secondPosition;
        if (this.world == null) {
            this.world = world;
        }
    }

    public boolean isComplete() {
        return firstPosition != null && secondPosition != null && world != null;
    }

    public int getBlockCount() {
        if (!isComplete()) {
            return 0;
        }
        
        int xMin = Math.min(firstPosition.getX(), secondPosition.getX());
        int yMin = Math.min(firstPosition.getY(), secondPosition.getY());
        int zMin = Math.min(firstPosition.getZ(), secondPosition.getZ());
        
        int xMax = Math.max(firstPosition.getX(), secondPosition.getX());
        int yMax = Math.max(firstPosition.getY(), secondPosition.getY());
        int zMax = Math.max(firstPosition.getZ(), secondPosition.getZ());
        
        int width = xMax - xMin + 1;
        int height = yMax - yMin + 1;
        int depth = zMax - zMin + 1;
        
        return width * height * depth;
    }

    public int getMinX() {
        return Math.min(firstPosition.getX(), secondPosition.getX());
    }

    public int getMinY() {
        return Math.min(firstPosition.getY(), secondPosition.getY());
    }

    public int getMinZ() {
        return Math.min(firstPosition.getZ(), secondPosition.getZ());
    }

    public int getMaxX() {
        return Math.max(firstPosition.getX(), secondPosition.getX());
    }

    public int getMaxY() {
        return Math.max(firstPosition.getY(), secondPosition.getY());
    }

    public int getMaxZ() {
        return Math.max(firstPosition.getZ(), secondPosition.getZ());
    }

    public World getWorld() {
        return world;
    }
}
