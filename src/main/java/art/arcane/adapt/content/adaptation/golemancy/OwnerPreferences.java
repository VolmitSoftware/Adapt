package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PlayerPreferenceData;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.world.AdaptPlayer;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class OwnerPreferences {
  private final Adaptation<?> adaptation;
  private final Map<UUID, Known> known = new ConcurrentHashMap<>();

  OwnerPreferences(Adaptation<?> adaptation) {
    this.adaptation = Objects.requireNonNull(adaptation);
  }

  boolean enabled(UUID owner, PlayerPreference<CommonPreferences.Toggle> preference) {
    return resolve(owner, preference) == CommonPreferences.Toggle.ON;
  }

  void remember(UUID owner, AdaptPlayer player) {
    known.put(owner, new Known(player.getData().getPreferences(), adaptation.getLevel(player)));
  }

  private <E extends Enum<E>> E resolve(UUID owner, PlayerPreference<E> preference) {
    Known current = current(owner);
    return current == null
        ? PlayerPreferences.resolve(adaptation, (PlayerPreferenceData) null, 0, preference)
        : PlayerPreferences.resolve(adaptation, current.preferences(), current.level(), preference);
  }

  private Known current(UUID owner) {
    AdaptPlayer player = adaptation.getServer().getOnlineAdaptPlayer(owner);
    if (player == null) {
      return known.get(owner);
    }
    PlayerPreferenceData preferences = player.getData().getPreferences();
    int level = adaptation.getLevel(player);
    Known last = known.get(owner);
    if (last != null && last.preferences() == preferences && last.level() == level) {
      return last;
    }
    Known next = new Known(preferences, level);
    known.put(owner, next);
    return next;
  }

  private record Known(PlayerPreferenceData preferences, int level) {
  }
}
