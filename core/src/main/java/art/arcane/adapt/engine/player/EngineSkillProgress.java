package art.arcane.adapt.engine.player;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.player.SkillProgress;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.engine.adaptation.AdaptationLearningTransaction;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.adaptation.AdaptationRequirements;
import art.arcane.adapt.engine.component.ComponentSupport;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.world.PlayerAdaptation;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.platform.value.Id;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * One player's line in one skill, read through to the saved line on every call. A mutated line answers for its lead,
 * its partner and the mutated skill's own id.
 */
public final class EngineSkillProgress implements SkillProgress {
  private final EnginePlayerProfile profile;
  private final Id skill;

  EngineSkillProgress(EnginePlayerProfile profile, Id skill) {
    this.profile = Objects.requireNonNull(profile, "profile");
    this.skill = Objects.requireNonNull(skill, "skill");
  }

  @Override
  public Id skill() {
    return skill;
  }

  @Override
  public int level() {
    PlayerSkillLine line = stored();
    return line == null ? 0 : line.getLevel();
  }

  @Override
  public double xp() {
    PlayerSkillLine line = stored();
    return line == null ? 0D : line.getXp() + line.getPooledXp();
  }

  @Override
  public double xpToNextLevel() {
    PlayerSkillLine line = stored();
    return line == null ? XP.getXpForLevel(1) : line.getXPForLevelUpAbsolute();
  }

  @Override
  public long knowledge() {
    PlayerSkillLine line = stored();
    return line == null ? 0L : line.getKnowledge();
  }

  @Override
  public Map<Id, Integer> adaptations() {
    PlayerSkillLine line = stored();
    Skill<?> owner = registeredSkill();
    if (line == null || owner == null) {
      return Map.of();
    }
    Map<Id, Integer> levels = new LinkedHashMap<>();
    for (Map.Entry<String, PlayerAdaptation> stored : new TreeMap<>(line.getAdaptations()).entrySet()) {
      Adaptation<?> adaptation = SkillMutations.find(owner, stored.getKey());
      if (adaptation != null && stored.getValue() != null && stored.getValue().getLevel() > 0) {
        levels.put(adaptation.getId(), stored.getValue().getLevel());
      }
    }
    return Map.copyOf(levels);
  }

  @Override
  public boolean isAvailable() {
    return live() != null;
  }

  @Override
  public void award(XpAward award) {
    Objects.requireNonNull(award, "award");
    profile.requireOwner();
    Skill<?> owner = requireLive();
    XP.award(profile.runtime(), owner, award);
    PlayerSkillLine line = stored();
    if (line != null) {
      line.flushXpPool(award.quiet() ? null : profile.runtime().getNot());
    }
  }

  @Override
  public void setLevel(int level) {
    profile.requireOwner();
    requireLive();
    PlayerSkillLine line = profile.data().getSkillLine(key());
    double target = XP.getXpForLevel(Math.max(0, level));
    line.flushXpPool(null);
    line.setXp(target);
    if (line.getLastXP() > target) {
      line.setLastXP(target);
    }
    if (line.getLastLevel() > level) {
      line.setLastLevel(Math.max(0, level));
    }
  }

  @Override
  public void addKnowledge(long amount) {
    profile.requireOwner();
    requireLive();
    profile.data().getSkillLine(key()).grantKnowledge(amount);
  }

  @Override
  public void reset() {
    profile.requireOwner();
    PlayerSkillLine line = stored();
    Skill<?> owner = live();
    if (line == null || owner == null) {
      return;
    }
    unlearnAll(owner);
    line.flushXpPool(null);
    line.setXp(0D);
    line.setLastXP(0D);
    line.setLastLevel(0);
    line.setKnowledge(0L);
  }

