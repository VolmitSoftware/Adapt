package art.arcane.adapt.command;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.command.FConst;

import java.util.Locale;

import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

/**
 * The one way a command argument names a skill or an adaptation: an id, where a bare value is in the {@code adapt}
 * namespace. A name that is not a registered id stops the command with an error naming the id that was looked for.
 */
public final class CommandTargets {
  private static final String MUTATED = "mutated";
  private static final int MUTATED_PART_SEGMENTS = 5;

  private CommandTargets() {
  }

  /** The id an argument names, or null when it is not an id at all. */
  public static Id id(String input) {
    return input == null ? null : Id.tryParseAdapt(input.trim().toLowerCase(Locale.ROOT));
  }

  /** The registered skill, enabled or not; null after telling the sender it is unknown. */
  public static Skill<?> skill(String input, CommandSource sender) {
    Id id = id(input);
    Skill<?> skill = findSkill(id);
    if (skill == null) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.UNKNOWN_SKILL, untrusted("skill", shown(input, id))))
          .send(sender);
    }
    return skill;
  }

  /** The registered adaptation of any registered skill, enabled or not; null after telling the sender it is unknown. */
  public static Adaptation<?> adaptation(String input, CommandSource sender) {
    Id id = id(input);
    Adaptation<?> found = findAdaptation(id);
    if (found == null) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.UNKNOWN_ADAPTATION,
          untrusted("adaptation", shown(input, id)))).send(sender);
    }
    return found;
  }

  /** The registered skill with this id, enabled or not, or null. */
  public static Skill<?> findSkill(Id id) {
    return id == null ? null : registry().getAnySkill(id);
  }

  /**
   * The registered adaptation with this id, enabled or not, or null: any skill's adaptations, upgrades and blocks, and
   * a mutated skill's own adaptations and signatures, whose skill is built here when no one has mutated the pair yet.
   */
  public static Adaptation<?> findAdaptation(Id id) {
    if (id == null) {
      return null;
    }
    for (Skill<?> skill : registry().getAllSkills()) {
      for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(skill)) {
        if (adaptation.getId().equals(id)) {
          return adaptation;
        }
      }
    }
    MutatedSkill mutated = mutatedOwner(id);
    if (mutated != null) {
      for (Adaptation<?> adaptation : mutated.switchedAdaptations()) {
        if (adaptation.getId().equals(id)) {
          return adaptation;
        }
      }
    }
    return null;
  }

  /**
   * The mutated skill whose content ids lie below {@link SkillPair#id()}, read from {@code id}:
   * {@code <lead namespace>:mutated/<lead>/<partner namespace>/<partner>/<part>}. Null unless both parents are
   * registered skills.
   */
  private static MutatedSkill mutatedOwner(Id id) {
    String[] segments = id.path().split("/");
    if (segments.length < MUTATED_PART_SEGMENTS || !segments[0].equals(MUTATED)) {
      return null;
    }
    Id lead = Id.tryParse(id.namespace() + ":" + segments[1]);
    Id partner = Id.tryParse(segments[2] + ":" + segments[3]);
    if (findSkill(lead) == null || findSkill(partner) == null || lead.equals(partner)) {
      return null;
    }
    try {
      return registry().getMutatedSkill(new SkillPair(lead, partner));
    } catch (IllegalArgumentException tooLong) {
      return null;
    }
  }

  /** How an argument is named back to the sender: the id it resolved to, or the text when it is no id. */
  public static String shown(String input, Id id) {
    return id == null ? String.valueOf(input).trim() : id.toString();
  }

  private static SkillRegistry registry() {
    return AdaptCore.get().getAdaptServer().getSkillRegistry();
  }
}
