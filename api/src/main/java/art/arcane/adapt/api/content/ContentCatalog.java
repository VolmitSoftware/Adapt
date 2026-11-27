package art.arcane.adapt.api.content;

import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;

/**
 * Everything registered in the running core, from every addon. Built-in content is here on the same terms as
 * anyone else's.
 *
 * <p>The instances returned are the live ones Adapt ticks and dispatches events to. A config reload or an addon
 * refresh replaces them, so look content up again after a {@code CatalogChangedEvent} instead of keeping it.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface ContentCatalog {
  /** Rises on every change to the catalog. */
  long revision();

  /** A registered skill by its id; empty when none is registered under it. */
  Optional<Skill<?>> skill(Id id);

  /** Every registered skill usable on this server, in registration order. Disabled and vetoed skills are included. */
  List<Skill<?>> skills();

  /** An adaptation, upgrade, mutation block or signature by its id. */
  Optional<Adaptation<?>> adaptation(Id id);

  /** Every learnable adaptation of every skill, in skill then registration order. */
  List<Adaptation<?>> adaptations();

  /**
   * Every registered custom item, each id once: those addons registered directly, then those of every adaptation,
   * upgrade, mutation block and signature. Mutation tools are not listed. A stack is at most one of them.
   */
  List<CustomItem.Definition<?>> items();

  /** A registered custom item by its id. */
  Optional<CustomItem.Definition<?>> item(Id id);

  /** The namespace of the addon that registered the id. */
  Optional<String> owner(Id id);

  /** The adaptation a mutation slot holds: its signature if one is registered, else the generated adaptation. */
  @ApiStatus.Experimental
  Optional<Adaptation<?>> mutated(MutationSlot slot);

  /** The signatures of every mutated skill built so far; a pair is built once a player mutates it or it is claimed. */
  @ApiStatus.Experimental
  List<Signature<?>> signatures();

  /** The adaptation and level that unlock crafting a recipe, when an adaptation registered it. */
  @ApiStatus.Experimental
  Optional<AdaptationRequirement> recipeUnlock(Id recipe);

  /** The vetoes in force; an addon places one with {@link art.arcane.adapt.api.addon.AddonHandle#veto}. */
  List<ContentVeto> vetoes();
}
