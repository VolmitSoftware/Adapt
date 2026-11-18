package art.arcane.adapt.mod.event;

import net.minecraft.world.entity.LivingEntity;

/** Members whose names differ between the supported Minecraft versions. */
final class ModCompat {
    private ModCompat() {
    }

    static int damageCooldown(LivingEntity entity) {
        return entity.damageCooldownTime;
    }
}
