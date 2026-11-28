package art.arcane.adapt.engine.gui;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.client.view.XpGuideView;
import art.arcane.adapt.client.view.XpGuideViews;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.PlayerAdaptation;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.SeatMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * The item a skill shows wherever a chest menu lists it: its own icon, its name, then its state (level, XP to the next
 * level, knowledge, power used), its description and how it earns XP. A skill the player has progress in glints.
 */
public final class SkillCards {
  private SkillCards() {
  }

  /** The skill's icon with its name and nothing else, for menus that add their own lines. */
  public static GuiElement icon(String id, Skill<?> skill) {
    CustomModel model = GuiConfig.skillModel(SkillHost.lineKey(skill), skill.getIcon().item(), ComponentHost.of(skill).getModel());
    return new GuiElement(id).setMaterial(model.material()).setModel(model).setName(skill.getDisplayName());
  }

  /** The full card; {@code line} null or {@code started} false shows the skill as not started. */
  public static GuiElement card(String id, Skill<?> skill, PlayerSkillLine line, boolean started, SkillRegistry registry) {
    GuiElement element = icon(id, skill);
    if (line == null || !started) {
      element.setName(TextFormat.DARK_GRAY + TextFormat.strip(skill.getDisplayName()))
          .addLore(TextFormat.GRAY + AdaptLanguage.text(SeatMessages.NOT_STARTED));
    } else {
      element.setEnchanted(true).addLore(state(line));
    }
    element.addLore(" ").addLore(TextFormat.GRAY + description(skill));
    addGuide(element, skill, registry);
    return element;
  }

  /** The card of a skill's own menu header: its state with how many of {@code shown} adaptations are learned. */
  public static GuiElement header(Skill<?> skill, PlayerSkillLine line, List<Adaptation<?>> shown, SkillRegistry registry) {
    int learned = 0;
    for (Adaptation<?> adaptation : shown) {
      PlayerAdaptation held = line.getAdaptation(adaptation.getId().toString());
      if (held != null && held.getLevel() > 0) {
        learned++;
      }
    }
    GuiElement element = icon("skill-header", skill).setEnchanted(skill instanceof MutatedSkill).addLore(state(line));
    if (!shown.isEmpty()) {
      element.addLore(TextFormat.GRAY + AdaptLanguage.text(GuiMessages.PACK_LEARNED_COUNT, trusted("learned", learned),
          trusted("total", shown.size())));
    }
    element.addLore(" ").addLore(TextFormat.GRAY + description(skill));
    addGuide(element, skill, registry);
    return element;
  }

  /** Level, XP to the next level, knowledge and power used, the order the skill pages show them. */
  static List<String> state(PlayerSkillLine line) {
    int power = 0;
    for (PlayerAdaptation adaptation : line.getAdaptations().values()) {
      if (adaptation != null) {
        power += Math.max(0, adaptation.getLevel());
      }
    }
    return List.of(
        TextFormat.WHITE + AdaptLanguage.text(GuiMessages.PACK_LEVEL, trusted("level", line.getLevel())),
        TextFormat.GRAY + AdaptLanguage.text(GuiMessages.PACK_XP, trusted("xp", Form.f((int) XP.getXpUntilLevelUp(line.getXp()))),
            trusted("level", line.getLevel() + 1)),
        TextFormat.AQUA + AdaptLanguage.text(GuiMessages.PACK_KNOWLEDGE, trusted("knowledge", line.getKnowledge())),
        TextFormat.GRAY + AdaptLanguage.text(SnippetsMessages.GUI_POWER_USED_VALUE, trusted("power", power)));
  }

  private static String description(Skill<?> skill) {
    return TextFormat.strip(skill.getDescription());
  }

  /** How the skill earns XP, under the guide's heading: a mutated skill's parents each under their name, then its rules. */
  static void addGuide(GuiElement element, Skill<?> skill, SkillRegistry registry) {
    XpGuideView guide = XpGuideViews.guide(registry, skill);
    if (guide.sections().isEmpty()) {
      return;
    }
    element.addLore(" ");
    element.addLore(TextFormat.GOLD + XpGuideViews.title());
    for (XpGuideView.Section section : guide.sections()) {
      if (guide.mutated()) {
        Skill<?> source = section.skillId().isEmpty() ? null : registry.getSkill(section.skillId());
        element.addLore((source == null ? TextFormat.LIGHT_PURPLE.toString() : SkillHost.legacyColor(source.getColor()).toString()) + section.title());
      }
      for (String line : section.lines()) {
        element.addLore(TextFormat.GRAY + "- " + line);
      }
    }
  }
}
