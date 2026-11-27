package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;

import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Borrowed Time's debt: part of a hit is held back and owed afterwards, one pulse a second. The debt is stored on the
 * player, so logging out never clears it; it is paid on return. Death clears it, and so does {@link #cancel}.
 */
public final class ChronosDebt {
  private static final int MAX_PENDING_HITS = 16;
  private static final ThreadLocal<Boolean> PAYING = new ThreadLocal<>();

  /** What the owner does when a hit is deferred and when a pulse is owed. */
  public interface Hooks {
    /** Deals one payback pulse to the player. */
    void pay(PlayerRef player, double damage);

    /** A hit went through with {@code amount} of it deferred. */
    void deferred(PlayerRef player, double amount);

    /** A pulse was paid; {@code cleared} when nothing more is owed. */
    void paid(PlayerRef player, double damage, boolean cleared);
  }

  private final DataKey<String> stamp;
  private final Map<UUID, Deque<DeferredDamage>> deferred;
  private final Map<UUID, Deque<PendingDamage>> pending;
  private final Hooks hooks;
  private final Set<UUID> settling = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean accepting = new AtomicBoolean(true);

  /** {@code stamp} keeps the debt on the player; both maps come from the owner's player state. */
  public ChronosDebt(DataKey<String> stamp, Map<UUID, Deque<DeferredDamage>> deferred,
                     Map<UUID, Deque<PendingDamage>> pending, Hooks hooks) {
    this.stamp = stamp;
    this.deferred = deferred;
    this.pending = pending;
    this.hooks = hooks;
  }

  /** Starts settling again and loads the debt of everyone online. */
  public void start() {
    accepting.set(true);
    for (PlayerRef player : AdaptApi.platform().host().onlinePlayers()) {
      AdaptApi.scheduler().runEntity(player, () -> restore(player));
    }
  }

  public void stop() {
    accepting.set(false);
    settling.clear();
    deferred.clear();
    pending.clear();
  }

  /** True while any debt deals a payback pulse on this thread, so no debt defers it again. */
  public boolean isPaying() {
    return Boolean.TRUE.equals(PAYING.get());
  }

  /** Holds back {@code fraction} of the hit; it becomes debt only if the hit goes through ({@link #finish}). */
  public void defer(PlayerRef player, EntityDamageEvent e, double fraction, double minimumDamage, int pulses) {
    if (e.isCancelled() || isPaying() || fraction <= 0D) {
      return;
    }
    double finalDamage = e.finalDamage();
    if (finalDamage < minimumDamage) {
      return;
    }
    UUID id = player.uuid();
    Deque<PendingDamage> hits = pending.computeIfAbsent(id, (UUID unused) -> new ConcurrentLinkedDeque<>());
    if (hits.size() >= MAX_PENDING_HITS) {
      return;
    }
    e.setDamage(Math.max(0D, e.damage() * (1D - Math.min(1D, fraction))));
    double deferredAmount = Math.max(0D, finalDamage - Math.max(0D, e.finalDamage()));
    if (deferredAmount <= 0D) {
      if (hits.isEmpty()) {
        pending.remove(id, hits);
      }
      return;
    }
    hits.add(new PendingDamage(e.dispatchId(), deferredAmount, Math.max(1, pulses)));
  }

  /** Turns the held-back part of a hit that went through into debt; a cancelled hit owes nothing. */
  public void finish(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef player)) {
      return;
    }
    UUID id = player.uuid();
    Deque<PendingDamage> hits = pending.get(id);
    if (hits == null) {
      return;
    }
    PendingDamage accepted = null;
    for (PendingDamage hit : hits) {
      if (hit.dispatchId() == e.dispatchId()) {
        hits.remove(hit);
        accepted = hit;
        break;
      }
    }
    if (hits.isEmpty()) {
      pending.remove(id, hits);
    }
    if (accepted == null || e.isCancelled()) {
      return;
    }
    Deque<DeferredDamage> queue = deferred.computeIfAbsent(id, (UUID unused) -> new ConcurrentLinkedDeque<>());
    queue.add(new DeferredDamage(accepted.amount() / accepted.pulses(), accepted.pulses()));
    persist(player, queue);
    hooks.deferred(player, accepted.amount());
  }

  /** Total still owed by the player. */
  public double owed(UUID id) {
    Deque<DeferredDamage> queue = deferred.get(id);
    double owed = 0D;
    if (queue != null) {
      for (DeferredDamage chunk : queue) {
        owed += chunk.perPulse() * chunk.pulsesRemaining();
      }
    }
    return owed;
  }

  /** Schedules one pulse for every player who owes. */
  public void tick() {
    if (!accepting.get() || deferred.isEmpty()) {
      return;
    }
    for (Map.Entry<UUID, Deque<DeferredDamage>> entry : deferred.entrySet()) {
      UUID id = entry.getKey();
      PlayerRef p = AdaptApi.platform().host().player(id);
      if (p == null) {
        deferred.remove(id, entry.getValue());
        settling.remove(id);
        continue;
      }
      Deque<DeferredDamage> queue = entry.getValue();
      if (!settling.add(id)) {
        continue;
      }
      if (!AdaptApi.scheduler().runEntity(p, () -> settle(p, queue))) {
        settling.remove(id);
      }
    }
  }

  void settle(PlayerRef player, Deque<DeferredDamage> queue) {
    UUID id = player.uuid();
    try {
      if (!accepting.get() || deferred.get(id) != queue || !player.isOnline()) {
        return;
      }
      if (player.isDead() || player.health() <= 0D) {
        deferred.remove(id, queue);
        player.data().remove(stamp);
        return;
      }
      double damage = pendingPulseAmount(queue);
      if (damage <= 0D) {
        deferred.remove(id, queue);
        player.data().remove(stamp);
        return;
      }
      if (player.noDamageTicks() > player.maxNoDamageTicks() / 2D) {
        return;
      }
      try {
        whilePaying(() -> hooks.pay(player, damage));
      } catch (Throwable error) {
        AdaptApi.log().error("Borrowed Time could not settle deferred damage for " + id + ".", null);
        AdaptApi.log().error("ChronosDebt failed", error);
        return;
      }
      if (deferred.get(id) != queue) {
        return;
      }
      commitDebtPulse(queue);
      boolean cleared = queue.isEmpty();
      if (cleared) {
        deferred.remove(id, queue);
      }
      persist(player, queue);
      hooks.paid(player, damage, cleared);
    } finally {
      settling.remove(id);
    }
  }

  /** Keeps the debt on the player and drops the runtime copy. */
  public void quit(PlayerRef player) {
    UUID id = player.uuid();
    pending.remove(id);
    Deque<DeferredDamage> queue = deferred.remove(id);
    if (queue != null && !queue.isEmpty()) {
      persist(player, queue);
    }
    settling.remove(id);
  }

  /** Clears everything the player owes. */
  public void cancel(PlayerRef player) {
    UUID id = player.uuid();
    deferred.remove(id);
    pending.remove(id);
    settling.remove(id);
    player.data().remove(stamp);
  }

  /** Loads the debt stored on the player. */
  public void restore(PlayerRef player) {
    UUID id = player.uuid();
    if (!player.isOnline() || deferred.containsKey(id)) {
      return;
    }
    String encoded = player.data().get(stamp);
    if (encoded == null) {
      return;
    }
    try {
      Deque<DeferredDamage> queue = decodeDeferredDamage(encoded);
      if (queue.isEmpty()) {
        player.data().remove(stamp);
        return;
      }
      deferred.putIfAbsent(id, queue);
    } catch (RuntimeException error) {
      AdaptApi.log().error("Borrowed Time could not restore deferred damage for " + id + ".", null);
      AdaptApi.log().error("ChronosDebt failed", error);
      player.data().remove(stamp);
    }
  }

  void whilePaying(Runnable damage) {
    Boolean previous = PAYING.get();
    PAYING.set(true);
    try {
      damage.run();
    } finally {
      if (previous == null) {
        PAYING.remove();
      } else {
        PAYING.set(previous);
      }
    }
  }

  private void persist(PlayerRef player, Deque<DeferredDamage> queue) {
    try {
      DataHolder data = player.data();
      if (queue.isEmpty()) {
        data.remove(stamp);
      } else {
        data.set(stamp, encodeDeferredDamage(queue));
      }
    } catch (Throwable error) {
      AdaptApi.log().error("Borrowed Time could not persist deferred damage for " + player.uuid() + ".", null);
      AdaptApi.log().error("ChronosDebt failed", error);
    }
  }

  static double pendingPulseAmount(Deque<DeferredDamage> queue) {
    double amount = 0D;
    for (DeferredDamage chunk : queue) {
      amount += chunk.perPulse();
    }
    return amount;
  }

  static void commitDebtPulse(Deque<DeferredDamage> queue) {
    Iterator<DeferredDamage> iterator = queue.iterator();
    while (iterator.hasNext()) {
      DeferredDamage chunk = iterator.next();
      if (chunk.consumePulse() <= 0) {
        iterator.remove();
      }
    }
  }

  static String encodeDeferredDamage(Deque<DeferredDamage> queue) {
    StringBuilder encoded = new StringBuilder();
    for (DeferredDamage chunk : queue) {
      if (encoded.length() > 0) {
        encoded.append(';');
      }
      encoded.append(chunk.perPulse()).append(',').append(chunk.pulsesRemaining());
    }
    return encoded.toString();
  }

  static Deque<DeferredDamage> decodeDeferredDamage(String encoded) {
    Deque<DeferredDamage> queue = new ConcurrentLinkedDeque<>();
    if (encoded == null || encoded.isBlank()) {
      return queue;
    }
    for (String value : encoded.split(";")) {
      String[] parts = value.split(",", 2);
      if (parts.length != 2) {
        throw new IllegalArgumentException("Invalid deferred damage chunk");
      }
      double perPulse = Double.parseDouble(parts[0]);
      int pulsesRemaining = Integer.parseInt(parts[1]);
      if (!Double.isFinite(perPulse) || perPulse <= 0D || pulsesRemaining <= 0) {
        throw new IllegalArgumentException("Invalid deferred damage values");
      }
      queue.add(new DeferredDamage(perPulse, pulsesRemaining));
    }
    return queue;
  }

  /** The held-back part of a hit still in flight. */
  public record PendingDamage(long dispatchId, double amount, int pulses) {
  }

  /** One deferred hit: a pulse size and the pulses left. */
  public static final class DeferredDamage {
    private final double perPulse;
    private int pulsesRemaining;

    DeferredDamage(double perPulse, int pulsesRemaining) {
      this.perPulse = perPulse;
      this.pulsesRemaining = pulsesRemaining;
    }

    double perPulse() {
      return perPulse;
    }

    int pulsesRemaining() {
      return pulsesRemaining;
    }

    int consumePulse() {
      pulsesRemaining--;
      return pulsesRemaining;
    }
  }
}
