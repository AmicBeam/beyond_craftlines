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

    public static final ModConfigSpec.IntValue VIRTUAL_CRAFTING_NODE_INTERVAL_TICKS = SERVER_BUILDER
            .push("crafting")
            .comment("Ticks between individual simulated crafting operations. Each operation calls the loaded recipe and returns its remaining items.")
            .translation("config.beyond_craftlines.virtual_crafting_node_interval_ticks")
            .defineInRange("virtualCraftingNodeIntervalTicks", 20, 1, 72_000);

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
