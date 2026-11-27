package art.arcane.adapt.client.view;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.text.Text;

import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.XpGuideMessages;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/** How each skill a viewer sees earns XP, in the viewer's language, for the client book and the chest menus. */
public final class XpGuideViews {
  private XpGuideViews() {
  }

  /** The guide for every registered skill and each of the viewer's mutated skills, in catalog order. */
  public static List<XpGuideView> build(SkillRegistry registry, UUID viewer, List<SkillPair> mutations) {
    return LanguageAudience.call(viewer, () -> {
      List<XpGuideView> guides = new ArrayList<>();
      for (Skill<?> skill : registry.getSkills()) {
        guides.add(guide(registry, skill));
      }
      for (SkillPair mutation : mutations) {
        MutatedSkill mutated = registry.getMutatedSkill(mutation);
        if (mutated != null) {
          guides.add(guide(registry, mutated));
        }
      }
      return guides;
    });
  }

  /** The heading guides are shown under, in the current audience's language. */
  public static String title() {
    return AdaptLanguage.text(XpGuideMessages.TITLE);
  }

  /** How {@code skill} earns XP, in the current audience's language. */
  public static XpGuideView guide(SkillRegistry registry, Skill<?> skill) {
    if (!(skill instanceof MutatedSkill mutated)) {
      return new XpGuideView(SkillHost.lineKey(skill), List.of(), List.of(section(skill)));
    }
    SkillPair mutation = mutated.getPair();
    List<XpGuideView.Section> sections = new ArrayList<>();
    for (Id parent : List.of(mutation.lead(), mutation.partner())) {
      Skill<?> original = registry.getSkill(parent);
      if (original != null) {
        sections.add(section(original));
      }
    }
    sections.add(new XpGuideView.Section("", AdaptLanguage.text(XpGuideMessages.MUTATION), rules()));
    return new XpGuideView(SkillHost.lineKey(skill), List.of(mutation.lead().toString(), mutation.partner().toString()), sections);
  }

  /** The mutation's rules, the share of the smaller award read from the server's config. */
  public static List<String> rules() {
    long share = Math.round(AdaptConfig.get().getMutations().getSameActionSmallerShare() * 100D);
    List<String> rules = new ArrayList<>();
    for (TextKey rule : XpGuideMessages.rules()) {
      rules.add(rule == XpGuideMessages.SAME_ACTION ? AdaptLanguage.text(rule, trusted("share", share)) : AdaptLanguage.text(rule));
    }
    return rules;
  }

  private static XpGuideView.Section section(Skill<?> skill) {
    List<String> lines = new ArrayList<>();
    for (Text line : skill.getXpGuide()) {
      lines.add(line.resolve());
    }
    lines.add(AdaptLanguage.text(XpGuideMessages.ADAPTATIONS));
    return new XpGuideView.Section(SkillHost.lineKey(skill), CatalogViews.plain(skill.getLocalizedName()), lines);
  }
}
