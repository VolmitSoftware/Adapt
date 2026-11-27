package art.arcane.adapt.engine.mutation.engine;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.Nullable;

/**
 * What a Trigger reports when it fires: the player, the entity the action was aimed at, where work happened, whether
 * that entity died from it, and whether the action is farmed (a spawner, egg or bred mob, a player-placed or
 * bone-mealed block), in which case no block that pays out runs.
 */
public record TriggerEvent(PlayerRef player, @Nullable EntityRef target, @Nullable Loc at, boolean slain,
                           boolean farmed) {
}
