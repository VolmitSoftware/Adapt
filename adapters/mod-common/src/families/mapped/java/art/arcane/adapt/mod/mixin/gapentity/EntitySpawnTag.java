package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.SpawnTagged;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the spawn reason from where an entity is created to where it enters a level. */
@Mixin(Entity.class)
public abstract class EntitySpawnTag implements SpawnTagged {
    @Unique
    private MobSpawnType adapt$vanillaSpawnReason;
    @Unique
    private CreatureSpawnEvent.Reason adapt$spawnReason;

    @Override
    public MobSpawnType adapt$vanillaSpawnReason() {
        return adapt$vanillaSpawnReason;
    }

    @Override
    public CreatureSpawnEvent.Reason adapt$spawnReason() {
        return adapt$spawnReason;
    }

    @Override
    public void adapt$spawnReason(MobSpawnType vanilla, CreatureSpawnEvent.Reason reason) {
        adapt$vanillaSpawnReason = vanilla;
        adapt$spawnReason = reason;
    }
}
