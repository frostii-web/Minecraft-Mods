package dev.frostii.irg.mixin;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import dev.frostii.irg.resource.ProductionTracker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Observe actual non-simulated output insertions, after chance rolls. Never modifies the recipe or its output. */
@Mixin(targets = "com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler", remap = false)
public abstract class GtOutputMixin {
    @Unique private ItemStack[] irg$before;

    @Inject(method = "handleRecipeInner", at = @At("HEAD"), remap = false, require = 1)
    private void irg$begin(IO io, GTRecipe recipe, List<Ingredient> left, boolean simulate, CallbackInfoReturnable<List<Ingredient>> callback) {
        if (!simulate && io == IO.OUT) irg$before = ProductionTracker.snapshot((IItemHandler) this);
    }

    @Inject(method = "handleRecipeInner", at = @At("RETURN"), remap = false, require = 1)
    private void irg$completed(IO io, GTRecipe recipe, List<Ingredient> left, boolean simulate, CallbackInfoReturnable<List<Ingredient>> callback) {
        if (!simulate && io == IO.OUT && irg$before != null) {
            ItemStack[] before = irg$before;
            irg$before = null;
            ProductionTracker.completedGtOutput(this, before);
        }
    }
}
