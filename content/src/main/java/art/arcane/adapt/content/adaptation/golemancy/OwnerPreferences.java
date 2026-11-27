package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

  void remember(PlayerProfile player) {
    known.put(player.uuid(), snapshot(player));
  }

  private <E extends Enum<E>> E resolve(UUID owner, PlayerPreference<E> preference) {
    Optional<PlayerProfile> online = AdaptApi.players().find(owner);
    Known current;
    if (online.isPresent()) {
      current = snapshot(online.get());
      known.put(owner, current);
    } else {
      current = known.get(owner);
    }
    return current == null ? adaptation.preferenceFor(0, null, preference) : current.resolve(adaptation, preference);
  }

  private Known snapshot(PlayerProfile player) {
    Map<String, String> saved = new HashMap<>();
    for (PlayerPreference<?> preference : adaptation.getPlayerPreferences()) {
      player.preferences().saved(adaptation.getId(), preference.id()).ifPresent((String value) -> saved.put(preference.id(), value));
    }
    return new Known(Map.copyOf(saved), adaptation.getLevel(player));
  }

  private record Known(Map<String, String> saved, int level) {
    private <E extends Enum<E>> E resolve(Adaptation<?> adaptation, PlayerPreference<E> preference) {
      return adaptation.preferenceFor(level, saved.get(preference.id()), preference);
    }
  }
}
