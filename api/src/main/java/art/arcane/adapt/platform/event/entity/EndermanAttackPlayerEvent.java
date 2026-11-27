package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * An enderman checks whether a player stares at it. Posted for every check: it starts cancelled when the player does
 * not provoke it, and an uncancelled event makes the enderman treat the player as staring. Mutable: cancelled.
 *
 * <p>On Bukkit servers only Paper posts it.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EndermanAttackPlayerEvent extends CancellableEvent {
  private final EntityRef enderman;
  private final PlayerRef player;

  public EndermanAttackPlayerEvent(EntityRef enderman, PlayerRef player, boolean provoked) {
    super(!provoked);
    this.enderman = Objects.requireNonNull(enderman, "enderman");
    this.player = Objects.requireNonNull(player, "player");
  }

  /** The enderman doing the check. */
  public EntityRef enderman() {
    return enderman;
  }

  /** The player being checked. */
  public PlayerRef player() {
    return player;
  }
}
