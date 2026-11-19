package art.arcane.adapt.mod.event.entity;

import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import net.minecraft.world.entity.MobSpawnType;

/** Implemented on every entity by a mixin: the spawn reason vanilla gave it, kept until the entity enters a level. */
public interface SpawnTagged {
    MobSpawnType adapt$vanillaSpawnReason();

    CreatureSpawnEvent.Reason adapt$spawnReason();

    void adapt$spawnReason(MobSpawnType vanilla, CreatureSpawnEvent.Reason reason);
}
