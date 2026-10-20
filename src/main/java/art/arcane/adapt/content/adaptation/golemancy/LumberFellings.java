package art.arcane.adapt.content.adaptation.golemancy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

final class LumberFellings {
  static final long STALE_MILLIS = 60_000L;
  static final long REPLANT_RETRY_MILLIS = 5_000L;
  static final long REPLANT_LIFETIME_MILLIS = 300_000L;

  private final Map<Key, Felling> fellings = new ConcurrentHashMap<>();

  Optional<Felling> get(UUID world, int x, int y, int z) {
    return Optional.ofNullable(fellings.get(new Key(world, x, y, z)));
  }

  Felling start(UUID world, LumberTrees.Tree tree, long nowMillis) {
    LumberTrees.Part base = tree.base();
    Felling felling = new Felling(tree, nowMillis);
    fellings.put(new Key(world, base.x(), base.y(), base.z()), felling);
    return felling;
  }

  void finish(UUID world, Felling felling) {
    LumberTrees.Part base = felling.tree().base();
    fellings.remove(new Key(world, base.x(), base.y(), base.z()), felling);
  }

  void keep(UUID world, Predicate<LumberTrees.Part> base, long nowMillis) {
    for (Map.Entry<Key, Felling> entry : fellings.entrySet()) {
      if (entry.getKey().world().equals(world) && base.test(entry.getValue().tree().base())) {
        entry.getValue().touch(nowMillis);
      }
    }
  }

  void sweep(long nowMillis) {
    fellings.values().removeIf(felling -> felling.expired(nowMillis) || nowMillis - felling.touchedAt() >= STALE_MILLIS);
  }

  List<Felling> replants(UUID world, Predicate<LumberTrees.Part> base, long nowMillis) {
    List<Felling> ready = new ArrayList<>();
    for (Map.Entry<Key, Felling> entry : fellings.entrySet()) {
      Felling felling = entry.getValue();
      if (entry.getKey().world().equals(world) && felling.replantReady(nowMillis) && base.test(felling.tree().base())) {
        ready.add(felling);
      }
    }
    return ready;
  }

  boolean pending() {
    return !fellings.isEmpty();
  }

  void forget() {
    fellings.clear();
  }

  static final class Felling {
    private final LumberTrees.Tree tree;
    private volatile int cursor;
    private volatile long touchedAt;
    private volatile long settleUntil;
    private volatile List<LumberTrees.Part> leaves;
    private volatile int swept;
    private volatile long replantUntil;
    private volatile long replantAt;

    private Felling(LumberTrees.Tree tree, long nowMillis) {
      this.tree = tree;
      this.touchedAt = nowMillis;
    }

    LumberTrees.Tree tree() {
      return tree;
    }

    int cursor() {
      return cursor;
    }

    void advance(int next) {
      cursor = Math.max(cursor, Math.min(next, tree.parts().size()));
    }

    boolean felled() {
      return cursor >= tree.parts().size();
    }

    boolean settling() {
      return leaves != null;
    }

    void settle(long untilMillis, List<LumberTrees.Part> sweep) {
      settleUntil = untilMillis;
      leaves = List.copyOf(sweep);
    }

    long settleUntil() {
      return settleUntil;
    }

    List<LumberTrees.Part> leaves() {
      List<LumberTrees.Part> current = leaves;
      return current == null ? List.of() : current;
    }

    int swept() {
      return swept;
    }

    void sweptTo(int next) {
      swept = Math.max(swept, Math.min(next, leaves().size()));
    }

    boolean sweptAll() {
      return swept >= leaves().size();
    }

    boolean replanting() {
      return replantUntil > 0L;
    }

    void retryReplant(long nowMillis) {
      if (!replanting()) {
        replantUntil = nowMillis + REPLANT_LIFETIME_MILLIS;
      }
      replantAt = nowMillis + REPLANT_RETRY_MILLIS;
    }

    boolean replantReady(long nowMillis) {
      return replanting() && nowMillis >= replantAt && !expired(nowMillis);
    }

    boolean expired(long nowMillis) {
      return replanting() && nowMillis >= replantUntil;
    }

    void touch(long nowMillis) {
      touchedAt = nowMillis;
    }

    long touchedAt() {
      return touchedAt;
    }
  }

  private record Key(UUID world, int x, int y, int z) {
  }
}
