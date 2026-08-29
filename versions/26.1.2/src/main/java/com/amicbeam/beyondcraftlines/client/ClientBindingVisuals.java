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
import net.minecraft.core.Direction;
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
    private static BlockPos selectedProvisioner;
    private static List<BoundFace> boundFaces = List.of();

    private ClientBindingVisuals() {}

    public static void initialize()
    {
        BindingVisualsPayload.clientReceiver = ClientBindingVisuals::accept;
    }

    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event)
    {
        BY_CHUNK.clear();
        dimension = null;
        selectedProvisioner = null;
        boundFaces = List.of();
        ClientPacketDistributor.sendToServer(new RequestBindingVisualsPayload());
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        BY_CHUNK.clear();
        dimension = null;
        selectedProvisioner = null;
        boundFaces = List.of();
    }

    public static boolean isBoundMachine(BlockPos position, Identifier blockId)
    {
        return BY_CHUNK.getOrDefault(ChunkPos.pack(position.getX() >> 4, position.getZ() >> 4), List.of())
                .stream().anyMatch(entry -> entry.position().equals(position)
                        && entry.visual().blockId().equals(blockId));
    }

    public static boolean isEditingProvisionerConnections()
    {
        return selectedProvisioner != null;
    }

    public static void render(RenderLevelStageEvent.AfterTranslucentParticles event)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        Identifier currentDimension = minecraft.level.dimension().identifier();
        if (!currentDimension.equals(dimension))
        {
            dimension = currentDimension;
            BY_CHUNK.clear();
            selectedProvisioner = null;
            boundFaces = List.of();
            ClientPacketDistributor.sendToServer(new RequestBindingVisualsPayload());
            return;
        }

        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        boolean holdingLinker = minecraft.player.getMainHandItem().is(
                com.amicbeam.beyondcraftlines.common.init.CraftlinesItems.NETWORK_LINKER.get())
                || minecraft.player.getOffhandItem().is(
                com.amicbeam.beyondcraftlines.common.init.CraftlinesItems.NETWORK_LINKER.get());
        boolean editingVisualActive = holdingLinker && selectedProvisioner != null;
        int renderDistance = CraftlinesConfig.BOUND_MACHINE_FRAME_RENDER_DISTANCE.get();
        double renderDistanceSquared = (double) renderDistance * renderDistance;
        int renderChunkRadius = (renderDistance + 15) / 16 + 1;
        RenderType frameType = RenderTypes.entityCutout(FRAME_TEXTURE);
        VertexConsumer frame = minecraft.renderBuffers().bufferSource().getBuffer(frameType);
        int cameraChunkX = net.minecraft.util.Mth.floor(camera.x) >> 4;
        int cameraChunkZ = net.minecraft.util.Mth.floor(camera.z) >> 4;
        if (CraftlinesConfig.SHOW_BOUND_MACHINE_FRAMES.get())
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
                drawFrame(event.getPoseStack().last(), frame, bounds(minecraft, pos), camera);
            }
        if (CraftlinesConfig.SHOW_PROVISIONER_BOUND_FACE_FRAMES.get())
            for (BoundFace connection : boundFaces)
                if ((!connection.editing() || !editingVisualActive)
                        && minecraft.level.isLoaded(connection.position())
                        && connection.position().distToCenterSqr(camera) <= renderDistanceSquared
                        && BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(connection.position()).getBlock())
                        .equals(connection.blockId()))
                    drawBoundFace(event.getPoseStack().last(), frame, bounds(minecraft, connection.position()),
                            camera, connection.face());
        minecraft.renderBuffers().bufferSource().endBatch(frameType);
        if (holdingLinker && selectedProvisioner != null)
        {
            RenderType highlightType = RenderTypes.lines();
            VertexConsumer highlight = minecraft.renderBuffers().bufferSource().getBuffer(highlightType);
            if (minecraft.level.isLoaded(selectedProvisioner))
                drawHighlightBox(event.getPoseStack().last(), highlight,
                        bounds(minecraft, selectedProvisioner), camera, 255, 255, 0);
            for (BoundFace connection : boundFaces)
                if (connection.editing() && minecraft.level.isLoaded(connection.position())
                        && BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(connection.position()).getBlock())
                        .equals(connection.blockId()))
                {
                    int red = connection.extracting() ? 255 : 0;
                    int green = connection.extracting() ? 100 : 128;
                    int blue = connection.extracting() ? 0 : 255;
                    drawFaceHighlight(event.getPoseStack().last(), highlight,
                            bounds(minecraft, connection.position()), camera, connection.face(),
                            SURFACE_OFFSET, red, green, blue);
                    if (minecraft.level.isLoaded(selectedProvisioner))
                        drawConnectionLine(event.getPoseStack().last(), highlight,
                                bounds(minecraft, selectedProvisioner),
                                bounds(minecraft, connection.position()), camera, connection.face(),
                                red, green, blue);
                }
            if (minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                    && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                    && !hit.getBlockPos().equals(selectedProvisioner)
                    && minecraft.level.getBlockEntity(hit.getBlockPos()) != null)
                drawFaceHighlight(event.getPoseStack().last(), highlight,
                        bounds(minecraft, hit.getBlockPos()), camera, hit.getDirection(),
                        SURFACE_OFFSET, 255, 255, 0);
            minecraft.renderBuffers().bufferSource().endBatch(highlightType);
        }
    }

    private static AABB bounds(Minecraft minecraft, BlockPos position)
    {
        VoxelShape shape = minecraft.level.getBlockState(position).getShape(minecraft.level, position);
        return shape.isEmpty() ? new AABB(position) : shape.bounds().move(position);
    }

    private static void drawConnectionLine(PoseStack.Pose pose, VertexConsumer consumer,
                                           AABB provisionerBounds, AABB targetBounds, Vec3 camera,
                                           Direction face, int red, int green, int blue)
    {
        Vec3 start = provisionerBounds.getCenter();
        Vec3 end = targetBounds.getCenter();
        end = switch (face)
        {
            case NORTH -> new Vec3(end.x, end.y, targetBounds.minZ - SURFACE_OFFSET);
            case SOUTH -> new Vec3(end.x, end.y, targetBounds.maxZ + SURFACE_OFFSET);
            case WEST -> new Vec3(targetBounds.minX - SURFACE_OFFSET, end.y, end.z);
            case EAST -> new Vec3(targetBounds.maxX + SURFACE_OFFSET, end.y, end.z);
            case DOWN -> new Vec3(end.x, targetBounds.minY - SURFACE_OFFSET, end.z);
            case UP -> new Vec3(end.x, targetBounds.maxY + SURFACE_OFFSET, end.z);
        };
        line(consumer, pose,
                start.x - camera.x, start.y - camera.y, start.z - camera.z,
                end.x - camera.x, end.y - camera.y, end.z - camera.z,
                red, green, blue);
    }

    private static void drawBoundFace(PoseStack.Pose pose, VertexConsumer consumer, AABB bounds,
                                      Vec3 camera, Direction face)
    {
        double minX = bounds.minX - camera.x, minY = bounds.minY - camera.y, minZ = bounds.minZ - camera.z;
        double maxX = bounds.maxX - camera.x, maxY = bounds.maxY - camera.y, maxZ = bounds.maxZ - camera.z;
        switch (face)
        {
            case NORTH -> quad(consumer, pose, minX, minY, minZ - SURFACE_OFFSET,
                    maxX, maxY, minZ - SURFACE_OFFSET, 0, 0, -1, FacePlane.XY);
            case SOUTH -> quad(consumer, pose, maxX, minY, maxZ + SURFACE_OFFSET,
                    minX, maxY, maxZ + SURFACE_OFFSET, 0, 0, 1, FacePlane.XY);
            case WEST -> quad(consumer, pose, minX - SURFACE_OFFSET, minY, maxZ,
                    minX - SURFACE_OFFSET, maxY, minZ, -1, 0, 0, FacePlane.ZY);
            case EAST -> quad(consumer, pose, maxX + SURFACE_OFFSET, minY, minZ,
                    maxX + SURFACE_OFFSET, maxY, maxZ, 1, 0, 0, FacePlane.ZY);
            case DOWN -> quad(consumer, pose, minX, minY - SURFACE_OFFSET, maxZ,
                    maxX, minY - SURFACE_OFFSET, minZ, 0, -1, 0, FacePlane.XZ);
            case UP -> quad(consumer, pose, minX, maxY + SURFACE_OFFSET, minZ,
                    maxX, maxY + SURFACE_OFFSET, maxZ, 0, 1, 0, FacePlane.XZ);
        }
    }

    private static void drawHighlightBox(PoseStack.Pose pose, VertexConsumer consumer, AABB bounds,
                                         Vec3 camera, int red, int green, int blue)
    {
        double minX = bounds.minX - camera.x - SURFACE_OFFSET;
        double minY = bounds.minY - camera.y - SURFACE_OFFSET;
        double minZ = bounds.minZ - camera.z - SURFACE_OFFSET;
        double maxX = bounds.maxX - camera.x + SURFACE_OFFSET;
        double maxY = bounds.maxY - camera.y + SURFACE_OFFSET;
        double maxZ = bounds.maxZ - camera.z + SURFACE_OFFSET;
        line(consumer, pose, minX, minY, minZ, maxX, minY, minZ, red, green, blue);
        line(consumer, pose, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue);
        line(consumer, pose, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue);
        line(consumer, pose, minX, minY, maxZ, minX, minY, minZ, red, green, blue);
        line(consumer, pose, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue);
        line(consumer, pose, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue);
        line(consumer, pose, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue);
        line(consumer, pose, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue);
        line(consumer, pose, minX, minY, minZ, minX, maxY, minZ, red, green, blue);
        line(consumer, pose, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue);
        line(consumer, pose, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue);
        line(consumer, pose, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue);
    }

    private static void drawFaceHighlight(PoseStack.Pose pose, VertexConsumer consumer, AABB bounds,
                                          Vec3 camera, Direction face, double offset,
                                          int red, int green, int blue)
    {
        double minX = bounds.minX - camera.x, minY = bounds.minY - camera.y, minZ = bounds.minZ - camera.z;
        double maxX = bounds.maxX - camera.x, maxY = bounds.maxY - camera.y, maxZ = bounds.maxZ - camera.z;
        switch (face)
        {
            case NORTH -> faceLines(consumer, pose, minX, minY, minZ - offset,
                    maxX, maxY, minZ - offset, FacePlane.XY, red, green, blue);
            case SOUTH -> faceLines(consumer, pose, minX, minY, maxZ + offset,
                    maxX, maxY, maxZ + offset, FacePlane.XY, red, green, blue);
            case WEST -> faceLines(consumer, pose, minX - offset, minY, minZ,
                    minX - offset, maxY, maxZ, FacePlane.ZY, red, green, blue);
            case EAST -> faceLines(consumer, pose, maxX + offset, minY, minZ,
                    maxX + offset, maxY, maxZ, FacePlane.ZY, red, green, blue);
            case DOWN -> faceLines(consumer, pose, minX, minY - offset, minZ,
                    maxX, minY - offset, maxZ, FacePlane.XZ, red, green, blue);
            case UP -> faceLines(consumer, pose, minX, maxY + offset, minZ,
                    maxX, maxY + offset, maxZ, FacePlane.XZ, red, green, blue);
        }
    }

    private static void faceLines(VertexConsumer consumer, PoseStack.Pose pose,
                                  double minA, double minB, double minC,
                                  double maxA, double maxB, double maxC, FacePlane plane,
                                  int red, int green, int blue)
    {
        switch (plane)
        {
            case XY -> {
                line(consumer, pose, minA, minB, minC, maxA, minB, minC, red, green, blue);
                line(consumer, pose, maxA, minB, minC, maxA, maxB, maxC, red, green, blue);
                line(consumer, pose, maxA, maxB, maxC, minA, maxB, maxC, red, green, blue);
                line(consumer, pose, minA, maxB, maxC, minA, minB, minC, red, green, blue);
            }
            case ZY -> {
                line(consumer, pose, minA, minB, minC, minA, minB, maxC, red, green, blue);
                line(consumer, pose, minA, minB, maxC, maxA, maxB, maxC, red, green, blue);
                line(consumer, pose, maxA, maxB, maxC, maxA, maxB, minC, red, green, blue);
                line(consumer, pose, maxA, maxB, minC, minA, minB, minC, red, green, blue);
            }
            case XZ -> {
                line(consumer, pose, minA, minB, minC, maxA, minB, minC, red, green, blue);
                line(consumer, pose, maxA, minB, minC, maxA, maxB, maxC, red, green, blue);
                line(consumer, pose, maxA, maxB, maxC, minA, maxB, maxC, red, green, blue);
                line(consumer, pose, minA, maxB, maxC, minA, minB, minC, red, green, blue);
            }
        }
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             int red, int green, int blue)
    {
        float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
        float length = net.minecraft.util.Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0.0F) return;
        dx /= length; dy /= length; dz /= length;
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(red, green, blue, 255).setNormal(pose, dx, dy, dz);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(red, green, blue, 255).setNormal(pose, dx, dy, dz);
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
        selectedProvisioner = data.contains("selected_provisioner")
                ? BlockPos.of(data.getLongOr("selected_provisioner", 0L)) : null;
        ArrayList<BoundFace> connections = new ArrayList<>();
        ListTag connectionList = data.getListOrEmpty("bound_provisioner_faces");
        for (int i = 0; i < connectionList.size(); i++)
        {
            CompoundTag entry = connectionList.getCompoundOrEmpty(i);
            Identifier block = Identifier.tryParse(entry.getStringOr("block", ""));
            if (block != null) connections.add(new BoundFace(
                    BlockPos.of(entry.getLongOr("pos", 0L)),
                    Direction.from3DDataValue(entry.getIntOr("face", 0)), block,
                    entry.getIntOr("role", 0) == 1,
                    entry.getBooleanOr("editing", false)));
        }
        boundFaces = List.copyOf(connections);
        dimension = payloadDimension;
    }

    private record BindingVisual(Identifier blockId, boolean provisionerTarget) {}
    private record PositionedVisual(BlockPos position, BindingVisual visual) {}
    private record BoundFace(BlockPos position, Direction face, Identifier blockId,
                             boolean extracting, boolean editing) {}
}
