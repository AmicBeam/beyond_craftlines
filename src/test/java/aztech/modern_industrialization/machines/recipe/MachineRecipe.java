package aztech.modern_industrialization.machines.recipe;

import java.util.List;

/** Structural fixture matching MI's public fields; no Minecraft or MI runtime dependency. */
public final class MachineRecipe
{
    public List<Input> itemInputs = List.of(new Input(28, 1), new Input(15, 1), new Input(2, 1));
    public List<Output> itemOutputs = List.of(new Output(1, 1));
    public List<Object> fluidInputs = List.of();
    public List<Object> fluidOutputs = List.of();

    public record Input(int amount, float probability) {}
    public record Output(int amount, float probability) {}
}
