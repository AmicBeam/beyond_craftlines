package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class SkyLogisticsEndpointDiscovery
{
    private static final String SKY_INTERFACE = "com.skylogistics.block.entity.SkyDimensionInterfaceBlockEntity";
    private static final String SKY_INTERFACE_BLOCK = "skylogistics:sky_dimension_interface";

    private SkyLogisticsEndpointDiscovery() {}

    public static List<SkyLogisticsEndpoint> find(ServerLevel level, SandboxSession session,
                                                   StructureSnapshot snapshot)
    {
        if (level == null || session == null || snapshot == null) return List.of();
        List<SkyLogisticsEndpoint> result = new ArrayList<>();
        BlockPos min = new BlockPos(session.slot().originX() - 2, session.slot().originY() - 2,
                session.slot().originZ() - 2);
        BlockPos max = new BlockPos(session.slot().maxX(snapshot.size().getX()) + 2,
                session.slot().maxY(snapshot.size().getY()) + 2,
                session.slot().maxZ(snapshot.size().getZ()) + 2);
        for (BlockPos pos : BlockPos.betweenClosed(min, max))
        {
            BlockEntity entity = level.getBlockEntity(pos);
            if (!isSkyInterface(entity)) continue;
            Integer network = invokeInt(entity, "getDimensionNetworkId");
            if (network == null || network < 0) continue;
            Direction direction = invokeDirection(entity, "getSingleEndpointDirection");
            if (direction == null) direction = Direction.NORTH;
            result.add(new SkyLogisticsEndpoint(pos.immutable(), network, direction,
                    invokeBoolean(entity, "isItemsEnabled", direction),
                    invokeBoolean(entity, "isFluidsEnabled", direction),
                    invokeBoolean(entity, "isEnergyEnabled", direction)));
        }
        return List.copyOf(result);
    }

    public static boolean isSkyInterface(BlockEntity entity)
    {
        if (entity == null) return false;
        Class<?> type = entity.getClass();
        while (type != null)
        {
            if (SKY_INTERFACE.equals(type.getName())) return true;
            type = type.getSuperclass();
        }
        return SKY_INTERFACE_BLOCK.equals(entity.getBlockState().getBlock().builtInRegistryHolder().key().location().toString());
    }

    private static Integer invokeInt(Object target, String name)
    {
        try { return (Integer) target.getClass().getMethod(name).invoke(target); }
        catch (ReflectiveOperationException | ClassCastException ignored) { return null; }
    }

    private static Direction invokeDirection(Object target, String name)
    {
        try { return (Direction) target.getClass().getMethod(name).invoke(target); }
        catch (ReflectiveOperationException | ClassCastException ignored) { return null; }
    }

    private static boolean invokeBoolean(Object target, String name, Direction direction)
    {
        try
        {
            Method method = target.getClass().getMethod(name, Direction.class);
            return (Boolean) method.invoke(target, direction);
        }
        catch (ReflectiveOperationException | ClassCastException ignored) { return true; }
    }
}
