package com.amicbeam.beyondcraftlines.common.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.UUID;

public record BindingRecord(
        UUID id,
        UUID owner,
        int networkId,
        ResourceKey<Level> dimension,
        BlockPos position,
        DeviceType deviceType,
        Set<ResourceLocation> jeiRecipeTypes,
        Set<String> recipeFamilies,
        ResourceLocation lastBlockId,
        ResourceKey<Level> provisionerDimension,
        BlockPos provisionerPosition,
        String nickname,
        boolean favorite,
        long boundGameTime
) {
    public BindingRecord {
        jeiRecipeTypes = Set.copyOf(jeiRecipeTypes);
        recipeFamilies = Set.copyOf(recipeFamilies);
    }
}
