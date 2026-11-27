package art.arcane.adapt.api.content;

import org.jetbrains.annotations.ApiStatus;

/**
 * Marks an adaptation whose {@code HIGHEST} priority {@code EntityDamageEvent} handlers postpone damage rather than
 * change it, such as spreading a hit over time. They run after every plain damage handler and before the
 * {@link DeathSave}s, so a save judges the hit that is left. Implemented by the adaptation class itself.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface DamageDeferral {
}
