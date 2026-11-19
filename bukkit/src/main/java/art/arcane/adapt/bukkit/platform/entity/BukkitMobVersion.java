package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.platform.entity.CopperGolemState;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Mob;

final class BukkitMobVersion {
  private static volatile boolean copperStateSupported = true;

  private BukkitMobVersion() {
  }

  static CopperGolemState copperGolemState(Mob mob) {
    if (!copperStateSupported || !(mob instanceof CopperGolem golem)) {
      return CopperGolemState.IDLE;
    }
    try {
      return CopperGolemState.valueOf(golem.getGolemState().name());
    } catch (NoSuchMethodError | NoClassDefFoundError absent) {
      copperStateSupported = false;
      return CopperGolemState.IDLE;
    } catch (IllegalArgumentException unknown) {
      return CopperGolemState.IDLE;
    }
  }

  static void setCopperGolemState(Mob mob, CopperGolemState state) {
    if (!copperStateSupported || !(mob instanceof CopperGolem golem)) {
      return;
    }
    try {
      golem.setGolemState(CopperGolem.State.valueOf(state.name()));
    } catch (NoSuchMethodError | NoClassDefFoundError absent) {
      copperStateSupported = false;
    } catch (IllegalArgumentException unknown) {
      Adapt.verbose("Copper golem state " + state + " is unknown on this server");
    }
  }
}
