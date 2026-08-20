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
            .comment("Maximum recipe demand nodes expanded by one order plan.")
            .translation("config.beyond_craftlines.max_planning_nodes")
            .defineInRange("maxPlanningNodes", 4_096, 64, 1_000_000);

    public static final ModConfigSpec.IntValue MAX_PLANNING_TIME_MILLIS = SERVER_BUILDER
            .comment("Cooperative main-thread time budget in milliseconds for one recipe plan.")
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
}
