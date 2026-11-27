package art.arcane.adapt.client.view;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationRequirement;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.api.preference.PreferenceToggleGroup;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
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
  public static CatalogView build(SkillRegistry registry, UUID viewer, List<SkillPair> mutations, boolean keptApart) {
    long revision = registry.getCatalogRevision();
    String locale = AdaptLanguage.locale(viewer);
    List<SkillView> skills = LanguageAudience.call(viewer, () -> skills(registry, mutations, keptApart));
    return new CatalogView(revision, locale, skills);
  }

  /**
   * The names {@code viewer} sees on each adaptation's own skill pages, for every adaptation of the catalog
   * {@link #build} gives them whose short name differs from its catalog name.
   */
  public static List<ShortNameView> shortNames(SkillRegistry registry, UUID viewer, List<SkillPair> mutations) {
    return LanguageAudience.call(viewer, () -> {
      List<ShortNameView> names = new ArrayList<>();
      Set<String> listed = new HashSet<>();
      for (Skill<?> skill : listedSkills(registry, mutations)) {
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
          String shortName = plain(adaptation.getShortName());
          if (!shortName.isEmpty() && !shortName.equals(plain(adaptation.getDisplayName())) && listed.add(adaptation.getId().toString())) {
            names.add(new ShortNameView(adaptation.getId().toString(), shortName));
          }
        }
      }
      return names;
    });
  }

  private static List<Skill<?>> listedSkills(SkillRegistry registry, List<SkillPair> mutations) {
    List<Skill<?>> skills = new ArrayList<>(registry.getSkills());
    for (SkillPair mutation : mutations) {
      MutatedSkill mutated = registry.getMutatedSkill(mutation);
      if (mutated != null) {
        skills.add(mutated);
      }
    }
    return skills;
  }

  private static List<SkillView> skills(SkillRegistry registry, List<SkillPair> mutations, boolean keptApart) {
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
    PreferenceView enabled = preference(PlayerPreferences.ownerKey(skill), CommonPreferences.SKILL_ENABLED,
        PlayerPreferences.policy(skill, CommonPreferences.SKILL_ENABLED), true);
    return new SkillView(
        SkillHost.lineKey(skill),
        plain(skill.getLocalizedName()),
        skill instanceof MutatedSkill mutated && !keptApart ? mutated.getDescriptionWithKept() : skill.getDescription(),
        ViewIcons.skill(skill),
        skill.getColor().packed() & 0xFFFFFF,
        skill.isEnabled(),
        SkillHost.adaptationRowGroups(skill),
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
      refunds.add(AdaptationHost.refundCostFor(adaptation, level - 1, level));
      powerCosts.add(adaptation.getPowerCostFor(level, level - 1));
      StatLines stats = new StatLines();
      adaptation.addLevelStats(level, stats);
      statLines.add(stats.lines());
    }
    List<RequirementView> requirements = new ArrayList<>();
    for (AdaptationRequirement requirement : adaptation.getRequirements()) {
      requirements.add(new RequirementView(requirement.adaptation().toString(), requirement.level()));
    }
    List<PreferenceView> preferences = new ArrayList<>();
    String owner = PlayerPreferences.ownerKey(adaptation);
    for (PlayerPreference<?> preference : adaptation.getPlayerPreferences()) {
      preferences.add(preference(owner, preference, PlayerPreferences.policy(adaptation, preference),
          !adaptation.isPlayerPreferenceBlocked(preference)));
    }
    List<PreferenceGroupView> groups = new ArrayList<>();
    for (PreferenceToggleGroup group : adaptation.getPlayerPreferenceGroups()) {
      groups.add(group(group));
    }
    return new AdaptationView(
        adaptation.getId().toString(),
        SkillHost.lineKey(skill),
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

  private static <E extends Enum<E>> PreferenceView preference(String owner, PlayerPreference<E> preference,
                                                               PreferencePolicy policy, boolean editable) {
    List<PreferenceChoiceView> choices = new ArrayList<>();
    for (PlayerPreference.Choice<E> choice : preference.choices()) {
      String value = choice.value().name();
      if (policy.allowedValues != null && policy.allowedValues.contains(value)) {
        choices.add(new PreferenceChoiceView(value,
            PlayerPreferences.shown(PlayerPreferences.label(owner, preference, choice.value())),
            IconRef.item(PlayerPreferences.shownIcon(choice).toString()), choice.minimumLevel()));
      }
    }
    E serverDefault = preference.parse(policy.defaultValue).orElse(null);
    return new PreferenceView(
        preference.id(),
        PlayerPreferences.shown(PlayerPreferences.label(owner, preference)),
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
    return new PreferenceGroupView(group.id(), group.label().resolve(), IconRef.item(group.icon().toString()),
        entries);
  }
}
