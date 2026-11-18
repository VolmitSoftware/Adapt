package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.inventory.ContainerHooks;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The crafting table and 2x2 grid result, before it is stored and sent to the player. The stored result (setItem
 * argument) and the local the remote slot and slot packet read must both carry the event's result, or the next
 * broadcast resends the stored one.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuEvents {
    @ModifyArg(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/ResultContainer;setItem(ILnet/minecraft/world/item/ItemStack;)V"), index = 1)
    private static ItemStack adapt$preparing(ItemStack result, @Local(argsOnly = true) Player player, @Local(argsOnly = true) CraftingContainer grid,
                                             @Local(argsOnly = true) ResultContainer results, @Local LocalRef<ItemStack> local) {
        ItemStack shown = ContainerHooks.preparingCraft(player, grid, results, result);
        if (shown != result) {
            local.set(shown);
        }
        return shown;
    }
}
