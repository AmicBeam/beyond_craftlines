package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Adds each provisioner's target item icon directly to its chunk-baked block material. */
public final class ProvisionerMaterialModel extends BakedModelWrapper<BakedModel>
{
    private static final ResourceLocation PROVISIONER = ResourceLocation.fromNamespaceAndPath(
            BeyondCraftlines.MOD_ID, "craftline_provisioner");
    /** The target sprite must retain its source texture's full alpha, not cut it to an opaque mask. */
    private static final RenderType ICON_RENDER_TYPE = RenderType.translucent();
    private static final ChunkRenderTypeSet ICON_LAYER = ChunkRenderTypeSet.of(ICON_RENDER_TYPE);
    private static final float FACE_OFFSET = 0.0005F;
    private static final Map<Item, Boolean> TEXT_FALLBACKS = new ConcurrentHashMap<>();

    private ProvisionerMaterialModel(BakedModel original)
    {
        super(original);
    }

    public static void install(ModelEvent.ModifyBakingResult event)
    {
        TEXT_FALLBACKS.clear();
        ProvisionerFallbackLabelRenderer.clearLayoutCache();
        event.getModels().replaceAll((location, model) -> location.id().equals(PROVISIONER)
                && !location.variant().equals("inventory") ? new ProvisionerMaterialModel(model) : model);
    }

    /** Returns whether this item has no static quads and therefore needs the text overlay. */
    public static boolean usesTextFallback(ItemStack icon)
    {
        if (icon.isEmpty()) return false;
        return TEXT_FALLBACKS.computeIfAbsent(icon.getItem(), ignored ->
        {
            Minecraft minecraft = Minecraft.getInstance();
            BakedModel model = minecraft.getItemRenderer().getModel(icon, minecraft.level, null, 0);
            return model.isCustomRenderer()
                    || itemQuads(model, RandomSource.create(42L)).isEmpty();
        });
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data)
    {
        ItemStack icon = data.get(CraftlineProvisionerBlockEntity.TARGET_ITEM_ICON);
        return !CraftlinesConfig.SHOW_PROVISIONER_TARGET_MATERIAL.get() || icon == null || icon.isEmpty()
                ? originalModel.getRenderTypes(state, rand, data)
                : ChunkRenderTypeSet.union(originalModel.getRenderTypes(state, rand, data), ICON_LAYER);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType)
    {
        List<BakedQuad> base = originalModel.getQuads(state, side, rand, data, renderType);
        ItemStack icon = data.get(CraftlineProvisionerBlockEntity.TARGET_ITEM_ICON);
        if (!CraftlinesConfig.SHOW_PROVISIONER_TARGET_MATERIAL.get() || side != null
                || renderType != ICON_RENDER_TYPE || icon == null || icon.isEmpty()) return base;

        Minecraft minecraft = Minecraft.getInstance();
        BakedModel itemModel = minecraft.getItemRenderer().getModel(icon, minecraft.level, null, 0);
        if (itemModel.isCustomRenderer())
        {
            TEXT_FALLBACKS.put(icon.getItem(), true);
            return base;
        }
        List<BakedQuad> itemQuads = itemQuads(itemModel, rand);
        if (itemQuads.isEmpty())
        {
            TEXT_FALLBACKS.put(icon.getItem(), true);
            return base;
        }
        TEXT_FALLBACKS.put(icon.getItem(), false);
        IconBounds iconBounds = iconBounds(itemModel, itemQuads);

        ArrayList<BakedQuad> result = new ArrayList<>(base.size() + itemQuads.size() * 4);
        result.addAll(base);
        for (Direction face : Direction.Plane.HORIZONTAL)
        {
            var transform = QuadTransformers.applying(iconTransform(itemModel, face, iconBounds));
            for (BakedQuad source : itemQuads)
            {
                BakedQuad quad = transform.process(source);
                if (quad.isTinted())
                {
                    int color = minecraft.getItemColors().getColor(icon, quad.getTintIndex());
                    quad = QuadTransformers.applyingColor(color).process(quad);
                }
                quad = QuadTransformers.settingMaxEmissivity().process(quad);
                result.add(new BakedQuad(quad.getVertices(), -1, face, quad.getSprite(), false, false));
            }
        }
        return List.copyOf(result);
    }

    private static List<BakedQuad> itemQuads(BakedModel model, RandomSource rand)
    {
        ArrayList<BakedQuad> result = new ArrayList<>();
        rand.setSeed(42L);
        result.addAll(model.getQuads(null, null, rand, ModelData.EMPTY, null));
        for (Direction side : Direction.values())
        {
            rand.setSeed(42L);
            result.addAll(model.getQuads(null, side, rand, ModelData.EMPTY, null));
        }
        return result;
    }

    private static IconBounds iconBounds(BakedModel model, List<BakedQuad> quads)
    {
        PoseStack pose = new PoseStack();
        model.applyTransform(ItemDisplayContext.GUI, pose, false);
        pose.translate(-0.5F, -0.5F, -0.5F);
        var matrix = pose.last().pose();
        int stride = DefaultVertexFormat.BLOCK.getVertexSize() / Integer.BYTES;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (BakedQuad quad : quads)
        {
            int[] vertices = quad.getVertices();
            for (int offset = 0; offset + 2 < vertices.length; offset += stride)
            {
                Vector3f position = new Vector3f(Float.intBitsToFloat(vertices[offset]),
                        Float.intBitsToFloat(vertices[offset + 1]),
                        Float.intBitsToFloat(vertices[offset + 2])).mulPosition(matrix);
                minX = Math.min(minX, position.x());
                minY = Math.min(minY, position.y());
                minZ = Math.min(minZ, position.z());
                maxX = Math.max(maxX, position.x());
                maxY = Math.max(maxY, position.y());
                maxZ = Math.max(maxZ, position.z());
            }
        }
        float extent = Math.max(maxX - minX, maxY - minY);
        if (!Float.isFinite(extent) || extent <= 0.0F) return new IconBounds(0, 0, 0, 0.5F);
        return new IconBounds((minX + maxX) * 0.5F, (minY + maxY) * 0.5F,
                (minZ + maxZ) * 0.5F, 0.5F / extent);
    }

    private static Transformation iconTransform(BakedModel model, Direction face, IconBounds bounds)
    {
        PoseStack pose = new PoseStack();
        pose.translate(0.5F + face.getStepX() * (0.5F + FACE_OFFSET), 0.5F,
                0.5F + face.getStepZ() * (0.5F + FACE_OFFSET));
        pose.mulPose(Axis.YP.rotationDegrees(switch (face)
        {
            case SOUTH -> 0.0F;
            case WEST -> -90.0F;
            case NORTH -> 180.0F;
            case EAST -> 90.0F;
            default -> 0.0F;
        }));
        pose.scale(bounds.scale(), bounds.scale(), 0.001F);
        pose.translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());
        model.applyTransform(ItemDisplayContext.GUI, pose, false);
        pose.translate(-0.5F, -0.5F, -0.5F);
        return new Transformation(pose.last().pose());
    }

    private record IconBounds(float centerX, float centerY, float centerZ, float scale) {}
}
