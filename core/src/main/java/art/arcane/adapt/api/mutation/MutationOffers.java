package art.arcane.adapt.api.mutation;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.blocks.MutatedText;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.localization.AdaptLanguage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Which mutations a player can make now, and what one would produce, without changing anything. */
public final class MutationOffers {
  private MutationOffers() {
  }

  /** Skills from {@code skills} the player could merge into {@code lead} now, in the given order. */
  public static List<Skill<?>> partners(PlayerData data, Skill<?> lead, Collection<? extends Skill<?>> skills,
                                        AdaptConfig.Mutations config) {
    List<Skill<?>> partners = new ArrayList<>();
    for (Skill<?> candidate : skills) {
      if (candidate != lead && SkillMutations.check(data, lead, candidate, config) == SkillMutations.Result.MUTATED) {
        partners.add(candidate);
      }
    }
    return partners;
  }

  /** Whether the skill's page offers Mutate: it and at least one other free skill qualify and a slot is left. */
  public static boolean offered(PlayerData data, Skill<?> skill, Collection<? extends Skill<?>> skills,
                                AdaptConfig.Mutations config) {
    for (Skill<?> candidate : skills) {
      if (candidate != skill && SkillMutations.check(data, skill, candidate, config) == SkillMutations.Result.MUTATED) {
        return true;
      }
    }
    return false;
  }

  /** Skills from {@code skills} whose page offers Mutate, in the given order. */
  public static List<Skill<?>> leads(PlayerData data, Collection<? extends Skill<?>> skills, AdaptConfig.Mutations config) {
    List<Skill<?>> qualified = new ArrayList<>();
    for (Skill<?> skill : skills) {
      PlayerSkillLine line = data.getSkillLineNullable(skill.getName());
      if (line != null && line.getLevel() >= config.getRequiredSkillLevel() && data.mutationOf(skill.getName()) == null) {
        qualified.add(skill);
      }
    }
    List<Skill<?>> leads = new ArrayList<>();
    if (qualified.size() < 2) {
      return leads;
    }
    for (Skill<?> skill : qualified) {
      if (offered(data, skill, qualified, config)) {
        leads.add(skill);
      }
    }
    return leads;
  }

  /**
   * What merging the two would produce now, computed the way {@link SkillMutations#mutate} computes it: the parent with
   * less XP gives its level and its knowledge plus its refunds; the other parent's surplus levels, knowledge and
   * refunds are lost, and every learned adaptation of both is unlearned.
   */
  public static Preview preview(PlayerData data, Skill<?> lead, Skill<?> partner) {
    PlayerSkillLine leadLine = data.getSkillLineNullable(lead.getName());
    PlayerSkillLine partnerLine = data.getSkillLineNullable(partner.getName());
    double leadXp = xp(leadLine);
    double partnerXp = xp(partnerLine);
    boolean leadIsLower = leadXp <= partnerXp;
    Skill<?> lowerSkill = leadIsLower ? lead : partner;
    Skill<?> higherSkill = leadIsLower ? partner : lead;
    PlayerSkillLine lowerLine = leadIsLower ? leadLine : partnerLine;
    PlayerSkillLine higherLine = leadIsLower ? partnerLine : leadLine;
    List<SkillMutations.HeldAdaptation> lowerHeld = SkillMutations.held(lowerSkill, lowerLine);
    List<SkillMutations.HeldAdaptation> higherHeld = SkillMutations.held(higherSkill, higherLine);

    int level = levelAt(Math.min(leadXp, partnerXp));
    long knowledge = knowledge(lowerLine) + refunds(lowerHeld);
    int lostLevels = Math.max(0, levelAt(Math.max(leadXp, partnerXp)) - level);
    long lostKnowledge = knowledge(higherLine) + refunds(higherHeld);
    List<SkillMutations.HeldAdaptation> unlearned = new ArrayList<>(SkillMutations.held(lead, leadLine));
    unlearned.addAll(SkillMutations.held(partner, partnerLine));
    List<Adaptation<?>> upgrades = new ArrayList<>(lead.getUpgrades());
    upgrades.addAll(partner.getUpgrades());
    SkillMutation mutation = new SkillMutation(lead.getName(), partner.getName());
    MutatedSkill mutated = lead.getServer().getSkillRegistry().getMutatedSkill(mutation.id());
    List<Adaptation<?>> own = mutated == null ? List.of() : List.copyOf(mutated.getMutationAdaptations());
    return new Preview(mutation, AdaptLanguage.render(MutatedText.mutationName(MutatedText.VIEWER, lead.getName(), partner.getName())), level, knowledge,
        List.copyOf(upgrades), own, higherSkill, lostLevels, lostKnowledge, List.copyOf(unlearned));
  }

  /**
   * One prospective mutation. {@code mutated} are the mutation's own adaptations available on this server;
   * {@code higher} is the parent whose surplus is lost; {@code unlearned} lists every learned adaptation of both
   * parents.
   */
  public record Preview(SkillMutation mutation, String name, int level, long knowledge, List<Adaptation<?>> upgrades,
                        List<Adaptation<?>> mutated, Skill<?> higher, int lostLevels, long lostKnowledge,
                        List<SkillMutations.HeldAdaptation> unlearned) {
    public boolean losesAnything() {
      return lostLevels > 0 || lostKnowledge > 0 || !unlearned.isEmpty();
    }
  }

  private static double xp(PlayerSkillLine line) {
    return line == null ? 0D : line.getXp() + line.getPooledXp();
  }

  private static long knowledge(PlayerSkillLine line) {
    return line == null ? 0L : line.getKnowledge();
  }

  private static long refunds(List<SkillMutations.HeldAdaptation> held) {
    long total = 0L;
    for (SkillMutations.HeldAdaptation adaptation : held) {
      total += adaptation.refund();
    }
    return total;
  }

  private static int levelAt(double xp) {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setXp(xp);
    return line.getLevel();
  }
}
