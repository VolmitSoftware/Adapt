package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Objects;

/** Right click on an entity. Covers the Bukkit at-entity variant through {@link #clickedPosition()}. Mutable: cancelled. */
public class PlayerInteractEntityEvent extends PlayerEvent {
  private final EntityRef clicked;
  private final Hand hand;
  private final Vec3 clickedPosition;

  public PlayerInteractEntityEvent(PlayerRef player, EntityRef clicked, Hand hand, Vec3 clickedPosition) {
    super(player);
    this.clicked = Objects.requireNonNull(clicked, "clicked");
    this.hand = hand == null ? Hand.MAIN_HAND : hand;
    this.clickedPosition = clickedPosition;
  }

  public EntityRef clicked() {
    return clicked;
  }

  public Hand hand() {
    return hand;
  }

  /** Hit position relative to the entity, or null when the platform did not report one. */
  public Vec3 clickedPosition() {
    return clickedPosition;
  }
}
