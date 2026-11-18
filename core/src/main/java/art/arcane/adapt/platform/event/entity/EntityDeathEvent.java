package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A living entity died. Mutable: the drops list (add, remove, replace) and dropped experience. The
 * bridge replaces the native drops with this list after dispatch.
 */
public class EntityDeathEvent extends AdaptEvent {
  private final LivingRef entity;
  private final PlayerRef killer;
  private final EntityRef directDamager;
  private final EntityRef causingEntity;
  private final DamageCause cause;
  private final List<ItemRef> drops;
  private int droppedExp;
  private boolean platformCancelled;

  public EntityDeathEvent(LivingRef entity, PlayerRef killer, EntityRef directDamager, EntityRef causingEntity,
                          DamageCause cause, List<ItemRef> drops, int droppedExp) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.killer = killer;
    this.directDamager = directDamager;
    this.causingEntity = causingEntity;
    this.cause = cause == null ? DamageCause.CUSTOM : cause;
    this.drops = new ArrayList<>(drops);
    this.droppedExp = droppedExp;
  }

  public LivingRef entity() {
    return entity;
  }

  /**
   * True when the platform marked this death cancelled before Adapt saw it (Paper lets plugins cancel deaths). The
   * core event itself cannot be cancelled; handlers that skipped cancelled deaths check this.
   */
  public boolean platformCancelled() {
    return platformCancelled;
  }

  /** Set by the platform bridge from the native event. */
  public void setPlatformCancelled(boolean platformCancelled) {
    this.platformCancelled = platformCancelled;
  }

  /** Player credited with the kill, or null. */
  public PlayerRef killer() {
    return killer;
  }

  /** Entity that dealt the final hit directly (arrow, wolf, player), or null. */
  public EntityRef directDamager() {
    return directDamager;
  }

  /** Entity responsible for the final hit (the shooter for projectiles), or null. */
  public EntityRef causingEntity() {
    return causingEntity;
  }

  public DamageCause cause() {
    return cause;
  }

  public List<ItemRef> drops() {
    return drops;
  }

  public int droppedExp() {
    return droppedExp;
  }

  public void setDroppedExp(int droppedExp) {
    this.droppedExp = Math.max(0, droppedExp);
  }
}
