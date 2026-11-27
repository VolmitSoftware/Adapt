package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The sneak and swap hands that Rewind or Second Chance used. They cancel it, and the Chronos Trigger still counts it,
 * unlike a swap another ability took.
 */
public final class ChronosGesture {
  private static final Map<UUID, Long> USED = new ConcurrentHashMap<>();

  private ChronosGesture() {
  }

  public static void use(PlayerSwapHandItemsEvent e) {
    e.setCancelled(true);
    USED.put(e.player().uuid(), e.dispatchId());
  }

  /** Whether Chronos used this swap; forgets the mark. */
  public static boolean take(PlayerSwapHandItemsEvent e) {
    return USED.remove(e.player().uuid(), e.dispatchId());
  }

  public static void forget(UUID player) {
    USED.remove(player);
  }
}
