package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.inventory.ContainerHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.BiConsumer;

/** The three enchanting offers, after vanilla computed them for the item in the table. */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuPrepareEvents {
    @WrapOperation(method = "slotsChanged", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/ContainerLevelAccess;execute(Ljava/util/function/BiConsumer;)V"))
    private void adapt$offering(ContainerLevelAccess access, BiConsumer<Level, BlockPos> task, Operation<Void> original) {
        original.call(access, task);
        EnchantmentMenu menu = (EnchantmentMenu) (Object) this;
        access.execute((Level level, BlockPos pos) -> ContainerHooks.preparedEnchant(menu, level, pos, menu.costs, menu.enchantClue, menu.levelClue));
    }
}
