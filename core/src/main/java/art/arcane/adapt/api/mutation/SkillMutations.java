package art.arcane.adapt.api.mutation;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationRequirements;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Merges two skills of one player into a mutated skill, and undoes such a merge for an admin or in debug mode. */
public final class SkillMutations {
  public enum Result {
    MUTATED,
    UNKNOWN_SKILL,
    UNAVAILABLE_SKILL,
    SAME_SKILL,
    ALREADY_MUTATED,
    LEVEL_TOO_LOW,
    LIMIT_REACHED
  }

  private SkillMutations() {
  }

  /** Whether the player may merge the two skills; {@link Result#MUTATED} when every rule passes. */
  public static Result check(PlayerData data, Skill<?> lead, Skill<?> partner, AdaptConfig.Mutations config) {
    if (lead == null || partner == null) {
      return Result.UNKNOWN_SKILL;
    }
    if (!mergeable(lead) || !mergeable(partner)) {
      return Result.UNAVAILABLE_SKILL;
    }
    if (lead.getName().equals(partner.getName())) {
      return Result.SAME_SKILL;
    }
    if (data.mutationOf(lead.getName()) != null || data.mutationOf(partner.getName()) != null) {
      return Result.ALREADY_MUTATED;
    }
    if (level(data, lead) < config.getRequiredSkillLevel() || level(data, partner) < config.getRequiredSkillLevel()) {
      return Result.LEVEL_TOO_LOW;
    }
    if (!config.allowsAnother(data.getMutations().size())) {
      return Result.LIMIT_REACHED;
    }
    return Result.MUTATED;
  }

  /**
   * Merges the skills when {@link #check} allows it. Every adaptation in both parents is unlearned with its knowledge
   * returned to that parent, then the parent with less XP gives the mutated line its XP, level and knowledge. The
   * merge pays no level-up rewards and cannot be undone by the player; {@link #unmutate} is for admins and debug mode.
   */
  public static Result mutate(PlayerData data, Skill<?> lead, Skill<?> partner, String playerName) {
    Result result = check(data, lead, partner, AdaptConfig.get().getMutations());
    if (result != Result.MUTATED) {
      return result;
    }
    PlayerSkillLine leadLine = data.getSkillLineNullable(lead.getName());
    PlayerSkillLine partnerLine = data.getSkillLineNullable(partner.getName());
    leadLine.flushXpPool(null);
    partnerLine.flushXpPool(null);
    List<AdaptationRequirements.Lowering> removed = new ArrayList<>();
    unlearnAll(lead, leadLine, removed);
    unlearnAll(partner, partnerLine, removed);

    PlayerSkillLine lower = leadLine.getXp() <= partnerLine.getXp() ? leadLine : partnerLine;
    SkillMutation mutation = new SkillMutation(lead.getName(), partner.getName());
    data.installMutation(mutation, settledLine(mutation.id(), lower.getXp(), lower.getKnowledge()),
        new MutationOrigin(leadLine.getXp(), partnerLine.getXp()));
    AdaptationRequirements.forfeitLevels(removed, data, playerName);
    return Result.MUTATED;
  }

  /**
   * Undoes one of the player's mutations; false when they do not hold it. Every adaptation on the mutated line is
   * unlearned with its knowledge returned, and every kept original raised above its free level refunds the levels
   * above it. Both parents come back at the mutated line's XP, or at the XP they held when
   * they merged when that was more and was recorded. The mutated line's knowledge, refunds included, is split between
   * them, the odd point to the lead. The restore pays no level-up rewards.
   */
  public static boolean unmutate(PlayerData data, SkillMutation mutation, String playerName) {
    if (mutation == null || !data.getMutations().contains(mutation)) {
      return false;
    }
    PlayerSkillLine merged = data.getSkillLineNullable(mutation.lead());
    double xp = 0D;
    long knowledge = 0L;
    List<AdaptationRequirements.Lowering> removed = new ArrayList<>();
    if (merged != null) {
      merged.flushXpPool(null);
      unlearnAll(mutatedSkill(mutation), merged, removed);
      xp = merged.getXp();
      knowledge = merged.getKnowledge();
    }
    AdaptationRequirements.forfeitLevels(removed, data, playerName);
    MutationOrigin origin = data.mutationOrigin(mutation);
    long partnerShare = knowledge / 2L;
    data.removeMutation(mutation,
        settledLine(mutation.lead(), origin == null ? xp : Math.max(xp, origin.leadXp()), knowledge - partnerShare),
        settledLine(mutation.partner(), origin == null ? xp : Math.max(xp, origin.partnerXp()), partnerShare));
    return true;
  }

