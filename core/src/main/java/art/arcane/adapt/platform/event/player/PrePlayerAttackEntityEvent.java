package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Objects;

/**
 * A player left-clicks an entity, before any attack logic. willAttack() is false when vanilla would
 * not attack it. Mutable: cancelled.
 */
public class PrePlayerAttackEntityEvent extends PlayerEvent {
  private final EntityRef attacked;
  private final boolean willAttack;

  public PrePlayerAttackEntityEvent(PlayerRef player, EntityRef attacked, boolean willAttack) {
    super(player);
    this.attacked = Objects.requireNonNull(attacked, "attacked");
    this.willAttack = willAttack;
  }

  public EntityRef attacked() {
    return attacked;
  }

  public boolean willAttack() {
    return willAttack;
  }
}
