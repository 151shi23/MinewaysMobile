package com.hivemc.chunker.conversion.intermediate.column.chunk.itemstack;

import com.hivemc.chunker.conversion.intermediate.world.Dimension;

import java.util.Objects;

/**
 * Data used for tracking the position with the lodestone compass.
 */
public record ChunkerLodestoneData(Dimension dimension, int x, int y, int z, boolean tracked) {
    /**
     * Create new lodestone data.
     *
     * @param dimension the dimension of the lodestone.
     * @param x         the x co-ordinate.
     * @param y         the y co-ordinate.
     * @param z         the z co-ordinate.
     * @param tracked   whether it should track the lodestone block or false if it should work regardless.
     */
    public ChunkerLodestoneData {
    }

    /**
     * Get the dimension the lodestone is inside.
     *
     * @return the dimension.
     */
    @Override
    public Dimension dimension() {
        return dimension;
    }

    /**
     * Get the x co-ordinate to point towards.
     *
     * @return the x co-ordinate.
     */
    @Override
    public int x() {
        return x;
    }

    /**
     * Get the y co-ordinate to point towards.
     *
     * @return the y co-ordinate.
     */
    @Override
    public int y() {
        return y;
    }

    /**
     * Get the z co-ordinate to point towards.
     *
     * @return the z co-ordinate.
     */
    @Override
    public int z() {
        return z;
    }

    /**
     * Whether the lodestone is being tracked or if the lodestone is not required.
     *
     * @return true if the lodestone is required.
     */
    @Override
    public boolean tracked() {
        return tracked;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ChunkerLodestoneData that)) return false;
        return x() == that.x() && y() == that.y() && z() == that.z() && tracked() == that.tracked() && dimension() == that.dimension();
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension(), x(), y(), z(), tracked());
    }

    @Override
    public String toString() {
        return "ChunkerLodestoneData{" +
                "dimension=" + dimension +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", tracked=" + tracked +
                '}';
    }
}
