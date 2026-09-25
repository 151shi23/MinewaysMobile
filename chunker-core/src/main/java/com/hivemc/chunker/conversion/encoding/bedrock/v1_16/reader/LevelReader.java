package com.hivemc.chunker.conversion.encoding.bedrock.v1_16.reader;

import com.hivemc.chunker.conversion.encoding.base.Converter;
import com.hivemc.chunker.conversion.encoding.base.Version;
import com.hivemc.chunker.conversion.encoding.bedrock.base.reader.BedrockWorldReader;
import com.hivemc.chunker.conversion.encoding.bedrock.util.PositionTrackingDBUtil;
import com.hivemc.chunker.conversion.intermediate.column.chunk.ChunkCoordPair;
import com.hivemc.chunker.conversion.intermediate.column.chunk.RegionCoordPair;
import com.hivemc.chunker.conversion.intermediate.column.chunk.itemstack.ChunkerLodestoneData;
import com.hivemc.chunker.conversion.intermediate.world.Dimension;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.Set;

public class LevelReader extends com.hivemc.chunker.conversion.encoding.bedrock.v1_14.reader.LevelReader {
    public LevelReader(File inputDirectory, Version inputVersion, Converter converter) {
        super(inputDirectory, inputVersion, converter);
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
    public BedrockWorldReader createWorldReader(Map<RegionCoordPair, Set<ChunkCoordPair>> presentRegions, Dimension dimension) {
        return new WorldReader(resolvers, converter, database, presentRegions, dimension);
    }
}
