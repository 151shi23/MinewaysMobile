package com.hivemc.chunker.conversion.encoding.bedrock.v1_21_110.writer;

import com.hivemc.chunker.conversion.encoding.base.Converter;
import com.hivemc.chunker.conversion.encoding.base.Version;
import com.hivemc.chunker.conversion.encoding.bedrock.base.writer.BedrockWorldWriter;
import com.hivemc.chunker.conversion.encoding.bedrock.util.LevelDBKey;
import com.hivemc.chunker.conversion.intermediate.column.biome.ChunkerCustomBiome;
import com.hivemc.chunker.conversion.intermediate.level.ChunkerLevelSettings;
import com.hivemc.chunker.nbt.TagType;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.collection.ListTag;

import java.io.File;
import java.util.Map;

public class LevelWriter extends com.hivemc.chunker.conversion.encoding.bedrock.v1_21_100.writer.LevelWriter {
    public LevelWriter(File outputFolder, Version version, Converter converter) {
        super(outputFolder, version, converter);
    }

    @Override
    public void writeCustomLevelSetting(ChunkerLevelSettings chunkerLevelSettings, CompoundTag output, String targetName, Object value) {
        // Check for AutumnDrop2025
        if (targetName.equals("AutumnDrop2025")) return; // Not needed for U11
        super.writeCustomLevelSetting(chunkerLevelSettings, output, targetName, value);
    }

    @Override
    public BedrockWorldWriter createWorldWriter() {
        return new WorldWriter(outputFolder, converter, resolvers, database);
    }

    @Override
    protected void writeBiomeList() throws Exception {
        ListTag<CompoundTag, Map<String, Tag<?>>> biomesList = new ListTag<>(TagType.COMPOUND);
        for (Map.Entry<ChunkerCustomBiome, Integer> entry : resolvers.biomeIdResolver().getCustomBiomes()) {
            CompoundTag tag = new CompoundTag(2);
            tag.put("id", entry.getValue().shortValue());
            tag.put("name", entry.getKey().getIdentifier());
            biomesList.add(tag);
        }

        if (biomesList.size() > 0) {
            CompoundTag biomesNameTable = new CompoundTag();
            biomesNameTable.put("list", biomesList);

            final byte[] bytes = Tag.writeBedrockNBT(biomesNameTable);
            database.put(LevelDBKey.BIOME_IDS_TABLE, bytes);
        }
    }
}
