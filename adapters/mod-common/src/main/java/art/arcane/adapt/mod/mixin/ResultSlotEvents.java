package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.InventoryHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A crafted batch handed to the player, once per take or shift-click run. */
@Mixin(ResultSlot.class)
public abstract class ResultSlotEvents {
    @Shadow
    @Final
    private Player player;

    @Shadow
    private int removeCount;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void adapt$crafted(ItemStack carried, CallbackInfo callback) {
        InventoryHooks.crafted(player, ((ResultSlot) (Object) this).container, carried, removeCount);
    }
}
