package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/** A player died. Entity death listeners receive it too. Mutable: drops, exp, keep flags, new level and exp. */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerDeathEvent extends EntityDeathEvent {
  private boolean keepInventory;
  private boolean keepLevel;
  private int newLevel;
  private int newExp;
  private int newTotalExp;

  public PlayerDeathEvent(PlayerRef player, PlayerRef killer, EntityRef directDamager, EntityRef causingEntity,
                          DamageCause cause, List<ItemRef> drops, int droppedExp, boolean keepInventory, boolean keepLevel,
                          int newLevel, int newExp, int newTotalExp) {
    super(player, killer, directDamager, causingEntity, cause, drops, droppedExp);
    this.keepInventory = keepInventory;
    this.keepLevel = keepLevel;
    this.newLevel = newLevel;
    this.newExp = newExp;
    this.newTotalExp = newTotalExp;
  }

  /** The player who died; the same object as {@code entity()}. */
  public PlayerRef player() {
    return (PlayerRef) entity();
  }

  /** True when the player keeps the inventory through respawn. */
  public boolean keepInventory() {
    return keepInventory;
  }

  /** Changes whether the inventory is kept. */
  public void setKeepInventory(boolean keepInventory) {
    this.keepInventory = keepInventory;
  }

  /** True when the player keeps experience level and progress; the {@code new*} values are then ignored. */
  public boolean keepLevel() {
    return keepLevel;
  }

  /** Changes whether experience is kept. */
  public void setKeepLevel(boolean keepLevel) {
    this.keepLevel = keepLevel;
  }

  /** Experience level the player respawns with when {@link #keepLevel()} is false. */
  public int newLevel() {
    return newLevel;
  }

  /** Changes the respawn level, clamped at 0. */
  public void setNewLevel(int newLevel) {
    this.newLevel = Math.max(0, newLevel);
  }

  /** Experience points given to the player on respawn when {@link #keepLevel()} is false. */
  public int newExp() {
    return newExp;
  }

  /** Changes the respawn experience points, clamped at 0. */
  public void setNewExp(int newExp) {
    this.newExp = Math.max(0, newExp);
  }

  /** Total experience the player respawns with when {@link #keepLevel()} is false. */
  public int newTotalExp() {
    return newTotalExp;
  }

  /** Changes the respawn total experience, clamped at 0. */
  public void setNewTotalExp(int newTotalExp) {
    this.newTotalExp = Math.max(0, newTotalExp);
  }
}
