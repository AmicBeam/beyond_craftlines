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

    public static final ModConfigSpec.BooleanValue SHOW_PROVISIONER_BOUND_FACE_FRAMES = CLIENT_BUILDER
            .comment("Show the persistent black-and-blue Beyond Dimensions frame on faces wirelessly bound to a Craftline Provisioner.")
            .translation("config.beyond_craftlines.show_provisioner_bound_face_frames")
            .define("showProvisionerBoundFaceFrames", true);

    public static final ModConfigSpec.IntValue BOUND_MACHINE_FRAME_RENDER_DISTANCE = CLIENT_BUILDER
            .comment("Maximum distance in blocks for bound-machine frames. Nearby bindings are indexed by chunk.")
            .translation("config.beyond_craftlines.bound_machine_frame_render_distance")
            .defineInRange("boundMachineFrameRenderDistance", 96, 16, 512);

    public static final ModConfigSpec.BooleanValue SHOW_PROVISIONER_TARGET_MATERIAL = CLIENT_BUILDER
            .comment("Show the bound target item's 8x8 dynamic material on Craftline Provisioner side faces.")
            .translation("config.beyond_craftlines.show_provisioner_target_material")
            .define("showProvisionerTargetMaterial", true);

    public static final ModConfigSpec.IntValue AMOUNT_PREVIEW_DELAY_TICKS = CLIENT_BUILDER
            .pop()
            .push("planning")
            .comment("Ticks to wait after the latest recipe-tree amount change before recalculating the preview.")
            .translation("config.beyond_craftlines.amount_preview_delay_ticks")
            .defineInRange("amountPreviewDelayTicks", 5, 1, 1_200);

    public static final ModConfigSpec.IntValue RECIPE_PREVIEW_DELAY_TICKS = CLIENT_BUILDER
            .comment("Ticks to wait after the latest recipe or ingredient choice change before recalculating the preview.")
            .translation("config.beyond_craftlines.recipe_preview_delay_ticks")
            .defineInRange("recipePreviewDelayTicks", 5, 1, 1_200);

    public static final ModConfigSpec.BooleanValue ENABLE_OPTIMAL_RECIPE_SEARCH = CLIENT_BUILDER
            .comment("Search alternative recipes and ingredient variants for the best craftable plan. Disable to try only the first preferred candidate at each branch and reduce background planning work.")
            .translation("config.beyond_craftlines.enable_optimal_recipe_search")
            .define("enableOptimalRecipeSearch", true);

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

    public static final ModConfigSpec.IntValue ORDER_STATUS_REFRESH_INTERVAL_TICKS = CLIENT_BUILDER
            .pop()
            .push("orders")
            .comment("Ticks between order-status refresh requests while the order status screen is open.")
            .translation("config.beyond_craftlines.order_status_refresh_interval_ticks")
            .defineInRange("orderStatusRefreshIntervalTicks", 20, 1, 72_000);

    public static final ModConfigSpec.IntValue VIRTUAL_CRAFTING_NODE_INTERVAL_TICKS = SERVER_BUILDER
            .push("crafting")
            .comment("Ticks between simulated crafting batches. Stack-stable inputs are processed as one BD long-count batch; state-changing tools remain sequential.")
            .translation("config.beyond_craftlines.virtual_crafting_node_interval_ticks")
            .defineInRange("virtualCraftingNodeIntervalTicks", 1, 1, 72_000);

    public static final ModConfigSpec.BooleanValue ENABLE_SMITHING_AND_STONECUTTING_RECIPE_PROXY = SERVER_BUILDER
            .comment("Let the BD network execute vanilla smithing and stonecutting recipes directly, without a bound workstation or provisioner.")
            .translation("config.beyond_craftlines.enable_smithing_and_stonecutting_recipe_proxy")
            .define("enableSmithingAndStonecuttingRecipeProxy", true);

    public static final ModConfigSpec.IntValue MAX_PROVISIONER_CONNECTIONS = SERVER_BUILDER
            .pop()
            .push("provisioner")
            .comment("Maximum number of wireless target devices bound to one Craftline Provisioner.")
            .translation("config.beyond_craftlines.max_provisioner_connections")
            .defineInRange("maxWirelessConnections", 16, 1, 1_024);

    public static final ModConfigSpec.BooleanValue RESET_PROVISIONER_ROUND_ROBIN_ON_ACTIVATION = SERVER_BUILDER
            .comment("Restart round-robin supply from the first wireless binding whenever a recipe request activates a provisioner. Blocking orders activate once per feeding round.")
            .translation("config.beyond_craftlines.reset_provisioner_round_robin_on_activation")
            .define("resetRoundRobinOnRecipeActivation", true);

    public static final ModConfigSpec.IntValue MAX_PLANNING_DEPTH = SERVER_BUILDER
            .pop().push("crafting")
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

    public static final ModConfigSpec.IntValue DASHBOARD_CHECK_INTERVAL_TICKS = SERVER_BUILDER
            .comment("Ticks between automatic stock checks performed by a Craftline Dashboard.")
            .translation("config.beyond_craftlines.dashboard_check_interval_ticks")
            .defineInRange("dashboardCheckIntervalTicks", 20, 1, 72_000);

    public static final ModConfigSpec.IntValue MAX_ACTIVE_AUTOMATIC_ORDERS_PER_NETWORK = SERVER_BUILDER
            .comment("Maximum active automatic dashboard orders on one BD network. Automatic orders do not consume a player's order limit.")
            .translation("config.beyond_craftlines.max_active_automatic_orders_per_network")
            .defineInRange("maxActiveAutomaticOrdersPerNetwork", 10, 1, 10_000);

    public static final ModConfigSpec.IntValue MAX_DASHBOARD_RECIPE_BYTES = SERVER_BUILDER
            .comment("Maximum estimated serialized bytes for one recipe tree stored in a Craftline Dashboard. A hard 64 KiB ceiling always applies.")
            .translation("config.beyond_craftlines.max_dashboard_recipe_bytes")
            .defineInRange("maxDashboardRecipeBytes", 32_768, 1_024, 65_536);

    public static final ModConfigSpec.IntValue MAX_DASHBOARD_RECIPE_CHOICES = SERVER_BUILDER
            .comment("Maximum combined recipe and ingredient choices stored in one Craftline Dashboard.")
            .translation("config.beyond_craftlines.max_dashboard_recipe_choices")
            .defineInRange("maxDashboardRecipeChoices", 4_096, 1, 16_384);

    public static final ModConfigSpec.IntValue MAX_CONCURRENT_ORDERS_PER_NETWORK = SERVER_BUILDER
            .comment("Maximum orders that may execute concurrently on one BD network when their non-crafting recipe families do not overlap. Set to one for strict FIFO execution.")
            .translation("config.beyond_craftlines.max_concurrent_orders_per_network")
            .defineInRange("maxConcurrentOrdersPerNetwork", 4, 1, 1_024);

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
