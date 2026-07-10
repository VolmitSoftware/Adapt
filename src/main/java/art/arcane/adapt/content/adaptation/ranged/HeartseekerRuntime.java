package art.arcane.adapt.content.adaptation.ranged;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

final class HeartseekerCoordinator {
  private final int activeLimit;
  private final int ownerLimit;
  private final Map<UUID, Slot> slots = new HashMap<>();
  private final Map<UUID, OwnerSlots> owners = new HashMap<>();
  private final ArrayDeque<UUID> readyOwners = new ArrayDeque<>();
  private final Set<UUID> readyOwnerIds = new HashSet<>();
  private long nextGeneration;

  HeartseekerCoordinator(int activeLimit, int ownerLimit) {
    if (activeLimit <= 0 || ownerLimit <= 0) {
      throw new IllegalArgumentException("Heartseeker coordinator limits must be positive");
    }
    this.activeLimit = activeLimit;
    this.ownerLimit = ownerLimit;
  }

  synchronized long admit(UUID ownerId, UUID arrowId) {
    Objects.requireNonNull(ownerId);
    Objects.requireNonNull(arrowId);
    if (slots.size() >= activeLimit || slots.containsKey(arrowId)) {
      return -1L;
    }

    OwnerSlots owner = owners.computeIfAbsent(ownerId, ignored -> new OwnerSlots());
    if (owner.all.size() >= ownerLimit) {
      if (owner.all.isEmpty()) {
        owners.remove(ownerId, owner);
      }
      return -1L;
    }

    long generation = ++nextGeneration;
    Slot slot = new Slot(ownerId, generation);
    slots.put(arrowId, slot);
    owner.all.add(arrowId);
    owner.queued.addLast(arrowId);
    enqueueOwner(ownerId);
    return generation;
  }

  synchronized List<Dispatch> takeDispatches(int limit) {
    if (limit <= 0 || readyOwners.isEmpty()) {
      return List.of();
    }

    List<Dispatch> dispatches = new ArrayList<>(Math.min(limit, readyOwners.size()));
    while (dispatches.size() < limit && !readyOwners.isEmpty()) {
      UUID ownerId = readyOwners.removeFirst();
      readyOwnerIds.remove(ownerId);
      OwnerSlots owner = owners.get(ownerId);
      if (owner == null) {
        continue;
      }

      SlotSelection selection = pollQueued(owner);
      if (selection != null) {
        selection.slot().state = SlotState.IN_FLIGHT;
        dispatches.add(new Dispatch(selection.arrowId(), selection.slot().generation));
      }
      if (!owner.queued.isEmpty()) {
        enqueueOwner(ownerId);
      }
    }
    return dispatches;
  }

  synchronized boolean complete(UUID arrowId, long generation) {
    Slot slot = slots.get(arrowId);
    if (slot == null || slot.generation != generation || slot.state != SlotState.IN_FLIGHT) {
      return false;
    }

    OwnerSlots owner = owners.get(slot.ownerId);
    if (owner == null) {
      slots.remove(arrowId, slot);
      return false;
    }
    slot.state = SlotState.QUEUED;
    owner.queued.addLast(arrowId);
    enqueueOwner(slot.ownerId);
    return true;
  }

  synchronized boolean suspend(UUID arrowId, long generation) {
    Slot slot = slots.get(arrowId);
    if (slot == null || slot.generation != generation || slot.state == SlotState.SUSPENDED) {
      return false;
    }
    slot.state = SlotState.SUSPENDED;
    OwnerSlots owner = owners.get(slot.ownerId);
    if (owner != null) {
      owner.queued.remove(arrowId);
    }
    return true;
  }

  synchronized boolean resume(UUID arrowId, long generation) {
    Slot slot = slots.get(arrowId);
    if (slot == null || slot.generation != generation || slot.state != SlotState.SUSPENDED) {
      return false;
    }

    OwnerSlots owner = owners.get(slot.ownerId);
    if (owner == null) {
      return false;
    }
    slot.state = SlotState.QUEUED;
    owner.queued.addLast(arrowId);
    enqueueOwner(slot.ownerId);
    return true;
  }

