package art.arcane.adapt.engine.player;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.player.OrphanedLine;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.PairKeys;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.PlayerAdaptation;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Saved progress whose content is not registered: whole lines of a missing skill, mutated lines with a missing
 * parent (suspended), and adaptations a registered line holds that its skill no longer has. {@code PlayerData} never
 * prunes these; only {@link #refund} and {@link #purge} change them.
 */
public final class Orphans {
  private Orphans() {
  }

  public static List<EngineOrphanedLine> list(PlayerData data, SkillRegistry registry) {
    List<EngineOrphanedLine> orphans = new ArrayList<>();
    for (Map.Entry<String, PlayerSkillLine> entry : new TreeMap<>(data.getSkillLines()).entrySet()) {
      String key = entry.getKey();
      PlayerSkillLine line = entry.getValue();
      OrphanedLine.Key lineKey = lineKey(key);
      if (line == null || lineKey == null) {
        continue;
      }
      Skill<?> skill = skill(registry, key);
      if (skill == null) {
        orphans.add(new EngineOrphanedLine(key, lineKey, Optional.empty(), missingNamespace(registry, key),
            line.getXp(), line.getKnowledge(), levels(line)));
        continue;
      }
      for (Map.Entry<String, PlayerAdaptation> stored : new TreeMap<>(line.getAdaptations()).entrySet()) {
        Id adaptation = Id.tryParse(stored.getKey());
        PlayerAdaptation held = stored.getValue();
        int level = held == null ? 0 : held.getLevel();
        if (adaptation == null || level <= 0 || registered(registry, skill, stored.getKey())) {
          continue;
        }
        orphans.add(new EngineOrphanedLine(key, lineKey, Optional.of(adaptation), adaptation.namespace(), 0D,
            held.isRegionGranted() ? 0L : Math.max(0L, held.getKnowledge()), Map.of(adaptation, level)));
      }
    }
    return List.copyOf(orphans);
  }

  /**
   * Deletes the orphaned adaptations of {@code namespace} held in registered lines and returns the knowledge their
   * levels cost, as recorded when they were learned, to the line that holds them; nothing else in that line changes.
   * Orphaned whole lines have no registered line to credit and are left alone, and so is an adaptation whose refund a
   * listener cancelled. Returns the knowledge returned.
   */
  public static long refund(PlayerData data, SkillRegistry registry, String namespace) {
    long refunded = 0L;
    for (EngineOrphanedLine orphan : list(data, registry)) {
      if (orphan.adaptation().isEmpty() || !orphan.namespace().equals(namespace)) {
        continue;
      }
      PlayerSkillLine line = data.getSkillLines().get(orphan.saveKey());
      if (line == null) {
        continue;
      }
      long applied = orphan.knowledge() == 0L ? 0L : line.grantKnowledge(orphan.knowledge());
      if (orphan.knowledge() == 0L || applied > 0L) {
        data.deleteAdaptation(orphan.saveKey(), orphan.adaptation().get().toString());
        refunded += applied;
      }
    }
    return refunded;
  }

  /**
   * Deletes every orphan of {@code namespace}. A suspended mutated line is split first, so the parent that is still
   * registered gets its line back, and the missing parent's line is then deleted. Returns the entries deleted.
   */
  public static int purge(PlayerData data, SkillRegistry registry, String namespace, String playerName) {
    int purged = 0;
    for (EngineOrphanedLine orphan : list(data, registry)) {
      if (!orphan.namespace().equals(namespace)) {
        continue;
      }
      if (orphan.adaptation().isPresent()) {
        purged += data.deleteAdaptation(orphan.saveKey(), orphan.adaptation().get().toString()) ? 1 : 0;
        continue;
      }
      SkillPair mutation = PairKeys.parse(orphan.saveKey());
      if (mutation == null) {
        purged += data.deleteLine(orphan.saveKey()) ? 1 : 0;
        continue;
      }
      if (SkillMutations.unmutate(data, mutation, playerName) != SkillMutations.Unmutation.UNMUTATED) {
        continue;
      }
      for (Id parent : List.of(mutation.lead(), mutation.partner())) {
        if (registry.getAnySkill(parent) == null) {
          data.deleteLine(parent.toString());
        }
      }
      purged++;
    }
    return purged;
  }

  /** The typed key of a saved line; null for a key that is neither a skill id nor a pair key. */
  static OrphanedLine.Key lineKey(String line) {
    SkillPair mutation = PairKeys.parse(line);
    if (mutation != null) {
      return new OrphanedLine.Pair(mutation);
    }
    Id id = Id.tryParseAdapt(line);
    return id == null ? null : new OrphanedLine.Line(id);
  }

  /** The key {@code line} is saved under. */
  public static String saveKey(OrphanedLine.Key line) {
    return switch (line) {
      case OrphanedLine.Line skill -> skill.skill().toString();
      case OrphanedLine.Pair pair -> pair.pair().key();
    };
  }

  /** The registered skill a saved line belongs to; null for a missing skill or a suspended mutated line. */
  static Skill<?> skill(SkillRegistry registry, String line) {
    SkillPair mutation = PairKeys.parse(line);
    if (mutation != null) {
      return registry.getMutatedSkill(mutation);
    }
    Id id = Id.tryParseAdapt(line);
    return id == null ? null : registry.getAnySkill(id);
  }

  static boolean registered(SkillRegistry registry, Skill<?> skill, String adaptation) {
    Id id = Id.tryParse(adaptation);
    return SkillMutations.find(skill, adaptation) != null || (id != null && registry.getAdaptation(id) != null);
  }

  private static String missingNamespace(SkillRegistry registry, String line) {
    SkillPair mutation = PairKeys.parse(line);
    if (mutation != null) {
      return registry.getAnySkill(mutation.lead()) == null ? mutation.lead().namespace()
          : mutation.partner().namespace();
    }
    Id id = Id.tryParseAdapt(line);
    return id == null ? line : id.namespace();
  }

  private static Map<Id, Integer> levels(PlayerSkillLine line) {
    Map<Id, Integer> levels = new LinkedHashMap<>();
    for (Map.Entry<String, PlayerAdaptation> stored : new TreeMap<>(line.getAdaptations()).entrySet()) {
      Id id = Id.tryParse(stored.getKey());
      if (id != null && stored.getValue() != null && stored.getValue().getLevel() > 0) {
        levels.put(id, stored.getValue().getLevel());
      }
    }
    return levels;
  }
}
