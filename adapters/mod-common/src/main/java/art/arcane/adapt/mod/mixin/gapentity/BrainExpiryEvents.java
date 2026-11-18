package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.OwnedBy;
import art.arcane.adapt.mod.event.entity.AiHooks;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemorySlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An attack target memory reaching the end of its time to live, before the brain clears it. */
@Mixin(Brain.class)
public abstract class BrainExpiryEvents {
    @Shadow
    private <T> MemorySlot<T> getMemorySlotIfPresent(MemoryModuleType<T> type) {
        throw new AssertionError();
    }

    @Inject(method = "forgetOutdatedMemories", at = @At("HEAD"))
    @SuppressWarnings("unchecked")
    private void adapt$expiring(CallbackInfo callback) {
        if (!(((Object) this) instanceof OwnedBy owned) || owned.adapt$owner() == null) {
            return;
        }
        MemorySlot<?> slot = getMemorySlotIfPresent(MemoryModuleType.ATTACK_TARGET);
        if (slot != null && slot.hasValue() && slot.canExpire() && slot.hasExpired()) {
            AiHooks.brainExpiring(owned.adapt$owner(), (MemorySlot<Object>) slot);
        }
    }
}
