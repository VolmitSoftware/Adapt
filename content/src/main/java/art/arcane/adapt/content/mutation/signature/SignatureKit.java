package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** What several signatures share: the Chronos clock gesture and level scaling outside a run. */
final class SignatureKit {
  static final Id CLOCK = Id.minecraft("clock");
  private static final Set<UUID> REFUSED = ConcurrentHashMap.newKeySet();

  private SignatureKit() {
  }

  /** Sneak and swap hands with a clock in either hand, the Chronos Trigger's gesture. */
  static boolean clockSwap(PlayerSwapHandItemsEvent e) {
    PlayerRef player = e.player();
    return player.isSneaking()
        && (CLOCK.equals(player.inventory().mainHand().type()) || CLOCK.equals(player.inventory().offHand().type()));
  }

  /** The clock gesture reached the signature, which turned it down with feedback; it keeps the swap. */
  static void refuse(PlayerRef player) {
    REFUSED.add(player.uuid());
  }

  /** Takes the swap when the signature used or refused it, so Rewind and Second Chance leave it. */
  static void settle(PlayerSwapHandItemsEvent e, boolean used) {
    if (REFUSED.remove(e.player().uuid()) || used) {
      e.setCancelled(true);
    }
  }

  /** {@code atFirstLevel} at level 1, rising in a line to {@code atTopLevel} at {@code topLevel}. */
  static double scaled(int level, int topLevel, double atFirstLevel, double atTopLevel) {
    double power = topLevel <= 1 ? 1D : Math.min(1D, Math.max(0D, (level - 1D) / (topLevel - 1D)));
    return atFirstLevel + ((atTopLevel - atFirstLevel) * power);
  }
}
