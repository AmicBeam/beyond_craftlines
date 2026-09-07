package com.amicbeam.beyondcraftlines.common.crafting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void shipsSupportedSemanticInputGroupProfiles()
    {
        var ars = read("ars_nouveau.json");
        assertEquals("ars_nouveau:enchanting_apparatus", ars.jeiType());
        assertEquals(java.util.Set.of(
                        "com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe",
                        "com.hollingsworth.arsnouveau.api.enchanting_apparatus.EnchantingApparatusRecipe"),
                ars.recipeClasses());
        assertEquals(List.of("reagent", "pedestal_items"),
                ars.sections().stream().map(JeiInputGroupProfileRegistry.Section::group).toList());

        var goety = read("goety.json");
        assertEquals("goety:ritual", goety.jeiType());
        assertTrue(goety.matchesType("goety:ritual_craft"));
        org.junit.jupiter.api.Assertions.assertFalse(goety.matchesType("goety:cauldron"));
        assertTrue(goety.recipeClasses().isEmpty());
        assertEquals(List.of("activation_item", "offerings"),
                goety.sections().stream().map(JeiInputGroupProfileRegistry.Section::group).toList());
        assertEquals(java.util.Set.of("getActivationItem", "activationItem"),
                java.util.Set.copyOf(goety.sections().get(0).members()));
        assertEquals(java.util.Set.of("getIngredients", "ingredients"),
                java.util.Set.copyOf(goety.sections().get(1).members()));
        assertEquals(List.of("activation_item", "offerings", "offerings"),
                JeiInputGroupProfileRegistry.resolve(goety, new GoetyFixture(), 3));

        var skylogistics = read("skylogistics.json");
        assertEquals("skylogistics:sky_offering", skylogistics.jeiType());
        assertEquals(java.util.Set.of("com.skylogistics.recipe.OfferingRecipe"),
                skylogistics.recipeClasses());
        assertEquals(List.of("main_offering", "offerings"),
                skylogistics.sections().stream().map(JeiInputGroupProfileRegistry.Section::group).toList());
        assertEquals(List.of("main"), skylogistics.sections().get(0).members());
        assertEquals(List.of("offerings"), skylogistics.sections().get(1).members());

        var malum = read("malum.json");
        assertEquals("malum:spirit_infusion", malum.jeiType());
        assertEquals(List.of("spirits", "extra_items", "input"),
                malum.sections().stream().map(JeiInputGroupProfileRegistry.Section::group).toList());
        assertEquals(java.util.Set.of("extraInputs", "extraItems"),
                java.util.Set.copyOf(malum.sections().get(1).members()));
    }

    @Test
    void sourceBackedProfilesFollowJeiCreationOrder()
    {
        var petals = read("botania_petals.json");
        assertEquals("botania:petals", petals.jeiType());
        assertEquals(List.of("water", "reagent", "ingredients", "ingredients"),
                resolveFixture(petals, new GroupFixture(), 4));
        assertEquals(List.of("livingrock", "ingredients", "ingredients"),
                resolveFixture(read("botania_runic_altar.json"), new GroupFixture(), 3));
        assertEquals(List.of("center", "ingredients", "ingredients"),
                resolveFixture(read("embers_alchemy.json"), new GroupFixture(), 3));
        assertEquals(List.of("activation_item", "offerings", "offerings"),
                resolveFixture(read("occultism_ritual.json"), new GroupFixture(), 3));
        assertEquals(List.of("mercury", "sulfur", "salt"),
                resolveFixture(read("theurgy_incubation.json"), new GroupFixture(), 3));
        assertEquals(List.of("ingredients", "ingredients", "input_fluid"),
                resolveFixture(read("industrialforegoing_dissolution.json"), new GroupFixture(), 3));
        assertEquals(List.of(), resolveFixture(petals, new GroupFixture(), 3));
    }

    private static List<String> resolveFixture(JeiInputGroupProfileRegistry.Profile source, Object fixture, int slots)
    {
        return JeiInputGroupProfileRegistry.resolve(new JeiInputGroupProfileRegistry.Profile(source.jeiType(),
                java.util.Set.of(fixture.getClass().getName()), source.sections()), fixture, slots);
    }

    public static final class GroupFixture
    {
        public Object getReagent() { return new Object(); }
        public Object getCenterInput() { return new Object(); }
        public Object getActivationItem() { return new Object(); }
        public Object getMercury() { return new Object(); }
        public Object getSulfur() { return new Object(); }
        public Object getSalt() { return new Object(); }
        public List<Object> getIngredients() { return List.of(new Object(), new Object()); }
        public List<Object> getInputs() { return getIngredients(); }
        public final Object[] input = {new Object(), new Object()};
        public final Object inputFluid = new Object();
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

    public static final class GoetyFixture
    {
        public Object getActivationItem() { return new Object(); }
        public List<Object> getIngredients() { return List.of(new Object(), new Object()); }
    }
}