  /**
   * Unlearns every registered adaptation in the line with no refund, each through the forced unlearn; a disabled
   * skill's line, which the transaction refuses, is cleared after the same event.
   */
  @Override
  public void loseXp(double amount) {
    profile.requireOwner();
    PlayerSkillLine line = stored();
    if (line == null) {
      return;
    }
    double xp = line.getXp();
    double loss = Double.isFinite(amount) ? Math.min(Math.max(xp, 0D), Math.max(0D, amount)) : 0D;
    if (loss > 0D) {
      line.setXp(xp - loss);
    }
    line.setLastXP(xp);
  }

  @Override
  public void flush() {
    profile.requireOwner();
    PlayerSkillLine line = stored();
    if (line != null) {
      line.flushXpPool(profile.runtime().getNot());
    }
  }

  @Override
  public @Nullable Object stored(String key) {
    Objects.requireNonNull(key, "key");
    PlayerSkillLine line = stored();
    return line == null ? null : line.getStorage().get(key);
  }

  @Override
  public void store(String key, @Nullable Object value) {
    Objects.requireNonNull(key, "key");
    profile.requireOwner();
    Map<String, Object> storage = profile.data().getSkillLine(key()).getStorage();
    if (value == null) {
      storage.remove(key);
    } else {
      storage.put(key, value);
    }
  }

  @Override
  public void lowerAdaptations(int levels) {
    profile.requireOwner();
    PlayerSkillLine line = stored();
    if (line == null || levels <= 0) {
      return;
    }
    List<AdaptationRequirements.Lowering> lowered = new ArrayList<>();
    for (Map.Entry<String, PlayerAdaptation> entry : line.getAdaptations().entrySet()) {
      PlayerAdaptation stored = entry.getValue();
      Adaptation<?> adaptation = AdaptationRequirements.find(Id.tryParse(entry.getKey()));
      if (adaptation != null && !stored.isRegionGranted() && stored.getLevel() > 0) {
        lowered.add(new AdaptationRequirements.Lowering(adaptation, stored.getLevel()));
      }
      stored.setLevel(Math.max(stored.getLevel() - levels, 0));
    }
    PlayerData data = profile.data();
    AdaptationRequirements.forfeitLevels(lowered, data, profile.player().name());
    ComponentSupport.recalcTotalExp(profile.player());
    data.pruneAdaptationsForPowerBudget();
  }

  void unlearnAll(Skill<?> owner) {
    PlayerSkillLine line = stored();
    if (line == null) {
      return;
    }
    for (String stored : List.copyOf(line.getAdaptations().keySet())) {
      Adaptation<?> adaptation = SkillMutations.find(owner, stored);
      if (adaptation == null) {
        continue;
      }
      AdaptationLearningTransaction.Result result =
          AdaptationLearningTransaction.unlearn(adaptation, profile.player(), 0, true);
      int level = line.getAdaptationLevel(stored);
      if (result == AdaptationLearningTransaction.Result.SKILL_LINE_UNAVAILABLE && level > 0
          && ProgressEvents.unlearn(profile.player(), adaptation.getId(), line.getLine(), level, 0, 0L, true)) {
        line.setAdaptation(adaptation, 0);
      }
    }
  }

  /** The key the line is saved under: the player's mutation key when the skill is merged, else the skill id. */
  String key() {
    PlayerData data = profile.data();
    for (SkillPair mutation : data.getMutations()) {
      Skill<?> mutated = profile.registry().getMutatedSkill(mutation);
      if (mutated != null && mutated.getId().equals(skill)) {
        return mutation.key();
      }
    }
    return data.lineIdFor(skill.toString());
  }

  private PlayerSkillLine stored() {
    return profile.data().getSkillLineNullable(key());
  }

  private Skill<?> registeredSkill() {
    return Orphans.skill(profile.registry(), key());
  }

  /** The skill when the line can take changes, else null. */
  private Skill<?> live() {
    return profile.registry().getSkill(key());
  }

  private Skill<?> requireLive() {
    Skill<?> owner = live();
    if (owner == null) {
      throw new IllegalStateException(skill + " is not an enabled skill line for " + profile.player().name());
    }
    return owner;
  }
}
