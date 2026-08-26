package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeFurnaceFamilyTest
{
    @Test
    void mapsEveryNativeFurnaceClassToItsRecipeFamily()
    {
        String prefix = "com.wintercogs.beyonddimensions.common.block.entity.";
        assertEquals("smelting", NativeFurnaceFamily.forClassName(prefix + "NetFurnaceBlockEntity"));
        assertEquals("blasting", NativeFurnaceFamily.forClassName(prefix + "NetBlastFurnaceBlockEntity"));
        assertEquals("smoking", NativeFurnaceFamily.forClassName(prefix + "NetSmokerBlockEntity"));
    }
}
