package art.arcane.adapt.papi;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.platform.value.Id;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdaptCatalogSnapshot {
  private final long revision;
  private final Map<String, AdaptCatalogSkill> skills;
  private final Map<String, AdaptCatalogAdaptation> adaptations;
  private final String skillCountText;
  private final String adaptationCountText;

  private AdaptCatalogSnapshot(
      long revision,
      Map<String, AdaptCatalogSkill> skills,
      Map<String, AdaptCatalogAdaptation> adaptations
  ) {
    this.revision = revision;
    this.skills = Map.copyOf(skills);
    this.adaptations = Map.copyOf(adaptations);
    this.skillCountText = PlaceholderValues.count(this.skills.size());
    this.adaptationCountText = PlaceholderValues.count(this.adaptations.size());
  }

  public static AdaptCatalogSnapshot build(long revision, List<Skill<?>> skills) {
    Map<String, AdaptCatalogSkill> skillIndex = new HashMap<>();
    Map<String, AdaptCatalogAdaptation> adaptationIndex = new HashMap<>();

    for (Skill<?> skill : skills) {
      if (skill == null) {
        continue;
      }

      String skillId = normalize(SkillHost.lineKey(skill));

      if (skillId == null) {
        continue;
      }

      boolean skillEnabled = skill.isEnabled();
      String skillName = PlaceholderValues.text(skill.getLocalizedName());
      List<? extends Adaptation<?>> owned = skill.getAdaptations();
      int adaptationCount = 0;

      for (Adaptation<?> adaptation : owned) {
        if (adaptation == null) {
          continue;
        }

        String adaptationId = normalize(adaptation.getId().toString());

        if (adaptationId == null) {
          continue;
        }

        adaptationCount++;
        adaptationIndex.putIfAbsent(adaptationId, describe(adaptationId, adaptation, skillId, skillName, skillEnabled));
      }

      skillIndex.putIfAbsent(skillId, new AdaptCatalogSkill(
          skillId,
          skillName,
          skillEnabled,
          adaptationCount,
          PlaceholderValues.count(adaptationCount)
      ));
    }

    return new AdaptCatalogSnapshot(revision, skillIndex, adaptationIndex);
  }

  private static AdaptCatalogAdaptation describe(
      String adaptationId,
      Adaptation<?> adaptation,
      String skillId,
      String skillName,
      boolean skillEnabled
  ) {
    int maxLevel = Math.max(0, adaptation.getMaxLevel());
    int[] cumulative = new int[maxLevel + 1];

    for (int level = 1; level <= maxLevel; level++) {
      cumulative[level] = cumulative[level - 1] + adaptation.getCostFor(level);
    }

    return new AdaptCatalogAdaptation(
        adaptationId,
        adaptation.getId().toString(),
        skillId,
        skillName,
        adaptation.isEnabled(),
        skillEnabled,
        adaptation.isPermanent(),
        maxLevel,
        cumulative,
        PlaceholderValues.text(adaptation.getDisplayName()),
        PlaceholderValues.count(maxLevel)
    );
  }

  private static String normalize(String raw) {
    if (raw == null) {
      return null;
    }

    String trimmed = raw.trim().toLowerCase(Locale.ROOT);
    return trimmed.isEmpty() ? null : trimmed;
  }

  public long revision() {
    return revision;
  }

  /** The skill a placeholder names by id; a bare id is in the {@code adapt} namespace. */
  public AdaptCatalogSkill skill(String id) {
    Id parsed = id == null ? null : Id.tryParseAdapt(id);
    return parsed == null ? null : skills.get(parsed.toString());
  }

  /** The adaptation a placeholder names by id; a bare id is in the {@code adapt} namespace. */
  public AdaptCatalogAdaptation adaptation(String id) {
    Id parsed = id == null ? null : Id.tryParseAdapt(id);
    return parsed == null ? null : adaptations.get(parsed.toString());
  }

  public String skillCountText() {
    return skillCountText;
  }

  public String adaptationCountText() {
    return adaptationCountText;
  }

}
