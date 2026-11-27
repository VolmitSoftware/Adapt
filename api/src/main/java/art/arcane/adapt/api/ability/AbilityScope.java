package art.arcane.adapt.api.ability;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.Set;

/** Which abilities a policy or cost provider is asked about. An empty scope is everything. */
@ApiStatus.AvailableSince("1.0.0")
public final class AbilityScope {
  private static final AbilityScope ALL = new AbilityScope(Set.of(), Set.of());

  private final Set<Id> abilities;
  private final Set<Id> skills;

  private AbilityScope(Set<Id> abilities, Set<Id> skills) {
    this.abilities = abilities;
    this.skills = skills;
  }

  /** Every adaptation of every skill. */
  public static AbilityScope everything() {
    return ALL;
  }

  /** Only these adaptations. */
  public static AbilityScope abilities(Id... abilities) {
    return abilities.length == 0 ? ALL : new AbilityScope(Set.of(abilities), Set.of());
  }

  /** Every adaptation of these skills. */
  public static AbilityScope skills(Id... skills) {
    return skills.length == 0 ? ALL : new AbilityScope(Set.of(), Set.of(skills));
  }

  /** Whether this scope names no adaptation and no skill, and so covers everything. */
  public boolean isEverything() {
    return abilities.isEmpty() && skills.isEmpty();
  }

  /** Whether the adaptation {@code ability} of {@code skill} falls inside this scope. */
  public boolean matches(Id ability, Id skill) {
    Objects.requireNonNull(ability, "ability");
    Objects.requireNonNull(skill, "skill");
    return isEverything() || abilities.contains(ability) || skills.contains(skill);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AbilityScope scope && abilities.equals(scope.abilities) && skills.equals(scope.skills);
  }

  @Override
  public int hashCode() {
    return Objects.hash(abilities, skills);
  }

  @Override
  public String toString() {
    return isEverything() ? "AbilityScope[everything]" : "AbilityScope[abilities=" + abilities + ", skills=" + skills + "]";
  }
}
