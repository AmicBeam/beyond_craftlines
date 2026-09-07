package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.common.block.CraftlineDashboardBlock;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineDashboardBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.InventoryMenu;

public final class CraftlineDashboardRenderer implements BlockEntityRenderer<CraftlineDashboardBlockEntity>
{
    private final Font font;

    public CraftlineDashboardRenderer(BlockEntityRendererProvider.Context context)
    { font = context.getFont(); }

    @Override public void render(CraftlineDashboardBlockEntity dashboard, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay)
    {
        var target = dashboard.target();
        if (target == null || target.isEmpty()) return;
        pose.pushPose();
        pose.translate(.5, .5, .5);
        orient(pose, dashboard.getBlockState().getValue(CraftlineDashboardBlock.FACING));
        pose.translate(0, 0, .376);

        boolean itemTarget = target instanceof ItemStackKey;
        DashboardResourceSprite.Icon resourceIcon = itemTarget ? null
                : DashboardResourceSprite.resolve(target);
        if (target instanceof ItemStackKey item)
        {
            pose.pushPose();
            pose.translate(0, .0675, -.012);
            pose.scale(.24f, .24f, .08f);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    item.getReadOnlyStack().copyWithCount(1), ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT, packedOverlay, pose, buffers,
                    dashboard.getLevel(), 0);
            pose.popPose();
        }
        else if (resourceIcon != null)
            renderResourceIcon(pose, buffers, resourceIcon, packedOverlay);

        Component name = target instanceof ItemStackKey item
                ? item.getReadOnlyStack().getHoverName()
                : target.getRender().getDisplayName(target);
        boolean visualIcon = itemTarget || resourceIcon != null;
        String label = font.plainSubstrByWidth(name.getString(), 54);
        String amount = dashboard.lastObserved() + "/" + dashboard.desiredAmount();
        pose.pushPose();
        pose.translate(0, visualIcon ? -.0625 : -.0325, -.018);
        float textScale = visualIcon ? .0065f : .0075f;
        pose.scale(-textScale, -textScale, textScale);
        font.drawInBatch(label, -font.width(label) / 2f, 0, 0xFFE8F6FF, false,
                pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET,
                0xA0000000, LightTexture.FULL_BRIGHT);
        font.drawInBatch(amount, -font.width(amount) / 2f, 10, 0xFF64D7FF, false,
                pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET,
                0xA0000000, LightTexture.FULL_BRIGHT);
        pose.popPose();
        pose.popPose();
    }

    private static void renderResourceIcon(PoseStack pose, MultiBufferSource buffers,
                                           DashboardResourceSprite.Icon icon, int packedOverlay)
    {
        VertexConsumer consumer = buffers.getBuffer(
                RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose current = pose.last();
        float half = .11f;
        float centerY = .0675f;
        float z = -.013f;
        int tint = icon.tint();
        int alpha = tint >>> 24 & 0xFF;
        if (alpha == 0) alpha = 255;
        int red = tint >> 16 & 0xFF;
        int green = tint >> 8 & 0xFF;
        int blue = tint & 0xFF;
        var sprite = icon.sprite();
        vertex(consumer, current, -half, centerY - half, z,
                sprite.getU0(), sprite.getV1(), red, green, blue, alpha, packedOverlay);
        vertex(consumer, current, half, centerY - half, z,
                sprite.getU1(), sprite.getV1(), red, green, blue, alpha, packedOverlay);
        vertex(consumer, current, half, centerY + half, z,
                sprite.getU1(), sprite.getV0(), red, green, blue, alpha, packedOverlay);
        vertex(consumer, current, -half, centerY + half, z,
                sprite.getU0(), sprite.getV0(), red, green, blue, alpha, packedOverlay);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v,
                               int red, int green, int blue, int alpha, int packedOverlay)
    {
        consumer.addVertex(pose, x, y, z).setColor(red, green, blue, alpha).setUv(u, v)
                .setOverlay(packedOverlay).setLight(LightTexture.FULL_BRIGHT)
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
}
