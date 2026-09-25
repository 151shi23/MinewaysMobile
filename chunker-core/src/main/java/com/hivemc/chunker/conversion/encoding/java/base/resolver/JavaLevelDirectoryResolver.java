package com.hivemc.chunker.conversion.encoding.java.base.resolver;

import com.hivemc.chunker.conversion.intermediate.world.Dimension;

import java.io.File;

/**
 * This resolver holds useful methods to finding where files should be saved or loaded from.
 */
public class JavaLevelDirectoryResolver {
    protected final File rootDirectory;

    /**
     * Create a new Java level directory resolver.
     *
     * @param rootDirectory the root directory of the level.
     */
    public JavaLevelDirectoryResolver(File rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    /**
     * Get the base directory used for a dimension.
     *
     * @param dimension the dimension.
     * @return the folder which the dimension resides in.
     */
    public File getDimensionBaseDirectory(Dimension dimension) {
        if (dimension == Dimension.OVERWORLD) return rootDirectory;
        if (dimension == Dimension.NETHER) return new File(rootDirectory, "DIM-1");
        if (dimension == Dimension.THE_END) return new File(rootDirectory, "DIM1");
        String[] parts = dimension.getIdentifier().split(":", 2);
        return new File(rootDirectory, "dimensions/" + parts[0] + "/" + parts[1]);
    }

    /**
     * Get the directory used for datapacks.
     *
     * @return the directory (may not be present).
     */
    public File getDataPacksDirectory() {
        return new File(rootDirectory, "datapacks");
    }

    /**
     * Get the directory used for minecraft data.
     *
     * @return the directory (may not be present).
     */
    public File getMinecraftDataDirectory() {
        // Shared with other data on earlier than 26.1
        return new File(rootDirectory, "data");
    }

    /**
     * Get the directory used for playerdata.
     *
     * @return the directory (may not be present).
     */
    public File getPlayersDataDirectory() {
        return new File(rootDirectory, "playerdata");
    }

    /**
     * Get the directory used for map data (item maps).
     *
     * @return the directory (may not be present).
     */
    public File getMapsDirectory() {
        // Shared with other data on earlier than 26.1
        return getMinecraftDataDirectory();
    }

    /**
     * Get the POI directory for a dimension.
     *
     * @param dimensionDirectory the root directory for the dimension.
     * @return the directory (may not be present).
     */
    public File getDimensionPOIDirectory(File dimensionDirectory) {
        return new File(dimensionDirectory, "poi");
    }

    /**
     * Get the POI directory for a dimension.
     *
     * @param dimension the dimension type.
     * @return the directory (may not be present).
     */
    public File getDimensionPOIDirectory(Dimension dimension) {
        return getDimensionPOIDirectory(getDimensionBaseDirectory(dimension));
    }

    /**
     * Get the region directory for a dimension.
     *
     * @param dimensionDirectory the root directory for the dimension.
     * @return the directory (may not be present).
     */
    public File getDimensionRegionDirectory(File dimensionDirectory) {
        return new File(dimensionDirectory, "region");
    }

    /**
     * Get the region directory for a dimension.
     *
     * @param dimension the dimension type.
     * @return the directory (may not be present).
     */
    public File getDimensionRegionDirectory(Dimension dimension) {
        return getDimensionRegionDirectory(getDimensionBaseDirectory(dimension));
    }

    /**
     * Get the vanilla data directory for a dimension.
     *
     * @param dimensionDirectory the root directory for the dimension.
     * @return the directory (may not be present).
     */
    public File getDimensionMinecraftDataDirectory(File dimensionDirectory) {
        throw new IllegalArgumentException("Not supported on less than 26.1");
    }

    /**
     * Get the vanilla data directory for a dimension.
     *
     * @param dimension the dimension type.
     * @return the directory (may not be present).
     */
    public File getDimensionMinecraftDataDirectory(Dimension dimension) {
        return getDimensionMinecraftDataDirectory(getDimensionBaseDirectory(dimension));
    }

    /**
     * Get the entities directory for a dimension.
     *
     * @param dimensionDirectory the root directory for the dimension.
     * @return the directory (may not be present).
     */
    public File getDimensionEntitiesDirectory(File dimensionDirectory) {
        // Only used from 1.17 and above
        return new File(dimensionDirectory, "entities");
    }

    /**
     * Get the entities directory for a dimension.
     *
     * @param dimension the dimension type.
     * @return the directory (may not be present).
     */
    public File getDimensionEntitiesDirectory(Dimension dimension) {
        // Only used from 1.17 and above
        return getDimensionEntitiesDirectory(getDimensionBaseDirectory(dimension));
    }
}
