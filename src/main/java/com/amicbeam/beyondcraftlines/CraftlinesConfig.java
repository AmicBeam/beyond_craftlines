package com.amicbeam.beyondcraftlines;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class CraftlinesConfig
{
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOW_BOUND_MACHINE_FRAMES = CLIENT_BUILDER
            .push("binding")
            .comment("Show the black-and-blue Beyond Dimensions frame around machines bound to a Craftlines network.")
            .translation("config.beyond_craftlines.show_bound_machine_frames")
            .define("showBoundMachineFrames", true);

    public static final ModConfigSpec.IntValue BOUND_MACHINE_FRAME_RENDER_DISTANCE = CLIENT_BUILDER
            .comment("Maximum distance in blocks for bound-machine frames. Nearby bindings are indexed by chunk.")
            .translation("config.beyond_craftlines.bound_machine_frame_render_distance")
            .defineInRange("boundMachineFrameRenderDistance", 96, 16, 512);

    public static final ModConfigSpec.BooleanValue SHOW_PROVISIONER_TARGET_MATERIAL = CLIENT_BUILDER
            .comment("Show the bound target item's 8x8 dynamic material on Craftline Provisioner side faces.")
            .translation("config.beyond_craftlines.show_provisioner_target_material")
            .define("showProvisionerTargetMaterial", true);

    public static final ModConfigSpec.IntValue RECIPE_INDEX_MAX_PER_TICK = CLIENT_BUILDER
            .pop()
            .push("planning")
            .comment("Maximum recipes processed per client tick in each of the two recipe indexing stages.")
            .translation("config.beyond_craftlines.recipe_index_max_per_tick")
            .defineInRange("recipeIndexMaxPerTick", 256, 16, 65_536);

    public static final ModConfigSpec.BooleanValue COLLAPSE_DUPLICATE_TREE_RESOURCES = CLIENT_BUILDER
            .comment("Show only the occurrence closest to the root for an identical component-aware resource in the recipe tree; later occurrences become clickable jump references.")
            .translation("config.beyond_craftlines.collapse_duplicate_tree_resources")
            .define("collapseDuplicateTreeResources", true);

    public static final ModConfigSpec.BooleanValue SHOW_JEI_ORDER_BUTTON_EVERYWHERE = CLIENT_BUILDER
            .pop()
            .push("jei")
            .comment("Show the Craftlines order button in JEI from every screen. When disabled, it is only available while a Beyond Dimensions network menu is open.")
            .translation("config.beyond_craftlines.show_jei_order_button_everywhere")
            .define("showOrderButtonEverywhere", true);

    public static final ModConfigSpec.IntValue VIRTUAL_CRAFTING_NODE_INTERVAL_TICKS = SERVER_BUILDER
            .push("crafting")
            .comment("Ticks between simulated crafting batches. Stack-stable inputs are processed as one BD long-count batch; state-changing tools remain sequential.")
            .translation("config.beyond_craftlines.virtual_crafting_node_interval_ticks")
            .defineInRange("virtualCraftingNodeIntervalTicks", 20, 1, 72_000);

    public static final ModConfigSpec.IntValue MAX_PLANNING_DEPTH = SERVER_BUILDER
            .comment("Maximum recursive recipe-tree depth per order.")
            .translation("config.beyond_craftlines.max_planning_depth")
            .defineInRange("maxPlanningDepth", 48, 1, 256);

    public static final ModConfigSpec.IntValue MAX_PLANNING_NODES = SERVER_BUILDER
            .comment("Maximum candidate branches explored during automatic recipe planning. Fixed client proposals are validated directly and do not consume this budget.")
            .translation("config.beyond_craftlines.max_planning_nodes")
            .defineInRange("maxPlanningNodes", 4_096, 64, 1_000_000);

    public static final ModConfigSpec.IntValue MAX_PLANNING_TIME_MILLIS = SERVER_BUILDER
            .comment("Cooperative main-thread time budget in milliseconds for automatic candidate search. Fixed client proposals are validated directly and do not consume this budget.")
            .translation("config.beyond_craftlines.max_planning_time_millis")
            .defineInRange("maxPlanningTimeMillis", 50, 1, 10_000);

    public static final ModConfigSpec.IntValue ORDER_SUBMIT_COOLDOWN_TICKS = SERVER_BUILDER
            .comment("Minimum ticks between order submissions by the same player; zero disables throttling.")
            .translation("config.beyond_craftlines.order_submit_cooldown_ticks")
            .defineInRange("orderSubmitCooldownTicks", 10, 0, 1_200);

    public static final ModConfigSpec.IntValue MAX_ACTIVE_ORDERS = SERVER_BUILDER
            .comment("Maximum active Craftlines orders across the server.")
            .translation("config.beyond_craftlines.max_active_orders")
            .defineInRange("maxActiveOrders", 1_024, 1, 100_000);

    public static final ModConfigSpec.IntValue MAX_ACTIVE_ORDERS_PER_PLAYER = SERVER_BUILDER
            .comment("Maximum active Craftlines orders owned by one player.")
            .translation("config.beyond_craftlines.max_active_orders_per_player")
            .defineInRange("maxActiveOrdersPerPlayer", 64, 1, 10_000);

    public static final ModConfigSpec.IntValue TERMINAL_ORDER_HISTORY_LIMIT = SERVER_BUILDER
            .comment("Completed, cancelled, or failed orders retained in SavedData; zero retains none.")
            .translation("config.beyond_craftlines.terminal_order_history_limit")
            .defineInRange("terminalOrderHistoryLimit", 200, 0, 100_000);

    static
    {
        CLIENT_BUILDER.pop();
        SERVER_BUILDER.pop();
    }

    private static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
    private static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    private CraftlinesConfig() {}

    public static void register(ModContainer container)
    {
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
        container.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
    }

    public static boolean isClientConfig(ModConfig config)
    { return config.getSpec() == CLIENT_SPEC; }
}
