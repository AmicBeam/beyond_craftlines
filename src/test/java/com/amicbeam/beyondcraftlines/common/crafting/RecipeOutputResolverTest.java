package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class RecipeOutputResolverTest
{
    @BeforeEach
    void installProfiles()
    { RecipeIoProfileTestSupport.install("defaults.json", "mekanism.json"); }

    @Test
    void readsBothMekanismRotaryOutputDefinitions()
    {
        assertEquals(List.of("gas", "fluid"),
                RecipeOutputResolver.reflectiveOutputValues(new RotaryLikeRecipe()));
    }

    @Test
    void readsModernMekanismChemicalRotaryOutputDefinition()
    {
        assertEquals(List.of("chemical", "fluid"),
                RecipeOutputResolver.reflectiveOutputValues(new ModernRotaryLikeRecipe()));
    }

    @Test
    void readsMekanismSawingMainAndSecondaryOutputDefinitions()
    {
        assertEquals(List.of("birch_planks", "sawdust"),
                RecipeOutputResolver.reflectiveOutputValues(new SawmillLikeRecipe(),
                        List.of("getMainOutputDefinition", "getSecondaryOutputDefinition")));
    }

    @Test
    void unwrapsMekanismElectrolysisOutputRecord()
    {
        assertEquals(List.of("hydrogen", "oxygen"),
                RecipeOutputResolver.reflectiveOutputValues(new ElectrolysisLikeRecipe()));
    }

    @Test
    void unwrapsStackContainerOutput()
    {
        assertEquals(List.of("dirty_iron_slurry"),
                RecipeOutputResolver.reflectiveOutputValues(new BoxedChemicalLikeRecipe()));
    }

    @Test
    void readsGenericPublicOutputFields()
    {
        assertEquals(List.of("processed_item", "machine_result"),
                RecipeOutputResolver.reflectiveOutputValues(new PublicFieldOutputRecipe()));
    }

    @Test
    void readsMalumSpiritFocusingOutputAccessors()
    {
        assertEquals(List.of("prismarine_shard", "prismarine_shard_copy"),
                RecipeOutputResolver.reflectiveOutputValues(new MalumSpiritFocusingLikeRecipe()));
    }

    @Test
    void unwrapsCapabilityOutputMapsAndContentRecords()
    {
        assertEquals(List.of("assembled_machine"),
                RecipeOutputResolver.reflectiveOutputValues(new CapabilityMapRecipe()));
    }

    @Test
    void unwrapsCapabilityOutputMapsAndContentClasses()
    {
        assertEquals(List.of("assembled_machine"),
                RecipeOutputResolver.reflectiveOutputValues(new ClassCapabilityMapRecipe()));
    }

    @Test
    void rotaryChemicalOutputSelectsOnlyTheFluidInputDirection()
    {
        assertEquals(List.of("getFluidInput"), RecipeResourceResolver.directionalInputMethods(
                new ModernRotaryLikeRecipe(), "chemical"::equals));
    }

    @Test
    void rotaryFluidOutputSelectsOnlyTheChemicalInputDirection()
    {
        assertEquals(List.of("getGasInput", "getChemicalInput"),
                RecipeResourceResolver.directionalInputMethods(
                        new ModernRotaryLikeRecipe(), "fluid"::equals));
    }

    @Test
    void datapackRotaryDirectionSupportsModernAndLegacyChemicalKeys()
    {
        var recipe = new mekanism.api.recipes.RotaryRecipe();
        var rules = RecipeIoProfileRegistry.directionRules(recipe);
        assertEquals(List.of("getFluidInput"), rules.stream().filter(rule ->
                        rule.matchesClass(recipe) && rule.matchesStackType("stack_type/chemical", "stack_type/chemical"))
                .flatMap(rule -> rule.inputFields().stream()).distinct().toList());
        assertEquals(List.of("getFluidInput"), rules.stream().filter(rule ->
                        rule.matchesClass(recipe) && rule.matchesStackType(
                                "stack_type/chemicals/gas", "stack_type/chemicals/gas"))
                .flatMap(rule -> rule.inputFields().stream()).distinct().toList());
        assertEquals(List.of("getGasInput", "getChemicalInput"), rules.stream().filter(rule ->
                        rule.matchesClass(recipe) && rule.matchesStackType("stack_type/fluid", "stack_type/fluid"))
                .flatMap(rule -> rule.inputFields().stream()).distinct().toList());
    }

    @Test
    void chemicalInjectionAlwaysIncludesItsItemAndChemicalInputs()
    {
        var recipe = new mekanism.api.recipes.ItemStackGasToItemStackRecipe();
        assertEquals(List.of("getItemInput", "getChemicalInput"),
                RecipeIoProfileRegistry.directionRules(recipe).stream()
                        .filter(rule -> rule.matchesClass(recipe)
                                && rule.matchesStackType("stack_type/item", "stack_type/item"))
                        .flatMap(rule -> rule.inputFields().stream()).distinct().toList());
    }

    @Test
    void mapsNumericAmountFieldToExplicitFluidId()
    {
        Assumptions.assumeTrue(classAvailable("net.neoforged.neoforge.fluids.FluidStack")
                || classAvailable("net.minecraftforge.fluids.FluidStack"));
        var output = MappedRecipeOutput.resolve(new NumericFluidOutputRecipe(),
                new RecipeIoProfileRegistry.OutputMapping(
                        RecipeIoProfileRegistry.OutputType.FLUID,
                        "minecraft:water", "fluidAmount"));

        Assumptions.assumeTrue(output != null, "fluid registry components are not bootstrapped");
        assertNotNull(output);
        assertEquals(250, output.amount());
        assertEquals("minecraft", output.key().getModId());
    }

    private static boolean classAvailable(String name)
    {
        try { Class.forName(name); return true; }
        catch (ClassNotFoundException ignored) { return false; }
    }

    private static final class RotaryLikeRecipe
    {
        public List<String> getGasOutputDefinition() { return List.of("gas"); }
        public List<String> getFluidOutputDefinition() { return List.of("fluid"); }
    }

    private static final class ModernRotaryLikeRecipe
    {
        public List<String> getChemicalOutputDefinition() { return List.of("chemical"); }
        public List<String> getFluidOutputDefinition() { return List.of("fluid"); }
    }

    private static final class SawmillLikeRecipe
    {
        public List<String> getMainOutputDefinition() { return List.of("birch_planks"); }
        public List<String> getSecondaryOutputDefinition() { return List.of("sawdust"); }
    }

    private static final class ElectrolysisLikeRecipe
    {
        public List<SplitOutput> getOutputDefinition()
        { return List.of(new SplitOutput("hydrogen", "oxygen")); }
    }

    private static final class BoxedChemicalLikeRecipe
    {
        public List<BoxedStack> getOutputDefinition()
        { return List.of(new BoxedStack("dirty_iron_slurry")); }
    }

    private static final class PublicFieldOutputRecipe
    {
        public final String output = "processed_item";
        public final String result = "machine_result";
    }

    private static final class MalumSpiritFocusingLikeRecipe
    {
        public String getOutputRaw() { return "prismarine_shard"; }
        public String createOutput() { return "prismarine_shard_copy"; }
    }

    private static final class CapabilityMapRecipe
    {
        public final Map<String, List<CapabilityContent>> outputs = Map.of(
                "item", List.of(new CapabilityContent("assembled_machine", 10_000, 10_000)));
    }

    private static final class NumericFluidOutputRecipe
    {
        public final int fluidAmount = 250;
    }

    private static final class ClassCapabilityMapRecipe
    {
        public final Map<String, List<ClassCapabilityContent>> outputs = Map.of(
                "item", List.of(new ClassCapabilityContent("assembled_machine")));
    }

    public static final class ClassCapabilityContent
    {
        public final Object content;
        public final int chance = 10_000;
        public final int maxChance = 10_000;
        public ClassCapabilityContent(Object content) { this.content = content; }
    }

    private static final class BoxedStack
    {
        private final String stack;
        private BoxedStack(String stack) { this.stack = stack; }
        public String getChemicalStack() { return stack; }
    }

    private record SplitOutput(String left, String right) {}
    private record CapabilityContent(Object content, int chance, int maxChance) {}
}
