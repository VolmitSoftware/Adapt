package art.arcane.adapt.command;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
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

  /** The registered adaptation with this id, of any skill and enabled or not, or null. */
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
    return null;
  }

  /** How an argument is named back to the sender: the id it resolved to, or the text when it is no id. */
  public static String shown(String input, Id id) {
    return id == null ? String.valueOf(input).trim() : id.toString();
  }

  private static SkillRegistry registry() {
    return AdaptCore.get().getAdaptServer().getSkillRegistry();
  }
}
