package art.arcane.adapt.client.view;

import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutation;
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
import art.arcane.volmlib.util.format.ColorFormatter;
import art.arcane.volmlib.util.localization.LanguageAudience;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Builds the {@link CatalogView} a viewer sees, with every text in the viewer's locale. */
public final class CatalogViews {
  private CatalogViews() {
  }

  /**
   * The catalog for {@code viewer}; {@code mutations} adds the viewer's mutated skills after the registered ones. A
   * mutated skill's description lists the originals it keeps unless {@code keptApart}, for clients that show them on
   * their own.
   */
  public static CatalogView build(SkillRegistry registry, UUID viewer, List<SkillMutation> mutations, boolean keptApart) {
    long revision = registry.getCatalogRevision();
    String locale = AdaptLanguage.locale(viewer);
    List<SkillView> skills = LanguageAudience.call(viewer, () -> skills(registry, mutations, keptApart));
    return new CatalogView(revision, locale, skills);
  }

  /**
   * The names {@code viewer} sees on each adaptation's own skill pages, for every adaptation of the catalog
   * {@link #build} gives them whose short name differs from its catalog name.
   */
  public static List<ShortNameView> shortNames(SkillRegistry registry, UUID viewer, List<SkillMutation> mutations) {
    return LanguageAudience.call(viewer, () -> {
      List<ShortNameView> names = new ArrayList<>();
      Set<String> listed = new HashSet<>();
      for (Skill<?> skill : listedSkills(registry, mutations)) {
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
          String shortName = plain(adaptation.getShortName());
          if (!shortName.isEmpty() && !shortName.equals(plain(adaptation.getDisplayName())) && listed.add(adaptation.getName())) {
            names.add(new ShortNameView(adaptation.getName(), shortName));
          }
        }
      }
      return names;
    });
  }

  private static List<Skill<?>> listedSkills(SkillRegistry registry, List<SkillMutation> mutations) {
    List<Skill<?>> skills = new ArrayList<>(registry.getSkills());
    for (SkillMutation mutation : mutations) {
      MutatedSkill mutated = registry.getMutatedSkill(mutation.id());
      if (mutated != null) {
        skills.add(mutated);
      }
    }
    return skills;
  }

  private static List<SkillView> skills(SkillRegistry registry, List<SkillMutation> mutations, boolean keptApart) {
    List<SkillView> skills = new ArrayList<>();
    for (Skill<?> skill : listedSkills(registry, mutations)) {
      skills.add(skill(skill, keptApart));
    }
    return skills;
  }

  private static SkillView skill(Skill<?> skill, boolean keptApart) {
    List<AdaptationView> adaptations = new ArrayList<>();
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      adaptations.add(adaptation(skill, adaptation));
    }
    PreferenceView enabled = preference(CommonPreferences.SKILL_ENABLED,
        PlayerPreferences.policy(skill, CommonPreferences.SKILL_ENABLED), true);
    return new SkillView(
        skill.getName(),
        plain(skill.getLocalizedName()),
        skill instanceof MutatedSkill mutated && !keptApart ? mutated.getDescriptionWithKept() : skill.getDescription(),
        ViewIcons.skill(skill),
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
      adaptation.addLevelStats(level, stats);
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
        ViewIcons.adaptation(adaptation),
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
