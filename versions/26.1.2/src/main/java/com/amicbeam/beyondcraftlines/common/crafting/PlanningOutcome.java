package com.amicbeam.beyondcraftlines.common.crafting;

public enum PlanningOutcome
{
    SEARCHING("searching"),READY("ready"),NO_RECIPE("no_recipe"),MISSING_INPUTS("missing_inputs"),
    CYCLE("cycle"),BUDGET_EXHAUSTED("budget_exhausted"),RUNTIME_UNAVAILABLE("runtime_unavailable"),STALE("stale");
    private final String id;PlanningOutcome(String id){this.id=id;}public String id(){return id;}public boolean craftable(){return this==READY;}
    public static PlanningOutcome byId(String id){if(id!=null)for(PlanningOutcome value:values())if(value.id.equals(id))return value;return RUNTIME_UNAVAILABLE;}
    public static PlanningOutcome completed(boolean missing,boolean rootNoRecipe,boolean cycle,boolean budgetExhausted){if(!missing)return READY;if(rootNoRecipe)return NO_RECIPE;if(cycle)return CYCLE;if(budgetExhausted)return BUDGET_EXHAUSTED;return MISSING_INPUTS;}
}
