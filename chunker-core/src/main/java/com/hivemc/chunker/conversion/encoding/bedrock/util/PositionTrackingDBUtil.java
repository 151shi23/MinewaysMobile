package com.hivemc.chunker.conversion.encoding.bedrock.util;

import com.hivemc.chunker.conversion.intermediate.column.chunk.itemstack.ChunkerLodestoneData;
import com.hivemc.chunker.conversion.intermediate.world.Dimension;
import com.hivemc.chunker.conversion.intermediate.world.DimensionRegistry;
import com.hivemc.chunker.nbt.TagType;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.collection.ListTag;
import com.hivemc.chunker.nbt.tags.primitive.IntTag;
import org.iq80.leveldb.DB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for using the "PositionTrackDB" used for lodestones in Bedrock.
 */
public class PositionTrackingDBUtil {
    /**
     * Get lodestone data from a database based on index.
     * Note: Any operations may need locking if using this multithreaded.
     *
     * @param database          the LevelDB database.
     * @param dimensionRegistry the Dimension registry
     * @param index             the index of the data (starting at 1).
     * @return the value or null if it wasn't found.
     * @throws IOException if it failed to parse the data from LevelDB.
     */
    @Nullable
    public static ChunkerLodestoneData getLodestoneData(DB database, @NotNull DimensionRegistry dimensionRegistry, int index) throws IOException {
        // Format is PosTrackDB-0x00000001
        byte[] key = LevelDBKey.key(LevelDBKey.POS_TRACK_DB, String.format("%08x", index).getBytes(StandardCharsets.UTF_8));
        byte[] bytes = database.get(key);
        if (bytes == null) return null; // Not found

        // Parse the NBT
        CompoundTag entry = Objects.requireNonNull(Tag.readBedrockNBT(bytes));
        List<Integer> position = entry.getListValues("pos", IntTag.class, null);
        if (position == null || position.size() < 3) return null; // Not valid as it doesn't have a position

        return new ChunkerLodestoneData(
                dimensionRegistry.fromBedrockNBT(entry.get("dim"), Dimension.OVERWORLD),
                position.get(0),
                position.get(1),
                position.get(2),
                entry.getByte("status") == 0 // Bedrock doesn't have the concept of tracking but status 1 seems to be pending update
        );
    }

    /**
     * Set the lodestone data from a database based on index.
     * Note: Any operations may need locking if using this multithreaded.
     *
     * @param database the LevelDB database.
     * @param index    the index of the data (starting at 1).
     * @param data     the value to set.
     * @throws IOException if it failed to serialize the data to LevelDB.
     */
    public static void setLodestoneData(DB database, int index, ChunkerLodestoneData data) throws IOException {
        String id = String.format("%08x", index);

        // Create the NBT
        CompoundTag tag = new CompoundTag();
        tag.put("dim", data.dimension().getBedrockID());
        tag.put("id", "0x" + id);
        tag.put("pos", ListTag.fromValues(TagType.INT, List.of(
                data.x(),
                data.y(),
                data.z()
        )));
        tag.put("status", data.tracked() ? (byte) 0 : (byte) 1);
        tag.put("version", (byte) 1);

        // Set the key
        byte[] key = LevelDBKey.key(LevelDBKey.POS_TRACK_DB, id.getBytes(StandardCharsets.UTF_8));
        byte[] value = Tag.writeBedrockNBT(tag);
        database.put(key, value);
    }

    /**
     * Get the number of lodestone data entries.
     * Note: Any operations may need locking if using this multithreaded.
     *
     * @param database the LevelDB database.
     * @return the number of lodestone data entries, 0 if there are no entries.
     * @throws IOException if it failed to parse the data from LevelDB.
     */
    public static int getLodestoneDataCount(DB database) throws IOException {
        byte[] bytes = database.get(LevelDBKey.POS_TRACK_DB_LAST_ID);
        if (bytes == null) return 0; // Not found

        // Parse the NBT
        CompoundTag entry = Objects.requireNonNull(Tag.readBedrockNBT(bytes));
        String hex = entry.getString("id", "0x0000000").substring(2);
        return Integer.parseUnsignedInt(hex, 16);
    }

    /**
     * Set the number of lodestone data entries.
     * Note: Any operations may need locking if using this multithreaded.
     *
     * @param database the LevelDB database.
     * @param count    the new number of entries.
     * @throws IOException if it failed to serialize the data to LevelDB.
     */
    public static void setLodestoneDataCount(DB database, int count) throws IOException {
        String id = String.format("%08x", count);

        // Create the NBT
        CompoundTag tag = new CompoundTag();
        tag.put("id", "0x" + id);
        tag.put("version", (byte) 1);

        // Set the key
        byte[] value = Tag.writeBedrockNBT(tag);
        database.put(LevelDBKey.POS_TRACK_DB_LAST_ID, value);
    }

    /**
     * Get or create the lodestone data in the database.
     * Note: Any operations may need locking if using this multithreaded.
     *
     * @param database          the LevelDB database.
     * @param dimensionRegistry the Dimension registry
     * @param lodestoneData     the lodestone data to find/save.
     * @return the new index of the entry (starting at 1) or existing index if it was found.
     * @throws IOException if it failed to deserialize/serialize the data to LevelDB.
     */
    public static int getOrCreateLodestoneData(DB database, @NotNull DimensionRegistry dimensionRegistry, ChunkerLodestoneData lodestoneData) throws IOException {
        // Loop through all the current lodestone data to see if it already exists
        int count = getLodestoneDataCount(database);
        for (int i = 1; i <= count; i++) {
            ChunkerLodestoneData chunkerLodestoneData = getLodestoneData(database, dimensionRegistry, i);
            if (chunkerLodestoneData != null && chunkerLodestoneData.equals(lodestoneData)) {
                return i; // This index matches
            }
        }

        // Add the new value
        count++;
        setLodestoneData(database, count, lodestoneData);

        // Increment the counter
        setLodestoneDataCount(database, count);

        // Return the new index
        return count;
    }
}
