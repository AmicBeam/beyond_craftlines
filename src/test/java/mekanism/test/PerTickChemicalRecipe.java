package mekanism.test;

public final class PerTickChemicalRecipe
{
    private final boolean perTickUsage;

    public PerTickChemicalRecipe(boolean perTickUsage)
    {
        this.perTickUsage = perTickUsage;
    }

    public boolean perTickUsage()
    {
        return perTickUsage;
    }
}
