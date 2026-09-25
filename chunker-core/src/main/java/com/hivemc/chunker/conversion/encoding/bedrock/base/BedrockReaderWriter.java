package com.hivemc.chunker.conversion.encoding.bedrock.base;

import com.hivemc.chunker.conversion.encoding.EncodingType;
import com.hivemc.chunker.conversion.encoding.base.Converter;
import com.hivemc.chunker.conversion.encoding.base.LevelReaderWriter;
import com.hivemc.chunker.conversion.encoding.base.Version;
import com.hivemc.chunker.conversion.encoding.bedrock.base.reader.pretransform.BedrockReaderPreTransformManager;
import com.hivemc.chunker.conversion.encoding.bedrock.base.resolver.BedrockResolversBuilder;
import com.hivemc.chunker.conversion.encoding.bedrock.base.resolver.biome.BedrockBiomeIDResolver;
import com.hivemc.chunker.conversion.encoding.bedrock.base.resolver.blockentity.BedrockBlockEntityResolver;
import com.hivemc.chunker.conversion.encoding.bedrock.base.resolver.entity.BedrockEntityResolver;
import com.hivemc.chunker.conversion.encoding.bedrock.base.resolver.entity.BedrockEntityTypeResolver;
import com.hivemc.chunker.conversion.encoding.bedrock.base.resolver.entity.BedrockPaintingMotiveResolver;
import com.hivemc.chunker.conversion.encoding.bedrock.base.resolver.identifier.BedrockBlockIdentifierResolver;
import com.hivemc.chunker.conversion.encoding.bedrock.base.resolver.identifier.BedrockItemIdentifierResolver;
import com.hivemc.chunker.conversion.encoding.bedrock.base.resolver.identifier.legacy.BedrockLegacyBlockIdentifierResolver;
import com.hivemc.chunker.conversion.encoding.bedrock.base.resolver.identifier.legacy.BedrockNBTLegacyBlockIdentifierResolver;
import com.hivemc.chunker.conversion.encoding.bedrock.base.resolver.itemstack.*;
import com.hivemc.chunker.conversion.encoding.bedrock.base.resolver.itemstack.legacy.BedrockLegacyItemStackResolver;
import com.hivemc.chunker.conversion.encoding.bedrock.base.writer.pretransform.BedrockWriterPreTransformManager;
import com.hivemc.chunker.conversion.intermediate.column.chunk.itemstack.ChunkerLodestoneData;
import org.jetbrains.annotations.Nullable;

/**
 * A Bedrock Level Reader / Writer, used to share resolvers.
 */
public interface BedrockReaderWriter extends LevelReaderWriter {
    @Override
    default EncodingType getEncodingType() {
        return EncodingType.BEDROCK;
    }

    /**
     * Get or create an index for lodestone data.
     *
     * @param lodestoneData the lodestone data to add the pool.
     * @return the index or -1 if it was not possible to create the data.
     */
    int getOrCreateLodestoneData(ChunkerLodestoneData lodestoneData);

    /**
     * Get the lodestone data from an index.
     *
     * @param index the index to lookup.
     * @return the lodestone data or null if the index wasn't found.
     */
    @Nullable
    ChunkerLodestoneData getLodestoneData(int index);

    /**
     * Build the resolvers which should be used for this version.
     *
     * @param converter the instance of the converter.
     * @return a builder so that this method can be overridden to update the resolvers to use.
     */
    default BedrockResolversBuilder buildResolvers(Converter converter) {
        Version version = getVersion();
        return new BedrockResolversBuilder(converter, version, true)
                .potionIDResolver(new BedrockPotionIDResolver(version))
                .entityTypeResolver(new BedrockEntityTypeResolver(version, converter.shouldAllowCustomIdentifiers()))
                .effectIDResolver(new BedrockEffectIDResolver(version))
                .nbtBlockIdentifierResolver(new BedrockNBTLegacyBlockIdentifierResolver(version))
                .blockIdentifierResolver(new BedrockLegacyBlockIdentifierResolver(converter, version, isReader(), converter.shouldAllowCustomIdentifiers()))
                .itemBlockIdentifierResolver(new BedrockBlockIdentifierResolver(converter, version, isReader(), converter.shouldAllowCustomIdentifiers()))
                .itemIdentifierResolver(new BedrockItemIdentifierResolver(converter, version, isReader()))
                .biomeIDResolver(new BedrockBiomeIDResolver(version, converter.shouldAllowCustomIdentifiers()))
                .enchantmentIDResolver(new BedrockEnchantmentIDResolver(version))
                .paintingMotiveResolver(new BedrockPaintingMotiveResolver(version))
                .trimPatternResolver(new BedrockTrimPatternResolver(version))
                .trimMaterialResolver(new BedrockTrimMaterialResolver(version))
                .bannerPatternResolver(new BedrockBannerPatternResolver(version))
                .itemStackResolverConstructor(BedrockLegacyItemStackResolver::new)
                .blockEntityResolverConstructor((resolvers) -> new BedrockBlockEntityResolver(version, resolvers))
                .entityResolverConstructor((resolvers) -> new BedrockEntityResolver(version, resolvers))
                .preTransformManager(isReader() ? new BedrockReaderPreTransformManager(version) : new BedrockWriterPreTransformManager(version))
                .lodestoneData(this::getLodestoneData, this::getOrCreateLodestoneData);
    }
}
