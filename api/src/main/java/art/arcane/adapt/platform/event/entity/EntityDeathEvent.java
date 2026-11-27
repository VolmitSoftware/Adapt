package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A living entity died. Posted after the platform decided the drops and before they spawn. The core event cannot be
 * cancelled; see {@link #platformCancelled()}. Mutable: the drops list (add, remove, replace) and dropped experience.
 * The bridge replaces the native drops with this list after dispatch.
 *
 * <p>Player deaths post {@link art.arcane.adapt.platform.event.player.PlayerDeathEvent}, which handlers of this class
 * receive too.
 */
@ApiStatus.AvailableSince("1.0.0")
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

  /** The entity that died. */
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

  /** Cause of the final hit; {@link DamageCause#CUSTOM} when unknown. */
  public DamageCause cause() {
    return cause;
  }

  /** Items to drop, editable. The platform drops what the list holds after dispatch and skips null entries. */
  public List<ItemRef> drops() {
    return drops;
  }

  /** Experience the death drops. */
  public int droppedExp() {
    return droppedExp;
  }

  /** Changes the dropped experience; negative values become 0. */
  public void setDroppedExp(int droppedExp) {
    this.droppedExp = Math.max(0, droppedExp);
  }
}
