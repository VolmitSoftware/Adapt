package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.List;

/** A player died. Entity death listeners receive it too. Mutable: drops, exp, keep flags, new level and exp. */
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

  public PlayerRef player() {
    return (PlayerRef) entity();
  }

  public boolean keepInventory() {
    return keepInventory;
  }

  public void setKeepInventory(boolean keepInventory) {
    this.keepInventory = keepInventory;
  }

  public boolean keepLevel() {
    return keepLevel;
  }

  public void setKeepLevel(boolean keepLevel) {
    this.keepLevel = keepLevel;
  }

  public int newLevel() {
    return newLevel;
  }

  public void setNewLevel(int newLevel) {
    this.newLevel = Math.max(0, newLevel);
  }

  public int newExp() {
    return newExp;
  }

  public void setNewExp(int newExp) {
    this.newExp = Math.max(0, newExp);
  }

  public int newTotalExp() {
    return newTotalExp;
  }

  public void setNewTotalExp(int newTotalExp) {
    this.newTotalExp = Math.max(0, newTotalExp);
  }
}
