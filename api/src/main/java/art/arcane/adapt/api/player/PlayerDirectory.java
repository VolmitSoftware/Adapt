package art.arcane.adapt.api.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The players Adapt has loaded. Only online players have a profile; of an offline player's data only an addon's own
 * {@link PlayerSection section} is readable, through {@link #peek(UUID, PlayerSection)}.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface PlayerDirectory {
  /** The profile of an online player whose data has finished loading. */
  Optional<PlayerProfile> find(PlayerRef player);

  /** As {@link #find(PlayerRef)}, by the player's UUID; empty for an offline player. */
  Optional<PlayerProfile> find(UUID player);

  /**
   * The profile of an online player.
   *
   * @throws IllegalStateException when the player's data has not loaded yet, which is the case for a short time
   *                               after they join
   */
  PlayerProfile require(PlayerRef player);

  /**
   * A player's data section for reading, whether or not they are online: the live value while they are, otherwise
   * the one in their save. Do not change it; changes to an offline player's copy are never saved. Reading a save can
   * block, so call it off the server thread.
   *
   * @param section the object {@link art.arcane.adapt.api.addon.ContentRegistrar#playerSection} returned
   * @throws IllegalStateException when Adapt is not running, or the section is refused as by
   *                               {@link PlayerProfile#section}
   */
  @ApiStatus.Experimental
  <T> T peek(UUID player, PlayerSection<T> section);

  /** Whether the player has Adapt's debug mode on ({@code /adapt debug}), which unlocks commands meant for testing. */
  @ApiStatus.Experimental
  boolean isDebugging(UUID player);

  /** A snapshot of the profiles of every online player whose data has loaded. */
  List<PlayerProfile> online();

  /**
   * Multiplies the XP every player earns for {@code durationMillis}, on top of each player's own boosts. Players
   * who join while it lasts earn with it too.
   */
  void boost(double multiplier, long durationMillis);
}
