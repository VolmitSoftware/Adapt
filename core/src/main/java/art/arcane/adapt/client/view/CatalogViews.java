package art.arcane.adapt.client.view;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationRequirement;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.api.preference.PreferenceToggleGroup;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.volmlib.util.format.ColorFormatter;
import art.arcane.volmlib.util.localization.LanguageAudience;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builds the {@link CatalogView} a viewer sees, with every text in the viewer's locale. */
public final class CatalogViews {
  private CatalogViews() {
  }

  public static CatalogView build(SkillRegistry registry, UUID viewer) {
    long revision = registry.getCatalogRevision();
    String locale = AdaptLanguage.locale(viewer);
    List<SkillView> skills = LanguageAudience.call(viewer, () -> skills(registry));
    return new CatalogView(revision, locale, skills);
  }

  public static IconRef icon(CustomModel model) {
    Id modelKey = model.modelKey();
    String itemModel = modelKey == null || CustomModel.EMPTY_KEY.equals(modelKey) ? null : modelKey.toString();
    return new IconRef(model.material().toString(), model.model(), itemModel, null);
  }

  private static List<SkillView> skills(SkillRegistry registry) {
    List<SkillView> skills = new ArrayList<>();
    for (Skill<?> skill : registry.getSkills()) {
      skills.add(skill(skill));
    }
    return skills;
  }

  private static SkillView skill(Skill<?> skill) {
    List<AdaptationView> adaptations = new ArrayList<>();
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      adaptations.add(adaptation(skill, adaptation));
    }
    PreferenceView enabled = preference(CommonPreferences.SKILL_ENABLED,
        PlayerPreferences.policy(skill, CommonPreferences.SKILL_ENABLED), true);
    return new SkillView(
        skill.getName(),
        plain(skill.getLocalizedName()),
        skill.getDescription(),
        icon(GuiConfig.skillModel(skill.getName(), skill.getIcon(), skill.getModel())),
        Integer.parseInt(skill.getColor().hex().substring(1), 16),
        skill.isEnabled(),
        skill.getAdaptationRowGroups(),
        List.of(enabled),
        adaptations
    );
  }

  private static AdaptationView adaptation(Skill<?> skill, Adaptation<?> adaptation) {
    int maxLevel = Math.max(0, adaptation.getMaxLevel());
    List<Integer> knowledgeCosts = new ArrayList<>(maxLevel);
    List<Integer> refunds = new ArrayList<>(maxLevel);
    List<Integer> powerCosts = new ArrayList<>(maxLevel);
    List<List<String>> statLines = new ArrayList<>(maxLevel);
    for (int level = 1; level <= maxLevel; level++) {
      knowledgeCosts.add(adaptation.getCostFor(level));
      refunds.add(adaptation.getRefundCostFor(level - 1, level));
      powerCosts.add(adaptation.getPowerCostFor(level, level - 1));
      StatLines stats = new StatLines();
      adaptation.addStats(level, stats);
      statLines.add(stats.lines());
    }
    List<RequirementView> requirements = new ArrayList<>();
    for (AdaptationRequirement requirement : adaptation.getRequirements()) {
      requirements.add(new RequirementView(requirement.adaptationId(), requirement.level()));
    }
    List<PreferenceView> preferences = new ArrayList<>();
    for (PlayerPreference<?> preference : adaptation.getPlayerPreferences()) {
      preferences.add(preference(preference, PlayerPreferences.policy(adaptation, preference),
          !adaptation.isPlayerPreferenceBlocked(preference)));
    }
    List<PreferenceGroupView> groups = new ArrayList<>();
    for (PreferenceToggleGroup group : adaptation.getPlayerPreferenceGroups()) {
      groups.add(group(group));
    }
    return new AdaptationView(
        adaptation.getName(),
        skill.getName(),
        plain(adaptation.getDisplayName()),
        adaptation.getDescription(),
        icon(GuiConfig.adaptationModel(adaptation.getName(), adaptation.getIcon(), adaptation.getModel())),
        adaptation.isEnabled(),
        maxLevel,
        adaptation.isPermanent(),
        knowledgeCosts,
        refunds,
        powerCosts,
        requirements,
        statLines,
        preferences,
        groups
    );
  }

  /** {@code text} without colour codes or surrounding blanks. */
  public static String plain(String text) {
    return text == null ? "" : ColorFormatter.stripColor(text).trim();
  }

  private static <E extends Enum<E>> PreferenceView preference(PlayerPreference<E> preference, PreferencePolicy policy,
                                                               boolean editable) {
    List<PreferenceChoiceView> choices = new ArrayList<>();
    for (PlayerPreference.Choice<E> choice : preference.choices()) {
      String value = choice.value().name();
      if (policy.allowedValues != null && policy.allowedValues.contains(value)) {
        choices.add(new PreferenceChoiceView(value, AdaptLanguage.text(choice.label()),
            IconRef.item(choice.shownIcon().toString()), choice.minimumLevel()));
      }
    }
    E serverDefault = preference.parse(policy.defaultValue);
    return new PreferenceView(
        preference.id(),
        AdaptLanguage.text(preference.label()),
        (serverDefault == null ? preference.defaultValue() : serverDefault).name(),
        choices,
        editable && policy.playerEditable && AdaptConfig.get().isPlayerSettingsEnabled()
    );
  }

  private static PreferenceGroupView group(PreferenceToggleGroup group) {
    List<PreferenceGroupView.Entry> entries = new ArrayList<>();
    for (PreferenceToggleGroup.Entry entry : group.entries()) {
      entries.add(new PreferenceGroupView.Entry(entry.preference().id(), IconRef.item(entry.icon().toString())));
    }
    return new PreferenceGroupView(group.id(), AdaptLanguage.text(group.label()), IconRef.item(group.icon().toString()),
        entries);
  }
}
