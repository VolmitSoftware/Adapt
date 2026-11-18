package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.LidRef;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

final class ContainerLids {
  static final int CLOSE_TICKS = 15;
  private static final long TICK_MILLIS = 50L;

  private final LidSpace space;
  private final LongSupplier clock;
  private final Map<LidKey, Pending> pending = new ConcurrentHashMap<>();

  ContainerLids(LidSpace space, LongSupplier clock) {
    this.space = Objects.requireNonNull(space);
    this.clock = Objects.requireNonNull(clock);
  }

  static ContainerLids create() {
    return new ContainerLids(LiveLidSpace.INSTANCE, System::currentTimeMillis);
  }

  void visit(BlockRef container, int holdTicks) {
    long closeAt = clock.getAsLong() + Math.max(1, holdTicks) * TICK_MILLIS;
    for (BlockRef half : space.halves(container)) {
      visitHalf(half, closeAt);
    }
  }

  void shutdown() {
    for (Pending entry : pending.values()) {
      space.lid(entry.half).ifPresent(LidRef::close);
    }
    pending.clear();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ChunkEvent e) {
    if (e.phase() != ChunkEvent.Phase.UNLOAD) {
      return;
    }
    UUID world = e.world().uid();
    int chunkX = e.chunk().x();
    int chunkZ = e.chunk().z();
    pending.keySet().removeIf(key -> key.in(world, chunkX, chunkZ));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    pending.remove(LidKey.of(e.block()));
  }

  private void visitHalf(BlockRef half, long closeAt) {
    LidKey key = LidKey.of(half);
    Pending held = pending.get(key);
    if (held != null) {
      held.extend(closeAt);
      return;
    }
    LidRef lid = space.lid(half).orElse(null);
    if (lid == null || lid.viewed() || lid.isOpen()) {
      return;
    }
    lid.open();
    Pending created = new Pending(half, closeAt);
    pending.put(key, created);
    arm(key, created, ticksUntil(closeAt));
  }

  private void arm(LidKey key, Pending entry, int delayTicks) {
    if (!space.runAt(entry.half, () -> settle(key, entry), delayTicks)) {
      pending.remove(key, entry);
      space.lid(entry.half).ifPresent(LidRef::close);
    }
  }

  private void settle(LidKey key, Pending entry) {
    if (pending.get(key) != entry) {
      return;
    }
    long closeAt = entry.closeAt();
    if (closeAt > clock.getAsLong()) {
      arm(key, entry, ticksUntil(closeAt));
      return;
    }
    LidRef lid = space.lid(entry.half).orElse(null);
    if (lid == null) {
      pending.remove(key, entry);
      return;
    }
    if (lid.viewed()) {
      arm(key, entry, CLOSE_TICKS);
      return;
    }
    pending.remove(key, entry);
    lid.close();
  }

  private int ticksUntil(long closeAt) {
    long remaining = closeAt - clock.getAsLong();
    return (int) Math.max(1L, (remaining + TICK_MILLIS - 1L) / TICK_MILLIS);
  }

  private record LidKey(UUID world, int x, int y, int z) {
    private static LidKey of(BlockRef block) {
      return new LidKey(block.world().uid(), block.x(), block.y(), block.z());
    }

    private boolean in(UUID otherWorld, int chunkX, int chunkZ) {
      return world.equals(otherWorld) && (x >> 4) == chunkX && (z >> 4) == chunkZ;
    }
  }

  private static final class Pending {
    private final BlockRef half;
    private volatile long closeAt;

    private Pending(BlockRef half, long closeAt) {
      this.half = half;
      this.closeAt = closeAt;
    }

    private long closeAt() {
      return closeAt;
    }

    private void extend(long next) {
      if (next > closeAt) {
        closeAt = next;
      }
    }
  }
}
