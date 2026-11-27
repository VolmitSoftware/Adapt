package art.arcane.adapt.util.common.world;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.math.BoundedSphereScan;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Budgeted nearest-match block scans around a point, spread over ticks and dispatched per chunk on the owning
 * region. Completions run on the global tick.
 */
public final class BlockScanScheduler {
  public static final int MAX_READS_PER_TICK = 4096;
  public static final int MAX_READS_PER_JOB_TICK = 512;
  public static final int MAX_JOBS_PER_TICK = 64;
  public static final int MAX_COMPLETIONS_PER_TICK = 128;
  public static final int MAX_JOB_AGE_TICKS = 100;

  private static final Object LOCK = new Object();
  private static final ArrayDeque<ScanJob> JOBS = new ArrayDeque<>();
  private static final IdentityHashMap<Object, Map<UUID, ScanJob>> JOBS_BY_OWNER = new IdentityHashMap<>();
  private static final ConcurrentLinkedQueue<Runnable> COMPLETIONS = new ConcurrentLinkedQueue<>();
  private static boolean tickScheduled;
  private static long tickSequence;
  private static volatile long generation;

  private BlockScanScheduler() {
  }

  public static UUID submit(Object owner, UUID playerId, ScanRequest request) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(request, "request");

    UUID scanId = UUID.randomUUID();
    ScanJob job = new ScanJob(owner, playerId, scanId, request);
    long scheduleGeneration = -1L;
    synchronized (LOCK) {
      Map<UUID, ScanJob> ownerJobs = JOBS_BY_OWNER.computeIfAbsent(owner, ignored -> new HashMap<>());
      ScanJob previous = ownerJobs.put(playerId, job);
      if (previous != null) {
        previous.cancel();
        JOBS.remove(previous);
      }
      JOBS.addLast(job);
      if (markScheduled()) {
        scheduleGeneration = generation;
      }
    }
    if (scheduleGeneration >= 0L) {
      scheduleTick(scheduleGeneration);
    }
    return scanId;
  }

  public static void cancel(Object owner, UUID playerId) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(playerId, "playerId");
    synchronized (LOCK) {
      Map<UUID, ScanJob> ownerJobs = JOBS_BY_OWNER.get(owner);
      if (ownerJobs == null) {
        return;
      }

      ScanJob job = ownerJobs.remove(playerId);
      if (job != null) {
        job.cancel();
        JOBS.remove(job);
      }
      if (ownerJobs.isEmpty()) {
        JOBS_BY_OWNER.remove(owner);
      }
    }
  }

  public static void cancelOwner(Object owner) {
    Objects.requireNonNull(owner, "owner");
    synchronized (LOCK) {
      Map<UUID, ScanJob> ownerJobs = JOBS_BY_OWNER.remove(owner);
      if (ownerJobs == null) {
        return;
      }

      for (ScanJob job : ownerJobs.values()) {
        job.cancel();
        JOBS.remove(job);
      }
    }
  }

  public static void reset() {
    synchronized (LOCK) {
      generation++;
      for (ScanJob job : JOBS) {
        job.cancel();
      }
      JOBS.clear();
      JOBS_BY_OWNER.clear();
      COMPLETIONS.clear();
      tickScheduled = false;
      tickSequence = 0L;
    }
  }

  private static void tick(long expectedGeneration) {
    if (expectedGeneration != generation) {
      return;
    }
    drainCompletions();

    long currentTick;
    List<ScanJob> selected;
    synchronized (LOCK) {
      if (expectedGeneration != generation) {
        return;
      }
      currentTick = ++tickSequence;
      selected = new ArrayList<>(Math.min(MAX_JOBS_PER_TICK, JOBS.size()));
      int examined = 0;
      int available = JOBS.size();
      while (examined < available && !JOBS.isEmpty() && selected.size() < MAX_JOBS_PER_TICK) {
        ScanJob job = JOBS.removeFirst();
        JOBS.addLast(job);
        job.expire(currentTick);
        if (job.isEligible()) {
          selected.add(job);
        }
        examined++;
      }
    }

    FairReadBudget.Allocation allocation = FairReadBudget.allocate(
        MAX_READS_PER_TICK,
        MAX_READS_PER_JOB_TICK,
        selected.size()
    );
    for (int i = 0; i < selected.size(); i++) {
      int grant = allocation.grant(i, 0);
      if (grant > 0) {
        selected.get(i).dispatch(grant);
      }
    }

    boolean scheduleNext;
    synchronized (LOCK) {
      if (JOBS.isEmpty() && COMPLETIONS.isEmpty()) {
        tickScheduled = false;
        scheduleNext = false;
      } else {
        scheduleNext = true;
      }
    }
    if (scheduleNext) {
      scheduleTick(expectedGeneration);
    }
  }

  private static void drainCompletions() {
    Runnable completion;
    int completed = 0;
    while (completed < MAX_COMPLETIONS_PER_TICK && (completion = COMPLETIONS.poll()) != null) {
      try {
        completion.run();
      } catch (Throwable error) {
        AdaptLog.warn("World block scan completion failed: " + error.getClass().getSimpleName()
            + (error.getMessage() == null ? "" : " - " + error.getMessage()));
        AdaptLog.error(error);
      }
      completed++;
    }
  }

  private static boolean markScheduled() {
    if (tickScheduled) {
      return false;
    }
    tickScheduled = true;
    return true;
  }

  private static void remove(ScanJob job) {
    long scheduleGeneration = -1L;
    synchronized (LOCK) {
      JOBS.remove(job);
      Map<UUID, ScanJob> ownerJobs = JOBS_BY_OWNER.get(job.owner);
      if (ownerJobs != null) {
        ownerJobs.remove(job.playerId, job);
        if (ownerJobs.isEmpty()) {
          JOBS_BY_OWNER.remove(job.owner);
        }
      }
      if (markScheduled()) {
        scheduleGeneration = generation;
      }
    }
    if (scheduleGeneration >= 0L) {
      scheduleTick(scheduleGeneration);
    }
  }

  private static void scheduleTick(long expectedGeneration) {
    Tasks.s(() -> tick(expectedGeneration), 1);
  }

  public record AdditionalMatch(int x, int y, int z, Id material, double distanceSquared) {
    public AdditionalMatch {
      Objects.requireNonNull(material, "material");
      if (!Double.isFinite(distanceSquared) || distanceSquared < 0D) {
        throw new IllegalArgumentException("distanceSquared must be finite and non-negative");
      }
    }
  }

  public record Match(int x, int y, int z, Id material, double distanceSquared, boolean supplied) {
    public Loc center(WorldRef world) {
      return new Loc(world, x + 0.5D, y + 0.5D, z + 0.5D);
    }
  }

  public record ScanResult(UUID scanId, WorldRef world, List<Match> matches, boolean budgetExpired) {
    public ScanResult {
      matches = List.copyOf(matches);
    }
  }

  public static final class ScanRequest {
    private final WorldRef world;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final int radius;
    private final int denseRadius;
    private final int maxSamples;
    private final int maxResults;
    private final int seed;
    private final List<AdditionalMatch> additionalMatches;
    private final Predicate<Id> matcher;
    private final Predicate<BlockRef> blockMatcher;
    private final Consumer<ScanResult> completion;

    private ScanRequest(Builder builder) {
      world = builder.world;
      originX = builder.originX;
      originY = builder.originY;
      originZ = builder.originZ;
      radius = builder.radius;
      denseRadius = builder.denseRadius;
      maxSamples = builder.maxSamples;
      maxResults = builder.maxResults;
      seed = builder.seed;
      additionalMatches = List.copyOf(builder.additionalMatches);
      matcher = builder.matcher;
      blockMatcher = builder.blockMatcher;
      completion = Objects.requireNonNull(builder.completion, "completion");
      if (matcher == null && blockMatcher == null) {
        throw new IllegalArgumentException("matcher or blockMatcher must be provided");
      }
      if (radius < 0) {
        throw new IllegalArgumentException("radius must be non-negative");
      }
      if (maxSamples < 0) {
        throw new IllegalArgumentException("maxSamples must be non-negative");
      }
      if (maxResults < 1) {
        throw new IllegalArgumentException("maxResults must be positive");
      }
    }

    public static Builder builder(Loc origin) {
      return new Builder(origin);
    }

    public static final class Builder {
      private final WorldRef world;
      private final int originX;
      private final int originY;
      private final int originZ;
      private int radius;
      private int denseRadius;
      private int maxSamples;
      private int maxResults = 1;
      private int seed;
      private List<AdditionalMatch> additionalMatches = List.of();
      private Predicate<Id> matcher;
      private Predicate<BlockRef> blockMatcher;
      private Consumer<ScanResult> completion;

      private Builder(Loc origin) {
        Loc requiredOrigin = Objects.requireNonNull(origin, "origin");
        world = Objects.requireNonNull(requiredOrigin.world(), "origin world");
        originX = requiredOrigin.blockX();
        originY = requiredOrigin.blockY();
        originZ = requiredOrigin.blockZ();
      }

      public Builder radius(int value) {
        radius = value;
        return this;
      }

      public Builder denseRadius(int value) {
        denseRadius = value;
        return this;
      }

      public Builder maxSamples(int value) {
        maxSamples = value;
        return this;
      }

      public Builder maxResults(int value) {
        maxResults = value;
        return this;
      }

      public Builder seed(int value) {
        seed = value;
        return this;
      }

      public Builder additionalMatches(List<AdditionalMatch> value) {
        additionalMatches = List.copyOf(Objects.requireNonNull(value, "additionalMatches"));
        return this;
      }

      public Builder matcher(Predicate<Id> value) {
        matcher = value;
        return this;
      }

      public Builder blockMatcher(Predicate<BlockRef> value) {
        blockMatcher = value;
        return this;
      }

      public Builder completion(Consumer<ScanResult> value) {
        completion = value;
        return this;
      }

      public ScanRequest build() {
        return new ScanRequest(this);
      }
    }
  }

  private static final class ScanJob {
    private final Object owner;
    private final UUID playerId;
    private final UUID scanId;
    private final ScanRequest request;
    private final BoundedSphereScan.Cursor cursor;
    private final NearestMatches matches;
    private final AtomicBoolean cancelled;
    private final AtomicBoolean completed;
    private final AtomicInteger pendingBatches;
    private final long expiresAtTick;
    private final long generation;
    private volatile boolean inFlight;
    private volatile boolean cursorExhausted;
    private volatile boolean budgetExpired;
    private int additionalIndex;

    private ScanJob(Object owner, UUID playerId, UUID scanId, ScanRequest request) {
      this.owner = owner;
      this.playerId = playerId;
      this.scanId = scanId;
      this.request = request;
      cursor = BoundedSphereScan.cursor(request.radius, request.denseRadius, request.maxSamples, request.seed);
      matches = new NearestMatches(request.maxResults);
      cancelled = new AtomicBoolean(false);
      completed = new AtomicBoolean(false);
      pendingBatches = new AtomicInteger();
      synchronized (LOCK) {
        expiresAtTick = tickSequence + MAX_JOB_AGE_TICKS;
        generation = BlockScanScheduler.generation;
      }
    }

    private boolean isEligible() {
      return !cancelled.get() && !completed.get() && !inFlight;
    }

    private void expire(long currentTick) {
      if (currentTick < expiresAtTick || cancelled.get() || completed.get()) {
        return;
      }
      budgetExpired = true;
      additionalIndex = request.additionalMatches.size();
      cursorExhausted = true;
      if (!inFlight) {
        complete();
      }
    }

    private void dispatch(int budget) {
      if (!isEligible()) {
        return;
      }

      LinkedHashMap<Long, ChunkBatch> batches = new LinkedHashMap<>();
      int consumed = 0;
      while (consumed < budget && additionalIndex < request.additionalMatches.size()) {
        AdditionalMatch additional = request.additionalMatches.get(additionalIndex++);
        consumed++;
        if (additional.y < request.world.minHeight() || additional.y >= request.world.maxHeight()) {
          continue;
        }
        addToBatch(batches, additional.x, additional.y, additional.z, additional.material, additional.distanceSquared, true);
      }

      while (consumed < budget && !cursorExhausted) {
        if (!cursor.advance()) {
          cursorExhausted = true;
          break;
        }
        consumed++;
        int blockY = request.originY + cursor.y();
        if (blockY < request.world.minHeight() || blockY >= request.world.maxHeight()) {
          continue;
        }
        int blockX = request.originX + cursor.x();
        int blockZ = request.originZ + cursor.z();
        addToBatch(batches, blockX, blockY, blockZ, null, cursor.distanceSquared(), false);
      }

      if (batches.isEmpty()) {
        if (isFinished()) {
          complete();
        }
        return;
      }

      inFlight = true;
      pendingBatches.set(batches.size());
      for (ChunkBatch batch : batches.values()) {
        dispatch(batch);
      }
    }

    private void addToBatch(Map<Long, ChunkBatch> batches, int x, int y, int z, Id material,
                            double distanceSquared, boolean supplied) {
      int chunkX = x >> 4;
      int chunkZ = z >> 4;
      long chunkKey = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
      ChunkBatch batch = batches.computeIfAbsent(chunkKey, ignored -> new ChunkBatch(chunkX, chunkZ));
      batch.add(x, y, z, material, distanceSquared, supplied);
    }

    private void dispatch(ChunkBatch batch) {
      if (!Tasks.isFoliaThreading()) {
        process(batch);
        return;
      }

      Loc anchor = new Loc(request.world, (batch.chunkX << 4) + 8, request.originY, (batch.chunkZ << 4) + 8);
      if (!Tasks.runAt(anchor, () -> process(batch))) {
        batchFinished();
      }
    }

    private void process(ChunkBatch batch) {
      try {
        if (cancelled.get() || !request.world.isChunkLoaded(batch.chunkX, batch.chunkZ)) {
          return;
        }

        for (int i = 0; i < batch.size && !cancelled.get(); i++) {
          Id material = batch.material[i];
          if (material == null) {
            BlockRef block = request.world.blockAt(batch.x[i], batch.y[i], batch.z[i]);
            material = block.type();
            if (request.blockMatcher != null) {
              if (!request.blockMatcher.test(block)) {
                continue;
              }
            } else if (!request.matcher.test(material)) {
              continue;
            }
          }
          matches.offer(batch.x[i], batch.y[i], batch.z[i], material, batch.distanceSquared[i], batch.supplied[i]);
        }
      } catch (Throwable error) {
        AdaptLog.warn("World block scan batch failed in " + request.world.name() + " at chunk "
            + batch.chunkX + "," + batch.chunkZ + ": " + error.getClass().getSimpleName()
            + (error.getMessage() == null ? "" : " - " + error.getMessage()));
        AdaptLog.error(error);
      } finally {
        batchFinished();
      }
    }

    private void batchFinished() {
      if (pendingBatches.decrementAndGet() != 0) {
        return;
      }
      inFlight = false;
      if (isFinished()) {
        complete();
      }
    }

    private boolean isFinished() {
      return additionalIndex >= request.additionalMatches.size() && cursorExhausted;
    }

    private void complete() {
      if (!completed.compareAndSet(false, true)) {
        return;
      }
      if (cancelled.get() || generation != BlockScanScheduler.generation) {
        return;
      }
      ScanResult result = new ScanResult(scanId, request.world, matches.snapshot(), budgetExpired);
      COMPLETIONS.add(() -> request.completion.accept(result));
      BlockScanScheduler.remove(this);
    }

    private void cancel() {
      cancelled.set(true);
    }
  }

  private static final class ChunkBatch {
    private final int chunkX;
    private final int chunkZ;
    private int[] x;
    private int[] y;
    private int[] z;
    private Id[] material;
    private double[] distanceSquared;
    private boolean[] supplied;
    private int size;

    private ChunkBatch(int chunkX, int chunkZ) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      x = new int[16];
      y = new int[16];
      z = new int[16];
      material = new Id[16];
      distanceSquared = new double[16];
      supplied = new boolean[16];
    }

    private void add(int blockX, int blockY, int blockZ, Id blockMaterial, double blockDistanceSquared,
                     boolean suppliedMatch) {
      ensureCapacity(size + 1);
      x[size] = blockX;
      y[size] = blockY;
      z[size] = blockZ;
      material[size] = blockMaterial;
      distanceSquared[size] = blockDistanceSquared;
      supplied[size] = suppliedMatch;
      size++;
    }

    private void ensureCapacity(int required) {
      if (required <= x.length) {
        return;
      }

      int capacity = Math.max(required, x.length * 2);
      x = Arrays.copyOf(x, capacity);
      y = Arrays.copyOf(y, capacity);
      z = Arrays.copyOf(z, capacity);
      material = Arrays.copyOf(material, capacity);
      distanceSquared = Arrays.copyOf(distanceSquared, capacity);
      supplied = Arrays.copyOf(supplied, capacity);
    }
  }

  private static final class NearestMatches {
    private final int[] x;
    private final int[] y;
    private final int[] z;
    private final Id[] material;
    private final double[] distanceSquared;
    private final boolean[] supplied;
    private int size;

    private NearestMatches(int capacity) {
      x = new int[capacity];
      y = new int[capacity];
      z = new int[capacity];
      material = new Id[capacity];
      distanceSquared = new double[capacity];
      supplied = new boolean[capacity];
    }

    private synchronized void offer(int blockX, int blockY, int blockZ, Id blockMaterial,
                                    double blockDistanceSquared, boolean suppliedMatch) {
      if (contains(blockX, blockY, blockZ)) {
        return;
      }

      int insertion = 0;
      while (insertion < size && !nearer(blockDistanceSquared, blockY, blockX, blockZ, insertion)) {
        insertion++;
      }
      if (insertion >= x.length) {
        return;
      }

      int movable = Math.min(size, x.length - 1) - insertion;
      if (movable > 0) {
        System.arraycopy(x, insertion, x, insertion + 1, movable);
        System.arraycopy(y, insertion, y, insertion + 1, movable);
        System.arraycopy(z, insertion, z, insertion + 1, movable);
        System.arraycopy(material, insertion, material, insertion + 1, movable);
        System.arraycopy(distanceSquared, insertion, distanceSquared, insertion + 1, movable);
        System.arraycopy(supplied, insertion, supplied, insertion + 1, movable);
      }
      x[insertion] = blockX;
      y[insertion] = blockY;
      z[insertion] = blockZ;
      material[insertion] = blockMaterial;
      distanceSquared[insertion] = blockDistanceSquared;
      supplied[insertion] = suppliedMatch;
      if (size < x.length) {
        size++;
      }
    }

    /** Whether the block goes before the match at {@code index}: nearer, or as near and lower in y, x, then z. */
    private boolean nearer(double blockDistanceSquared, int blockY, int blockX, int blockZ, int index) {
      int distance = Double.compare(blockDistanceSquared, distanceSquared[index]);
      if (distance != 0) {
        return distance < 0;
      }
      if (blockY != y[index]) {
        return blockY < y[index];
      }
      if (blockX != x[index]) {
        return blockX < x[index];
      }
      return blockZ < z[index];
    }

    private boolean contains(int blockX, int blockY, int blockZ) {
      for (int i = 0; i < size; i++) {
        if (x[i] == blockX && y[i] == blockY && z[i] == blockZ) {
          return true;
        }
      }
      return false;
    }

    private synchronized List<Match> snapshot() {
      ArrayList<Match> snapshot = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        snapshot.add(new Match(x[i], y[i], z[i], material[i], distanceSquared[i], supplied[i]));
      }
      return snapshot;
    }
  }
}
