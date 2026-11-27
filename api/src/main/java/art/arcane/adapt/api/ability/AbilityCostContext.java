package art.arcane.adapt.api.ability;

import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/** One cost an ability is about to take. Adapt creates these; new accessors may be added. */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface AbilityCostContext {
  /** The use the cost belongs to, always in {@link AbilityPhase#ACTIVATE}. */
  AbilityContext ability();

  /** Names the cost within the ability, such as {@code unlink} or {@code durability}; lower case. */
  String costKey();

  /** What the ability takes by default. */
  AbilityCostKind kind();

  /** One unit of the item the ability takes by default, for {@link AbilityCostKind#ITEM}. */
  Optional<ItemRef> defaultItem();

  /** How much of {@link #kind()} the ability takes by default, never negative. */
  int defaultAmount();
}
