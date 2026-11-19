package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.mod.event.OwnedBy;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Optional;

/**
 * Attack targets of brain-driven mobs. Every write of the attack target memory, erasing included, passes through
 * this one method, whichever behaviour, sensor or anger handler made it.
 */
@Mixin(Brain.class)
public abstract class BrainEvents implements OwnedBy {
    @Unique
    private LivingEntity adapt$owner;

    @Shadow
    @Final
    private Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories;

    @Override
    public LivingEntity adapt$owner() {
        return adapt$owner;
    }

    @Override
    public void adapt$owner(LivingEntity owner) {
        adapt$owner = owner;
    }

    @Inject(method = "setMemoryInternal(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/util/Optional;)V", at = @At("HEAD"),
        cancellable = true)
    private void adapt$remembering(MemoryModuleType<?> type, Optional<? extends ExpirableValue<?>> memory, CallbackInfo callback,
                                   @Local(argsOnly = true) LocalRef<Optional<? extends ExpirableValue<?>>> chosen) {
        if (type != MemoryModuleType.ATTACK_TARGET || adapt$owner == null || !memories.containsKey(type)) {
            return;
        }
        Optional<? extends ExpirableValue<?>> held = memories.get(type);
        Object current = held == null ? null : held.map(ExpirableValue::getValue).orElse(null);
        Object value = memory.map(ExpirableValue::getValue).orElse(null);
        Object result = CombatHooks.brainTargeting(adapt$owner, current, value);
        if (result == CombatHooks.KEEP_TARGET) {
            callback.cancel();
        } else if (result != value) {
            chosen.set(result == null ? Optional.empty()
                : Optional.of(memory.isPresent() && memory.get().canExpire()
                    ? ExpirableValue.of(result, memory.get().getTimeToLive()) : ExpirableValue.of(result)));
        }
    }
}
