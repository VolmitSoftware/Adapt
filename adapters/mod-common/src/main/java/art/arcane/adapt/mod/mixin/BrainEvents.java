package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.mod.event.OwnedBy;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemorySlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Attack targets of brain-driven mobs. Every write of the attack target memory passes through these
 * three methods, whichever behaviour, sensor or anger handler made it.
 */
@Mixin(Brain.class)
public abstract class BrainEvents implements OwnedBy {
    @Unique
    private LivingEntity adapt$owner;

    @Shadow
    private <T> MemorySlot<T> getMemorySlotIfPresent(MemoryModuleType<T> type) {
        throw new AssertionError();
    }

    @Override
    public LivingEntity adapt$owner() {
        return adapt$owner;
    }

    @Override
    public void adapt$owner(LivingEntity owner) {
        adapt$owner = owner;
    }

    @Inject(method = "setMemoryInternal(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;)V", at = @At("HEAD"),
        cancellable = true)
    private void adapt$remembering(MemoryModuleType<?> type, Object value, CallbackInfo callback,
                                   @Local(argsOnly = true) LocalRef<Object> chosen) {
        adapt$targeting(type, value, callback, chosen);
    }

    @Inject(method = "setMemoryInternal(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;J)V", at = @At("HEAD"),
        cancellable = true)
    private void adapt$rememberingFor(MemoryModuleType<?> type, Object value, long ttl, CallbackInfo callback,
                                      @Local(argsOnly = true) LocalRef<Object> chosen) {
        adapt$targeting(type, value, callback, chosen);
    }

    @Inject(method = "eraseMemory", at = @At("HEAD"), cancellable = true)
    private void adapt$forgetting(MemoryModuleType<?> type, CallbackInfo callback) {
        if (type != MemoryModuleType.ATTACK_TARGET || adapt$owner == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        MemorySlot<Object> slot = (MemorySlot<Object>) getMemorySlotIfPresent(type);
        if (slot == null || slot.value() == null) {
            return;
        }
        Object result = CombatHooks.brainTargeting(adapt$owner, slot.value(), null);
        if (result == CombatHooks.KEEP_TARGET) {
            callback.cancel();
        } else if (result != null) {
            slot.set(result);
            callback.cancel();
        }
    }

    @Unique
    private void adapt$targeting(MemoryModuleType<?> type, Object value, CallbackInfo callback, LocalRef<Object> chosen) {
        if (type != MemoryModuleType.ATTACK_TARGET || adapt$owner == null) {
            return;
        }
        MemorySlot<?> slot = getMemorySlotIfPresent(type);
        if (slot == null) {
            return;
        }
        Object result = CombatHooks.brainTargeting(adapt$owner, slot.value(), value);
        if (result == CombatHooks.KEEP_TARGET) {
            callback.cancel();
        } else if (result != value) {
            chosen.set(result);
        }
    }
}
