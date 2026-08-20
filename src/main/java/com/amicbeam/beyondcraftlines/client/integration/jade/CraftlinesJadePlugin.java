package com.amicbeam.beyondcraftlines.client.integration.jade;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex;
import com.amicbeam.beyondcraftlines.common.block.CraftlineProvisionerBlock;
import com.amicbeam.beyondcraftlines.common.data.BindingSavedData;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

import java.util.Comparator;

/** Adds Craftlines recipe bindings beside BD's own network binding line. */
@WailaPlugin
public final class CraftlinesJadePlugin implements IWailaPlugin
{
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            BeyondCraftlines.MOD_ID, "provisioner_recipe_bindings");
    private static final String RECIPE_TYPES = BeyondCraftlines.MOD_ID + ".recipe_types";

    @Override
    public void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(Provider.INSTANCE, CraftlineProvisionerBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(Provider.INSTANCE, CraftlineProvisionerBlock.class);
    }

    private enum Provider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor>
    {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor)
        {
            var server = accessor.getLevel().getServer();
            if (server == null) return;
            ListTag types = new ListTag();
            BindingSavedData.get(server)
                    .recipeTypesForProvisioner(accessor.getLevel().dimension(), accessor.getPosition()).stream()
                    .sorted(Comparator.comparing(ResourceLocation::toString))
                    .forEach(type -> types.add(StringTag.valueOf(type.toString())));
            data.put(RECIPE_TYPES, types);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            ListTag types = accessor.getServerData().getList(RECIPE_TYPES, Tag.TAG_STRING);
            if (types.isEmpty())
            {
                tooltip.add(Component.translatable("tooltip.jade.beyond_craftlines.provisioner_recipe.unbound"));
                return;
            }
            for (int i = 0; i < types.size(); i++)
            {
                ResourceLocation type = ResourceLocation.tryParse(types.getString(i));
                Component title = type == null ? null : JeiCatalystIndex.recipeTypeTitle(type).orElse(null);
                if (title == null)
                    title = Component.translatable("tooltip.jade.beyond_craftlines.provisioner_recipe.unknown");
                tooltip.add(Component.translatable(
                        "tooltip.jade.beyond_craftlines.provisioner_recipe.bound", title));
            }
        }

        @Override
        public boolean shouldRequestData(BlockAccessor accessor)
        {
            return accessor.getBlockEntity() instanceof CraftlineProvisionerBlockEntity;
        }

        @Override
        public ResourceLocation getUid()
        {
            return UID;
        }
    }
}
