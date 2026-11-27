package art.arcane.adapt.engine.mutation.upgrade;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-adaptation facts that every level read of a mutated player needs: the sources and fused originals of an
 * upgrade, which upgrades an original is a source of, which fuse it, and the level a mutated skill keeps it at. They change only when an adaptation registers,
 * gains a recipe or reloads its config.
 */
public final class UpgradeCaches {
  static final Map<SkillUpgrade<?>, List<Id>> SOURCES = new ConcurrentHashMap<>();
  static final Map<SkillUpgrade<?>, Set<Id>> FUSES = new ConcurrentHashMap<>();
  static final Map<Adaptation<?>, List<SkillUpgrade<?>>> SOURCED = new ConcurrentHashMap<>();
  static final Map<Adaptation<?>, List<SkillUpgrade<?>>> FUSING = new ConcurrentHashMap<>();
  static final Map<Adaptation<?>, Integer> KEPT_LEVELS = new ConcurrentHashMap<>();

  private UpgradeCaches() {
  }

  public static void forget() {
    SOURCES.clear();
    FUSES.clear();
    SOURCED.clear();
    FUSING.clear();
    KEPT_LEVELS.clear();
  }
}
