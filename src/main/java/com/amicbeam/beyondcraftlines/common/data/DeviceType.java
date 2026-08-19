package com.amicbeam.beyondcraftlines.common.data;

public enum DeviceType
{
    BEYOND_FURNACE,
    BEYOND_BLAST_FURNACE,
    BEYOND_SMOKER,
    EXTERNAL_GUI_ONLY;

    public static DeviceType fromBlockId(String id)
    {
        String path = id.substring(id.indexOf(':') + 1);
        if (path.contains("blast_furnace")) return BEYOND_BLAST_FURNACE;
        if (path.contains("smoker")) return BEYOND_SMOKER;
        if (path.contains("furnace")) return BEYOND_FURNACE;
        return EXTERNAL_GUI_ONLY;
    }
}
