package art.arcane.adapt.api.golem;

import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

final class ContainerLids implements Listener {
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
    return new ContainerLids(BukkitLidSpace.INSTANCE, System::currentTimeMillis);
  }

  void visit(Block container, int holdTicks) {
    long closeAt = clock.getAsLong() + Math.max(1, holdTicks) * TICK_MILLIS;
    for (Block half : space.halves(container)) {
      visitHalf(half, closeAt);
    }
  }

  void shutdown() {
    for (Pending entry : pending.values()) {
      space.lid(entry.half).ifPresent(LidSpace.Lid::close);
    }
    pending.clear();
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ChunkUnloadEvent e) {
    Chunk chunk = e.getChunk();
    UUID world = chunk.getWorld().getUID();
    int chunkX = chunk.getX();
    int chunkZ = chunk.getZ();
    pending.keySet().removeIf(key -> key.in(world, chunkX, chunkZ));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    pending.remove(LidKey.of(e.getBlock()));
  }

  private void visitHalf(Block half, long closeAt) {
    LidKey key = LidKey.of(half);
    Pending held = pending.get(key);
    if (held != null) {
      held.extend(closeAt);
      return;
    }
    LidSpace.Lid lid = space.lid(half).orElse(null);
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
      space.lid(entry.half).ifPresent(LidSpace.Lid::close);
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
    LidSpace.Lid lid = space.lid(entry.half).orElse(null);
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
    private static LidKey of(Block block) {
      return new LidKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private boolean in(UUID otherWorld, int chunkX, int chunkZ) {
      return world.equals(otherWorld) && (x >> 4) == chunkX && (z >> 4) == chunkZ;
    }
  }

  private static final class Pending {
    private final Block half;
    private volatile long closeAt;

    private Pending(Block half, long closeAt) {
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
