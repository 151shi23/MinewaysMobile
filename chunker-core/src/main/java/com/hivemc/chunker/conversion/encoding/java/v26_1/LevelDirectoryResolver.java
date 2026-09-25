package com.hivemc.chunker.conversion.encoding.java.v26_1;

import com.hivemc.chunker.conversion.encoding.java.base.resolver.JavaLevelDirectoryResolver;
import com.hivemc.chunker.conversion.intermediate.world.Dimension;

import java.io.File;

public class LevelDirectoryResolver extends JavaLevelDirectoryResolver {
    public LevelDirectoryResolver(File rootDirectory) {
        super(rootDirectory);
    }

    @Override
    public File getDimensionBaseDirectory(Dimension dimension) {
        String[] keyNamespace = dimension.getIdentifier().split(":", 2);
        if (keyNamespace.length != 2) throw new IllegalArgumentException("Invalid dimension identifier");

        // Return the namespaced directory
        File dimensionsDirectory = new File(rootDirectory, "dimensions");
        File namespaceDimensionsDirectory = new File(dimensionsDirectory, keyNamespace[0]);
        return new File(namespaceDimensionsDirectory, keyNamespace[1]);
    }

    @Override
    public File getMinecraftDataDirectory() {
        return new File(new File(rootDirectory, "data"), "minecraft");
    }

    @Override
    public File getMapsDirectory() {
        return new File(getMinecraftDataDirectory(), "maps");
    }

    @Override
    public File getDimensionMinecraftDataDirectory(File dimensionDirectory) {
        return new File(new File(dimensionDirectory, "data"), "minecraft");
    }

    @Override
    public File getPlayersDataDirectory() {
        return new File(new File(rootDirectory, "players"), "data");
    }
}
