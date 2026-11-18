package art.arcane.adapt.mod.event.entity;

import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import net.minecraft.world.entity.EntitySpawnReason;

/** Implemented on every entity by a mixin: the spawn reason vanilla gave it, kept until the entity enters a level. */
public interface SpawnTagged {
    EntitySpawnReason adapt$vanillaSpawnReason();

    CreatureSpawnEvent.Reason adapt$spawnReason();

    void adapt$spawnReason(EntitySpawnReason vanilla, CreatureSpawnEvent.Reason reason);
}
