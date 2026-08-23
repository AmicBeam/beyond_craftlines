package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlocks;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import net.neoforged.neoforge.client.model.quad.BakedNormals;
import net.neoforged.neoforge.client.model.quad.QuadTransforms;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Adds each provisioner's target item icon directly to its chunk-baked block material. */
public final class ProvisionerMaterialModel extends DelegateBlockStateModel
{
    private static final float FACE_OFFSET = 0.0005F;
    private static final Map<Item, Boolean> TEXT_FALLBACKS = new ConcurrentHashMap<>();

    private ProvisionerMaterialModel(BlockStateModel original)
    {
        super(original);
    }

    public static void install(ModelEvent.ModifyBakingResult event)
    {
        TEXT_FALLBACKS.clear();
        ProvisionerFallbackLabelRenderer.clearLayoutCache();
        event.getBakingResult().blockStateModels().replaceAll((state, model) ->
                state.is(CraftlinesBlocks.CRAFTLINE_PROVISIONER.get())
                        ? new ProvisionerMaterialModel(model) : model);
    }

    /** Returns whether this item has no static quads and therefore needs the text overlay. */
    public static boolean usesTextFallback(ItemStack icon)
    {
        if (icon.isEmpty()) return false;
        return TEXT_FALLBACKS.computeIfAbsent(icon.getItem(), ignored -> captureItemQuads(icon).isEmpty());
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                             RandomSource random, List<BlockStateModelPart> parts)
    {
        delegate.collectParts(level, pos, state, random, parts);
        IconPart icon = iconPart(level, pos, state);
        if (icon != null) parts.add(icon);
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                    RandomSource random)
    {
        Object originalKey = delegate.createGeometryKey(level, pos, state, random);
        ItemStack icon = targetIcon(level, pos);
        boolean visible = CraftlinesConfig.SHOW_PROVISIONER_TARGET_MATERIAL.get() && !icon.isEmpty();
        return new GeometryKey(originalKey == null ? delegate : originalKey, visible,
                visible ? icon.getItem() : null, visible ? icon.immutableComponents() : null);
    }

