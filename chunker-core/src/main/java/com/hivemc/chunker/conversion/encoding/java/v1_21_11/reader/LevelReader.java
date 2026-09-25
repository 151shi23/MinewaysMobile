package com.hivemc.chunker.conversion.encoding.java.v1_21_11.reader;

import com.hivemc.chunker.conversion.encoding.base.Converter;
import com.hivemc.chunker.conversion.encoding.base.Version;
import com.hivemc.chunker.conversion.encoding.java.base.reader.JavaWorldReader;
import com.hivemc.chunker.conversion.intermediate.world.Dimension;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.primitive.ByteTag;
import com.hivemc.chunker.nbt.tags.primitive.IntTag;

import java.io.File;

public class LevelReader extends com.hivemc.chunker.conversion.encoding.java.v1_21_9.reader.LevelReader {
    public LevelReader(File inputDirectory, Version inputVersion, Converter converter) {
        super(inputDirectory, inputVersion, converter);
    }

    @Override
    protected CompoundTag prepareNBTForLevelSettings(CompoundTag level) throws Exception {
        // Make a copy (this ensures that we don't overwrite the original
        level = level.clone();

        // Extract the new game_rules
        if (level.contains("game_rules")) {
            // Transform all the Spawn settings to the old names so we can parse it
            CompoundTag gameRules = (CompoundTag) level.remove("game_rules");
            level.put("GameRules", gameRules);

            // Boolean game rules
            convertBooleanGameRule(gameRules, "announceAdvancements", "minecraft:show_advancement_messages");
            convertBooleanGameRule(gameRules, "commandBlockOutput", "minecraft:command_block_output");
            convertBooleanGameRule(gameRules, "doDaylightCycle", "minecraft:advance_time");
            convertBooleanGameRule(gameRules, "doEntityDrops", "minecraft:entity_drops");
            convertBooleanGameRule(gameRules, "doLimitedCrafting", "minecraft:limited_crafting");
            convertBooleanGameRule(gameRules, "doMobLoot", "minecraft:mob_drops");
            convertBooleanGameRule(gameRules, "doMobSpawning", "minecraft:spawn_mobs");
            convertBooleanGameRule(gameRules, "doTileDrops", "minecraft:block_drops");
            convertBooleanGameRule(gameRules, "doWeatherCycle", "minecraft:advance_weather");
            convertBooleanGameRule(gameRules, "keepInventory", "minecraft:keep_inventory");
            convertBooleanGameRule(gameRules, "logAdminCommands", "minecraft:log_admin_commands");
            convertBooleanGameRule(gameRules, "mobGriefing", "minecraft:mob_griefing");
            convertBooleanGameRule(gameRules, "naturalRegeneration", "minecraft:natural_health_regeneration");
            convertBooleanGameRule(gameRules, "reducedDebugInfo", "minecraft:reduced_debug_info");
            convertBooleanGameRule(gameRules, "sendCommandFeedback", "minecraft:send_command_feedback");
            convertBooleanGameRule(gameRules, "showDeathMessages", "minecraft:show_death_messages");
            convertBooleanGameRule(gameRules, "spectatorsGenerateChunks", "minecraft:spectators_generate_chunks");

            // Inverted boolean game rule
            convertInvertedBooleanGameRule(gameRules, "disableElytraMovementCheck", "minecraft:elytra_movement_check");

            // Special case for doFireTick
            if (gameRules.contains("minecraft:fire_spread_radius_around_player")) {
                IntTag tag = (IntTag) gameRules.remove("minecraft:fire_spread_radius_around_player");
                gameRules.put("doFireTick", tag.getValue() == 0 ? "false" : "true");
            } else {
                gameRules.put("doFireTick", "true");
            }

            // Integer game rules
            convertIntegerGameRule(gameRules, "maxEntityCramming", "minecraft:max_entity_cramming");
            convertIntegerGameRule(gameRules, "maxCommandChainLength", "minecraft:max_command_sequence_length");
            convertIntegerGameRule(gameRules, "randomTickSpeed", "minecraft:random_tick_speed");
            convertIntegerGameRule(gameRules, "spawnRadius", "minecraft:respawn_radius");
        }

        // Call the super
        return super.prepareNBTForLevelSettings(level);
    }

    protected void convertBooleanGameRule(CompoundTag gameRules, String oldName, String newName) {
        Tag<?> oldTag = gameRules.remove(newName);
        if (oldTag instanceof ByteTag byteTag) {
            gameRules.put(oldName, byteTag.getValue() == (byte) 1 ? "true" : "false");
        }
    }

    protected void convertInvertedBooleanGameRule(CompoundTag gameRules, String oldName, String newName) {
        Tag<?> oldTag = gameRules.remove(newName);
        if (oldTag instanceof ByteTag byteTag) {
            gameRules.put(oldName, byteTag.getValue() == (byte) 1 ? "false" : "true");
        }
    }

    protected void convertIntegerGameRule(CompoundTag gameRules, String oldName, String newName) {
        Tag<?> oldTag = gameRules.remove(newName);
        if (oldTag instanceof IntTag intTag) {
            gameRules.put(oldName, String.valueOf(intTag.getValue()));
        }
    }

    @Override
    public JavaWorldReader createWorldReader(File dimensionFolder, Dimension dimension) {
        return new WorldReader(converter, resolvers, dimensionFolder, dimension);
    }
}
