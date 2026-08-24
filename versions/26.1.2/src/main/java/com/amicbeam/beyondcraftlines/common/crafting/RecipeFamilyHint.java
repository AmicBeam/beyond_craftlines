package com.amicbeam.beyondcraftlines.common.crafting;

/** Client-discovered JEI category evidence. The server must verify the recipe id before trusting it. */
public record RecipeFamilyHint(String jeiType, String family, String recipeId)
{
    public RecipeFamilyHint
    {
        jeiType = jeiType == null ? "" : jeiType;
        family = family == null ? "" : family;
        recipeId = recipeId == null ? "" : recipeId;
    }

    public String encode() { return jeiType + "|" + family + "|" + recipeId; }

    public static RecipeFamilyHint decode(String encoded)
    {
        if (encoded == null) return null;
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank()) return null;
        return new RecipeFamilyHint(parts[0], parts[1], parts[2]);
    }
}