    @Override
    @BakedQuad.MaterialFlags
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state)
    {
        return delegate.materialFlags(level, pos, state)
                | (iconVisible(level, pos) ? BakedQuad.FLAG_TRANSLUCENT : 0);
    }

    private static boolean iconVisible(BlockAndTintGetter level, BlockPos pos)
    {
        return CraftlinesConfig.SHOW_PROVISIONER_TARGET_MATERIAL.get() && !targetIcon(level, pos).isEmpty();
    }

    private static ItemStack targetIcon(BlockAndTintGetter level, BlockPos pos)
    {
        ItemStack icon = level.getModelData(pos).get(CraftlineProvisionerBlockEntity.TARGET_ITEM_ICON);
        return icon == null ? ItemStack.EMPTY : icon;
    }

    private IconPart iconPart(BlockAndTintGetter level, BlockPos pos, BlockState state)
    {
        ItemStack icon = targetIcon(level, pos);
        if (!CraftlinesConfig.SHOW_PROVISIONER_TARGET_MATERIAL.get() || icon.isEmpty()) return null;

        List<ColoredQuad> itemQuads = captureItemQuads(icon);
        if (itemQuads.isEmpty())
        {
            TEXT_FALLBACKS.put(icon.getItem(), true);
            return null;
        }
        TEXT_FALLBACKS.put(icon.getItem(), false);

        IconBounds bounds = iconBounds(itemQuads);
        EnumMap<Direction, List<BakedQuad>> byFace = new EnumMap<>(Direction.class);
        int flags = 0;
        for (Direction face : Direction.Plane.HORIZONTAL)
        {
            Transformation transform = iconTransform(face, bounds);
            ArrayList<BakedQuad> transformed = new ArrayList<>(itemQuads.size());
            for (ColoredQuad source : itemQuads)
            {
                BakedQuad moved = QuadTransforms.applyTransformation(source.quad(), transform);
                var sourceMaterial = moved.materialInfo();
                var iconMaterial = new BakedQuad.MaterialInfo(sourceMaterial.sprite(),
                        ChunkSectionLayer.TRANSLUCENT, Sheets.translucentItemSheet(), -1,
                        false, 15, false);
                transformed.add(new BakedQuad(moved.position0(), moved.position1(), moved.position2(),
                        moved.position3(), moved.packedUV0(), moved.packedUV1(), moved.packedUV2(),
                        moved.packedUV3(), face, iconMaterial, BakedNormals.UNSPECIFIED,
                        source.color() == -1 ? moved.bakedColors() : BakedColors.of(source.color())));
                flags |= iconMaterial.flags();
            }
            byFace.put(face, List.copyOf(transformed));
        }
        return new IconPart(byFace, delegate.particleMaterial(level, pos, state), flags);
    }

    private static List<ColoredQuad> captureItemQuads(ItemStack icon)
    {
        CapturingItemState itemState = new CapturingItemState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(
                itemState, icon, ItemDisplayContext.NONE, null, null, 42);
        return itemState.quads();
    }

    private static IconBounds iconBounds(List<ColoredQuad> quads)
    {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (ColoredQuad colored : quads)
        {
            BakedQuad quad = colored.quad();
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++)
            {
                Vector3fc position = quad.position(vertex);
                minX = Math.min(minX, position.x());
                minY = Math.min(minY, position.y());
                minZ = Math.min(minZ, position.z());
                maxX = Math.max(maxX, position.x());
                maxY = Math.max(maxY, position.y());
                maxZ = Math.max(maxZ, position.z());
            }
        }
        float extent = Math.max(maxX - minX, maxY - minY);
        if (!Float.isFinite(extent) || extent <= 0.0F)
            return new IconBounds(0.5F, 0.5F, 0.5F, 0.5F);
        return new IconBounds((minX + maxX) * 0.5F, (minY + maxY) * 0.5F,
                (minZ + maxZ) * 0.5F, 0.5F / extent);
    }

    private static Transformation iconTransform(Direction face, IconBounds bounds)
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
        return new Transformation(pose.last().pose());
    }

    private static final class CapturingItemState extends ItemStackRenderState
    {
        private final List<LayerRenderState> capturedLayers = new ArrayList<>();

        @Override
        public LayerRenderState newLayer()
        {
            LayerRenderState layer = super.newLayer();
            capturedLayers.add(layer);
            return layer;
        }

        private List<ColoredQuad> quads()
        {
            ArrayList<ColoredQuad> result = new ArrayList<>();
            for (LayerRenderState layer : capturedLayers)
            {
                IntList tints = layer.tintLayers();
                for (BakedQuad quad : layer.prepareQuadList())
                {
                    int tint = quad.materialInfo().tintIndex();
                    result.add(new ColoredQuad(quad,
                            tint >= 0 && tint < tints.size() ? tints.getInt(tint) : -1));
                }
            }
            return result;
        }
    }

    private static final class IconPart implements BlockStateModelPart
    {
        private final EnumMap<Direction, List<BakedQuad>> byFace;
        private final Material.Baked particle;
        private final int flags;

        private IconPart(EnumMap<Direction, List<BakedQuad>> byFace, Material.Baked particle, int flags)
        {
            this.byFace = byFace;
            this.particle = particle;
            this.flags = flags;
        }

        @Override public List<BakedQuad> getQuads(Direction side)
        {
            return side == null ? List.of() : byFace.getOrDefault(side, List.of());
        }

        @Override public TriState ambientOcclusion() { return TriState.FALSE; }
        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public Material.Baked particleMaterial() { return particle; }
        @Override public int materialFlags() { return flags; }
    }

    private record ColoredQuad(BakedQuad quad, int color) {}
    private record IconBounds(float centerX, float centerY, float centerZ, float scale) {}
    private record GeometryKey(Object original, boolean visible, Object item, Object components) {}
}
