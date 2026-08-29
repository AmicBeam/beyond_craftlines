package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.common.block.CraftlineDashboardBlock;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineDashboardBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Blue-black front display: item targets render as icons; every resource renders a name and stock value. */
public final class CraftlineDashboardRenderer implements BlockEntityRenderer<
        CraftlineDashboardBlockEntity, CraftlineDashboardRenderer.State>
{
    private final Font font;
    private final ItemModelResolver itemModelResolver;

    public CraftlineDashboardRenderer(BlockEntityRendererProvider.Context context)
    {
        font = context.font();
        itemModelResolver = context.itemModelResolver();
    }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(CraftlineDashboardBlockEntity dashboard, State state,
                                             float partialTicks, Vec3 cameraPosition,
                                             ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(
                dashboard, state, partialTicks, cameraPosition, breakProgress);
        state.text = null;
        state.sprite = null;
        state.facing = dashboard.getBlockState().getValue(CraftlineDashboardBlock.FACING);
        ItemStack icon = dashboard.target() instanceof ItemStackKey item
                ? item.getReadOnlyStack().copyWithCount(1) : ItemStack.EMPTY;
        itemModelResolver.updateForTopItem(state.item, icon, ItemDisplayContext.FIXED,
                dashboard.getLevel(), null, (int) dashboard.getBlockPos().asLong());
        if (dashboard.target() == null || dashboard.target().isEmpty()) return;
        if (icon.isEmpty())
        {
            DashboardResourceSprite.Icon resourceIcon = DashboardResourceSprite.resolve(dashboard.target());
            if (resourceIcon != null)
            {
                state.sprite = resourceIcon.sprite();
                state.tint = resourceIcon.tint();
            }
        }
        String name = dashboard.target() instanceof ItemStackKey item
                ? item.getReadOnlyStack().getHoverName().getString()
                : dashboard.target().getRender().getDisplayName(dashboard.target()).getString();
        String value = name + "  " + dashboard.lastObserved() + "/" + dashboard.desiredAmount();
        boolean visualIcon = !state.item.isEmpty() || state.sprite != null;
        state.text = net.minecraft.network.chat.Component.literal(
                font.plainSubstrByWidth(value, visualIcon ? 40 : 48)).getVisualOrderText();
        state.width = font.width(state.text);
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector nodes,
                                 CameraRenderState camera)
    {
        if (state.text == null) return;
        pose.pushPose();
        pose.translate(.5, .5, .5);
        orient(pose, state.facing);
        if (!state.item.isEmpty())
        {
            pose.pushPose();
            pose.translate(0, .0675, .368);
            pose.scale(.24f, .24f, .08f);
            state.item.submit(pose, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        else if (state.sprite != null)
            submitResourceIcon(state, pose, nodes);
        boolean visualIcon = !state.item.isEmpty() || state.sprite != null;
        pose.translate(0, visualIcon ? -.0725 : -.0825, .360);
        float scale = visualIcon ? 1f / 120f : 1f / 112f;
        pose.scale(-scale, -scale, scale);
        nodes.submitText(pose, -state.width * .5f, 0, state.text, false,
                Font.DisplayMode.POLYGON_OFFSET, 15728880, 0xFFE8F6FF, 0, 0);
        pose.popPose();
    }

    private static void submitResourceIcon(State state, PoseStack pose, SubmitNodeCollector nodes)
    {
        pose.pushPose();
        pose.translate(0, 0, .368);
        nodes.submitCustomGeometry(pose, RenderTypes.entityTranslucent(state.sprite.atlasLocation()),
                (current, consumer) -> renderResourceIcon(current, consumer, state.sprite, state.tint));
        pose.popPose();
    }

    private static void renderResourceIcon(PoseStack.Pose pose, VertexConsumer consumer,
                                           TextureAtlasSprite sprite, int tint)
    {
        float half = .11f;
        float centerY = .0675f;
        int alpha = tint >>> 24 & 0xFF;
        if (alpha == 0) alpha = 255;
        int red = tint >> 16 & 0xFF;
        int green = tint >> 8 & 0xFF;
        int blue = tint & 0xFF;
        vertex(consumer, pose, -half, centerY - half, 0,
                sprite.getU0(), sprite.getV1(), red, green, blue, alpha);
        vertex(consumer, pose, half, centerY - half, 0,
                sprite.getU1(), sprite.getV1(), red, green, blue, alpha);
        vertex(consumer, pose, half, centerY + half, 0,
                sprite.getU1(), sprite.getV0(), red, green, blue, alpha);
        vertex(consumer, pose, -half, centerY + half, 0,
                sprite.getU0(), sprite.getV0(), red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v,
                               int red, int green, int blue, int alpha)
    {
        consumer.addVertex(pose, x, y, z).setColor(red, green, blue, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880)
                .setNormal(pose, 0, 0, -1);
    }

    private static void orient(PoseStack pose, Direction direction)
    {
        switch (direction)
        {
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(90));
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(90));
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(-90));
            default -> { }
        }
    }

    @Override public int getViewDistance() { return 32; }

    public static final class State extends BlockEntityRenderState
    {
        private final ItemStackRenderState item = new ItemStackRenderState();
        private @Nullable TextureAtlasSprite sprite;
        private int tint = 0xFFFFFFFF;
        private @Nullable FormattedCharSequence text;
        private int width;
        private Direction facing = Direction.NORTH;
    }
}
