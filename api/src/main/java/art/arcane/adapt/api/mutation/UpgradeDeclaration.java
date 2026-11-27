package art.arcane.adapt.api.mutation;

import art.arcane.adapt.api.content.ContentIds;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * An upgraded adaptation a kit declares.
 *
 * @param id      the id the built upgrade must report
 * @param factory builds a fresh upgrade
 * @param sources the skill's own adaptations the upgrade stands in for: a player who learns the upgrade stops holding
 *                them, and what they unlocked (recipes, brewing, kept settings) reads the level the upgrade lends
 * @param fuses   the sources whose own code keeps running for that player: every level and active-level query of
 *                such an original reads the level this upgrade lends. A source left out is replaced by the upgrade's
 *                own code and stays at 0 for the player. Another upgrade may fuse an original this one replaces.
 * @param price   {@link MutationPrice#UPGRADED}, or {@link MutationPrice#TOOL} for an upgrade that hands out a tool
 */
@ApiStatus.Experimental
public record UpgradeDeclaration(Id id, Supplier<? extends SkillUpgrade<?>> factory, List<Id> sources, Set<Id> fuses,
                                 MutationPrice price) {
  /** @throws IllegalArgumentException when {@code fuses} names an adaptation that is not one of the sources */
  public UpgradeDeclaration {
    ContentIds.require(id, "upgrade");
    Objects.requireNonNull(factory, "factory");
    Objects.requireNonNull(price, "price");
    sources = List.copyOf(sources);
    fuses = Set.copyOf(fuses);
    for (Id fused : fuses) {
      if (!sources.contains(fused)) {
        throw new IllegalArgumentException("Upgrade " + id + " fuses " + fused + ", which is not one of its sources");
      }
    }
  }
}
