package art.arcane.adapt.pack.link;

import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Who has the pack. Tracks each player's latest offer and the client's reports for it, and answers {@link #loaded}:
 * whether the client loaded the current offer. When the
 * pack is {@link #merged}, Adapt offers nothing and any server pack counts: a player has it from a report that some
 * pack loaded until a report that a pack was declined, failed or discarded, which also counts a client that loaded an
 * unrelated pack. Listeners hear when that answer turns true or false. Thread-safe; listeners run on the thread that
 * caused the change.
 */
public final class PackViewers {
  private final Predicate<UUID> excluded;
  private final boolean merged;
  private final Map<UUID, Offer> offers = new ConcurrentHashMap<>();
  private final List<Consumer<UUID>> loadedListeners = new CopyOnWriteArrayList<>();
  private final List<Consumer<UUID>> lostListeners = new CopyOnWriteArrayList<>();

  /** Viewers of the pack Adapt offers itself. */
  public PackViewers() {
    this(false, (UUID ignored) -> false);
  }

  private PackViewers(boolean merged, Predicate<UUID> excluded) {
    this.merged = merged;
    this.excluded = Objects.requireNonNull(excluded, "excluded");
  }

  /** Viewers of a pack merged into another one; reports from {@code excluded} players, such as Bedrock, never count. */
  public static PackViewers merged(Predicate<UUID> excluded) {
    return new PackViewers(true, excluded);
  }

  /** True when Adapt's pack reaches players inside another pack and Adapt offers none itself. */
  public boolean merged() {
    return merged;
  }

  /** Records a new offer; reports for earlier pack ids are ignored from now on. */
  public void offered(UUID playerId, UUID packId) {
    Objects.requireNonNull(packId, "packId");
    change(playerId, () -> {
      offers.put(playerId, new Offer(packId, PackState.OFFERED));
      return true;
    });
  }

  /**
   * Applies one client report. Returns false when it is for a pack this player was not last offered, or, when merged,
   * when it comes from an excluded player or says nothing about whether a pack is held.
   */
  public boolean status(UUID playerId, UUID packId, PackState state) {
    Objects.requireNonNull(state, "state");
    if (merged) {
      return change(playerId, () -> {
        if (!state.settled() || excluded.test(playerId)) {
          return false;
        }
        offers.put(playerId, new Offer(packId, state));
        return true;
      });
    }
    return change(playerId, () -> {
      Offer offer = offers.get(playerId);
      if (offer == null || !offer.packId.equals(packId)) {
        return false;
      }
      offers.put(playerId, new Offer(packId, state));
      return true;
    });
  }

  /** Forgets the player's offer after the pack was removed from their client. */
  public void removed(UUID playerId) {
    change(playerId, () -> offers.remove(playerId) != null);
  }

  /** Forgets the player without telling listeners; call when they leave. */
  public void quit(UUID playerId) {
    offers.remove(playerId);
  }

  public Optional<PackState> state(UUID playerId) {
    Offer offer = offers.get(playerId);
    return offer == null ? Optional.empty() : Optional.of(offer.state);
  }

  public Optional<UUID> packId(UUID playerId) {
    Offer offer = offers.get(playerId);
    return offer == null ? Optional.empty() : Optional.of(offer.packId);
  }

  /** True when book menus should be used for this player: their client loaded the pack. */
  public boolean loaded(UUID playerId) {
    Offer offer = offers.get(playerId);
    return offer != null && offer.state == PackState.LOADED;
  }

  public boolean loaded(PlayerRef player) {
    return player != null && loaded(player.uuid());
  }

  /** Calls {@code listener} when {@link #loaded} turns true; the returned action removes it. */
  public Runnable onLoaded(Consumer<UUID> listener) {
    loadedListeners.add(Objects.requireNonNull(listener, "listener"));
    return () -> loadedListeners.remove(listener);
  }

  /** Calls {@code listener} when {@link #loaded} turns false; the returned action removes it. */
  public Runnable onLost(Consumer<UUID> listener) {
    lostListeners.add(Objects.requireNonNull(listener, "listener"));
    return () -> lostListeners.remove(listener);
  }

  private boolean change(UUID playerId, BooleanSupplier update) {
    Objects.requireNonNull(playerId, "playerId");
    boolean before;
    boolean applied;
    boolean after;
    synchronized (this) {
      before = loaded(playerId);
      applied = update.getAsBoolean();
      after = loaded(playerId);
    }
    if (before != after) {
      for (Consumer<UUID> listener : after ? loadedListeners : lostListeners) {
        listener.accept(playerId);
      }
    }
    return applied;
  }

  private record Offer(UUID packId, PackState state) {
  }
}