  /** Undoes every mutation the player holds, each as {@link #unmutate} does; returns the ones removed. */
  public static List<SkillMutation> unmutateAll(PlayerData data, String playerName) {
    List<SkillMutation> removed = new ArrayList<>();
    for (SkillMutation mutation : data.getMutations()) {
      if (unmutate(data, mutation, playerName)) {
        removed.add(mutation);
      }
    }
    return removed;
  }

  /** The player's mutation a name points at: the mutated skill's id, its lead or its partner; null for none. */
  public static SkillMutation mutationNamed(PlayerData data, String skill) {
    if (skill == null) {
      return null;
    }
    String name = skill.trim().toLowerCase(Locale.ROOT);
    for (SkillMutation mutation : data.getMutations()) {
      if (mutation.id().equals(name) || mutation.involves(name)) {
        return mutation;
      }
    }
    return null;
  }

  private static PlayerSkillLine settledLine(String id, double xp, long knowledge) {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine(id);
    line.setXp(xp);
    line.setKnowledge(knowledge);
    line.settleWithoutRewards();
    return line;
  }

  private static Skill<?> mutatedSkill(SkillMutation mutation) {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    return server == null ? null : server.getSkillRegistry().getMutatedSkill(mutation.id());
  }

  /**
   * The skill a listing shows in place of a registered one: the player's mutated skill for its lead, null for its
   * partner so the merged pair appears once, and the skill itself otherwise.
   */
  public static Skill<?> listedSkill(SkillRegistry registry, PlayerData data, Skill<?> registered) {
    SkillMutation mutation = data.mutationOf(registered.getName());
    if (mutation == null) {
      return registered;
    }
    return mutation.lead().equals(registered.getName()) ? registry.getMutatedSkill(mutation.id()) : null;
  }

  /** The adaptations a merge unlearns from this parent: every learned one the skill still has, with its level. */
  public static List<HeldAdaptation> held(Skill<?> skill, PlayerSkillLine line) {
    List<HeldAdaptation> held = new ArrayList<>();
    if (line == null) {
      return held;
    }
    for (Map.Entry<String, PlayerAdaptation> entry : line.getAdaptations().entrySet()) {
      Adaptation<?> adaptation = find(skill, entry.getKey());
      PlayerAdaptation stored = entry.getValue();
      if (adaptation != null && stored != null && stored.getLevel() > 0) {
        held.add(new HeldAdaptation(adaptation, stored.getLevel(),
            stored.isRegionGranted() ? 0 : adaptation.getRefundCostFor(0, stored.getLevel())));
      }
    }
    return held;
  }

  /** A learned adaptation a merge unlearns, and the knowledge it gives back to its parent. */
  public record HeldAdaptation(Adaptation<?> adaptation, int level, long refund) {
  }

  private static boolean mergeable(Skill<?> skill) {
    return !(skill instanceof MutatedSkill) && skill.isEnabled() && !skill.getAdaptations().isEmpty()
        && SkillWords.of(skill.getName()) != null;
  }

  private static int level(PlayerData data, Skill<?> skill) {
    PlayerSkillLine line = data.getSkillLineNullable(skill.getName());
    return line == null ? 0 : line.getLevel();
  }

  private static void unlearnAll(Skill<?> skill, PlayerSkillLine line, List<AdaptationRequirements.Lowering> removed) {
    for (Map.Entry<String, PlayerAdaptation> held : List.copyOf(line.getAdaptations().entrySet())) {
      Adaptation<?> adaptation = find(skill, held.getKey());
      PlayerAdaptation stored = held.getValue();
      if (adaptation == null) {
        line.getAdaptations().remove(held.getKey());
        continue;
      }
      int floor = skill instanceof MutatedSkill && SkillUpgrades.kept(adaptation) ? SkillUpgrades.keptLevel(adaptation) : 0;
      if (stored != null && !stored.isRegionGranted() && stored.getLevel() > floor) {
        line.giveKnowledge(adaptation.getRefundCostFor(floor, stored.getLevel()));
        removed.add(new AdaptationRequirements.Lowering(adaptation, stored.getLevel()));
      }
      line.setAdaptation(adaptation, 0);
    }
  }

  private static Adaptation<?> find(Skill<?> skill, String adaptationId) {
    if (skill == null) {
      return null;
    }
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (adaptation.getName().equals(adaptationId)) {
        return adaptation;
      }
    }
    return skill instanceof MutatedSkill mutated ? mutated.findKept(adaptationId) : null;
  }
}
