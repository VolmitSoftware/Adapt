package art.arcane.adapt.api.content;

import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

/**
 * Per-player cooldown timestamps of one component. Obtained from {@code cooldowns()} in a field initialiser; Adapt
 * forgets players who leave.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface Cooldowns {
  /** True when the player has no mark or the mark is at least {@code cooldownMillis} old; a stale mark is dropped. */
  boolean isReady(UUID player, long cooldownMillis);

  /**
   * Starts a cooldown without showing it, for input debounce, proc gates and keys that are not players, such as a
   * target's id.
   */
  void mark(UUID player);

  /** Starts a cooldown and shows its glyph on the player's HUD when the owner is an adaptation. */
  void mark(UUID player, long cooldownMillis);

  /** Milliseconds until a cooldown of {@code cooldownMillis} from the last mark ends; 0 when ready or never marked. */
  long remaining(UUID player, long cooldownMillis);

  /** Ends the player's cooldown early and removes a glyph {@link #mark(UUID, long)} showed. */
  void clear(UUID player);

  /** Drops every mark at least {@code cooldownMillis} old. */
  void clearExpired(long cooldownMillis);
}
