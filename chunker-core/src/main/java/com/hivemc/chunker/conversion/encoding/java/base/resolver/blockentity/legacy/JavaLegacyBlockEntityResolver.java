package com.hivemc.chunker.conversion.encoding.java.base.resolver.blockentity.legacy;

import com.hivemc.chunker.conversion.encoding.base.Version;
import com.hivemc.chunker.conversion.encoding.base.resolver.blockentity.BlockEntityResolver;
import com.hivemc.chunker.conversion.encoding.base.resolver.blockentity.EmptyBlockEntityHandler;
import com.hivemc.chunker.conversion.encoding.java.base.resolver.JavaResolvers;
import com.hivemc.chunker.conversion.encoding.java.base.resolver.blockentity.legacy.handlers.*;
import com.hivemc.chunker.conversion.intermediate.column.blockentity.BlockEntity;
import com.hivemc.chunker.conversion.intermediate.column.blockentity.DaylightDetectorBlockEntity;
import com.hivemc.chunker.conversion.intermediate.column.blockentity.container.EnderChestBlockEntity;
import com.hivemc.chunker.conversion.intermediate.column.blockentity.container.HopperBlockEntity;
import com.hivemc.chunker.conversion.intermediate.column.blockentity.container.randomizable.*;
import com.hivemc.chunker.conversion.intermediate.column.blockentity.end.EndPortalBlockEntity;
import com.hivemc.chunker.conversion.intermediate.column.chunk.identifier.ChunkerItemStackIdentifierType;
import com.hivemc.chunker.conversion.intermediate.column.chunk.identifier.type.block.ChunkerVanillaBlockType;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.util.InvertibleMap;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolver for converting legacy Java block entities between Chunker and NBT.
 */
public class JavaLegacyBlockEntityResolver extends BlockEntityResolver<JavaResolvers, CompoundTag> {
    /**
     * The pre-1.11 IDs used the identifiers of the block entities.
     */
    public static final InvertibleMap<String, String> OLD_TO_NEW_ID = InvertibleMap.create();

    static {
        OLD_TO_NEW_ID.put("Airportal", "minecraft:end_portal");
        OLD_TO_NEW_ID.put("Banner", "minecraft:banner");
        OLD_TO_NEW_ID.put("Bed", "minecraft:bed");
        OLD_TO_NEW_ID.put("Beacon", "minecraft:beacon");
        OLD_TO_NEW_ID.put("Cauldron", "minecraft:brewing_stand");
        OLD_TO_NEW_ID.put("TrappedChest", "minecraft:chest");
        OLD_TO_NEW_ID.put("Chest", "minecraft:chest");
        OLD_TO_NEW_ID.put("Comparator", "minecraft:comparator");
        OLD_TO_NEW_ID.put("Control", "minecraft:command_block");
        OLD_TO_NEW_ID.put("DLDetector", "minecraft:daylight_detector");
        OLD_TO_NEW_ID.put("Dropper", "minecraft:dropper");
        OLD_TO_NEW_ID.put("EnchantTable", "minecraft:enchanting_table");
        OLD_TO_NEW_ID.put("EndGateway", "minecraft:end_gateway");
        OLD_TO_NEW_ID.put("EnderChest", "minecraft:ender_chest");
        OLD_TO_NEW_ID.put("FlowerPot", "minecraft:flower_pot");
        OLD_TO_NEW_ID.put("Furnace", "minecraft:furnace");
        OLD_TO_NEW_ID.put("Hopper", "minecraft:hopper");
        OLD_TO_NEW_ID.put("MobSpawner", "minecraft:mob_spawner");
        OLD_TO_NEW_ID.put("Music", "minecraft:noteblock");
        OLD_TO_NEW_ID.put("Piston", "minecraft:piston");
        OLD_TO_NEW_ID.put("RecordPlayer", "minecraft:jukebox");
        OLD_TO_NEW_ID.put("Sign", "minecraft:sign");
        OLD_TO_NEW_ID.put("Skull", "minecraft:skull");
        OLD_TO_NEW_ID.put("Structure", "minecraft:structure_block");
        OLD_TO_NEW_ID.put("Trap", "minecraft:dispenser");
        OLD_TO_NEW_ID.put("Shulker", "minecraft:shulker_box");
    }

    /**
     * Create a new legacy java block entity resolver.
     *
     * @param version   the java version.
     * @param resolvers the resolvers to use.
     */
    public JavaLegacyBlockEntityResolver(Version version, JavaResolvers resolvers) {
        super(version, resolvers, resolvers.converter().shouldAllowNBTCopying());
    }

