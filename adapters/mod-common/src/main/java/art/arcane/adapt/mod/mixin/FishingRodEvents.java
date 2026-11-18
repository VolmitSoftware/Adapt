package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.PlayerHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Casting a rod: a cancelled cast spawns no hook and leaves the player not fishing. */
@Mixin(FishingRodItem.class)
public abstract class FishingRodEvents {
    @WrapOperation(method = "use", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectile(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/projectile/Projectile;"))
    private Projectile adapt$casting(Projectile projectile, ServerLevel level, ItemStack rod, Operation<Projectile> original,
                                     @Local(argsOnly = true) Player player) {
        if (projectile instanceof FishingHook hook && PlayerHooks.fishing(player, hook)) {
            if (player.fishing == hook) {
                player.fishing = null;
            }
            return projectile;
        }
        return original.call(projectile, level, rod);
    }
}
