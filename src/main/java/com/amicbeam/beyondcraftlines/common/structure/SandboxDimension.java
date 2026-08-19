package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class SandboxDimension
{
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("beyond_craftlines", "sandbox");
    public static final ResourceKey<Level> KEY = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION, ID);

    private SandboxDimension() {}

    public static ServerLevel resolve(MinecraftServer server)
    {
        ServerLevel sandbox = server.getLevel(KEY);
        if (sandbox == null) throw new IllegalStateException("sandbox dimension is not loaded");
        return sandbox;
    }

    public static boolean isDedicatedDimension(MinecraftServer server)
    {
        return server.getLevel(KEY) != null;
    }
}
