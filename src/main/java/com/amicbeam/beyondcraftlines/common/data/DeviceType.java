package com.amicbeam.beyondcraftlines.common.data;

public enum DeviceType
{
    BEYOND_FURNACE,
    BEYOND_BLAST_FURNACE,
    BEYOND_SMOKER,
    EXTERNAL_RECIPE_MACHINE,
    PROVISIONER_RECIPE_BINDING;

    public boolean isNativeBeyondRecipeMachine()
    {
        return this == BEYOND_FURNACE || this == BEYOND_BLAST_FURNACE || this == BEYOND_SMOKER;
    }

    public static boolean isBindableMachine(String blockId)
    {
        int separator = blockId.indexOf(':');
        if (separator <= 0) return false;
        String namespace = blockId.substring(0, separator);
        return !"beyonddimensions".equals(namespace) && !"beyond_craftlines".equals(namespace);
    }

    public static DeviceType fromBlockId(String id)
    {
        int separator = id.indexOf(':');
        if (separator <= 0) return EXTERNAL_RECIPE_MACHINE;
        String namespace = id.substring(0, separator);
        String path = id.substring(separator + 1);
        if (!"beyonddimensions".equals(namespace)) return EXTERNAL_RECIPE_MACHINE;
        if (path.equals("net_blast_furnace_block")) return BEYOND_BLAST_FURNACE;
        if (path.equals("net_smoker_block")) return BEYOND_SMOKER;
        if (path.equals("net_furnace_block")) return BEYOND_FURNACE;
        return EXTERNAL_RECIPE_MACHINE;
    }
}
