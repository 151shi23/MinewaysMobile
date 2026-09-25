package com.hivemc.chunker.conversion.encoding.bedrock.v1_21_110.reader;

import com.hivemc.chunker.conversion.encoding.base.Converter;
import com.hivemc.chunker.conversion.encoding.base.Version;
import com.hivemc.chunker.conversion.encoding.bedrock.base.reader.BedrockWorldReader;
import com.hivemc.chunker.conversion.encoding.bedrock.util.LevelDBKey;
import com.hivemc.chunker.conversion.intermediate.column.chunk.ChunkCoordPair;
import com.hivemc.chunker.conversion.intermediate.column.chunk.RegionCoordPair;
import com.hivemc.chunker.conversion.intermediate.level.ChunkerLevelSettings;
import com.hivemc.chunker.conversion.intermediate.level.ChunkerLevel;
import com.hivemc.chunker.conversion.intermediate.world.Dimension;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.collection.ListTag;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class LevelReader extends com.hivemc.chunker.conversion.encoding.bedrock.v1_21_100.reader.LevelReader {
    public LevelReader(File inputDirectory, Version inputVersion, Converter converter) {
        super(inputDirectory, inputVersion, converter);
    }

    @Override
    public @Nullable Object readCustomLevelSetting(@NotNull CompoundTag root, @NotNull ChunkerLevelSettings chunkerLevelSettings, @NotNull String targetName, @NotNull Class<?> type) {
        // Check for AutumnDrop2025
        if (targetName.equals("AutumnDrop2025")) return true; // U11 supports this
        return super.readCustomLevelSetting(root, chunkerLevelSettings, targetName, type);
    }

    @Override
    public BedrockWorldReader createWorldReader(Map<RegionCoordPair, Set<ChunkCoordPair>> presentRegions, Dimension dimension) {
        return new WorldReader(resolvers, converter, database, presentRegions, dimension);
    }

    @Override
    protected void parseBiomeList(ChunkerLevel output) {
        try {
            byte[] value = database.get(LevelDBKey.BIOME_IDS_TABLE);
            if (value == null) return;

            // Read the data
            CompoundTag wrappedData = Objects.requireNonNull(Tag.readBedrockNBT(value));
            ListTag<CompoundTag, Map<String, Tag<?>>> list = wrappedData.getList("list", CompoundTag.class, null);
            if (list == null) return;

            Int2ObjectOpenHashMap<String> customBiomes = new Int2ObjectOpenHashMap<>(list.size());
            for (CompoundTag record : list) {
                int id = record.getShort("id");
                String name = record.getString("name");
                customBiomes.put(id, name);
            }

            resolvers.biomeIdResolver().loadCustom(customBiomes);
        } catch (Exception e) {
            converter.logNonFatalException(e);
        }
    }
}
