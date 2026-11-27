package art.arcane.adapt.engine.preference;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.engine.adaptation.PlayerStateRegistry;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PreferenceMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

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

  public static boolean confirm(Adaptation<?> adaptation, PlayerRef player, String action, ItemRef... items) {
    Objects.requireNonNull(adaptation);
    Objects.requireNonNull(player);
    Objects.requireNonNull(action);
    if (!player.isOnline() || !Tasks.isOwnedByCurrentRegion(player)) {
      return false;
    }
    List<ItemRef> snapshot = new ArrayList<>(items.length);
    for (ItemRef item : items) {
      snapshot.add(item == null || item.isEmpty() ? null : item.copy());
    }
    long now = System.nanoTime();
    Pending previous = PENDING.remove(player.uuid());
    if (previous != null && now - previous.startedNanos() <= WINDOW_NANOS
        && previous.adaptation().equals(adaptation.getId().toString()) && previous.action().equals(action)
        && sameItems(previous.items(), snapshot)) {
      return true;
    }
    PENDING.put(player.uuid(), new Pending(adaptation.getId().toString(), action, snapshot, now));
    player.sendMessage(AdaptLanguage.text(PreferenceMessages.CONFIRM));
    return false;
  }

  public static void clear(PlayerRef player) {
    PENDING.remove(player.uuid());
  }

  private static boolean sameItems(List<ItemRef> previous, List<ItemRef> current) {
    if (previous.size() != current.size()) {
      return false;
    }
    for (int index = 0; index < previous.size(); index++) {
      ItemRef before = previous.get(index);
      ItemRef after = current.get(index);
      if (before == null || after == null) {
        if (before != after) {
          return false;
        }
        continue;
      }
      if (before.amount() != after.amount() || !before.isSimilar(after)) {
        return false;
      }
    }
    return true;
  }

  private record Pending(String adaptation, String action, List<ItemRef> items, long startedNanos) {
  }
}
