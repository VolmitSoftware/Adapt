package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.platform.entity.CopperGolemState;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Mob;

/** Copper golem behaviour states, which only Paper exposes. Loaded only on Paper. */
final class PaperCopperGolems {
  private PaperCopperGolems() {
  }

  static CopperGolemState state(Mob mob) {
    if (!(mob instanceof CopperGolem golem)) {
      return CopperGolemState.IDLE;
    }
    try {
      return CopperGolemState.valueOf(golem.getGolemState().name());
    } catch (IllegalArgumentException unknown) {
      return CopperGolemState.IDLE;
    }
  }

  static void setState(Mob mob, CopperGolemState state) {
    if (!(mob instanceof CopperGolem golem)) {
      return;
    }
    try {
      golem.setGolemState(CopperGolem.State.valueOf(state.name()));
    } catch (IllegalArgumentException unknown) {
      Adapt.verbose("Copper golem state " + state + " is unknown on this server");
    }
  }
}
