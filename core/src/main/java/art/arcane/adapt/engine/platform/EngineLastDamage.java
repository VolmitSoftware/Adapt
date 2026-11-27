package art.arcane.adapt.engine.platform;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.event.entity.DamageCause;

public record EngineLastDamage(DamageCause cause, EntityRef damager, double finalDamage) implements LastDamage {
}
