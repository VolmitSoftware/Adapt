package art.arcane.adapt.mod.feature.mixin;

import art.arcane.adapt.mod.feature.recipe.ExactPlacement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Recipe-book placement of Adapt recipes with exact or matching ingredients (see {@link ExactPlacement}). */
@Mixin(ServerPlaceRecipe.class)
public abstract class ServerPlaceRecipeHooks {
    @WrapOperation(method = "placeRecipe(Lnet/minecraft/recipebook/ServerPlaceRecipe$CraftingMenuAccess;IILjava/util/List;Ljava/util/List;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/crafting/RecipeHolder;ZZ)Lnet/minecraft/world/inventory/RecipeBookMenu$PostPlaceAction;", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Inventory;fillStackedContents(Lnet/minecraft/world/entity/player/StackedItemContents;)V"))
    private static void adapt$exactInventory(Inventory inventory, StackedItemContents contents, Operation<Void> original,
                                             @Local(argsOnly = true) RecipeHolder<?> recipe) {
        if (ExactPlacement.narrows(recipe)) {
            ExactPlacement.fillInventory(inventory, contents, recipe);
        } else {
            original.call(inventory, contents);
        }
    }

    @WrapOperation(method = "placeRecipe(Lnet/minecraft/recipebook/ServerPlaceRecipe$CraftingMenuAccess;IILjava/util/List;Ljava/util/List;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/crafting/RecipeHolder;ZZ)Lnet/minecraft/world/inventory/RecipeBookMenu$PostPlaceAction;", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/recipebook/ServerPlaceRecipe$CraftingMenuAccess;fillCraftSlotsStackedContents(Lnet/minecraft/world/entity/player/StackedItemContents;)V"))
    private static void adapt$exactGrid(ServerPlaceRecipe.CraftingMenuAccess<?> menu, StackedItemContents contents, Operation<Void> original,
                                        @Local(argsOnly = true, ordinal = 0) List<Slot> grid, @Local(argsOnly = true) RecipeHolder<?> recipe) {
        if (ExactPlacement.narrows(recipe)) {
            ExactPlacement.fillGrid(grid, contents, recipe);
        } else {
            original.call(menu, contents);
        }
    }

    @WrapOperation(method = "placeRecipe(Lnet/minecraft/recipebook/ServerPlaceRecipe$CraftingMenuAccess;IILjava/util/List;Ljava/util/List;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/crafting/RecipeHolder;ZZ)Lnet/minecraft/world/inventory/RecipeBookMenu$PostPlaceAction;", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/recipebook/ServerPlaceRecipe;tryPlaceRecipe(Lnet/minecraft/world/item/crafting/RecipeHolder;Lnet/minecraft/world/entity/player/StackedItemContents;)Lnet/minecraft/world/inventory/RecipeBookMenu$PostPlaceAction;"))
    private static RecipeBookMenu.PostPlaceAction adapt$exactPlacing(ServerPlaceRecipe<?> placer, RecipeHolder<?> recipe, StackedItemContents contents,
                                                                     Operation<RecipeBookMenu.PostPlaceAction> original) {
        return ExactPlacement.placing(recipe, () -> original.call(placer, recipe, contents));
    }

    @WrapOperation(method = "moveItemToGrid", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Inventory;findSlotMatchingCraftingIngredient(Lnet/minecraft/core/Holder;Lnet/minecraft/world/item/ItemStack;)I"))
    private int adapt$exactSlot(Inventory inventory, Holder<Item> item, ItemStack existing, Operation<Integer> original) {
        Integer slot = ExactPlacement.slotFor(inventory, item, existing);
        return slot == null ? original.call(inventory, item, existing) : slot;
    }
}
