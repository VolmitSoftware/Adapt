package art.arcane.adapt.engine.gui;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.client.view.MutationEntry;
import art.arcane.adapt.client.view.MutationSide;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.MutatedMessages;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * What a mutation takes and gives, grouped under headings from the same spread the book and native pages show: per
 * parent the levels and knowledge it gives up and every adaptation that goes; then the mutated skill with its own
 * adaptations, each parent's upgrades, the originals kept without learning and the tools.
 */
public final class MutationSummary {
  private MutationSummary() {
  }

  /** One heading: what it names ({@link Kind}), its skill id for a skill heading, a detail line, and its entries. */
  public record Section(Kind kind, String skillId, String heading, String detail, List<MutationEntry> entries) {
    public Section {
      entries = List.copyOf(entries);
    }
  }

  public enum Kind {
    RESULT,
    PARENT,
    KEPT,
    TOOLS
  }

  /** Per parent, lead first: its surplus as the detail and every adaptation the merge unlearns or removes. */
  public static List<Section> losses(MutationSpreadView spread, SkillRegistry registry) {
    List<Section> sections = new ArrayList<>(2);
    for (MutationSide side : List.of(spread.leadSide(), spread.partnerSide())) {
      if (!side.lost().isEmpty() || !side.surplus().isEmpty()) {
        sections.add(new Section(Kind.PARENT, side.skillId(), parentHeading(side, registry), side.surplus(), side.lost()));
      }
    }
    return sections;
  }

  /** The mutated skill and its own adaptations, each parent's upgrades, the originals kept, the tools. */
  public static List<Section> gains(MutationSpreadView spread, SkillRegistry registry) {
    List<Section> sections = new ArrayList<>(5);
    sections.add(new Section(Kind.RESULT, spread.lead(), spread.name(), spread.summary(), spread.mutated()));
    for (MutationSide side : List.of(spread.leadSide(), spread.partnerSide())) {
      if (!side.upgrades().isEmpty()) {
        sections.add(new Section(Kind.PARENT, side.skillId(), skillName(side.skillId(), registry), "", side.upgrades()));
      }
    }
    List<MutationEntry> kept = new ArrayList<>(spread.leadSide().kept());
    kept.addAll(spread.partnerSide().kept());
    if (!kept.isEmpty()) {
      sections.add(new Section(Kind.KEPT, "", AdaptLanguage.text(MutatedMessages.KEPT), "", kept));
    }
    List<MutationEntry> tools = new ArrayList<>(spread.leadSide().tools());
    tools.addAll(spread.partnerSide().tools());
    if (!tools.isEmpty()) {
      sections.add(new Section(Kind.TOOLS, "", AdaptLanguage.text(GuiMessages.PACK_TOOLS), "", tools));
    }
    return sections;
  }

  /**
   * The tooltip of a summary item: each section's heading in its skill's colour, its detail, then its entries as a
   * comma list of at most {@link LoreLists#CAP} names and "and N more".
   */
  public static List<String> lore(List<Section> sections, SkillRegistry registry) {
    List<String> lore = new ArrayList<>();
    for (Section section : sections) {
      if (!lore.isEmpty()) {
        lore.add(" ");
      }
      lore.add(color(section, registry) + section.heading());
      if (!section.detail().isEmpty()) {
        lore.add(TextFormat.WHITE + section.detail());
      }
      List<String> names = new ArrayList<>(section.entries().size());
      for (MutationEntry entry : section.entries()) {
        names.add(entry.name());
      }
      String joined = LoreLists.joined(names);
      if (!joined.isEmpty()) {
        lore.add(TextFormat.GRAY + joined);
      }
    }
    return lore;
  }

  private static String parentHeading(MutationSide side, SkillRegistry registry) {
    return skillName(side.skillId(), registry) + " " + TextFormat.DARK_GRAY
        + AdaptLanguage.text(GuiMessages.PACK_LV, trusted("level", side.level()));
  }

  private static String skillName(String skillId, SkillRegistry registry) {
    Skill<?> skill = registry.getSkill(skillId);
    return skill == null ? skillId : skill.getLocalizedName();
  }

  private static String color(Section section, SkillRegistry registry) {
    return switch (section.kind()) {
      case PARENT, RESULT -> {
        Skill<?> skill = registry.getSkill(section.skillId());
        yield skill == null ? TextFormat.WHITE.toString() : SkillHost.legacyColor(skill.getColor()).toString();
      }
      case KEPT -> TextFormat.GREEN.toString();
      case TOOLS -> TextFormat.GOLD.toString();
    };
  }
}
