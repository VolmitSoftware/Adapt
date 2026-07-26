package art.arcane.adapt.api.ability;

import java.util.LinkedHashSet;
import java.util.Set;

public final class AbilityScope {
  private static final AbilityScope ALL = new AbilityScope(Set.of(), Set.of());

  private final Set<String> abilityIds;
  private final Set<String> skillIds;

  private AbilityScope(Set<String> abilityIds, Set<String> skillIds) {
    this.abilityIds = abilityIds;
    this.skillIds = skillIds;
  }

  public static AbilityScope everything() {
    return ALL;
  }

  public static AbilityScope abilities(String... abilityIds) {
    Set<String> ids = collect(abilityIds);
    return ids.isEmpty() ? ALL : new AbilityScope(ids, Set.of());
  }

  public static AbilityScope skill(String skillId) {
    return new AbilityScope(Set.of(), Set.of(AbilityText.requireId(skillId, "skillId")));
  }

  public static AbilityScope skills(String... skillIds) {
    Set<String> ids = collect(skillIds);
    return ids.isEmpty() ? ALL : new AbilityScope(Set.of(), ids);
  }

  public AbilityScope and(AbilityScope other) {
    if (other == null) {
      return this;
    }

    if (unscoped() || other.unscoped()) {
      return ALL;
    }

    Set<String> mergedAbilities = new LinkedHashSet<>(abilityIds);
    Set<String> mergedSkills = new LinkedHashSet<>(skillIds);
    mergedAbilities.addAll(other.abilityIds);
    mergedSkills.addAll(other.skillIds);
    return new AbilityScope(Set.copyOf(mergedAbilities), Set.copyOf(mergedSkills));
  }

  public boolean unscoped() {
    return abilityIds.isEmpty() && skillIds.isEmpty();
  }

  public int size() {
    return abilityIds.size() + skillIds.size();
  }

  public boolean matches(String abilityId, String skillId) {
    if (unscoped()) {
      return true;
    }

    if (abilityId != null && abilityIds.contains(abilityId)) {
      return true;
    }

    return skillId != null && skillIds.contains(skillId);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AbilityScope scope && abilityIds.equals(scope.abilityIds)
        && skillIds.equals(scope.skillIds);
  }

  @Override
  public int hashCode() {
    return 31 * abilityIds.hashCode() + skillIds.hashCode();
  }

  @Override
  public String toString() {
    return unscoped() ? "AbilityScope[every ability]" : "AbilityScope[abilities=" + abilityIds + ", skills="
        + skillIds + "]";
  }

  private static Set<String> collect(String[] ids) {
    if (ids == null || ids.length == 0) {
      return Set.of();
    }

    Set<String> out = new LinkedHashSet<>(ids.length);

    for (String id : ids) {
      String normalized = AbilityText.normalizeId(id);

      if (normalized.isEmpty()) {
        continue;
      }

      out.add(normalized);
    }

    return Set.copyOf(out);
  }
}
