package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.OwnedBy;
import art.arcane.adapt.mod.event.entity.AiHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Optional;

/** An attack target memory reaching the end of its time to live, before the brain clears it. */
@Mixin(Brain.class)
public abstract class BrainExpiryEvents {
    @Shadow
    @Final
    private Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories;

    @Inject(method = "forgetOutdatedMemories()V", at = @At("HEAD"))
    @SuppressWarnings("unchecked")
    private void adapt$expiring(CallbackInfo callback) {
        if (!(((Object) this) instanceof OwnedBy owned) || owned.adapt$owner() == null) {
            return;
        }
        Optional<? extends ExpirableValue<?>> memory = memories.get(MemoryModuleType.ATTACK_TARGET);
        if (memory != null && memory.isPresent() && memory.get().canExpire() && memory.get().hasExpired()) {
            AiHooks.brainExpiring(owned.adapt$owner(), (Brain<?>) (Object) this, (ExpirableValue<LivingEntity>) memory.get());
        }
    }
}