    @Override
    protected void registerTypeHandlers(Version version) {
        // Handlers which write/read abstract types which others use
        register(new JavaLegacyBlockEntityHandler());
        register(new JavaLegacyContainerBlockEntityHandler());
        register(new JavaLegacyRandomizableContainerBlockEntityHandler(resolvers.converter().shouldProcessLootTables()));

        // Cauldron isn't a block entity in Java
        register(new JavaLegacyCauldronBlockEntityHandler());

        // Block entities
        register(new EmptyBlockEntityHandler<>("Airportal", EndPortalBlockEntity.class, EndPortalBlockEntity::new));
        register(new JavaLegacyBannerBlockEntityHandler());
        register(new JavaLegacyBeaconBlockEntityHandler());
        register(new JavaLegacyBrewingStandBlockEntityHandler());
        register(new EmptyBlockEntityHandler<>("Chest", ChestBlockEntity.class, ChestBlockEntity::new));
        register(new JavaLegacyComparatorBlockEntityHandler());
        register(new JavaLegacyCommandBlockBlockEntityHandler());
        register(new EmptyBlockEntityHandler<>("DLDetector", DaylightDetectorBlockEntity.class, DaylightDetectorBlockEntity::new));
        register(new EmptyBlockEntityHandler<>("Dropper", DropperBlockEntity.class, DropperBlockEntity::new));
        register(new JavaLegacyEnchantmentBlockEntityHandler());
        register(new JavaLegacyEndGatewayBlockEntityHandler());
        register(new EmptyBlockEntityHandler<>("EnderChest", EnderChestBlockEntity.class, EnderChestBlockEntity::new));
        register(new JavaLegacyFurnaceBlockEntityHandler());
        register(new EmptyBlockEntityHandler<>("Hopper", HopperBlockEntity.class, HopperBlockEntity::new));
        register(new JavaLegacySpawnerBlockEntityHandler());
        register(new JavaLegacyPistonArmBlockEntityHandler());
        register(new JavaLegacyJukeboxBlockEntityHandler());
        register(new JavaLegacySignBlockEntityHandler());
        register(new JavaLegacySkullBlockEntityHandler());
        register(new JavaLegacyBedBlockEntityHandler(version.isLessThan(1, 12, 0)));
        register(new JavaLegacyFlowerPotBlockEntityHandler());
        register(new JavaLegacyNoteBlockBlockEntityHandler());
        register(new JavaLegacyStructureBlockEntityHandler());
        register(new EmptyBlockEntityHandler<>("Trap", DispenserBlockEntity.class, DispenserBlockEntity::new));

        // Chest handler
        register(new JavaLegacyChestBlockEntityHandler()); // Also splits into TrappedChest (which has an empty handler below)
        register(new EmptyBlockEntityHandler<>("TrappedChest", TrappedChestBlockEntity.class, () -> {
            throw new IllegalArgumentException("Cannot make TrappedChest");
        }));

        // Shulker is added in 1.12
        if (version.isGreaterThanOrEqual(1, 12, 0)) {
            register(new EmptyBlockEntityHandler<>("Shulker", ShulkerBoxBlockEntity.class, ShulkerBoxBlockEntity::new));
        }
    }

    @Override
    protected CompoundTag constructDataType(String key) {
        // Create a new compoundTag with the ID
        CompoundTag compoundTag = new CompoundTag(4);

        // Newer versions use a namespaced key
        if (version.isGreaterThanOrEqual(1, 11, 0)) {
            key = Objects.requireNonNull(OLD_TO_NEW_ID.forward().get(key));
        }
        compoundTag.put("id", key);
        return compoundTag;
    }

    @Override
    public Optional<String> getKey(CompoundTag input) {
        return input.getOptionalValue("id", String.class).flatMap(id -> {
            // Newer versions use a namespaced key
            if (version.isGreaterThanOrEqual(1, 11, 0)) {
                // Ensure prefix of minecraft:
                if (!id.contains(":")) {
                    id = "minecraft:" + id;
                }
                return Optional.ofNullable(OLD_TO_NEW_ID.inverse().get(id));
            } else {
                // In rare occasions worlds contain semi-upgraded block-entity names, so if we know it's a newer name
                // Use the older name
                id = OLD_TO_NEW_ID.inverse().getOrDefault(id, id);

                // Return the ID
                return Optional.ofNullable(id);
            }
        });
    }

    @Override
    public Optional<Class<? extends BlockEntity>> getBlockEntityClass(ChunkerItemStackIdentifierType itemStackType) {
        // Legacy Java doesn't have trapped chest
        if (itemStackType == ChunkerVanillaBlockType.TRAPPED_CHEST) {
            return Optional.of(ChestBlockEntity.class);
        }
        return super.getBlockEntityClass(itemStackType);
    }
}
