package art.arcane.adapt.papi;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdaptCatalogSnapshot {
  private final long revision;
  private final Map<String, AdaptCatalogSkill> skills;
  private final Map<String, AdaptCatalogAdaptation> adaptations;
  private final Map<String, AdaptCatalogMutation> mutations;
  private final String skillCountText;
  private final String adaptationCountText;
  private final String mutationCountText;

  private AdaptCatalogSnapshot(
      long revision,
      Map<String, AdaptCatalogSkill> skills,
      Map<String, AdaptCatalogAdaptation> adaptations,
      Map<String, AdaptCatalogMutation> mutations
  ) {
    this.revision = revision;
    this.skills = Map.copyOf(skills);
    this.adaptations = Map.copyOf(adaptations);
    this.mutations = Map.copyOf(mutations);
    this.skillCountText = PlaceholderValues.count(this.skills.size());
    this.adaptationCountText = PlaceholderValues.count(this.adaptations.size());
    this.mutationCountText = PlaceholderValues.count(this.mutations.size());
  }

  public static AdaptCatalogSnapshot build(long revision, List<Skill<?>> skills) {
    Map<String, AdaptCatalogSkill> skillIndex = new HashMap<>();
    Map<String, AdaptCatalogAdaptation> adaptationIndex = new HashMap<>();

    for (Skill<?> skill : skills) {
      if (skill == null) {
        continue;
      }

      String skillId = normalize(skill.getName());

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

        String adaptationId = normalize(adaptation.getName());

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

    return new AdaptCatalogSnapshot(revision, skillIndex, adaptationIndex, buildMutations());
  }

  private static Map<String, AdaptCatalogMutation> buildMutations() {
    Map<String, AdaptCatalogMutation> index = new HashMap<>();

    for (MutationType type : MutationType.values()) {
      String id = normalize(type.id());

      if (id == null) {
        continue;
      }

      index.putIfAbsent(id, new AdaptCatalogMutation(id, PlaceholderValues.text(type.displayName()), type));
    }

    return index;
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
        adaptation.getName(),
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

  public AdaptCatalogSkill skill(String id) {
    return skills.get(id);
  }

  public AdaptCatalogAdaptation adaptation(String id) {
    return adaptations.get(id);
  }

  public AdaptCatalogMutation mutation(String id) {
    return mutations.get(id);
  }

  public String skillCountText() {
    return skillCountText;
  }

  public String adaptationCountText() {
    return adaptationCountText;
  }

  public String mutationCountText() {
    return mutationCountText;
  }
}
