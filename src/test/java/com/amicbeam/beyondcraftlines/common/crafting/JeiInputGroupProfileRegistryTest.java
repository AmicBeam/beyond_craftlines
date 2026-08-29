package com.amicbeam.beyondcraftlines.common.crafting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class JeiInputGroupProfileRegistryTest
{
    @Test
    void resolvesSingleAndCollectionSectionsWithoutCoordinates()
    {
        var profile = JeiInputGroupProfileRegistry.parse(JsonParser.parseString("""
                {
                  "jei_type": "test:apparatus",
                  "recipe_classes": [
                    "com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupProfileRegistryTest$ArsFixture"
                  ],
                  "input_sections": [
                    {"group":"reagent","members":["reagent"],"cardinality":"single"},
                    {"group":"pedestal_items","members":["pedestalItems"],"cardinality":"collection"}
                  ]
                }
                """).getAsJsonObject());

        assertEquals(List.of("reagent", "pedestal_items", "pedestal_items"),
                JeiInputGroupProfileRegistry.resolve(profile, new ArsFixture(), 3));
        assertEquals(List.of(), JeiInputGroupProfileRegistry.resolve(profile, new ArsFixture(), 4));
    }

    @Test
    void acceptsAccessorAndCrossVersionMemberAliases()
    {
        var profile = JeiInputGroupProfileRegistry.parse(JsonParser.parseString("""
                {
                  "jei_type": "test:infusion",
                  "recipe_classes": [
                    "com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupProfileRegistryTest$MalumFixture"
                  ],
                  "input_sections": [
                    {"group":"spirits","members":["spirits"],"cardinality":"collection"},
                    {"group":"extra_items","members":["extraInputs","extraItems"],"cardinality":"collection"},
                    {"group":"input","members":["input"],"cardinality":"single"}
                  ]
                }
                """).getAsJsonObject());

        assertEquals(List.of("spirits", "spirits", "extra_items", "input"),
                JeiInputGroupProfileRegistry.resolve(profile, new MalumFixture(), 4));
    }

    @Test
    void shipsArsNouveauAndMalumProfiles()
    {
        var ars = read("ars_nouveau.json");
        assertEquals("ars_nouveau:enchanting_apparatus", ars.jeiType());
        assertEquals(java.util.Set.of(
                        "com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe",
                        "com.hollingsworth.arsnouveau.api.enchanting_apparatus.EnchantingApparatusRecipe"),
                ars.recipeClasses());
        assertEquals(List.of("reagent", "pedestal_items"),
                ars.sections().stream().map(JeiInputGroupProfileRegistry.Section::group).toList());

        var malum = read("malum.json");
        assertEquals("malum:spirit_infusion", malum.jeiType());
        assertEquals(List.of("spirits", "extra_items", "input"),
                malum.sections().stream().map(JeiInputGroupProfileRegistry.Section::group).toList());
        assertEquals(java.util.Set.of("extraInputs", "extraItems"),
                java.util.Set.copyOf(malum.sections().get(1).members()));
    }

    private static JeiInputGroupProfileRegistry.Profile read(String name)
    {
        var stream = JeiInputGroupProfileRegistryTest.class.getResourceAsStream(
                "/assets/beyond_craftlines/jei_input_group_profiles/" + name);
        assertNotNull(stream);
        JsonObject object = JsonParser.parseReader(new InputStreamReader(
                stream, StandardCharsets.UTF_8)).getAsJsonObject();
        var profile = JeiInputGroupProfileRegistry.parse(object);
        assertNotNull(profile);
        return profile;
    }

    public static final class ArsFixture
    {
        public final Object reagent = new Object();
        public final List<Object> pedestalItems = List.of(new Object(), new Object());
    }

    public static final class MalumFixture
    {
        public List<Object> spirits() { return List.of(new Object(), new Object()); }
        public List<Object> extraInputs() { return List.of(new Object()); }
        public Object input() { return new Object(); }
    }
}
