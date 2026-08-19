package com.amicbeam.beyondcraftlines.common.data;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BlueprintComponents
{
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(
            net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, "beyond_craftlines");
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> BLUEPRINT_ID = COMPONENTS.register(
            "blueprint_id", () -> DataComponentType.<ResourceLocation>builder().persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> BLUEPRINT_HASH = COMPONENTS.register(
            "blueprint_hash", () -> DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

    public static void register(IEventBus bus) { COMPONENTS.register(bus); }
    private BlueprintComponents() {}
}