  synchronized boolean transfer(UUID sourceArrowId, long generation, UUID targetArrowId) {
    Objects.requireNonNull(sourceArrowId);
    Objects.requireNonNull(targetArrowId);
    Slot slot = slots.get(sourceArrowId);
    if (slot == null || slot.generation != generation || slots.containsKey(targetArrowId)) {
      return false;
    }

    OwnerSlots owner = owners.get(slot.ownerId);
    if (owner == null || !owner.all.remove(sourceArrowId)) {
      return false;
    }
    owner.queued.remove(sourceArrowId);
    slots.remove(sourceArrowId, slot);
    slots.put(targetArrowId, slot);
    owner.all.add(targetArrowId);
    if (slot.state == SlotState.QUEUED) {
      owner.queued.addLast(targetArrowId);
      enqueueOwner(slot.ownerId);
    }
    return true;
  }

  synchronized boolean remove(UUID arrowId, long generation) {
    Slot slot = slots.get(arrowId);
    if (slot == null || slot.generation != generation) {
      return false;
    }
    removeSlot(arrowId, slot);
    return true;
  }

  synchronized List<UUID> removeOwner(UUID ownerId) {
    OwnerSlots owner = owners.remove(ownerId);
    if (owner == null) {
      return List.of();
    }

    readyOwnerIds.remove(ownerId);
    readyOwners.remove(ownerId);
    List<UUID> removed = new ArrayList<>(owner.all);
    for (UUID arrowId : removed) {
      slots.remove(arrowId);
    }
    return removed;
  }

  synchronized List<UUID> clear() {
    List<UUID> removed = new ArrayList<>(slots.keySet());
    slots.clear();
    owners.clear();
    readyOwners.clear();
    readyOwnerIds.clear();
    return removed;
  }

  synchronized boolean isCurrent(UUID arrowId, long generation) {
    Slot slot = slots.get(arrowId);
    return slot != null && slot.generation == generation;
  }

  synchronized int activeCount() {
    return slots.size();
  }

  synchronized int ownerActiveCount(UUID ownerId) {
    OwnerSlots owner = owners.get(ownerId);
    return owner == null ? 0 : owner.all.size();
  }

  private SlotSelection pollQueued(OwnerSlots owner) {
    while (!owner.queued.isEmpty()) {
      UUID arrowId = owner.queued.removeFirst();
      Slot slot = slots.get(arrowId);
      if (slot != null && slot.state == SlotState.QUEUED) {
        return new SlotSelection(arrowId, slot);
      }
    }
    return null;
  }

  private void enqueueOwner(UUID ownerId) {
    if (readyOwnerIds.add(ownerId)) {
      readyOwners.addLast(ownerId);
    }
  }

  private void removeSlot(UUID arrowId, Slot slot) {
    slots.remove(arrowId, slot);
    OwnerSlots owner = owners.get(slot.ownerId);
    if (owner == null) {
      return;
    }
    owner.all.remove(arrowId);
    owner.queued.remove(arrowId);
    if (owner.all.isEmpty()) {
      owners.remove(slot.ownerId, owner);
      readyOwnerIds.remove(slot.ownerId);
      readyOwners.remove(slot.ownerId);
    }
  }

  record Dispatch(UUID arrowId, long generation) {
  }

  private enum SlotState {
    QUEUED,
    IN_FLIGHT,
    SUSPENDED
  }

  private static final class Slot {
    private final UUID ownerId;
    private final long generation;
    private SlotState state = SlotState.QUEUED;

    private Slot(UUID ownerId, long generation) {
      this.ownerId = ownerId;
      this.generation = generation;
    }
  }

  private static final class OwnerSlots {
    private final Set<UUID> all = new HashSet<>();
    private final ArrayDeque<UUID> queued = new ArrayDeque<>();
  }

  private record SlotSelection(UUID arrowId, Slot slot) {
  }
}

