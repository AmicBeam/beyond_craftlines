package net.minecraftforge.network.handling;

import net.minecraft.world.entity.player.Player;

public interface IPayloadContext {
    Player player();
    void enqueueWork(Runnable work);
    void reply(Object payload);
}
