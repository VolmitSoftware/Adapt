package art.arcane.adapt.mod.feature.mixin;

import art.arcane.adapt.mod.feature.recipe.ExactPlacement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Recipe-book placement of Adapt recipes with exact or matching ingredients (see {@link ExactPlacement}). */
@Mixin(ServerPlaceRecipe.class)
public abstract class ServerPlaceRecipeHooks {
    @WrapOperation(method = "recipeClicked(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/crafting/RecipeHolder;Z)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Inventory;fillStackedContents(Lnet/minecraft/world/entity/player/StackedContents;)V"))
    private void adapt$exactInventory(Inventory inventory, StackedContents contents, Operation<Void> original,
                                      @Local(argsOnly = true) RecipeHolder<?> recipe) {
        if (ExactPlacement.narrows(recipe)) {
            ExactPlacement.fillInventory(inventory, contents, recipe);
        } else {
            original.call(inventory, contents);
        }
    }

    @WrapOperation(method = "recipeClicked(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/crafting/RecipeHolder;Z)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/RecipeBookMenu;fillCraftSlotsStackedContents(Lnet/minecraft/world/entity/player/StackedContents;)V"))
    private void adapt$exactGrid(RecipeBookMenu<?, ?> menu, StackedContents contents, Operation<Void> original,
                                 @Local(argsOnly = true) RecipeHolder<?> recipe) {
        if (ExactPlacement.narrows(recipe)) {
            ExactPlacement.fillGrid(menu, contents, recipe);
        } else {
            original.call(menu, contents);
        }
    }

    @WrapOperation(method = "recipeClicked(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/crafting/RecipeHolder;Z)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/recipebook/ServerPlaceRecipe;handleRecipeClicked(Lnet/minecraft/world/item/crafting/RecipeHolder;Z)V"))
    private void adapt$exactPlacing(ServerPlaceRecipe<?, ?> placer, RecipeHolder<?> recipe, boolean all, Operation<Void> original) {
        ExactPlacement.placing(recipe, () -> original.call(placer, recipe, all));
    }

    @WrapOperation(method = "moveItemToGrid(Lnet/minecraft/world/inventory/Slot;Lnet/minecraft/world/item/ItemStack;I)I", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Inventory;findSlotMatchingUnusedItem(Lnet/minecraft/world/item/ItemStack;)I"))
    private int adapt$exactSlot(Inventory inventory, ItemStack wanted, Operation<Integer> original, @Local(argsOnly = true) Slot slot) {
        Integer found = ExactPlacement.slotFor(inventory, wanted, slot.getItem());
        return found == null ? original.call(inventory, wanted) : found;
    }
}
