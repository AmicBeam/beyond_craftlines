package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Draws a localized target name when no target icon can be baked into the provisioner model. */
public final class ProvisionerFallbackLabelRenderer implements BlockEntityRenderer<
        CraftlineProvisionerBlockEntity, ProvisionerFallbackLabelRenderer.LabelState>
{
    private static final float MAX_LABEL_WIDTH = 0.72F;
    private static final float MAX_TEXT_SCALE = 1.0F / 64.0F;
    private static final float FACE_OFFSET = 0.001F;
    private static final int FULL_BRIGHT = 15728880;
    private static final Map<String, LabelLayout> LAYOUTS = new ConcurrentHashMap<>();

    private final Font font;

    public ProvisionerFallbackLabelRenderer(BlockEntityRendererProvider.Context context)
    {
        this.font = context.font();
    }

    public static void clearLayoutCache() { LAYOUTS.clear(); }

    @Override public LabelState createRenderState() { return new LabelState(); }

    @Override
    public void extractRenderState(CraftlineProvisionerBlockEntity provisioner, LabelState state,
                                   float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(
                provisioner, state, partialTicks, cameraPosition, breakProgress);
        state.layout = null;
        if (!CraftlinesConfig.SHOW_PROVISIONER_TARGET_MATERIAL.get()) return;
        ItemStack icon = provisioner.targetItemIcon();
        String name = label(provisioner, icon);
        if (!name.isBlank()) state.layout = LAYOUTS.computeIfAbsent(name, this::layout);
    }

    private static String label(CraftlineProvisionerBlockEntity provisioner, ItemStack icon)
    {
        if (!icon.isEmpty()) return ProvisionerMaterialModel.usesTextFallback(icon)
                ? icon.getHoverName().getString() : "";
        if (provisioner.recipeCandidates().size() != 1) return "";
        var type = provisioner.recipeCandidates().iterator().next();
        return JeiCatalystIndex.recipeTypeTitle(type).map(component -> component.getString())
                .filter(value -> !value.isBlank()).orElse(type.toString());
    }

    private LabelLayout layout(String text)
    {
        FormattedCharSequence visual = net.minecraft.network.chat.Component.literal(text)
                .getVisualOrderText();
        int width = Math.max(1, font.width(visual));
        float scale = Math.min(MAX_TEXT_SCALE, MAX_LABEL_WIDTH / width);
        return new LabelLayout(visual, width, scale);
    }

    @Override
    public void submit(LabelState state, PoseStack poseStack, SubmitNodeCollector nodes,
                       CameraRenderState camera)
    {
        if (state.layout == null) return;
        for (Direction face : Direction.Plane.HORIZONTAL)
        {
            poseStack.pushPose();
            poseStack.translate(0.5F + face.getStepX() * (0.5F + FACE_OFFSET), 0.5F,
                    0.5F + face.getStepZ() * (0.5F + FACE_OFFSET));
            poseStack.mulPose(Axis.YP.rotationDegrees(switch (face)
            {
                case SOUTH -> 0.0F;
                case WEST -> -90.0F;
                case NORTH -> 180.0F;
                case EAST -> 90.0F;
                default -> 0.0F;
            }));
            poseStack.scale(state.layout.scale(), -state.layout.scale(), state.layout.scale());
            nodes.submitText(poseStack, -state.layout.width() * 0.5F, -font.lineHeight * 0.5F,
                    state.layout.text(), false, Font.DisplayMode.POLYGON_OFFSET,
                    FULL_BRIGHT, 0xFFFFFFFF, 0, 0);
            poseStack.popPose();
        }
    }

    @Override public int getViewDistance() { return 32; }

    public static final class LabelState extends BlockEntityRenderState
    {
        private @Nullable LabelLayout layout;
    }

    private record LabelLayout(FormattedCharSequence text, int width, float scale) {}
}
