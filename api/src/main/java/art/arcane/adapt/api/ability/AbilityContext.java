package art.arcane.adapt.api.ability;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;
import java.util.UUID;

/** One use of an adaptation that a policy or cost provider is asked about. Adapt creates these; new accessors may be added. */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface AbilityContext {
  /** The same for the check, the charge and the settle or refund of one activation. */
  UUID activationId();

  /** The adaptation. */
  Id ability();

  /** The skill it is registered under. */
  Id skill();

  /** The player's level in it, never negative. */
  int level();

  /** {@link AbilityPhase#CHECK} when nothing happens yet, {@link AbilityPhase#ACTIVATE} when it fires. */
  AbilityPhase phase();

  /** The player using the ability. */
  PlayerRef player();

  /** Where the ability acts, when it has a place. */
  Optional<Loc> origin();
}
