package com.hivemc.chunker.conversion.encoding.java.v26_1.writer;

import com.hivemc.chunker.conversion.encoding.base.Converter;
import com.hivemc.chunker.conversion.encoding.base.Version;
import com.hivemc.chunker.conversion.encoding.java.base.resolver.JavaResolversBuilder;
import com.hivemc.chunker.conversion.encoding.java.base.writer.JavaWorldWriter;
import com.hivemc.chunker.conversion.encoding.java.v26_1.LevelDirectoryResolver;
import com.hivemc.chunker.conversion.intermediate.world.Dimension;
import com.hivemc.chunker.conversion.intermediate.world.DimensionRegistry;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;

import java.io.File;
import java.util.UUID;

public class LevelWriter extends com.hivemc.chunker.conversion.encoding.java.v1_21_11.writer.LevelWriter {
    public static final UUID EMPTY_UUID = new UUID(0, 0);

    public LevelWriter(File outputFolder, Version version, Converter converter) {
        super(outputFolder, version, converter);
    }

    @Override
    protected void writeExtraLevelSettings(CompoundTag data) throws Exception {
        // Write the previous extra settings
        super.writeExtraLevelSettings(data);

        // Write the new difficulty_settings
        CompoundTag difficultySettings = data.getOrCreateCompound("difficulty_settings");
        String difficulty = switch (data.getByte("Difficulty", (byte) 2)) {
            case 0 -> "peaceful";
            case 1 -> "easy";
            case 3 -> "hard";
            default -> "normal"; // 2
        };
        difficultySettings.put("difficulty", difficulty);
        difficultySettings.put("locked", data.getByte("DifficultyLocked", (byte) 0));
        difficultySettings.put("hardcore", data.getByte("hardcore", (byte) 0));

        // Remove the old fields
        data.remove("Difficulty");
        data.remove("DifficultyLocked");
        data.remove("hardcore");

        // Rename DragonFight (should be empty)
        Tag<?> dragonFight = data.remove("DragonFight");
        if (dragonFight != null) {
            data.put("dragon_fight", dragonFight);
        }

        // Rename WorldGenSettings
        CompoundTag worldGenSettings = (CompoundTag) data.remove("WorldGenSettings");
        if (worldGenSettings != null) {
            data.put("world_gen_settings", worldGenSettings);
            Tag<?> generateFeatures = worldGenSettings.remove("generate_features");
            if (generateFeatures != null) {
                worldGenSettings.put("generate_structures", generateFeatures);
            }
        }

        // Write the new weather_data
        CompoundTag weatherData = data.getOrCreateCompound("weather_data");
        weatherData.put("clear_weather_time", data.getInt("clearWeatherTime", 0));
        weatherData.put("rain_time", data.getInt("rainTime", 0));
        weatherData.put("thunder_time", data.getInt("thunderTime", 0));
        weatherData.put("raining", data.getByte("raining", (byte) 0));
        weatherData.put("thundering", data.getByte("thundering", (byte) 0));

        // Remove the old fields
        data.remove("clearWeatherTime");
        data.remove("rainTime");
        data.remove("thunderTime");
        data.remove("raining");
        data.remove("thundering");

        // Write the new world_clocks (fall back to time)
        long dayTime = data.getLong("DayTime", data.getLong("Time", 0));
        CompoundTag worldClocks = data.getOrCreateCompound("world_clocks");

        // Create the overworld clock
        CompoundTag overworldClock = worldClocks.getOrCreateCompound("minecraft:overworld");
        overworldClock.put("total_ticks", dayTime);
        overworldClock.put("paused", (byte) 0);

        // Remove the old field
        data.remove("DayTime");

        // Separate to new files
        File dataDirectory = resolvers.javaLevelDirectoryResolver().getMinecraftDataDirectory();
        writeIfPresentToFile(
                new File(
                        dataDirectory,
                        "weather.dat"
                ),
                (CompoundTag) data.remove("weather_data"),
                true
        );
        writeIfPresentToFile(
                new File(
                        resolvers.javaLevelDirectoryResolver().getDimensionMinecraftDataDirectory(Dimension.THE_END),
                        "ender_dragon_fight.dat"
                ),
                (CompoundTag) data.remove("dragon_fight"),
                true
        );
        writeIfPresentToFile(
                new File(
                        dataDirectory,
                        "game_rules.dat"
                ),
                (CompoundTag) data.remove("game_rules"),
                true
        );
        writeIfPresentToFile(
                new File(
                        dataDirectory,
                        "world_gen_settings.dat"
                ),
                (CompoundTag) data.remove("world_gen_settings"),
                true
        );
        writeIfPresentToFile(
                new File(
                        dataDirectory,
                        "world_clocks.dat"
                ),
                (CompoundTag) data.remove("world_clocks"),
                true
        );

        // World Border
        CompoundTag worldBorder = (CompoundTag) data.remove("world_border");
        if (worldBorder != null) {
            DimensionRegistry dimensionRegistry = converter.getDimensionRegistry();
            for (Dimension dimension : dimensionRegistry.getDimensions()) {
                CompoundTag dimensionWorldBorder = getWorldBorderForDimension(dimension, worldBorder);
                writeIfPresentToFile(
                        new File(
                                resolvers.javaLevelDirectoryResolver().getDimensionMinecraftDataDirectory(dimension),
                                "world_border.dat"
                        ),
                        dimensionWorldBorder,
                        true
                );
            }
        }

        // Extract Player to its own file
        CompoundTag player = (CompoundTag) data.remove("Player");
        if (player != null && player.size() > 0) {
            int[] parsedSingleplayerUUID = data.getIntArray("singleplayer_uuid", null);

            // Use the empty uuid (same as vanilla) if there is no known singleplayer uuid
            UUID uuid = EMPTY_UUID;
            if (parsedSingleplayerUUID != null && parsedSingleplayerUUID.length == 4) {
                uuid = new UUID(
                        (long) parsedSingleplayerUUID[0] << 32 | (parsedSingleplayerUUID[1] & 0xFFFFFFFFL),
                        (long) parsedSingleplayerUUID[2] << 32 | (parsedSingleplayerUUID[3] & 0xFFFFFFFFL)
                );
            }

            // Write the player to a file
            writeIfPresentToFile(
                    new File(resolvers.javaLevelDirectoryResolver().getPlayersDataDirectory(),
                            uuid.toString() + ".dat"),
                    player,
                    false
            );

            // Write the UUID
            data.put("singleplayer_uuid", new int[]{
                    (int) (uuid.getMostSignificantBits() >> 32),
                    (int) uuid.getMostSignificantBits(),
                    (int) (uuid.getLeastSignificantBits() >> 32),
                    (int) uuid.getLeastSignificantBits()
            });
        }
    }

