package com.amicbeam.beyondcraftlines.common.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public record BindingRecord(
        UUID id,
        UUID owner,
        int networkId,
        ResourceKey<Level> dimension,
        BlockPos position,
        DeviceType deviceType,
        Set<Identifier> jeiRecipeTypes,
        Set<String> recipeFamilies,
        Map<String, Set<String>> provisionerInputGroups,
        Identifier lastBlockId,
        ResourceKey<Level> provisionerDimension,
        BlockPos provisionerPosition,
        String nickname,
        boolean favorite,
        long boundGameTime
) {
    public static final String ALL_INPUT_GROUPS =
            com.amicbeam.beyondcraftlines.common.crafting.ProvisionerInputGroupSelection.ALL;

    public BindingRecord {
        jeiRecipeTypes = Set.copyOf(jeiRecipeTypes);
        recipeFamilies = Set.copyOf(recipeFamilies);
        HashMap<String, Set<String>> groups = new HashMap<>();
        provisionerInputGroups.forEach((family, values) -> groups.put(family, Set.copyOf(values)));
        provisionerInputGroups = Map.copyOf(groups);
    }

    public boolean acceptsInputGroup(String family, String group)
    {
        Set<String> accepted = provisionerInputGroups.get(family);
        if (accepted == null && deviceType == DeviceType.EXTERNAL_RECIPE_MACHINE)
            return recipeFamilies.contains(family);
        return accepted != null && (accepted.contains(ALL_INPUT_GROUPS) || accepted.contains(group));
    }

    public boolean acceptsAnyInputGroup(String family)
    {
        Set<String> accepted = provisionerInputGroups.get(family);
        if (accepted == null && deviceType == DeviceType.EXTERNAL_RECIPE_MACHINE)
            return recipeFamilies.contains(family);
        return accepted != null && !accepted.isEmpty();
    }

    public int inputGroupRoutingPriority(String family, String group)
    {
        Set<String> accepted = provisionerInputGroups.get(family);
        if (accepted == null && deviceType == DeviceType.EXTERNAL_RECIPE_MACHINE)
            accepted = Set.of(ALL_INPUT_GROUPS);
        return accepted == null
                ? com.amicbeam.beyondcraftlines.common.crafting.ProvisionerInputGroupSelection.REJECTED_PRIORITY
                : com.amicbeam.beyondcraftlines.common.crafting.ProvisionerInputGroupSelection
                .routingPriority(accepted, group);
    }
}
