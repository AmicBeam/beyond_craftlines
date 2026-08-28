package com.amicbeam.beyondcraftlines.common.dashboard;

import com.amicbeam.beyondcraftlines.common.crafting.RecipeResolutionOverrides;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public record DashboardRecipeConfig(RecipeResolutionOverrides overrides, boolean blockingMode) {
    public static final DashboardRecipeConfig EMPTY=new DashboardRecipeConfig(RecipeResolutionOverrides.EMPTY,false);
    public DashboardRecipeConfig{overrides=overrides==null?RecipeResolutionOverrides.EMPTY:overrides;}
    public boolean configured(){return !overrides.recipeChoices().isEmpty();}
    public int choiceCount(){return overrides.recipeChoices().size()+overrides.ingredientChoices().size();}
    public int estimatedBytes(){long bytes=64;for(var c:overrides.recipeChoices())bytes+=utf8(c.output())+utf8(c.recipe().toString())+32L;for(var c:overrides.ingredientChoices())bytes+=utf8(c.recipe().toString())+utf8(c.item().toString())+40L;return(int)Math.min(Integer.MAX_VALUE,bytes);}
    public CompoundTag save(){CompoundTag root=new CompoundTag();root.putBoolean("blocking",blockingMode);ListTag recipes=new ListTag();for(var c:overrides.recipeChoices()){CompoundTag v=new CompoundTag();v.putString("output",c.output());v.putString("recipe",c.recipe().toString());recipes.add(v);}root.put("recipes",recipes);ListTag ingredients=new ListTag();for(var c:overrides.ingredientChoices()){CompoundTag v=new CompoundTag();v.putString("recipe",c.recipe().toString());v.putInt("slot",c.slot());v.putString("item",c.item().toString());ingredients.add(v);}root.put("ingredients",ingredients);return root;}
    public static DashboardRecipeConfig load(CompoundTag root){if(root==null)return EMPTY;var recipes=new ArrayList<RecipeResolutionOverrides.RecipeChoice>();ListTag rs=root.getListOrEmpty("recipes");for(int i=0;i<rs.size();i++){CompoundTag v=rs.getCompoundOrEmpty(i);Identifier r=Identifier.tryParse(v.getStringOr("recipe",""));String o=v.getStringOr("output","");if(r!=null&&!o.isBlank())recipes.add(new RecipeResolutionOverrides.RecipeChoice(o,r));}var ingredients=new ArrayList<RecipeResolutionOverrides.IngredientChoice>();ListTag is=root.getListOrEmpty("ingredients");for(int i=0;i<is.size();i++){CompoundTag v=is.getCompoundOrEmpty(i);Identifier r=Identifier.tryParse(v.getStringOr("recipe",""));Identifier item=Identifier.tryParse(v.getStringOr("item",""));int slot=v.getIntOr("slot",-1);if(r!=null&&item!=null&&slot>=0)ingredients.add(new RecipeResolutionOverrides.IngredientChoice(r,slot,item));}try{return new DashboardRecipeConfig(new RecipeResolutionOverrides(recipes,ingredients),root.getBooleanOr("blocking",false));}catch(RuntimeException ignored){return EMPTY;}}
    private static int utf8(String value){return value==null?0:value.getBytes(StandardCharsets.UTF_8).length;}
}
