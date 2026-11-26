package art.arcane.adapt.api.mutation.upgrade;

import art.arcane.adapt.api.adaptation.Adaptation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-adaptation facts that every level read of a mutated player needs: which upgrades fuse an original and the level a
 * mutated skill keeps it at. They change only when an adaptation registers, gains a recipe or reloads its config.
 */
public final class UpgradeCaches {
  static final Map<Adaptation<?>, List<SkillUpgrade<?>>> FUSING = new ConcurrentHashMap<>();
  static final Map<Adaptation<?>, Integer> KEPT_LEVELS = new ConcurrentHashMap<>();

  private UpgradeCaches() {
  }

  public static void forget() {
    FUSING.clear();
    KEPT_LEVELS.clear();
  }
}
