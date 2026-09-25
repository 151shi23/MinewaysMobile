package com.hivemc.chunker.conversion.encoding.java.v1_21_11.writer;

import com.hivemc.chunker.conversion.encoding.base.Converter;
import com.hivemc.chunker.conversion.encoding.base.Version;
import com.hivemc.chunker.conversion.encoding.java.base.writer.JavaWorldWriter;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.primitive.StringTag;

import java.io.File;

public class LevelWriter extends com.hivemc.chunker.conversion.encoding.java.v1_21_9.writer.LevelWriter {
    public LevelWriter(File outputFolder, Version version, Converter converter) {
        super(outputFolder, version, converter);
    }

    @Override
    protected void writeExtraLevelSettings(CompoundTag data) throws Exception {
        // Write the previous extra settings
        super.writeExtraLevelSettings(data);

        // Transform game rules to new registry format
        CompoundTag oldGameRules = (CompoundTag) data.remove("GameRules");
        CompoundTag newGameRules = data.getOrCreateCompound("game_rules");

        // Boolean game rules
        convertBooleanGameRule(oldGameRules, newGameRules, "announceAdvancements", "minecraft:show_advancement_messages");
        convertBooleanGameRule(oldGameRules, newGameRules, "commandBlockOutput", "minecraft:command_block_output");
        convertBooleanGameRule(oldGameRules, newGameRules, "doDaylightCycle", "minecraft:advance_time");
        convertBooleanGameRule(oldGameRules, newGameRules, "doEntityDrops", "minecraft:entity_drops");
        convertBooleanGameRule(oldGameRules, newGameRules, "doLimitedCrafting", "minecraft:limited_crafting");
        convertBooleanGameRule(oldGameRules, newGameRules, "doMobLoot", "minecraft:mob_drops");
        convertBooleanGameRule(oldGameRules, newGameRules, "doMobSpawning", "minecraft:spawn_mobs");
        convertBooleanGameRule(oldGameRules, newGameRules, "doTileDrops", "minecraft:block_drops");
        convertBooleanGameRule(oldGameRules, newGameRules, "doWeatherCycle", "minecraft:advance_weather");
        convertBooleanGameRule(oldGameRules, newGameRules, "keepInventory", "minecraft:keep_inventory");
        convertBooleanGameRule(oldGameRules, newGameRules, "logAdminCommands", "minecraft:log_admin_commands");
        convertBooleanGameRule(oldGameRules, newGameRules, "mobGriefing", "minecraft:mob_griefing");
        convertBooleanGameRule(oldGameRules, newGameRules, "naturalRegeneration", "minecraft:natural_health_regeneration");
        convertBooleanGameRule(oldGameRules, newGameRules, "reducedDebugInfo", "minecraft:reduced_debug_info");
        convertBooleanGameRule(oldGameRules, newGameRules, "sendCommandFeedback", "minecraft:send_command_feedback");
        convertBooleanGameRule(oldGameRules, newGameRules, "showDeathMessages", "minecraft:show_death_messages");
        convertBooleanGameRule(oldGameRules, newGameRules, "spectatorsGenerateChunks", "minecraft:spectators_generate_chunks");

        // Inverted boolean game rule
        convertInvertedBooleanGameRule(oldGameRules, newGameRules, "disableElytraMovementCheck", "minecraft:elytra_movement_check");

        // Special case for doFireTick (only write if false)
        if (oldGameRules.contains("doFireTick")) {
            if (oldGameRules.getString("doFireTick").equals("false")) {
                newGameRules.put("minecraft:fire_spread_radius_around_player", 0);
            }
            oldGameRules.remove("doFireTick");
        }

        // Integer game rules
        convertIntegerGameRule(oldGameRules, newGameRules, "maxEntityCramming", "minecraft:max_entity_cramming");
        convertIntegerGameRule(oldGameRules, newGameRules, "maxCommandChainLength", "minecraft:max_command_sequence_length");
        convertIntegerGameRule(oldGameRules, newGameRules, "randomTickSpeed", "minecraft:random_tick_speed");
        convertIntegerGameRule(oldGameRules, newGameRules, "spawnRadius", "minecraft:respawn_radius");
    }

    protected void convertBooleanGameRule(CompoundTag oldGameRules, CompoundTag newGameRules, String oldName, String newName) {
        Tag<?> oldTag = oldGameRules.remove(oldName);
        if (oldTag instanceof StringTag stringTag) {
            newGameRules.put(newName, Boolean.parseBoolean(stringTag.getValue()) ? (byte) 1 : (byte) 0);
        }
    }

    protected void convertInvertedBooleanGameRule(CompoundTag oldGameRules, CompoundTag newGameRules, String oldName, String newName) {
        Tag<?> oldTag = oldGameRules.remove(oldName);
        if (oldTag instanceof StringTag stringTag) {
            newGameRules.put(newName, Boolean.parseBoolean(stringTag.getValue()) ? (byte) 0 : (byte) 1);
        }
    }

    protected void convertIntegerGameRule(CompoundTag oldGameRules, CompoundTag newGameRules, String oldName, String newName) {
        Tag<?> oldTag = oldGameRules.remove(oldName);
        if (oldTag instanceof StringTag stringTag) {
            newGameRules.put(newName, Integer.parseInt(stringTag.getValue()));
        }
    }

    @Override
    public JavaWorldWriter createWorldWriter() {
        return new WorldWriter(outputFolder, converter, resolvers);
    }
}