final class HeartseekerFrameBudget {
  private final int dispatchLimit;
  private final int rayTraceLimit;
  private final int targetSnapshotLimit;
  private final int candidateScanLimit;
  private final int candidateHandoffLimit;
  private final int trailPointLimit;
  private final long windowNanos;
  private final LongSupplier clock;
  private long window = Long.MIN_VALUE;
  private int dispatches;
  private int rayTraces;
  private int targetSnapshots;
  private int candidateScans;
  private int candidateHandoffs;
  private int trailPoints;

  HeartseekerFrameBudget(int dispatchLimit, int rayTraceLimit, int targetSnapshotLimit,
                         int candidateScanLimit, int candidateHandoffLimit, int trailPointLimit,
                         long windowNanos) {
    this(dispatchLimit, rayTraceLimit, targetSnapshotLimit, candidateScanLimit,
        candidateHandoffLimit, trailPointLimit, windowNanos, System::nanoTime);
  }

  HeartseekerFrameBudget(int dispatchLimit, int rayTraceLimit, int targetSnapshotLimit,
                         int candidateScanLimit, int candidateHandoffLimit, int trailPointLimit,
                         long windowNanos, LongSupplier clock) {
    if (dispatchLimit <= 0 || rayTraceLimit <= 0 || targetSnapshotLimit <= 0
        || candidateScanLimit <= 0 || candidateHandoffLimit <= 0 || trailPointLimit <= 0
        || windowNanos <= 0) {
      throw new IllegalArgumentException("Heartseeker work limits must be positive");
    }
    this.dispatchLimit = dispatchLimit;
    this.rayTraceLimit = rayTraceLimit;
    this.targetSnapshotLimit = targetSnapshotLimit;
    this.candidateScanLimit = candidateScanLimit;
    this.candidateHandoffLimit = candidateHandoffLimit;
    this.trailPointLimit = trailPointLimit;
    this.windowNanos = windowNanos;
    this.clock = Objects.requireNonNull(clock);
  }

  synchronized boolean tryDispatch() {
    rotateWindow();
    if (dispatches >= dispatchLimit) {
      return false;
    }
    dispatches++;
    return true;
  }

  synchronized boolean tryRayTrace() {
    rotateWindow();
    if (rayTraces >= rayTraceLimit) {
      return false;
    }
    rayTraces++;
    return true;
  }

  synchronized boolean tryTargetSnapshot() {
    rotateWindow();
    if (targetSnapshots >= targetSnapshotLimit) {
      return false;
    }
    targetSnapshots++;
    return true;
  }

  synchronized boolean tryCandidateScan() {
    rotateWindow();
    if (candidateScans >= candidateScanLimit) {
      return false;
    }
    candidateScans++;
    return true;
  }

  synchronized boolean tryCandidateHandoff() {
    rotateWindow();
    if (candidateHandoffs >= candidateHandoffLimit) {
      return false;
    }
    candidateHandoffs++;
    return true;
  }

  synchronized boolean tryTrailPoint() {
    rotateWindow();
    if (trailPoints >= trailPointLimit) {
      return false;
    }
    trailPoints++;
    return true;
  }

  synchronized int remainingDispatches() {
    rotateWindow();
    return Math.max(0, dispatchLimit - dispatches);
  }

  private void rotateWindow() {
    long currentWindow = Math.floorDiv(clock.getAsLong(), windowNanos);
    if (currentWindow == window) {
      return;
    }
    window = currentWindow;
    dispatches = 0;
    rayTraces = 0;
    targetSnapshots = 0;
    candidateScans = 0;
    candidateHandoffs = 0;
    trailPoints = 0;
  }
}

final class HeartseekerPendingBudget {
  private final int limit;
  private int active;

  HeartseekerPendingBudget(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("Heartseeker pending limit must be positive");
    }
    this.limit = limit;
  }

  synchronized boolean tryReserve() {
    if (active >= limit) {
      return false;
    }
    active++;
    return true;
  }

  synchronized void release() {
    if (active > 0) {
      active--;
    }
  }

  synchronized int activeCount() {
    return active;
  }
}

final class HeartseekerChainRules {
  private HeartseekerChainRules() {
  }

