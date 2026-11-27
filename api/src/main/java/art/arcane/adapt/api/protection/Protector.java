package art.arcane.adapt.api.protection;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * A land or region protection that adaptations must respect. Adapt asks before an adaptation breaks, places,
 * damages or opens anything for a player.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface Protector {
  /** A short unique name. Admins switch protectors per adaptation by it in config. */
  String name();

  /** Whether adaptations respect this protector unless their config says otherwise. */
  boolean enabledByDefault();

  /**
   * Whether {@code player} may do {@code check} at {@code location}. A protector must deny a check it does not know:
   * a minor release may add {@link ProtectionCheck} constants. A protector that throws counts as a denial and is
   * logged with its name.
   *
   * @param victim     the entity about to be damaged, for {@link ProtectionCheck#PVP} and {@link ProtectionCheck#PVE}
   *                   when one is known
   * @param adaptation the adaptation acting for the player, or null for a check made for no adaptation
   */
  boolean allows(ProtectionCheck check, PlayerRef player, Loc location, @Nullable EntityRef victim,
                 @Nullable Id adaptation);
}
