package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.mod.event.OwnedBy;
import art.arcane.adapt.mod.event.PlayerHooks;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Food gains from any source, hunger loss from exhaustion, and natural regeneration reported as satiated healing. */
@Mixin(FoodData.class)
public abstract class FoodDataEvents implements OwnedBy {
    @Unique
    private LivingEntity adapt$owner;

    @Override
    public LivingEntity adapt$owner() {
        return adapt$owner;
    }

    @Override
    public void adapt$owner(LivingEntity owner) {
        adapt$owner = owner;
    }

    /** Every eat path (food items, cake, other mods) ends here; a cancel adds neither food nor saturation. */
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void adapt$feeding(int food, float saturation, CallbackInfo callback, @Local(argsOnly = true) LocalIntRef nutrition) {
        if (!(adapt$owner instanceof Player player)) {
            return;
        }
        int chosen = PlayerHooks.feeding(player, (FoodData) (Object) this, food);
        if (chosen == Integer.MIN_VALUE) {
            callback.cancel();
        } else if (chosen != food) {
            nutrition.set(chosen);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void adapt$ticking(ServerPlayer player, CallbackInfo callback) {
        adapt$owner = player;
        CombatHooks.healReason(EntityRegainHealthEvent.Reason.SATIATED);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void adapt$ticked(ServerPlayer player, CallbackInfo callback) {
        CombatHooks.healReason(null);
    }

    @WrapOperation(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/food/FoodData;foodLevel:I",
        opcode = Opcodes.PUTFIELD))
    private void adapt$hungering(FoodData food, int level, Operation<Void> original, @Local(argsOnly = true) ServerPlayer player) {
        int chosen = PlayerHooks.hungering(player, level);
        if (chosen >= 0) {
            original.call(food, chosen);
        }
    }
}
