package art.arcane.adapt.engine.mutation;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.adaptation.AdaptationRequirements;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.engine.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.engine.player.ProgressEvents;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerAdaptation;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.platform.value.Id;

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
    LIMIT_REACHED,
    CANCELLED
  }

  public enum Unmutation {
    UNMUTATED,
    NOT_MUTATED,
    CANCELLED
  }

  private SkillMutations() {
  }

  /** Whether the player may merge the two skills; {@link Result#MUTATED} when every rule passes. */
  public static Result check(PlayerData data, Skill<?> lead, Skill<?> partner, AdaptConfig.Mutations config) {
    if (lead == null || partner == null) {
      return Result.UNKNOWN_SKILL;
    }
    if (!Kits.mergeable(lead) || !Kits.mergeable(partner)) {
      return Result.UNAVAILABLE_SKILL;
    }
    if (SkillHost.lineKey(lead).equals(SkillHost.lineKey(partner))) {
      return Result.SAME_SKILL;
    }
    if (data.mutationOf(SkillHost.lineKey(lead)) != null || data.mutationOf(SkillHost.lineKey(partner)) != null) {
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
    SkillPair mutation = new SkillPair(lead.getId(), partner.getId());
    if (!ProgressEvents.mutate(data.ownerPlayer(), mutation)) {
      return Result.CANCELLED;
    }
    PlayerSkillLine leadLine = data.getSkillLineNullable(SkillHost.lineKey(lead));
    PlayerSkillLine partnerLine = data.getSkillLineNullable(SkillHost.lineKey(partner));
    leadLine.flushXpPool(null);
    partnerLine.flushXpPool(null);
    List<AdaptationRequirements.Lowering> removed = new ArrayList<>();
    unlearnAll(lead, leadLine, removed);
    unlearnAll(partner, partnerLine, removed);

    PlayerSkillLine lower = leadLine.getXp() <= partnerLine.getXp() ? leadLine : partnerLine;
    data.installMutation(mutation, settledLine(mutation.key(), lower.getXp(), lower.getKnowledge()),
        new MutationOrigin(leadLine.getXp(), partnerLine.getXp()));
    AdaptationRequirements.forfeitLevels(removed, data, playerName);
    return Result.MUTATED;
  }

  /**
   * Undoes one of the player's mutations: {@link Unmutation#NOT_MUTATED} when they do not hold it,
   * {@link Unmutation#CANCELLED} when a listener refused. Every adaptation on the mutated line is
   * unlearned with its knowledge returned, and every kept original raised above its free level refunds the levels
   * above it. Both parents come back at the mutated line's XP, or at the XP they held when
   * they merged when that was more and was recorded. The mutated line's knowledge, refunds included, is split between
   * them, the odd point to the lead. The restore pays no level-up rewards.
   */
  public static Unmutation unmutate(PlayerData data, SkillPair mutation, String playerName) {
    if (mutation == null || !data.getMutations().contains(mutation)) {
      return Unmutation.NOT_MUTATED;
    }
    if (!ProgressEvents.unmutate(data.ownerPlayer(), mutation)) {
      return Unmutation.CANCELLED;
    }
    PlayerSkillLine merged = data.getSkillLineNullable(mutation.key());
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
        settledLine(mutation.lead().toString(), origin == null ? xp : Math.max(xp, origin.leadXp()), knowledge - partnerShare),
        settledLine(mutation.partner().toString(), origin == null ? xp : Math.max(xp, origin.partnerXp()), partnerShare));
    return Unmutation.UNMUTATED;
  }

  /** Undoes every mutation the player holds, each as {@link #unmutate} does; returns the ones removed. */
  public static List<SkillPair> unmutateAll(PlayerData data, String playerName) {
    List<SkillPair> removed = new ArrayList<>();
    for (SkillPair mutation : data.getMutations()) {
      if (unmutate(data, mutation, playerName) == Unmutation.UNMUTATED) {
        removed.add(mutation);
      }
    }
    return removed;
  }

  /** The player's mutation a name points at: the mutated skill's id, its lead or its partner; null for none. */
  public static SkillPair mutationNamed(PlayerData data, String skill) {
    if (skill == null) {
      return null;
    }
    String name = skill.trim().toLowerCase(Locale.ROOT);
    SkillPair named = PairKeys.parse(name);
    Id parent = named == null ? Id.tryParseAdapt(name) : null;
    for (SkillPair mutation : data.getMutations()) {
      if (mutation.equals(named) || (parent != null && mutation.involves(parent))) {
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

  private static Skill<?> mutatedSkill(SkillPair mutation) {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    return server == null ? null : server.getSkillRegistry().getMutatedSkill(mutation);
  }

  /**
   * The skill a listing shows in place of a registered one: the player's mutated skill for its lead, null for its
   * partner so the merged pair appears once, and the skill itself otherwise.
   */
  public static Skill<?> listedSkill(SkillRegistry registry, PlayerData data, Skill<?> registered) {
    SkillPair mutation = data.mutationOf(SkillHost.lineKey(registered));
    if (mutation == null) {
      return registered;
    }
    return mutation.lead().equals(registered.getId()) ? registry.getMutatedSkill(mutation) : null;
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
            stored.isRegionGranted() ? 0 : AdaptationHost.refundCostFor(adaptation, 0, stored.getLevel())));
      }
    }
    return held;
  }

  /** A learned adaptation a merge unlearns, and the knowledge it gives back to its parent. */
  public record HeldAdaptation(Adaptation<?> adaptation, int level, long refund) {
  }

  private static int level(PlayerData data, Skill<?> skill) {
    PlayerSkillLine line = data.getSkillLineNullable(SkillHost.lineKey(skill));
    return line == null ? 0 : line.getLevel();
  }

  private static void unlearnAll(Skill<?> skill, PlayerSkillLine line, List<AdaptationRequirements.Lowering> removed) {
    for (Map.Entry<String, PlayerAdaptation> held : List.copyOf(line.getAdaptations().entrySet())) {
      Adaptation<?> adaptation = find(skill, held.getKey());
      PlayerAdaptation stored = held.getValue();
      if (adaptation == null) {
        if (stored != null && !stored.isRegionGranted() && stored.getLevel() > 0) {
          line.giveKnowledge(Math.max(0L, stored.getKnowledge()));
        }
        line.getAdaptations().remove(held.getKey());
        continue;
      }
      int floor = skill instanceof MutatedSkill && SkillUpgrades.kept(adaptation) ? SkillUpgrades.keptLevel(adaptation) : 0;
      if (stored != null && !stored.isRegionGranted() && stored.getLevel() > floor) {
        line.giveKnowledge(AdaptationHost.refundCostFor(adaptation, floor, stored.getLevel()));
        removed.add(new AdaptationRequirements.Lowering(adaptation, stored.getLevel()));
      }
      line.setAdaptation(adaptation, 0);
    }
  }

  /** The adaptation of that id a skill's line can hold: one of its own, or a kept original of a mutated skill. */
  public static Adaptation<?> find(Skill<?> skill, String adaptationId) {
    if (skill == null) {
      return null;
    }
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (adaptation.getId().toString().equals(adaptationId)) {
        return adaptation;
      }
    }
    return skill instanceof MutatedSkill mutated ? mutated.findKept(adaptationId) : null;
  }
}