    private CompoundTag getWorldBorderForDimension(Dimension dimension, CompoundTag worldBorder) {
        CompoundTag dimensionWorldBorder = worldBorder;
        if (dimension == Dimension.NETHER) {
            dimensionWorldBorder = dimensionWorldBorder.clone();
            if (dimensionWorldBorder.contains("center_x")) {
                dimensionWorldBorder.put("center_x", dimensionWorldBorder.getDouble("center_x", 0D) / 8D);
            }
            if (dimensionWorldBorder.contains("center_z")) {
                dimensionWorldBorder.put("center_z", dimensionWorldBorder.getDouble("center_z", 0D) / 8D);
            }
        }
        return dimensionWorldBorder;
    }

    protected void writeIfPresentToFile(File file, CompoundTag data, boolean wrapWithData) throws Exception {
        if (data == null || data.size() == 0) return;
        file.getParentFile().mkdirs(); // Make directories

        // Wrap in a compound tag if wrapWithData is true
        CompoundTag root = wrapWithData ? new CompoundTag(2) : data;
        if (wrapWithData) {
            root.put("data", data);
        }
        root.put("DataVersion", resolvers.dataVersion().getDataVersion());

        // Write to disk
        Tag.writeGZipJavaNBT(file, root);
    }

    @Override
    protected String getMapFileName(long mapId) {
        return mapId + ".dat";
    }

    @Override
    protected File getMapCountFile() {
        return new File(resolvers.javaLevelDirectoryResolver().getMapsDirectory(), "last_id.dat");
    }

    @Override
    public JavaWorldWriter createWorldWriter() {
        return new WorldWriter(outputFolder, converter, resolvers);
    }

    @Override
    public JavaResolversBuilder buildResolvers(Converter converter) {
        return super.buildResolvers(converter)
                .levelDirectoryResolver(new LevelDirectoryResolver(getLevelDirectory()));
    }
}
