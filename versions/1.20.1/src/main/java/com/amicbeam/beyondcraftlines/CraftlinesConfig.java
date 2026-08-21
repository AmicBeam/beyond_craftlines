package com.amicbeam.beyondcraftlines;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class CraftlinesConfig {
    private static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue SHOW_BOUND_MACHINE_FRAMES = CLIENT_BUILDER
            .push("binding").define("showBoundMachineFrames", true);
    public static final ForgeConfigSpec.IntValue BOUND_MACHINE_FRAME_RENDER_DISTANCE = CLIENT_BUILDER
            .defineInRange("boundMachineFrameRenderDistance", 96, 16, 512);
    public static final ForgeConfigSpec.BooleanValue SHOW_PROVISIONER_TARGET_MATERIAL = CLIENT_BUILDER
            .define("showProvisionerTargetMaterial", true);
    public static final ForgeConfigSpec.IntValue RECIPE_INDEX_MAX_PER_TICK = CLIENT_BUILDER
            .pop().push("planning").defineInRange("recipeIndexMaxPerTick", 256, 16, 65_536);
    public static final ForgeConfigSpec.BooleanValue COLLAPSE_DUPLICATE_TREE_RESOURCES = CLIENT_BUILDER
            .define("collapseDuplicateTreeResources", true);
    public static final ForgeConfigSpec.BooleanValue SHOW_JEI_ORDER_BUTTON_EVERYWHERE = CLIENT_BUILDER
            .pop().push("jei").define("showOrderButtonEverywhere", true);
    public static final ForgeConfigSpec.IntValue VIRTUAL_CRAFTING_NODE_INTERVAL_TICKS = SERVER_BUILDER
            .push("crafting").defineInRange("virtualCraftingNodeIntervalTicks", 20, 1, 72_000);
    public static final ForgeConfigSpec.IntValue MAX_PLANNING_DEPTH = SERVER_BUILDER
            .defineInRange("maxPlanningDepth", 48, 1, 256);
    public static final ForgeConfigSpec.IntValue MAX_PLANNING_NODES = SERVER_BUILDER
            .comment("Maximum candidate branches explored during automatic recipe planning. Fixed client proposals are validated directly and do not consume this budget.")
            .defineInRange("maxPlanningNodes", 4_096, 64, 1_000_000);
    public static final ForgeConfigSpec.IntValue MAX_PLANNING_TIME_MILLIS = SERVER_BUILDER
            .comment("Cooperative main-thread time budget in milliseconds for automatic candidate search. Fixed client proposals are validated directly and do not consume this budget.")
            .defineInRange("maxPlanningTimeMillis", 50, 1, 10_000);
    public static final ForgeConfigSpec.IntValue ORDER_SUBMIT_COOLDOWN_TICKS = SERVER_BUILDER
            .defineInRange("orderSubmitCooldownTicks", 10, 0, 1_200);
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_ORDERS = SERVER_BUILDER
            .defineInRange("maxActiveOrders", 1_024, 1, 100_000);
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_ORDERS_PER_PLAYER = SERVER_BUILDER
            .defineInRange("maxActiveOrdersPerPlayer", 64, 1, 10_000);
    public static final ForgeConfigSpec.IntValue TERMINAL_ORDER_HISTORY_LIMIT = SERVER_BUILDER
            .defineInRange("terminalOrderHistoryLimit", 200, 0, 100_000);

    static { CLIENT_BUILDER.pop(); SERVER_BUILDER.pop(); }
    private static final ForgeConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
    private static final ForgeConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    private CraftlinesConfig() {}

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
    }

    public static boolean isClientConfig(ModConfig config) { return config.getSpec() == CLIENT_SPEC; }
}
