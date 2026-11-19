package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.platform.entity.CopperGolemState;
import org.bukkit.entity.Mob;

final class BukkitMobVersion {
  private BukkitMobVersion() {
  }

  static CopperGolemState copperGolemState(Mob mob) {
    return CopperGolemState.IDLE;
  }

  static void setCopperGolemState(Mob mob, CopperGolemState state) {
  }
}
