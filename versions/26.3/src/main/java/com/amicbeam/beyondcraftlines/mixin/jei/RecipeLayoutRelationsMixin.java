package com.amicbeam.beyondcraftlines.mixin.jei;

import com.amicbeam.beyondcraftlines.client.integration.jei.JeiLayoutRelations;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Pseudo
@Mixin(targets = "mezz.jei.library.gui.recipes.layout.builder.RecipeLayoutBuilder", remap = false)
public abstract class RecipeLayoutRelationsMixin
{
    @Shadow @Final private List<?> visibleSlots;
    @Shadow @Final private List<List<?>> focusLinkedSlots;

    @Inject(method = "buildRecipeLayout", at = @At("RETURN"), remap = false)
    private void beyondCraftlines$rememberLinks(CallbackInfoReturnable<Object> callback)
    { JeiLayoutRelations.record(callback.getReturnValue(), visibleSlots, focusLinkedSlots); }
}
