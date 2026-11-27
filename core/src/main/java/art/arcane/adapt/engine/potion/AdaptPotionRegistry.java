package art.arcane.adapt.engine.potion;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records which potion effect types Adapt itself applied to a player.
 * <p>
 * Adaptations push effects straight onto the entity, so without this ledger there is no way
 * to tell an adaptation buff apart from a potion the player drank. A reset needs to strip exactly
 * the former and leave the latter alone.
 */
public final class AdaptPotionRegistry {
  private static final Map<UUID, Set<Id>> APPLIED = new ConcurrentHashMap<>();

  private AdaptPotionRegistry() {
  }

  public static void record(UUID playerId, Id type) {
    if (playerId == null || type == null) {
      return;
    }

    APPLIED.computeIfAbsent(playerId, (UUID unused) -> ConcurrentHashMap.newKeySet()).add(type);
  }

  public static Set<Id> applied(UUID playerId) {
    Set<Id> types = playerId == null ? null : APPLIED.get(playerId);
    return types == null ? Set.of() : Set.copyOf(types);
  }

  public static void forget(UUID playerId) {
    if (playerId != null) {
      APPLIED.remove(playerId);
    }
  }

  /**
   * Strips every effect Adapt applied to this player and forgets the ledger. Must run on the
   * player's owning thread. Returns the number of effects actually removed.
   */
  public static int strip(PlayerRef player) {
    if (player == null) {
      return 0;
    }

    Set<Id> types = APPLIED.remove(player.uuid());
    if (types == null || types.isEmpty()) {
      return 0;
    }

    int removed = 0;
    for (Id type : types) {
      if (type != null && player.hasEffect(type)) {
        player.removeEffect(type);
        removed++;
      }
    }
    return removed;
  }

  /**
   * Prunes the ledger down to effects still active on the player. Called on quit so the map does
   * not grow with every player ever seen, while effects that persist across relogs stay strippable.
   */
  public static void retainActive(PlayerRef player) {
    if (player == null) {
      return;
    }

    Set<Id> types = APPLIED.get(player.uuid());
    if (types == null) {
      return;
    }

    types.removeIf((Id type) -> type == null || !player.hasEffect(type));
    if (types.isEmpty()) {
      APPLIED.remove(player.uuid(), types);
    }
  }

  public static void reset() {
    APPLIED.clear();
  }
}
