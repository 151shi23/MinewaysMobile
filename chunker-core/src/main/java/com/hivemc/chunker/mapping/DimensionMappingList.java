package com.hivemc.chunker.mapping;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Class to hold a list of custom Dimension definitions.
 */
public class DimensionMappingList {
    private @Nullable List<DimensionMapping> dimensions;

    /**
     * Get the list of mappings
     *
     * @return the backing list of mappings.
     */
    public List<DimensionMapping> getMappings() {
        return dimensions;
    }

    /**
     * Set the mappings to a new list (can be null).
     *
     * @param dimensions the dimension mappings.
     */
    public void setMappings(List<DimensionMapping> dimensions) {
        this.dimensions = dimensions;
    }
}
