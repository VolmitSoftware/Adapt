package art.arcane.adapt.mod.platform.entity;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.event.entity.DamageCause;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Last uncancelled damage per living entity, recorded by the damage hooks the way Bukkit keeps its last damage cause. */
public final class ModLastDamage {
    private static final Map<LivingEntity, Entry> LAST = Collections.synchronizedMap(new WeakHashMap<>());

    private ModLastDamage() {
    }

    public static void record(LivingEntity entity, DamageCause cause, Entity damager, double finalDamage) {
        LAST.put(entity, new Entry(cause, damager, finalDamage));
    }

    static LastDamage of(LivingEntity entity) {
        Entry entry = LAST.get(entity);
        if (entry == null) {
            return null;
        }
        return new LastDamage(entry.cause(), entry.damager() == null ? null : ModRefs.entity(entry.damager()), entry.finalDamage());
    }

    private record Entry(DamageCause cause, Entity damager, double finalDamage) {
    }
}
