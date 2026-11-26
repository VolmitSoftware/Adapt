package art.arcane.adapt.api.batch;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;

/** The server-wide cap on blocks all build batches together may change in one tick ({@code [mutations] buildBlocksPerTick}). */
final class BuildBudget {
  private long tick = Long.MIN_VALUE;
  private int used;

  /** Grants up to {@code wanted} blocks from what is left of this tick's cap. */
  synchronized int claim(int wanted) {
    long now = AdaptCore.platform().host().currentTick();
    if (now != tick) {
      tick = now;
      used = 0;
    }
    int granted = Math.max(0, Math.min(wanted, AdaptConfig.get().getMutations().getBuildBlocksPerTick() - used));
    used += granted;
    return granted;
  }
}
