package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.Adapt;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.chunk.ChunkPosition;
import art.arcane.volmlib.nativelib.chunk.GeneratedChunkStructures;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.generator.structure.StructureType;
import org.bukkit.util.BoundingBox;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class GeneratedStructureSearch {
  static final int REQUESTS_PER_TICK = 8;
  static final int MAX_IN_FLIGHT = 16;

  private final Queue<Sweep> pending = new ConcurrentLinkedQueue<>();
  private final AtomicInteger inFlight = new AtomicInteger();
  private volatile boolean closed;
  private GeneratedChunkStructures metadata;

  void initialize() {
    try {
      metadata = NativeAdapters.require(GeneratedChunkStructures.class);
    } catch (RuntimeException | LinkageError failure) {
      close();
      Adapt.error("Sixth Sense is unavailable: generated structure metadata is unsupported on this server", failure);
    }
  }

  Sweep begin(Request request) {
    Sweep sweep = new Sweep(request);
    if (closed) {
      sweep.cancel();
    } else {
      pending.add(sweep);
    }
    return sweep;
  }

  boolean hasWork() {
    return !pending.isEmpty() || inFlight.get() > 0;
  }

  void tick() {
    for (int dispatched = 0; dispatched < REQUESTS_PER_TICK && inFlight.get() < MAX_IN_FLIGHT; dispatched++) {
      Sweep sweep = pending.poll();
      if (sweep == null) {
        return;
      }
      if (sweep.cancelled.get()) {
        continue;
      }
      Position position = sweep.next();
      if (position == null) {
        sweep.exhausted = true;
        if (sweep.inFlight.get() > 0) {
          pending.add(sweep);
        }
        continue;
      }
      pending.add(sweep);
      inFlight.incrementAndGet();
      sweep.inFlight.incrementAndGet();
      Location location = new Location(sweep.request.world(), position.x() * 16D, 0D, position.z() * 16D);
      if (!J.runAt(location, () -> loadOwned(sweep, position))) {
        complete(sweep);
      }
    }
  }

  void close() {
    closed = true;
    for (Sweep sweep : pending) {
      sweep.cancel();
    }
    pending.clear();
  }

  private void loadOwned(Sweep sweep, Position position) {
    if (closed || sweep.cancelled.get()) {
      complete(sweep);
      return;
    }
    try {
      sweep.request.world().getChunkAtAsync(position.x(), position.z(), false, false)
          .whenComplete((chunk, failure) -> loaded(sweep, chunk, failure));
    } catch (RuntimeException failure) {
      failed(sweep, failure);
      complete(sweep);
    }
  }

  private void loaded(Sweep sweep, Chunk chunk, Throwable failure) {
    if (failure != null) {
      failed(sweep, failure);
      complete(sweep);
      return;
    }
    if (closed || sweep.cancelled.get() || chunk == null) {
      complete(sweep);
      return;
    }
    inspectOwned(sweep, chunk);
  }

  private void inspectOwned(Sweep sweep, Chunk chunk) {
    try {
      if (closed || sweep.cancelled.get()) {
        return;
      }
      if (!Bukkit.isOwnedByCurrentRegion(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
        throw new IllegalStateException("Generated chunk completion did not run on its owning region");
      }
      GeneratedChunkStructures.Snapshot snapshot = metadata.read(chunk);
      if (sweep.cursor.contains(chunk.getX(), chunk.getZ())) {
        sweep.addReferences(snapshot.references());
      }
      for (GeneratedChunkStructures.Entry generated : snapshot.starts()) {
        StructureType type = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE_TYPE).get(generated.typeKey());
        if (!sweep.request.types().contains(type)) {
          continue;
        }
        GeneratedChunkStructures.Bounds box = generated.bounds();
        BoundingBox bounds = new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
        double x = bounds.getCenterX();
        double z = bounds.getCenterZ();
        if (sweep.withinRange(x, z)) {
          sweep.request.found().accept(new Candidate(sweep.request.id(), sweep.request.worldId(), x, z,
              DiscoverySixthSense.structureName(generated.structureKey(), type),
              DiscoverySixthSense.structureSymbol(type), bounds.clone()));
        }
      }
    } catch (RuntimeException failure) {
      failed(sweep, failure);
    } finally {
      complete(sweep);
    }
  }

  private void failed(Sweep sweep, Throwable failure) {
    sweep.cancel();
    if (sweep.failureLogged.compareAndSet(false, true)) {
      Adapt.error("Sixth Sense generated-structure search failed in world " + sweep.request.worldId(), failure);
    }
  }

  private void complete(Sweep sweep) {
    sweep.inFlight.decrementAndGet();
    inFlight.decrementAndGet();
  }

  record Request(UUID id, World world, UUID worldId, double x, double z, double range, List<StructureType> types,
                 Consumer<Candidate> found) {
  }

  record Candidate(UUID sweepId, UUID worldId, double x, double z, String name, String symbol, BoundingBox bounds) {
  }

  static final class Sweep {
    private final Request request;
    private final Cursor cursor;
    private final Queue<Position> references = new ConcurrentLinkedQueue<>();
    private final Set<Long> referenced = new HashSet<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean failureLogged = new AtomicBoolean();
    private final AtomicInteger inFlight = new AtomicInteger();
    private volatile boolean exhausted;

    private Sweep(Request request) {
      this.request = request;
      cursor = new Cursor(request.x(), request.z(), request.range());
    }

    private Position next() {
      Position reference = references.poll();
      return reference == null ? cursor.next() : reference;
    }

    private synchronized void addReferences(List<ChunkPosition> positions) {
      for (ChunkPosition position : positions) {
        long packed = ((long) position.z() << 32) | (position.x() & 0xffffffffL);
        if (!cursor.contains(position.x(), position.z()) && referenced.add(packed)) {
          references.add(new Position(position.x(), position.z()));
          exhausted = false;
        }
      }
    }

    boolean running() {
      return !cancelled.get() && (!exhausted || inFlight.get() > 0);
    }

    boolean accepts(UUID worldId) {
      return !cancelled.get() && request.worldId().equals(worldId);
    }

    boolean owns(Candidate candidate) {
      return accepts(candidate.worldId()) && request.id().equals(candidate.sweepId());
    }

    void cancel() {
      cancelled.set(true);
    }

    boolean withinRange(double x, double z) {
      double dx = x - request.x();
      double dz = z - request.z();
      return dx * dx + dz * dz <= request.range() * request.range();
    }
  }

  record Position(int x, int z) {
  }

  static final class Cursor {
    private final int centerX;
    private final int centerZ;
    private final int radius;
    private int x;
    private int z;
    private int dx;
    private int dz = -1;
    private int visited;

    Cursor(double x, double z, double range) {
      centerX = (int) Math.floor(x) >> 4;
      centerZ = (int) Math.floor(z) >> 4;
      radius = Math.max(1, (int) Math.ceil(range / 16D));
    }

    boolean contains(int chunkX, int chunkZ) {
      return Math.abs(chunkX - centerX) <= radius && Math.abs(chunkZ - centerZ) <= radius;
    }

    Position next() {
      int width = radius * 2 + 1;
      if (visited++ >= width * width) {
        return null;
      }
      Position position = new Position(centerX + x, centerZ + z);
      if (x == z || x < 0 && x == -z || x > 0 && x == 1 - z) {
        int previousDx = dx;
        dx = -dz;
        dz = previousDx;
      }
      x += dx;
      z += dz;
      return position;
    }
  }
}
