package art.arcane.adapt.engine.integration;

import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.world.BlockRef;

/** Tree felling by the Iris plugin (Bukkit only). Absent and declining when Iris is not enabled. */
public interface IrisTreeFellerPort {
  IrisTreeFellerPort NONE = new IrisTreeFellerPort() {
  };

  default boolean isAvailable() {
    return false;
  }

  /** Asks Iris to fell the tree the broken block belongs to; true when Iris accepted the request. */
  default boolean tryFell(BlockBreakEvent event, int durabilityPreservationChance, RunHooks runHooks) {
    return false;
  }

  /** True when the break is one Iris performs itself while felling a tree. */
  default boolean isManagedBreak(BlockBreakEvent event) {
    return false;
  }

  default boolean isTreeBlock(BlockRef block) {
    return false;
  }

  interface RunHooks {
    default boolean requiresSneaking() {
      return true;
    }

    void onActivationAccepted();

    boolean reserveLogCost();

    void commitLogCost();

    void refundLogCost();
  }
}
