package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.network.BindingVisualsPayload;
import com.amicbeam.beyondcraftlines.common.network.RequestBindingVisualsPayload;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

public final class ClientBindingVisuals
{
    private static final double MAX_RENDER_DISTANCE_SQUARED = 96.0D * 96.0D;
    private static final Map<BlockPos, BindingVisual> POSITIONS = new HashMap<>();
    private static ResourceLocation dimension;

    private ClientBindingVisuals() {}

    public static void initialize()
    {
        BindingVisualsPayload.clientReceiver = ClientBindingVisuals::accept;
    }

    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event)
    {
        POSITIONS.clear();
        dimension = null;
        PacketDistributor.sendToServer(new RequestBindingVisualsPayload());
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        POSITIONS.clear();
        dimension = null;
    }

    public static void render(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        ResourceLocation currentDimension = minecraft.level.dimension().location();
        if (!currentDimension.equals(dimension))
        {
            dimension = currentDimension;
            POSITIONS.clear();
            PacketDistributor.sendToServer(new RequestBindingVisualsPayload());
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        VertexConsumer lines = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        for (var entry : POSITIONS.entrySet())
        {
            BlockPos pos = entry.getKey();
            if (!minecraft.level.isLoaded(pos) || pos.distToCenterSqr(camera) > MAX_RENDER_DISTANCE_SQUARED
                    || minecraft.level.getBlockState(pos).isAir()
                    || !BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(pos).getBlock())
                    .equals(entry.getValue().blockId()) || !visible(entry.getValue())) continue;
            VoxelShape voxelShape = minecraft.level.getBlockState(pos).getShape(minecraft.level, pos);
            AABB shape = (voxelShape.isEmpty() ? new AABB(pos) : voxelShape.bounds().move(pos));
            draw(event, lines, shape.inflate(0.022D), camera, 0.015F, 0.025F, 0.045F, 1.0F);
            draw(event, lines, shape.inflate(0.014D), camera, 0.035F, 0.20F, 0.48F, 1.0F);
            draw(event, lines, shape.inflate(0.006D), camera, 0.10F, 0.70F, 0.95F, 1.0F);
        }
        minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static void draw(RenderLevelStageEvent event, VertexConsumer lines, AABB bounds, Vec3 camera,
                             float red, float green, float blue, float alpha)
    {
        LevelRenderer.renderLineBox(event.getPoseStack(), lines, bounds.move(-camera.x, -camera.y, -camera.z),
                red, green, blue, alpha);
    }

    private static void accept(CompoundTag data)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        ResourceLocation payloadDimension;
        try
        {
            payloadDimension = ResourceLocation.parse(data.getString("dimension"));
        }
        catch (RuntimeException ignored)
        {
            return;
        }
        if (!payloadDimension.equals(minecraft.level.dimension().location())) return;
        HashMap<BlockPos, BindingVisual> next = new HashMap<>();
        ListTag list = data.getList("positions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);
            try
            {
                next.put(BlockPos.of(entry.getLong("pos")), new BindingVisual(
                        ResourceLocation.parse(entry.getString("block")),
                        entry.getBoolean("provisioner_target")));
            }
            catch (RuntimeException ignored) {}
        }
        POSITIONS.clear();
        POSITIONS.putAll(next);
        dimension = payloadDimension;
    }

    private static boolean visible(BindingVisual visual)
    {
        return CraftlinesConfig.SHOW_BOUND_MACHINE_FRAMES.get();
    }

    private record BindingVisual(ResourceLocation blockId, boolean provisionerTarget) {}
}
