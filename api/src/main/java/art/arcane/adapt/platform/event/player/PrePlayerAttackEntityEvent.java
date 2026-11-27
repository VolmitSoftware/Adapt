package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A player left-clicks an entity, before any attack logic. {@link #willAttack()} is false when vanilla would not
 * attack it. Cancelling stops the attack. On Bukkit it needs Paper. Mutable: cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PrePlayerAttackEntityEvent extends PlayerEvent {
  private final EntityRef attacked;
  private final boolean willAttack;

  public PrePlayerAttackEntityEvent(PlayerRef player, EntityRef attacked, boolean willAttack) {
    super(player);
    this.attacked = Objects.requireNonNull(attacked, "attacked");
    this.willAttack = willAttack;
  }

  /** The entity clicked. */
  public EntityRef attacked() {
    return attacked;
  }

  /** False when vanilla would skip the attack because the entity cannot be attacked. */
  public boolean willAttack() {
    return willAttack;
  }
}
