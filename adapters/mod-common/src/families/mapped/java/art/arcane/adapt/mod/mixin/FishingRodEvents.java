package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.PlayerHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Casting a rod: a cancelled cast spawns no hook and leaves the player not fishing. */
@Mixin(FishingRodItem.class)
public abstract class FishingRodEvents {
    @WrapOperation(method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean adapt$casting(Level level, Entity entity, Operation<Boolean> original, @Local(argsOnly = true) Player player) {
        if (entity instanceof FishingHook hook && PlayerHooks.fishing(player, hook)) {
            if (player.fishing == hook) {
                player.fishing = null;
            }
            return false;
        }
        return original.call(level, entity);
    }
}
