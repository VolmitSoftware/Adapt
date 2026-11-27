package art.arcane.adapt.engine.integration;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;

/** AdvancedChests containers for Rift Access. NONE reports that no AdvancedChests container exists. */
public interface AdvancedChestsPort {
  AdvancedChestsPort NONE = new AdvancedChestsPort() {
  };

  enum Lookup { ABSENT, PRESENT, FAILED }

  /** Whether an AdvancedChests container sits at the block, or FAILED when the lookup threw. */
  default Lookup find(Loc block) {
    return Lookup.ABSENT;
  }

  /**
   * Opens the first page of the AdvancedChests container at the block. Returns the top inventory of the new view, or
   * null when no new view opened.
   */
  default InventoryRef open(PlayerRef player, Loc block) {
    return null;
  }
}
