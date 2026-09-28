package art.arcane.adapt.api.preference;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PreferenceMessages;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PreferenceConfirmation {
  private static final long WINDOW_NANOS = 5_000_000_000L;
  private static final Map<UUID, Pending> PENDING = PlayerStateRegistry.newPlayerMap();

  private PreferenceConfirmation() {
  }

  public static boolean confirm(Adaptation<?> adaptation, Player player, String action, ItemStack... items) {
    Objects.requireNonNull(adaptation);
    Objects.requireNonNull(player);
    Objects.requireNonNull(action);
    if (!player.isOnline() || !J.isOwnedByCurrentRegion(player)) {
      return false;
    }
    List<ItemStack> snapshot = new ArrayList<>(items.length);
    for (ItemStack item : items) {
      snapshot.add(item == null ? ItemStack.empty() : item.clone());
    }
    long now = System.nanoTime();
    Pending previous = PENDING.remove(player.getUniqueId());
    if (previous != null && now - previous.startedNanos() <= WINDOW_NANOS
        && previous.adaptation().equals(adaptation.getName()) && previous.action().equals(action)
        && previous.items().equals(snapshot)) {
      return true;
    }
    PENDING.put(player.getUniqueId(), new Pending(adaptation.getName(), action, List.copyOf(snapshot), now));
    player.sendMessage(AdaptLanguage.text(PreferenceMessages.CONFIRM));
    return false;
  }

  public static void clear(Player player) {
    PENDING.remove(player.getUniqueId());
  }

  private record Pending(String adaptation, String action, List<ItemStack> items, long startedNanos) {
  }
}
