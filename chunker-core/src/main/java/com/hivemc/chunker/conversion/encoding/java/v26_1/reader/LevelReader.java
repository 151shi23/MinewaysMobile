package com.hivemc.chunker.conversion.encoding.java.v26_1.reader;

import com.hivemc.chunker.conversion.encoding.base.Converter;
import com.hivemc.chunker.conversion.encoding.base.Version;
import com.hivemc.chunker.conversion.encoding.java.base.reader.JavaWorldReader;
import com.hivemc.chunker.conversion.encoding.java.base.resolver.JavaResolversBuilder;
import com.hivemc.chunker.conversion.encoding.java.v26_1.LevelDirectoryResolver;
import com.hivemc.chunker.conversion.intermediate.world.Dimension;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;

import java.io.File;
import java.util.UUID;
import java.util.regex.Pattern;

public class LevelReader extends com.hivemc.chunker.conversion.encoding.java.v1_21_11.reader.LevelReader {
    public static final Pattern MAP_FILE_PATTERN = Pattern.compile("\\d+\\.dat");

    public LevelReader(File inputDirectory, Version inputVersion, Converter converter) {
        super(inputDirectory, inputVersion, converter);
    }

    @Override
    protected CompoundTag prepareNBTForLevelSettings(CompoundTag level) throws Exception {
        // Make a copy (this ensures that we don't overwrite the original
        level = level.clone();

        // Separate from new files
        File dataDirectory = resolvers.javaLevelDirectoryResolver().getMinecraftDataDirectory();
        readIfPresentFromFile(
                new File(
                        dataDirectory,
                        "weather.dat"
                ),
                level,
                "weather_data"
        );
        readIfPresentFromFile(
                new File(
                        resolvers.javaLevelDirectoryResolver().getDimensionMinecraftDataDirectory(Dimension.THE_END),
                        "ender_dragon_fight.dat"
                ),
                level,
                "dragon_fight"
        );
        readIfPresentFromFile(
                new File(
                        dataDirectory,
                        "game_rules.dat"
                ),
                level,
                "game_rules"
        );
        readIfPresentFromFile(
                new File(
                        dataDirectory,
                        "world_gen_settings.dat"
                ),
                level,
                "world_gen_settings"
        );
        readIfPresentFromFile(
                new File(
                        dataDirectory,
                        "world_clocks.dat"
                ),
                level,
                "world_clocks"
        );

        // World Border (Load from overworld)
        readIfPresentFromFile(
                new File(
                        resolvers.javaLevelDirectoryResolver().getDimensionMinecraftDataDirectory(Dimension.OVERWORLD),
                        "world_border.dat"
                ),
                level,
                "world_border"
        );

        // Extract Player from its own file
        int[] singleplayerUUID = level.getIntArray("singleplayer_uuid", null);
        if (singleplayerUUID != null && singleplayerUUID.length == 4) {
            UUID parsed = new UUID(
                    (long) singleplayerUUID[0] << 32 | (singleplayerUUID[1] & 0xFFFFFFFFL),
                    (long) singleplayerUUID[2] << 32 | (singleplayerUUID[3] & 0xFFFFFFFFL)
            );

            // Write the player to a file
            readIfPresentFromFile(
                    new File(resolvers.javaLevelDirectoryResolver().getPlayersDataDirectory(), parsed + ".dat"),
                    level,
                    "Player"
            );
        }

        // Convert difficulty_settings to the intermediate format
        CompoundTag difficultySettings = (CompoundTag) level.remove("difficulty_settings");
        if (difficultySettings != null) {
            // Difficulty
            String difficulty = difficultySettings.getString("difficulty", null);
            if (difficulty != null) {
                byte value = switch (difficulty) {
                    case "peaceful" -> 0;
                    case "easy" -> 1;
                    case "hard" -> 3;
                    default -> 2; // normal
                };
                level.put("Difficulty", value);
            }

            // Locked
            if (difficultySettings.contains("locked")) {
                byte locked = difficultySettings.getByte("locked");
                level.put("DifficultyLocked", locked);
            }

            // Hardcore
            if (difficultySettings.contains("hardcore")) {
                byte hardcore = difficultySettings.getByte("hardcore");
                level.put("hardcore", hardcore);
            }
        }

        // Convert dragon_fight to the intermediate format
        CompoundTag dragonFight = (CompoundTag) level.remove("dragon_fight");
        if (dragonFight != null) {
            level.put("DragonFight", dragonFight);
        }

        // Rename WorldGenSettings
        CompoundTag worldGenSettings = (CompoundTag) level.remove("world_gen_settings");
        if (worldGenSettings != null) {
            level.put("WorldGenSettings", worldGenSettings);
            Tag<?> generateFeatures = worldGenSettings.remove("generate_structures");
            if (generateFeatures != null) {
                worldGenSettings.put("generate_features", generateFeatures);
            }
        }

        // Convert weather_data to the intermediate format
        CompoundTag weatherData = (CompoundTag) level.remove("weather_data");
        if (weatherData != null) {
            if (weatherData.contains("clear_weather_time")) {
                level.put("clearWeatherTime", weatherData.getInt("clear_weather_time", 0));
            }
            if (weatherData.contains("rain_time")) {
                level.put("rainTime", weatherData.getInt("rain_time", 0));
            }
            if (weatherData.contains("thunder_time")) {
                level.put("thunderTime", weatherData.getInt("thunder_time", 0));
            }
            if (weatherData.contains("raining")) {
                level.put("raining", weatherData.getByte("raining", (byte) 0));
            }
            if (weatherData.contains("thundering")) {
                level.put("thundering", weatherData.getByte("thundering", (byte) 0));
            }
        }

        // Convert world_clocks to the intermediate format
        CompoundTag worldClocks = (CompoundTag) level.remove("world_clocks");
        if (worldClocks != null) {
            CompoundTag overworldClocks = worldClocks.getCompound("minecraft:overworld");
            if (overworldClocks != null && overworldClocks.contains("total_ticks")) {
                level.put("DayTime", overworldClocks.getLong("total_ticks"));
            }
        }

        // Call the super
        return super.prepareNBTForLevelSettings(level);
    }

    protected void readIfPresentFromFile(File file, CompoundTag destinationRoot, String destinationTag) throws Exception {
        if (!file.exists()) return;

        // Read the tag
        CompoundTag tag = Tag.readGZipJavaNBT(file);

        // Add to the destination
        if (tag != null && tag.size() > 0) {
            destinationRoot.put(destinationTag, tag);
        }
    }

    @Override
    protected int getMapID(String fileName) {
        // Remove the .dat suffix
        String idString = fileName.substring(0, fileName.length() - 4);
        return Integer.parseInt(idString);
    }

    @Override
    protected boolean isMapFile(File directory, String name) {
        // Ensure it's <number>.dat
        return MAP_FILE_PATTERN.matcher(name).find();
    }

    @Override
    public JavaWorldReader createWorldReader(File dimensionFolder, Dimension dimension) {
        return new WorldReader(converter, resolvers, dimensionFolder, dimension);
    }

    @Override
    public JavaResolversBuilder buildResolvers(Converter converter) {
        return super.buildResolvers(converter)
                .levelDirectoryResolver(new LevelDirectoryResolver(getLevelDirectory()));
    }
}
