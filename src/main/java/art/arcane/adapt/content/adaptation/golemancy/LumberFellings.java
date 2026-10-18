package art.arcane.adapt.content.adaptation.golemancy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class LumberFellings {
  static final long STALE_MILLIS = 60_000L;

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

  void sweep(long nowMillis) {
    fellings.values().removeIf(felling -> nowMillis - felling.touchedAt() >= STALE_MILLIS);
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
