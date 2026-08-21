package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.network.BindingVisualsPayload;
import com.amicbeam.beyondcraftlines.common.network.RequestBindingVisualsPayload;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public final class ClientBindingVisuals
{
    private static final Identifier FRAME_TEXTURE = Identifier.fromNamespaceAndPath(
            "beyond_craftlines", "textures/block/bound_machine_frame.png");
    private static final double SURFACE_OFFSET = 0.003D;
    private static final Map<Long, List<PositionedVisual>> BY_CHUNK = new HashMap<>();
    private static Identifier dimension;

    private ClientBindingVisuals() {}

    public static void initialize()
    {
        BindingVisualsPayload.clientReceiver = ClientBindingVisuals::accept;
    }

    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event)
    {
        BY_CHUNK.clear();
        dimension = null;
        ClientPacketDistributor.sendToServer(new RequestBindingVisualsPayload());
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        BY_CHUNK.clear();
        dimension = null;
    }

    public static void render(RenderLevelStageEvent.AfterTranslucentParticles event)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !CraftlinesConfig.SHOW_BOUND_MACHINE_FRAMES.get()) return;
        Identifier currentDimension = minecraft.level.dimension().identifier();
        if (!currentDimension.equals(dimension))
        {
            dimension = currentDimension;
            BY_CHUNK.clear();
            ClientPacketDistributor.sendToServer(new RequestBindingVisualsPayload());
            return;
        }

        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        int renderDistance = CraftlinesConfig.BOUND_MACHINE_FRAME_RENDER_DISTANCE.get();
        double renderDistanceSquared = (double) renderDistance * renderDistance;
        int renderChunkRadius = (renderDistance + 15) / 16 + 1;
        RenderType frameType = RenderTypes.entityCutout(FRAME_TEXTURE);
        VertexConsumer frame = minecraft.renderBuffers().bufferSource().getBuffer(frameType);
        int cameraChunkX = net.minecraft.util.Mth.floor(camera.x) >> 4;
        int cameraChunkZ = net.minecraft.util.Mth.floor(camera.z) >> 4;
        for (int chunkX = cameraChunkX - renderChunkRadius;
             chunkX <= cameraChunkX + renderChunkRadius; chunkX++)
        for (int chunkZ = cameraChunkZ - renderChunkRadius;
             chunkZ <= cameraChunkZ + renderChunkRadius; chunkZ++)
        for (PositionedVisual entry : BY_CHUNK.getOrDefault(ChunkPos.pack(chunkX, chunkZ), List.of()))
        {
            BlockPos pos = entry.position();
            if (!minecraft.level.isLoaded(pos) || pos.distToCenterSqr(camera) > renderDistanceSquared
                    || minecraft.level.getBlockState(pos).isAir()
                    || !BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(pos).getBlock())
                    .equals(entry.visual().blockId())) continue;
            VoxelShape voxelShape = minecraft.level.getBlockState(pos).getShape(minecraft.level, pos);
            AABB shape = (voxelShape.isEmpty() ? new AABB(pos) : voxelShape.bounds().move(pos));
            drawFrame(event.getPoseStack().last(), frame, shape, camera);
        }
        minecraft.renderBuffers().bufferSource().endBatch(frameType);
    }

    private static void drawFrame(PoseStack.Pose pose, VertexConsumer consumer, AABB bounds, Vec3 camera)
    {
        double minX = bounds.minX - camera.x;
        double minY = bounds.minY - camera.y;
        double minZ = bounds.minZ - camera.z;
        double maxX = bounds.maxX - camera.x;
        double maxY = bounds.maxY - camera.y;
        double maxZ = bounds.maxZ - camera.z;

        quad(consumer, pose, minX, minY, minZ - SURFACE_OFFSET, maxX, maxY, minZ - SURFACE_OFFSET,
                0, 0, -1, FacePlane.XY);
        quad(consumer, pose, maxX, minY, maxZ + SURFACE_OFFSET, minX, maxY, maxZ + SURFACE_OFFSET,
                0, 0, 1, FacePlane.XY);
        quad(consumer, pose, minX - SURFACE_OFFSET, minY, maxZ, minX - SURFACE_OFFSET, maxY, minZ,
                -1, 0, 0, FacePlane.ZY);
        quad(consumer, pose, maxX + SURFACE_OFFSET, minY, minZ, maxX + SURFACE_OFFSET, maxY, maxZ,
                1, 0, 0, FacePlane.ZY);
        quad(consumer, pose, minX, minY - SURFACE_OFFSET, maxZ, maxX, minY - SURFACE_OFFSET, minZ,
                0, -1, 0, FacePlane.XZ);
        quad(consumer, pose, minX, maxY + SURFACE_OFFSET, minZ, maxX, maxY + SURFACE_OFFSET, maxZ,
                0, 1, 0, FacePlane.XZ);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             double minA, double minB, double minC,
                             double maxA, double maxB, double maxC,
                             float normalX, float normalY, float normalZ, FacePlane plane)
    {
        switch (plane)
        {
            case XY -> {
                vertex(consumer, pose, minA, minB, minC, 0, 1, normalX, normalY, normalZ);
                vertex(consumer, pose, maxA, minB, minC, 1, 1, normalX, normalY, normalZ);
                vertex(consumer, pose, maxA, maxB, maxC, 1, 0, normalX, normalY, normalZ);
                vertex(consumer, pose, minA, maxB, maxC, 0, 0, normalX, normalY, normalZ);
            }
            case ZY -> {
                vertex(consumer, pose, minA, minB, minC, 0, 1, normalX, normalY, normalZ);
                vertex(consumer, pose, minA, minB, maxC, 1, 1, normalX, normalY, normalZ);
                vertex(consumer, pose, maxA, maxB, maxC, 1, 0, normalX, normalY, normalZ);
                vertex(consumer, pose, maxA, maxB, minC, 0, 0, normalX, normalY, normalZ);
            }
            case XZ -> {
                vertex(consumer, pose, minA, minB, minC, 0, 1, normalX, normalY, normalZ);
                vertex(consumer, pose, maxA, minB, minC, 1, 1, normalX, normalY, normalZ);
                vertex(consumer, pose, maxA, maxB, maxC, 1, 0, normalX, normalY, normalZ);
                vertex(consumer, pose, minA, maxB, maxC, 0, 0, normalX, normalY, normalZ);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               double x, double y, double z, float u, float v,
                               float normalX, float normalY, float normalZ)
    {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private enum FacePlane { XY, ZY, XZ }

    private static void accept(CompoundTag data)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Identifier payloadDimension;
        try
        {
            payloadDimension = Identifier.parse(data.getStringOr("dimension", ""));
        }
        catch (RuntimeException ignored)
        {
            return;
        }
        if (!payloadDimension.equals(minecraft.level.dimension().identifier())) return;
        HashMap<Long, List<PositionedVisual>> next = new HashMap<>();
        ListTag list = data.getListOrEmpty("positions");
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            try
            {
                BlockPos position = BlockPos.of(entry.getLongOr("pos", 0L));
                BindingVisual visual = new BindingVisual(Identifier.parse(entry.getStringOr("block", "minecraft:air")),
                        entry.getBooleanOr("provisioner_target", false));
                next.computeIfAbsent(ChunkPos.pack(position.getX() >> 4, position.getZ() >> 4),
                        ignored -> new ArrayList<>()).add(new PositionedVisual(position, visual));
            }
            catch (RuntimeException ignored) {}
        }
        BY_CHUNK.clear();
        next.forEach((chunk, values) -> BY_CHUNK.put(chunk, List.copyOf(values)));
        dimension = payloadDimension;
    }

    private record BindingVisual(Identifier blockId, boolean provisionerTarget) {}
    private record PositionedVisual(BlockPos position, BindingVisual visual) {}
}
