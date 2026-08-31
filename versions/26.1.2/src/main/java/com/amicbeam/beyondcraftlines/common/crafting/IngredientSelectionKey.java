package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class IngredientSelectionKey
{
    private static final String EXACT_PREFIX="exact:";private IngredientSelectionKey(){}
    public static String exact(IStackKey<?> key){return exactResolution(RecipeResourceResolver.resolutionKey(key));}
    static String exactResolution(String resolutionKey){return EXACT_PREFIX+resolutionKey;}
    public static String legacy(Identifier item){return item.toString();}
    public static boolean matches(String selection,IStackKey<?> candidate){if(selection==null||candidate==null)return false;
        if(selection.startsWith(EXACT_PREFIX))return selection.equals(exact(candidate));Identifier item=Identifier.tryParse(selection);
        return item!=null&&candidate instanceof ItemStackKey itemKey&&item.equals(BuiltInRegistries.ITEM.getKey(itemKey.getSource()));}
    public static Identifier legacyItem(String selection){if(selection==null||selection.startsWith(EXACT_PREFIX))return null;return Identifier.tryParse(selection);}
}
