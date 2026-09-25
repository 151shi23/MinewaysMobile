package com.hivemc.chunker.conversion.encoding.bedrock.v1_16.writer;

import com.hivemc.chunker.conversion.encoding.base.Converter;
import com.hivemc.chunker.conversion.encoding.base.Version;
import com.hivemc.chunker.conversion.encoding.bedrock.base.writer.BedrockWorldWriter;
import com.hivemc.chunker.conversion.encoding.bedrock.util.PositionTrackingDBUtil;
import com.hivemc.chunker.conversion.intermediate.column.chunk.itemstack.ChunkerLodestoneData;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class LevelWriter extends com.hivemc.chunker.conversion.encoding.bedrock.v1_14.writer.LevelWriter {
    public LevelWriter(File outputFolder, Version version, Converter converter) {
        super(outputFolder, version, converter);
    }

    @Override
    public synchronized @Nullable ChunkerLodestoneData getLodestoneData(int index) {
        try {
            return PositionTrackingDBUtil.getLodestoneData(database, converter.getDimensionRegistry(), index);
        } catch (Exception e) {
            converter.logNonFatalException(e);
        }
        return null; // Unable to fetch the data
    }

    @Override
    public synchronized int getOrCreateLodestoneData(ChunkerLodestoneData lodestoneData) {
        try {
            return PositionTrackingDBUtil.getOrCreateLodestoneData(database, converter.getDimensionRegistry(), lodestoneData);
        } catch (Exception e) {
            converter.logNonFatalException(e);
        }
        return -1; // Unable to create
    }

    @Override
    public BedrockWorldWriter createWorldWriter() {
        return new WorldWriter(outputFolder, converter, resolvers, database);
    }
}
