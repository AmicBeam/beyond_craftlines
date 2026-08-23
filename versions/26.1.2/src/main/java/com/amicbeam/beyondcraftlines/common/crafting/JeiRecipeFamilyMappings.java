package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.LinkedHashSet;
import java.util.Set;

/** Pure, mod-agnostic mapping from JEI category ids to loaded server recipe type ids. */
public final class JeiRecipeFamilyMappings
{
    private JeiRecipeFamilyMappings() {}

    private static String serverFamily(String jeiType)
    {
        return switch (jeiType)
        {
            case "minecraft:furnace" -> "smelting";
            case "minecraft:campfire" -> "campfire_cooking";
            // Create calls its compacting JEI category "packing", while the recipes are backed
            // by the create:compacting server RecipeType. The Basin is a catalyst for both this
            // category and create:mixing, so omitting the alias silently drops compacting when a
            // Basin is scanned.
            case "create:packing" -> "create:compacting";
            case "mekanism:crusher" -> "mekanism:crushing";
            case "mekanism:enrichment_chamber" -> "mekanism:enriching";
            case "mekanism:energized_smelter" -> "mekanism:smelting";
            case "mekanism:chemical_infuser" -> "mekanism:chemical_infusing";
            case "mekanism:combiner" -> "mekanism:combining";
            case "mekanism:electrolytic_separator" -> "mekanism:separating";
            case "mekanism:chemical_washer" -> "mekanism:washing";
            case "mekanism:thermal_evaporation_controller" -> "mekanism:evaporating";
            case "mekanism:solar_neutron_activator" -> "mekanism:activating";
            case "mekanism:isotopic_centrifuge" -> "mekanism:centrifuging";
            case "mekanism:chemical_crystallizer" -> "mekanism:crystallizing";
            case "mekanism:chemical_dissolution_chamber" -> "mekanism:dissolution";
            case "mekanism:osmium_compressor" -> "mekanism:compressing";
            case "mekanism:purification_chamber" -> "mekanism:purifying";
            case "mekanism:chemical_injection_chamber" -> "mekanism:injecting";
            case "mekanism:antiprotonic_nucleosynthesizer" -> "mekanism:nucleosynthesizing";
            case "mekanism:chemical_oxidizer" -> "mekanism:oxidizing";
            case "mekanism:pigment_extractor" -> "mekanism:pigment_extracting";
            case "mekanism:pigment_mixer" -> "mekanism:pigment_mixing";
            case "mekanism:metallurgic_infuser" -> "mekanism:metallurgic_infusing";
            case "mekanism:painting_machine" -> "mekanism:painting";
            case "mekanism:pressurized_reaction_chamber" -> "mekanism:reaction";
            case "mekanism:precision_sawmill" -> "mekanism:sawing";
            // Mekanism exposes the two rotary directions as separate JEI categories,
            // while both are backed by the same server-side RecipeType.
            case "mekanism:condensentrating", "mekanism:decondensentrating" -> "mekanism:rotary";
            // Ars Nouveau names the JEI category after its apparatus while the
            // corresponding server recipe type is named after the process.
            case "ars_nouveau:enchantment_apparatus" -> "ars_nouveau:enchantment";
            default -> jeiType.startsWith("minecraft:")
                    ? jeiType.substring("minecraft:".length()) : jeiType;
        };
    }

    public static Resolution resolve(Set<String> jeiTypes, Set<String> loadedFamilies)
    {
        LinkedHashSet<String> acceptedTypes = new LinkedHashSet<>();
        LinkedHashSet<String> families = new LinkedHashSet<>();
        for (String jeiType : jeiTypes)
        {
            String family = serverFamily(jeiType);
            if (!loadedFamilies.contains(family)) continue;
            acceptedTypes.add(jeiType);
            families.add(family);
        }
        return new Resolution(Set.copyOf(acceptedTypes), Set.copyOf(families));
    }

    public record Resolution(Set<String> jeiTypes, Set<String> families)
    {
        public Resolution
        {
            jeiTypes = Set.copyOf(jeiTypes);
            families = Set.copyOf(families);
        }

        public boolean isEmpty() { return families.isEmpty(); }
    }
}