  static int resolvePasses(int launchedPierceLevel, int ricochetLevel, int maximumPasses) {
    return resolveBudget(launchedPierceLevel, ricochetLevel, maximumPasses).total();
  }

  static HeartseekerPassBudget resolveBudget(int launchedPierceLevel, int ricochetLevel,
                                             int maximumPasses) {
    int maximum = Math.max(0, maximumPasses);
    int piercing = Math.min(maximum, Math.max(0, launchedPierceLevel));
    int ricochet = Math.min(maximum - piercing, Math.max(0, ricochetLevel));
    return new HeartseekerPassBudget(piercing, ricochet);
  }
}

record HeartseekerPassBudget(int piercing, int ricochet) {
  HeartseekerPassBudget {
    if (piercing < 0 || ricochet < 0) {
      throw new IllegalArgumentException("Heartseeker pass counts cannot be negative");
    }
  }

  int total() {
    return piercing + ricochet;
  }

  HeartseekerPassBudget afterEntityPass() {
    if (piercing > 0) {
      return new HeartseekerPassBudget(piercing - 1, ricochet);
    }
    return ricochet > 0 ? new HeartseekerPassBudget(0, ricochet - 1) : this;
  }

  HeartseekerPassBudget afterBlockRicochet() {
    return ricochet > 0 ? new HeartseekerPassBudget(piercing, ricochet - 1) : this;
  }
}

final class HeartseekerChunkTraversal {
  private static final double CORNER_EPSILON = 0.000000001D;

  private HeartseekerChunkTraversal() {
  }

  static boolean allChunks(double startX, double startZ, double endX, double endZ,
                           ChunkAccess access) {
    int chunkX = chunk(startX);
    int chunkZ = chunk(startZ);
    int endChunkX = chunk(endX);
    int endChunkZ = chunk(endZ);
    if (!access.canAccess(chunkX, chunkZ)) {
      return false;
    }

    double deltaX = endX - startX;
    double deltaZ = endZ - startZ;
    int stepX = Integer.compare(endChunkX, chunkX);
    int stepZ = Integer.compare(endChunkZ, chunkZ);
    double nextX = stepX > 0 ? (chunkX + 1D) * 16D : chunkX * 16D;
    double nextZ = stepZ > 0 ? (chunkZ + 1D) * 16D : chunkZ * 16D;
    double maxX = stepX == 0 ? Double.POSITIVE_INFINITY : (nextX - startX) / deltaX;
    double maxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : (nextZ - startZ) / deltaZ;
    double stepDistanceX = stepX == 0 ? Double.POSITIVE_INFINITY : 16D / Math.abs(deltaX);
    double stepDistanceZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 16D / Math.abs(deltaZ);

    while (chunkX != endChunkX || chunkZ != endChunkZ) {
      if (Math.abs(maxX - maxZ) <= CORNER_EPSILON) {
        if (stepX != 0 && !access.canAccess(chunkX + stepX, chunkZ)) {
          return false;
        }
        if (stepZ != 0 && !access.canAccess(chunkX, chunkZ + stepZ)) {
          return false;
        }
        chunkX += stepX;
        chunkZ += stepZ;
        maxX += stepDistanceX;
        maxZ += stepDistanceZ;
      } else if (maxX < maxZ) {
        chunkX += stepX;
        maxX += stepDistanceX;
      } else {
        chunkZ += stepZ;
        maxZ += stepDistanceZ;
      }
      if (!access.canAccess(chunkX, chunkZ)) {
        return false;
      }
    }
    return true;
  }

  private static int chunk(double coordinate) {
    return (int) Math.floor(coordinate / 16D);
  }

  interface ChunkAccess {
    boolean canAccess(int chunkX, int chunkZ);
  }
}

final class HeartseekerLifecycle {
  private final AtomicLong generation = new AtomicLong(1L);

  long current() {
    return generation.get();
  }

  boolean isCurrent(long token) {
    return generation.get() == token;
  }

  long invalidate() {
    return generation.incrementAndGet();
  }
}
