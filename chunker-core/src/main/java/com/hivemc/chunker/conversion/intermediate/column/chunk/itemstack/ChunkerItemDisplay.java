package com.hivemc.chunker.conversion.intermediate.column.chunk.itemstack;


import com.hivemc.chunker.util.ChunkerColor;
import com.google.gson.JsonElement;
import com.hivemc.chunker.util.ChunkerColor;
import org.jetbrains.annotations.Nullable;

import com.hivemc.chunker.util.ChunkerColor;
import java.util.List;

/**
 * Display properties for item.
 *
 * @param displayName the custom name of the item, null if absent.
 * @param lore        the lore for the item, null if absent.
 * @param color       the color of the item text, null if absent.
 */
public record ChunkerItemDisplay(@Nullable JsonElement displayName, @Nullable List<JsonElement> lore,
                                 @Nullable ChunkerColor color) {
}
