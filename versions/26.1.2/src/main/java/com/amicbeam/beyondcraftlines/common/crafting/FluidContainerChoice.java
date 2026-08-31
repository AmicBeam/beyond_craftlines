package com.amicbeam.beyondcraftlines.common.crafting;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import net.minecraft.resources.Identifier;

public final class FluidContainerChoice
{
    private static final String PREFIX="fluid_proxy/";private FluidContainerChoice(){}
    public static Identifier proxy(Identifier item){return Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID,
            PREFIX+item.getNamespace()+"/"+item.getPath());}
    public static boolean isProxy(Identifier choice){return choice!=null&&BeyondCraftlines.MOD_ID.equals(choice.getNamespace())&&choice.getPath().startsWith(PREFIX);}
    public static Identifier itemOrSelf(Identifier choice){if(!isProxy(choice))return choice;String value=choice.getPath().substring(PREFIX.length());
        int separator=value.indexOf('/');Identifier item=separator<=0||separator==value.length()-1?null:
                Identifier.tryParse(value.substring(0,separator)+":"+value.substring(separator+1));return item==null?choice:item;}
}
