package art.arcane.adapt.content.adaptation.ranged;

import org.bukkit.util.Vector;

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

final class HeartseekerFlightMath {
  private static final double LAUNCH_SPEED_RATIO = 0.5D;
  private static final double FULL_DRAW_FLOOR_SPEED = 0.9D;
  private static final double FULL_DRAW_SEEK_SPEED = 1.5D;

  private HeartseekerFlightMath() {
  }

  static double seekSpeed(double launchSpeed) {
    double scaled = Math.max(0D, launchSpeed) * LAUNCH_SPEED_RATIO;
    return Math.max(FULL_DRAW_FLOOR_SPEED, Math.min(FULL_DRAW_SEEK_SPEED, scaled));
  }

  static Vector turnTowards(Vector current, Vector desired, double maximumRadians) {
    Vector currentDirection = normalized(current);
    Vector desiredDirection = normalized(desired);
    if (currentDirection.lengthSquared() <= 0.000001D) {
      return desiredDirection;
    }
    if (desiredDirection.lengthSquared() <= 0.000001D) {
      return currentDirection;
    }
    double dot = Math.max(-1D, Math.min(1D, currentDirection.dot(desiredDirection)));
    double angle = Math.acos(dot);
    double turn = Math.max(0D, maximumRadians);
    if (angle <= turn || angle <= 0.000001D) {
      return desiredDirection;
    }
    if (turn <= 0D) {
      return currentDirection;
    }

    Vector axis = currentDirection.clone().crossProduct(desiredDirection);
    if (axis.lengthSquared() <= 0.000001D) {
      axis = basis(currentDirection).side();
    } else {
      axis.normalize();
    }
    double cosine = Math.cos(turn);
    double sine = Math.sin(turn);
    Vector rotated = currentDirection.clone().multiply(cosine)
        .add(axis.clone().crossProduct(currentDirection).multiply(sine))
        .add(axis.clone().multiply(axis.dot(currentDirection) * (1D - cosine)));
    return normalized(rotated);
  }

  static Vector quadraticArcGuidance(Vector origin, Vector launchDirection, Vector target,
                                     double traveledDistance, double controlDistance,
                                     double arcDistance) {
    double launchLengthSquared = launchDirection.lengthSquared();
    double targetX = target.getX() - origin.getX();
    double targetY = target.getY() - origin.getY();
    double targetZ = target.getZ() - origin.getZ();
    if (launchLengthSquared <= 0.000001D) {
      return normalized(targetX, targetY, targetZ);
    }

    double inverseLaunchLength = 1D / Math.sqrt(launchLengthSquared);
    double controlLength = Math.max(0D, controlDistance);
    double controlX = launchDirection.getX() * inverseLaunchLength * controlLength;
    double controlY = launchDirection.getY() * inverseLaunchLength * controlLength;
    double controlZ = launchDirection.getZ() * inverseLaunchLength * controlLength;
    double safeArcDistance = Math.max(0.000001D, arcDistance);
    double progress = Math.max(0D, Math.min(1D, traveledDistance / safeArcDistance));
    double tangentX = (controlX * (1D - progress))
        + ((targetX - controlX) * progress);
    double tangentY = (controlY * (1D - progress))
        + ((targetY - controlY) * progress);
    double tangentZ = (controlZ * (1D - progress))
        + ((targetZ - controlZ) * progress);
    Vector tangent = normalized(tangentX, tangentY, tangentZ);
    return tangent.lengthSquared() <= 0.000001D
        ? normalized(targetX, targetY, targetZ)
        : tangent;
  }

  static boolean hasTraveledMinimum(Vector origin, Vector current, double minimumDistance) {
    double distance = Math.max(0D, minimumDistance);
    return current.distanceSquared(origin) >= distance * distance;
  }

  static double repeatExitDistance(double speed, int noDamageTicks, double maximumTurnDegrees,
                                   double configuredDistance) {
    double safeSpeed = Math.max(0D, speed);
    double safeTurnDegrees = Math.max(1D, maximumTurnDegrees);
    double turnTicks = 180D / safeTurnDegrees;
    double travelTicks = Math.max(0D, Math.max(0, noDamageTicks) + 2D - turnTicks);
    double immunityDistance = safeSpeed * travelTicks * 0.5D;
    return Math.min(24D, Math.max(Math.max(0D, configuredDistance), immunityDistance));
  }

  static Facing facing(Vector direction) {
    Vector normalized = normalized(direction);
    double horizontal = Math.sqrt((normalized.getX() * normalized.getX())
        + (normalized.getZ() * normalized.getZ()));
    float yaw = (float) Math.toDegrees(Math.atan2(-normalized.getX(), normalized.getZ()));
    float pitch = (float) Math.toDegrees(Math.atan2(-normalized.getY(), horizontal));
    return new Facing(yaw, pitch);
  }

  static Vector avoidance(Vector forward, int index, double strength) {
    Vector normalizedForward = normalized(forward);
    Basis basis = basis(normalizedForward);
    Vector side = basis.side();
    Vector vertical = basis.vertical();
    Vector offset = switch (Math.floorMod(index, 8)) {
      case 0 -> vertical;
      case 1 -> side;
      case 2 -> side.multiply(-1D);
      case 3 -> vertical.multiply(-1D);
      case 4 -> vertical.add(side).normalize();
      case 5 -> vertical.add(side.multiply(-1D)).normalize();
      case 6 -> vertical.multiply(-1D).add(side).normalize();
      default -> vertical.multiply(-1D).add(side.multiply(-1D)).normalize();
    };
    return normalizedForward.add(offset.multiply(strength)).normalize();
  }

  private static Basis basis(Vector forward) {
    Vector reference = Math.abs(forward.getY()) < 0.9D
        ? new Vector(0D, 1D, 0D)
        : new Vector(1D, 0D, 0D);
    Vector side = forward.clone().crossProduct(reference);
    if (side.lengthSquared() <= 0.000001D) {
      side = new Vector(0D, 0D, 1D);
    }
    side.normalize();
    Vector vertical = side.clone().crossProduct(forward).normalize();
    return new Basis(side, vertical);
  }

  private static Vector normalized(Vector vector) {
    return vector.lengthSquared() <= 0.000001D ? new Vector() : vector.clone().normalize();
  }

  private static Vector normalized(double x, double y, double z) {
    double lengthSquared = (x * x) + (y * y) + (z * z);
    if (lengthSquared <= 0.000001D) {
      return new Vector();
    }
    double inverseLength = 1D / Math.sqrt(lengthSquared);
    return new Vector(x * inverseLength, y * inverseLength, z * inverseLength);
  }

  private record Basis(Vector side, Vector vertical) {
  }

  record Facing(float yaw, float pitch) {
  }
}
