package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.InventoryHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Enchanting: the offer is posted after vanilla's checks and before the table applies it. A changed
 * offer is handed to vanilla through its own offer lookup, so the rest of vanilla's apply runs as is.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuEvents {
    @Shadow
    @Final
    private Container enchantSlots;

    @Shadow
    @Final
    private ContainerLevelAccess access;

    @Shadow
    @Final
    public int[] costs;

    @Shadow
    private List<EnchantmentInstance> getEnchantmentList(RegistryAccess registries, ItemStack item, int button, int cost) {
        throw new AssertionError();
    }

    @Inject(method = "clickMenuButton", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/ContainerLevelAccess;execute(Ljava/util/function/BiConsumer;)V"))
    private void adapt$enchanting(Player player, int button, CallbackInfoReturnable<Boolean> callback) {
        ItemStack item = enchantSlots.getItem(0);
        int cost = costs[button];
        boolean allowed = access.evaluate((Level level, BlockPos pos) -> {
            List<EnchantmentInstance> offered = getEnchantmentList(level.registryAccess(), item, button, cost);
            return InventoryHooks.enchanting(player, level, pos, item, button, cost, offered) != null;
        }, true);
        if (!allowed) {
            InventoryHooks.enchanted();
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "clickMenuButton", at = @At("RETURN"))
    private void adapt$enchanted(Player player, int button, CallbackInfoReturnable<Boolean> callback) {
        InventoryHooks.enchanted();
    }

    @Inject(method = "getEnchantmentList", at = @At("RETURN"), cancellable = true)
    private void adapt$offer(RegistryAccess registries, ItemStack item, int button, int cost,
                             CallbackInfoReturnable<List<EnchantmentInstance>> callback) {
        List<EnchantmentInstance> chosen = InventoryHooks.enchantments(callback.getReturnValue());
        if (chosen != callback.getReturnValue()) {
            callback.setReturnValue(chosen);
        }
    }
}
