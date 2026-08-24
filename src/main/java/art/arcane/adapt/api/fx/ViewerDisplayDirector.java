/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.api.fx;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class ViewerDisplayDirector {
  private static final int MAX_ACTIVE_DISPLAYS = 4_096;
  private static final int MAX_DISPLAYS_PER_VIEWER = 128;
  private static final int MAX_ORPHAN_CHUNKS_PER_TICK = 32;
  private static final int MAX_ORPHAN_PURGE_ATTEMPTS = 3;
  private static final String DISPLAY_TAG = "adapt_viewer_display";
  private static final Object LOCK = new Object();
  private static final Map<DisplayKey, DisplayLease> LEASES = new HashMap<>();
  private static final Map<String, Set<DisplayKey>> LEASE_KEYS_BY_CHANNEL = new HashMap<>();
  private static final Map<UUID, Set<DisplayKey>> LEASE_KEYS_BY_VIEWER = new HashMap<>();
  private static final Map<ViewerChannelKey, Set<DisplayKey>> LEASE_KEYS_BY_VIEWER_CHANNEL = new HashMap<>();
  private static final Map<ViewerKey, Set<DisplayKey>> LEASE_KEYS_BY_VIEWER_KEY = new HashMap<>();
  private static final Set<UUID> LEASED_DISPLAY_IDS = new HashSet<>();
  private static final Map<UUID, Integer> VIEWER_COUNTS = new HashMap<>();
  private static final Map<String, Long> CHANNEL_GENERATIONS = new HashMap<>();
  private static final Map<UUID, Long> VIEWER_GENERATIONS = new HashMap<>();
  private static final Map<ViewerChannelKey, Long> VIEWER_CHANNEL_GENERATIONS = new HashMap<>();
  private static final Map<ViewerKey, Long> VIEWER_KEY_GENERATIONS = new HashMap<>();
  private static final Map<UUID, Integer> PENDING_REQUESTS = new HashMap<>();
  private static final Map<DisplayKey, PendingRequest> PENDING_DISPLAY_REQUESTS = new HashMap<>();
  private static final Set<RequestTicket> ACTIVE_REQUESTS = new HashSet<>();
  private static final Set<UUID> RETIRING_VIEWERS = new HashSet<>();
  private static final Queue<ChunkAddress> ORPHAN_PURGE_QUEUE = new ConcurrentLinkedQueue<>();
  private static final AtomicBoolean ORPHAN_PURGE_SCHEDULED = new AtomicBoolean();
  private static final AtomicInteger ORPHAN_PURGE_FAILURES = new AtomicInteger();
  private static int pendingReservations;
  private static long globalGeneration;
  private static boolean acceptingRequests = true;

  private ViewerDisplayDirector() {
  }

  public static void startRuntime() {
    synchronized (LOCK) {
      acceptingRequests = true;
    }
  }

  public static boolean showBlock(String channel, String key, Player viewer, Location location,
                                  BlockData blockData, Color glowColor, int durationTicks) {
    return showBlockRequest(channel, key, viewer, location, blockData, glowColor, clampDuration(durationTicks));
  }

  public static boolean showPersistentBlock(String channel, String key, Player viewer, Location location,
                                            BlockData blockData, Color glowColor) {
    return showBlockRequest(channel, key, viewer, location, blockData, glowColor, 0);
  }

  private static boolean showBlockRequest(String channel, String key, Player viewer, Location location,
                                          BlockData blockData, Color glowColor, int durationTicks) {
    DisplayKey displayKey = displayKey(channel, key, viewer, location);
    Location ownedLocation = location.clone();
    BlockData ownedBlockData = blockData.clone();
    Transformation transformation = blockTransformation();
    DisplayRequestSpec request = new DisplayRequestSpec(
        displayKey,
        viewer,
        ownedLocation,
        ownedBlockData,
        glowColor,
        transformation,
        durationTicks
    );
    return submitRequest(request);
  }

  public static boolean showLine(String channel, String key, Player viewer, Location start, Location end,
                                 BlockData blockData, Color glowColor, double thickness, int durationTicks) {
    if (start.getWorld() == null || start.getWorld() != end.getWorld()) {
      return false;
    }

    Vector delta = end.toVector().subtract(start.toVector());
    if (!isRenderableLine(delta)) {
      return false;
    }

    double safeThickness = sanitizeLineThickness(thickness);
    DisplayKey displayKey = displayKey(channel, key, viewer, start);
    Location ownedLocation = start.clone();
    BlockData ownedBlockData = blockData.clone();
    Transformation transformation = lineTransformation(delta, safeThickness);
    DisplayRequestSpec request = new DisplayRequestSpec(
        displayKey,
        viewer,
        ownedLocation,
        ownedBlockData,
        glowColor,
        transformation,
        clampDuration(durationTicks)
    );
    return submitRequest(request);
  }

  public static void clearChannel(String channel) {
    if (channel == null) {
      return;
    }
    List<DisplayLease> removed;
    synchronized (LOCK) {
      CHANNEL_GENERATIONS.merge(channel, 1L, Long::sum);
      VIEWER_CHANNEL_GENERATIONS.keySet().removeIf(key -> channel.equals(key.channel()));
      VIEWER_KEY_GENERATIONS.keySet().removeIf(key -> channel.equals(key.channel()));
      removed = removeIndexedLocked(LEASE_KEYS_BY_CHANNEL.get(channel));
    }
    removeDisplays(removed);
  }

  public static void clearViewer(String channel, UUID viewerId) {
    if (channel == null || viewerId == null) {
      return;
    }
    List<DisplayLease> removed;
    synchronized (LOCK) {
      VIEWER_CHANNEL_GENERATIONS.merge(new ViewerChannelKey(channel, viewerId), 1L, Long::sum);
      VIEWER_KEY_GENERATIONS.keySet().removeIf(
          key -> channel.equals(key.channel()) && viewerId.equals(key.viewerId()));
      removed = removeIndexedLocked(LEASE_KEYS_BY_VIEWER_CHANNEL.get(new ViewerChannelKey(channel, viewerId)));
    }
    removeDisplays(removed);
  }

  public static void clearViewerKey(String channel, String key, UUID viewerId) {
    if (channel == null || key == null || viewerId == null) {
      return;
    }
    List<DisplayLease> removed;
    synchronized (LOCK) {
      ViewerKey viewerKey = new ViewerKey(channel, key, viewerId);
      VIEWER_KEY_GENERATIONS.merge(viewerKey, 1L, Long::sum);
      removed = removeIndexedLocked(LEASE_KEYS_BY_VIEWER_KEY.get(viewerKey));
    }
    removeDisplays(removed);
  }

  public static boolean isShowing(String channel, String key, UUID viewerId, Location location) {
    if (channel == null || key == null || viewerId == null || location == null || location.getWorld() == null) {
      return false;
    }
    DisplayKey displayKey = new DisplayKey(
        channel,
        key,
        viewerId,
        location.getWorld().getUID(),
        location.getBlockX(),
        location.getBlockY(),
        location.getBlockZ()
    );
    DisplayLease lease;
    synchronized (LOCK) {
      lease = LEASES.get(displayKey);
    }
    if (lease == null) {
      return false;
    }
    if (J.isOwnedByCurrentRegion(lease.anchor())) {
      return validateLeaseOwned(displayKey, lease);
    }
    scheduleLeaseValidation(displayKey, lease);
    return true;
  }

  public static void clearViewer(UUID viewerId) {
    if (viewerId == null) {
      return;
    }
    List<DisplayLease> removed;
    synchronized (LOCK) {
      VIEWER_GENERATIONS.merge(viewerId, 1L, Long::sum);
      VIEWER_CHANNEL_GENERATIONS.keySet().removeIf(key -> viewerId.equals(key.viewerId()));
      VIEWER_KEY_GENERATIONS.keySet().removeIf(key -> viewerId.equals(key.viewerId()));
      removed = removeIndexedLocked(LEASE_KEYS_BY_VIEWER.get(viewerId));
    }
    removeDisplays(removed);
  }

  public static void retireViewer(UUID viewerId) {
    if (viewerId == null) {
      return;
    }
    List<DisplayLease> removed;
    synchronized (LOCK) {
      RETIRING_VIEWERS.add(viewerId);
      VIEWER_GENERATIONS.merge(viewerId, 1L, Long::sum);
      VIEWER_CHANNEL_GENERATIONS.keySet().removeIf(key -> viewerId.equals(key.viewerId()));
      VIEWER_KEY_GENERATIONS.keySet().removeIf(key -> viewerId.equals(key.viewerId()));
      if (!PENDING_REQUESTS.containsKey(viewerId)) {
        clearViewerGenerationsLocked(viewerId);
      }
      removed = removeIndexedLocked(LEASE_KEYS_BY_VIEWER.get(viewerId));
    }
    removeDisplays(removed);
  }

  public static void clearAll() {
    CleanupSnapshot snapshot = detachAll(false);
    for (DisplayLease lease : snapshot.leases()) {
      removeDisplay(lease);
    }
  }

  public static boolean clearAllAndAwait(long timeoutMillis) {
    return clearAllAndAwait(timeoutMillis, J::isOwnedByCurrentRegion);
  }

  static boolean clearAllAndAwait(long timeoutMillis, Predicate<Location> currentOwnership) {
    CleanupSnapshot snapshot = detachAll(true);
    CleanupFailures failures = new CleanupFailures();
    List<CompletableFuture<Boolean>> completions = scheduleRemovalBatches(snapshot.leases(), failures);
    for (RequestTicket ticket : snapshot.requests()) {
      if (!ticket.started().get() || currentOwnership.test(ticket.anchor())) {
        finishRequest(ticket);
        continue;
      }
      completions.add(ticket.completion().thenApply(ignored -> true));
    }
    boolean successful = awaitCleanup(completions, timeoutMillis);
    for (RequestTicket ticket : snapshot.requests()) {
      finishRequest(ticket);
    }
    failures.report();
    return successful && failures.isEmpty();
  }

  public static void purgeOrphans() {
    ORPHAN_PURGE_QUEUE.clear();
    ORPHAN_PURGE_FAILURES.set(0);
    for (World world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        ORPHAN_PURGE_QUEUE.add(new ChunkAddress(world, chunk.getX(), chunk.getZ(), 0));
      }
    }
    scheduleOrphanPurge();
  }

  static boolean isRenderableLine(Vector delta) {
    return delta != null
        && Double.isFinite(delta.getX())
        && Double.isFinite(delta.getY())
        && Double.isFinite(delta.getZ())
        && delta.lengthSquared() > 1.0E-8D;
  }

  static double sanitizeLineThickness(double thickness) {
    return Double.isFinite(thickness)
        ? Math.max(0.015D, Math.min(0.5D, thickness))
        : 0.05D;
  }

  static Transformation lineTransformation(Vector delta, double thickness) {
    Vector direction = delta.clone().normalize();
    Quaternionf rotation = new Quaternionf().rotationTo(
        0F,
        0F,
        1F,
        (float) direction.getX(),
        (float) direction.getY(),
        (float) direction.getZ()
    );
    Vector3f centeredOffset = new Vector3f(
        (float) (-thickness * 0.5D),
        (float) (-thickness * 0.5D),
        0F
    );
    rotation.transform(centeredOffset);
    return new Transformation(
        centeredOffset,
        rotation,
        new Vector3f((float) thickness, (float) thickness, (float) delta.length()),
        new Quaternionf()
    );
  }

  static int activeCount() {
    synchronized (LOCK) {
      return LEASES.size();
    }
  }

  private static void spawnOrRenewOwned(DisplayRequest request) {
    DisplayLease existing;
    synchronized (LOCK) {
      if (!isGenerationCurrentLocked(request)) {
        return;
      }
      existing = LEASES.get(request.key());
      if (existing != null && existing.display().isValid()) {
        if (finishReservationLocked(request.ticket())) {
          releaseLocked(request.key().viewerId());
        }
        existing.renew(request.durationTicks());
      } else {
        if (existing != null) {
          removeLeaseLocked(request.key(), existing);
        }
        if (!request.ticket().reservationHeld().get() && !reserveLocked(request.ticket())) {
          return;
        }
        existing = null;
      }
    }

    if (existing != null) {
      DisplayLease renewed = existing;
      J.runEntity(renewed.display(), () -> updateOwned(renewed.display(), request));
      scheduleExpiry(request.key(), renewed);
      return;
    }

    BlockDisplay display;
    try {
      display = request.location().getWorld().spawn(request.location(), BlockDisplay.class, spawned -> configure(spawned, request));
    } catch (Throwable error) {
      releaseReservation(request.ticket());
      Adapt.warn("Failed to spawn private reveal display for " + request.key().channel() + ": "
          + error.getClass().getSimpleName()
          + (error.getMessage() == null ? "" : " - " + error.getMessage()));
      Adapt.error(error);
      return;
    }

    DisplayLease lease = new DisplayLease(display, request.location().clone(), request.durationTicks());
    boolean accepted;
    DisplayLease replaced;
    synchronized (LOCK) {
      boolean reservationHeld = finishReservationLocked(request.ticket());
      accepted = isGenerationCurrentLocked(request);
      replaced = accepted ? LEASES.put(request.key(), lease) : null;
      if (!accepted) {
        if (reservationHeld) {
          releaseLocked(request.key().viewerId());
        }
      } else {
        if (replaced != null) {
          LEASED_DISPLAY_IDS.remove(replaced.display().getUniqueId());
          releaseLocked(request.key().viewerId());
        }
        indexLeaseLocked(request.key());
        LEASED_DISPLAY_IDS.add(display.getUniqueId());
      }
    }
    if (replaced != null) {
      removeDisplay(replaced);
    }
    if (!accepted) {
      removeDisplayOwned(display);
      return;
    }

    if (!J.runEntity(request.viewer(), () -> showOwned(request.key(), lease, request.viewer()))) {
      remove(request.key(), lease);
      return;
    }
    scheduleExpiry(request.key(), lease);
  }

  private static boolean submitRequest(DisplayRequestSpec spec) {
    PendingRequest pending;
    synchronized (LOCK) {
      if (!acceptingRequests) {
        return false;
      }
      PendingRequest existing = PENDING_DISPLAY_REQUESTS.get(spec.key());
      GenerationStamp generation = generationLocked(spec.key());
      if (existing != null && !existing.ticket().finished().get()) {
        existing.replace(displayRequest(spec, generation, existing.ticket()));
        return true;
      }
      if (existing != null) {
        PENDING_DISPLAY_REQUESTS.remove(spec.key(), existing);
      }
      if (ACTIVE_REQUESTS.size() >= MAX_ACTIVE_DISPLAYS
          || PENDING_REQUESTS.getOrDefault(spec.key().viewerId(), 0) >= MAX_DISPLAYS_PER_VIEWER) {
        return false;
      }

      RequestTicket ticket = new RequestTicket(spec.key(), spec.location().clone());
      if (!LEASES.containsKey(spec.key()) && !reserveLocked(ticket)) {
        return false;
      }
      DisplayRequest request = displayRequest(spec, generation, ticket);
      pending = new PendingRequest(ticket, request);
      PENDING_DISPLAY_REQUESTS.put(spec.key(), pending);
      PENDING_REQUESTS.merge(spec.key().viewerId(), 1, Integer::sum);
      ACTIVE_REQUESTS.add(ticket);
    }
    return scheduleRequest(pending);
  }

  private static DisplayRequest displayRequest(
      DisplayRequestSpec spec,
      GenerationStamp generation,
      RequestTicket ticket
  ) {
    return new DisplayRequest(
        spec.key(),
        spec.viewer(),
        spec.location(),
        spec.blockData(),
        spec.glowColor(),
        spec.transformation(),
        spec.durationTicks(),
        generation,
        ticket
    );
  }

  private static boolean scheduleRequest(PendingRequest pending) {
    Location location;
    boolean active;
    synchronized (LOCK) {
      RequestTicket ticket = pending.ticket();
      active = !ticket.finished().get() && PENDING_DISPLAY_REQUESTS.get(ticket.key()) == pending;
      location = active ? pending.latest().location() : null;
    }
    if (!active) {
      finishRequest(pending.ticket());
      return false;
    }

    boolean scheduled;
    try {
      scheduled = J.runAt(location, () -> runRequestOwned(pending));
    } catch (RuntimeException | Error error) {
      finishRequest(pending.ticket());
      throw error;
    }
    if (!scheduled) {
      finishRequest(pending.ticket());
    }
    return scheduled;
  }

  private static void runRequestOwned(PendingRequest pending) {
    RequestTicket ticket = pending.ticket();
    ticket.started().set(true);
    DisplayRequest request;
    synchronized (LOCK) {
      if (ticket.finished().get() || PENDING_DISPLAY_REQUESTS.get(ticket.key()) != pending) {
        request = null;
      } else {
        request = pending.latest();
      }
    }
    if (request == null) {
      finishRequest(ticket);
      return;
    }

    try {
      spawnOrRenewOwned(request);
    } finally {
      completeRequestIteration(pending, request);
    }
  }

  private static void completeRequestIteration(PendingRequest pending, DisplayRequest processed) {
    boolean reschedule = false;
    RequestTicket ticket = pending.ticket();
    synchronized (LOCK) {
      if (!ticket.finished().get() && PENDING_DISPLAY_REQUESTS.get(ticket.key()) == pending) {
        if (pending.latest() != processed) {
          reschedule = true;
        } else {
          PENDING_DISPLAY_REQUESTS.remove(ticket.key(), pending);
        }
      }
    }
    if (reschedule) {
      scheduleRequest(pending);
    } else {
      finishRequest(ticket);
    }
  }

  private static void configure(BlockDisplay display, DisplayRequest request) {
    display.setPersistent(false);
    display.setInvulnerable(true);
    display.setGravity(false);
    display.setSilent(true);
    display.setVisibleByDefault(false);
    display.setViewRange(2F);
    display.setShadowRadius(0F);
    display.setShadowStrength(0F);
    display.setBrightness(new Display.Brightness(15, 15));
    display.setBlock(request.blockData());
    display.setGlowColorOverride(request.glowColor());
    display.setGlowing(true);
    display.setTransformation(request.transformation());
    display.addScoreboardTag(DISPLAY_TAG);
  }

  private static void updateOwned(BlockDisplay display, DisplayRequest request) {
    if (!display.isValid()) {
      return;
    }
    display.teleport(request.location());
    display.setBlock(request.blockData());
    display.setGlowColorOverride(request.glowColor());
    display.setGlowing(true);
    display.setTransformation(request.transformation());
  }

  private static void showOwned(DisplayKey key, DisplayLease lease, Player viewer) {
    if (!viewer.isOnline() || !isCurrent(key, lease)) {
      remove(key, lease);
      return;
    }
    viewer.showEntity(Adapt.instance, lease.display());
  }

  private static void scheduleExpiry(DisplayKey key, DisplayLease lease) {
    int delayTicks = lease.durationTicks();
    if (delayTicks <= 0) {
      return;
    }
    scheduleExpiry(key, lease, lease.generation(), delayTicks);
  }

  private static void scheduleExpiry(DisplayKey key, DisplayLease lease, long generation, int delayTicks) {
    if (!J.runAt(lease.anchor(), () -> expireOwned(key, lease, generation), delayTicks)) {
      remove(key, lease);
    }
  }

  private static void expireOwned(DisplayKey key, DisplayLease lease, long generation) {
    if (!isCurrent(key, lease)) {
      removeDisplayOwned(lease.display());
      return;
    }
    if (lease.generation() != generation) {
      return;
    }
    int remainingTicks = remainingExpiryTicks(lease.expiresAt(), System.currentTimeMillis());
    if (remainingTicks > 0) {
      scheduleExpiry(key, lease, generation, remainingTicks);
      return;
    }
    remove(key, lease);
  }

  private static List<DisplayLease> removeIndexedLocked(Set<DisplayKey> indexedKeys) {
    if (indexedKeys == null || indexedKeys.isEmpty()) {
      return List.of();
    }
    List<DisplayKey> keys = List.copyOf(indexedKeys);
    ArrayList<DisplayLease> removed = new ArrayList<>(keys.size());
    for (DisplayKey key : keys) {
      DisplayLease lease = LEASES.get(key);
      if (lease != null && removeLeaseLocked(key, lease)) {
        removed.add(lease);
      }
    }
    return removed;
  }

  private static void removeDisplays(List<DisplayLease> leases) {
    for (DisplayLease lease : leases) {
      removeDisplay(lease);
    }
  }

  private static CleanupSnapshot detachAll(boolean stopRequests) {
    synchronized (LOCK) {
      if (stopRequests) {
        acceptingRequests = false;
      }
      globalGeneration++;
      CHANNEL_GENERATIONS.clear();
      VIEWER_GENERATIONS.clear();
      VIEWER_CHANNEL_GENERATIONS.clear();
      VIEWER_KEY_GENERATIONS.clear();
      PENDING_REQUESTS.clear();
      PENDING_DISPLAY_REQUESTS.clear();
      RETIRING_VIEWERS.clear();
      for (RequestTicket ticket : ACTIVE_REQUESTS) {
        ticket.reservationHeld().set(false);
      }
      pendingReservations = 0;
      List<DisplayLease> leases = List.copyOf(LEASES.values());
      LEASES.clear();
      LEASE_KEYS_BY_CHANNEL.clear();
      LEASE_KEYS_BY_VIEWER.clear();
      LEASE_KEYS_BY_VIEWER_CHANNEL.clear();
      LEASE_KEYS_BY_VIEWER_KEY.clear();
      LEASED_DISPLAY_IDS.clear();
      VIEWER_COUNTS.clear();
      return new CleanupSnapshot(leases, List.copyOf(ACTIVE_REQUESTS));
    }
  }

  private static List<CompletableFuture<Boolean>> scheduleRemovalBatches(
      List<DisplayLease> leases,
      CleanupFailures failures
  ) {
    Map<RegionKey, DisplayRemovalBatch> batches = new LinkedHashMap<>();
    ArrayList<CompletableFuture<Boolean>> completions = new ArrayList<>();
    for (DisplayLease lease : leases) {
      Location anchor = lease.anchor();
      World world = anchor.getWorld();
      if (world == null) {
        IllegalStateException failure = new IllegalStateException(
            "Cannot remove an Adapt private display without an owning world: " + lease.display().getUniqueId());
        failures.record(failure);
        completions.add(CompletableFuture.completedFuture(false));
        continue;
      }
      RegionKey region = new RegionKey(world.getUID(), anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4);
      batches.computeIfAbsent(region, ignored -> new DisplayRemovalBatch(anchor, new ArrayList<>()))
          .leases().add(lease);
    }

    completions.ensureCapacity(completions.size() + batches.size());
    for (DisplayRemovalBatch batch : batches.values()) {
      CompletableFuture<Boolean> completion = new CompletableFuture<>();
      completions.add(completion);
      Runnable cleanup = () -> removeBatchOwned(batch, completion, failures);
      boolean accepted;
      try {
        if (J.isOwnedByCurrentRegion(batch.anchor())) {
          cleanup.run();
          accepted = true;
        } else {
          accepted = J.runAt(batch.anchor(), cleanup);
        }
      } catch (Throwable error) {
        failures.record(new IllegalStateException(
            "Failed to dispatch Adapt private display cleanup for region " + batch.anchor(), error));
        completion.complete(false);
        continue;
      }
      if (!accepted) {
        IllegalStateException failure = new IllegalStateException(
            "Failed to schedule Adapt private display cleanup for region " + batch.anchor());
        failures.record(failure);
        completion.complete(false);
      }
    }
    return completions;
  }

  private static void removeBatchOwned(
      DisplayRemovalBatch batch,
      CompletableFuture<Boolean> completion,
      CleanupFailures failures
  ) {
    boolean successful = true;
    for (DisplayLease lease : batch.leases()) {
      try {
        removeDisplayOwned(lease.display());
      } catch (Throwable error) {
        successful = false;
        failures.record(new IllegalStateException(
            "Failed to remove Adapt private display " + lease.display().getUniqueId(), error));
      }
    }
    completion.complete(successful);
  }

  private static boolean awaitCleanup(List<CompletableFuture<Boolean>> completions, long timeoutMillis) {
    if (completions.isEmpty()) {
      return true;
    }

    CompletableFuture<Void> all = CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new));
    try {
      all.get(Math.max(1L, timeoutMillis), TimeUnit.MILLISECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      Adapt.warn("Interrupted while waiting for Adapt private display cleanup.");
      Adapt.error(error);
      return false;
    } catch (ExecutionException | TimeoutException error) {
      Adapt.warn("Adapt private display cleanup did not complete before shutdown.");
      Adapt.error(error);
      return false;
    }

    for (CompletableFuture<Boolean> completion : completions) {
      if (!completion.join()) {
        return false;
      }
    }
    return true;
  }

  private static void remove(DisplayKey key, DisplayLease lease) {
    boolean removed;
    synchronized (LOCK) {
      removed = removeLeaseLocked(key, lease);
    }
    if (removed) {
      removeDisplay(lease);
    }
  }

  private static void removeDisplay(DisplayLease lease) {
    if (!Adapt.instance.isEnabled()) {
      if (!J.isFoliaThreading() || J.isOwnedByCurrentRegion(lease.display())) {
        removeDisplayOwned(lease.display());
      }
      return;
    }
    if (!J.runEntity(lease.display(), () -> removeDisplayOwned(lease.display()))) {
      J.runAt(lease.anchor(), () -> removeDisplayOwned(lease.display()));
    }
  }

  private static void removeDisplayOwned(BlockDisplay display) {
    if (display.isValid()) {
      display.remove();
    }
  }

  private static void scheduleLeaseValidation(DisplayKey key, DisplayLease lease) {
    if (!lease.validationPending().compareAndSet(false, true)) {
      return;
    }
    boolean scheduled;
    try {
      scheduled = J.runAt(lease.anchor(), () -> {
        try {
          validateLeaseOwned(key, lease);
        } finally {
          lease.validationPending().set(false);
        }
      });
    } catch (RuntimeException | Error error) {
      lease.validationPending().set(false);
      throw error;
    }
    if (!scheduled) {
      lease.validationPending().set(false);
    }
  }

  private static boolean validateLeaseOwned(DisplayKey key, DisplayLease lease) {
    if (lease.display().isValid()) {
      return true;
    }
    synchronized (LOCK) {
      removeLeaseLocked(key, lease);
    }
    return false;
  }

  private static boolean isCurrent(DisplayKey key, DisplayLease lease) {
    synchronized (LOCK) {
      return LEASES.get(key) == lease;
    }
  }

  private static boolean reserveLocked(RequestTicket ticket) {
    UUID viewerId = ticket.viewerId();
    int viewerCount = VIEWER_COUNTS.getOrDefault(viewerId, 0);
    if (LEASES.size() + pendingReservations >= MAX_ACTIVE_DISPLAYS
        || viewerCount >= MAX_DISPLAYS_PER_VIEWER) {
      return false;
    }
    VIEWER_COUNTS.put(viewerId, viewerCount + 1);
    pendingReservations++;
    ticket.reservationHeld().set(true);
    return true;
  }

  private static void releaseReservation(RequestTicket ticket) {
    synchronized (LOCK) {
      if (finishReservationLocked(ticket)) {
        releaseLocked(ticket.viewerId());
      }
    }
  }

  private static boolean finishReservationLocked(RequestTicket ticket) {
    if (!ticket.reservationHeld().compareAndSet(true, false)) {
      return false;
    }
    pendingReservations = Math.max(0, pendingReservations - 1);
    return true;
  }

  private static void releaseLocked(UUID viewerId) {
    int remaining = VIEWER_COUNTS.getOrDefault(viewerId, 0) - 1;
    if (remaining <= 0) {
      VIEWER_COUNTS.remove(viewerId);
    } else {
      VIEWER_COUNTS.put(viewerId, remaining);
    }
  }

  private static boolean removeLeaseLocked(DisplayKey key, DisplayLease lease) {
    if (LEASES.remove(key, lease)) {
      unindexLeaseLocked(key);
      LEASED_DISPLAY_IDS.remove(lease.display().getUniqueId());
      releaseLocked(key.viewerId());
      return true;
    }
    return false;
  }

  private static void indexLeaseLocked(DisplayKey key) {
    LEASE_KEYS_BY_CHANNEL.computeIfAbsent(key.channel(), ignored -> new HashSet<>()).add(key);
    LEASE_KEYS_BY_VIEWER.computeIfAbsent(key.viewerId(), ignored -> new HashSet<>()).add(key);
    LEASE_KEYS_BY_VIEWER_CHANNEL.computeIfAbsent(
        new ViewerChannelKey(key.channel(), key.viewerId()), ignored -> new HashSet<>()).add(key);
    LEASE_KEYS_BY_VIEWER_KEY.computeIfAbsent(
        new ViewerKey(key.channel(), key.key(), key.viewerId()), ignored -> new HashSet<>()).add(key);
  }

  private static void unindexLeaseLocked(DisplayKey key) {
    removeIndexKeyLocked(LEASE_KEYS_BY_CHANNEL, key.channel(), key);
    removeIndexKeyLocked(LEASE_KEYS_BY_VIEWER, key.viewerId(), key);
    removeIndexKeyLocked(LEASE_KEYS_BY_VIEWER_CHANNEL,
        new ViewerChannelKey(key.channel(), key.viewerId()), key);
    removeIndexKeyLocked(LEASE_KEYS_BY_VIEWER_KEY,
        new ViewerKey(key.channel(), key.key(), key.viewerId()), key);
  }

  private static <K> void removeIndexKeyLocked(Map<K, Set<DisplayKey>> index, K scope, DisplayKey key) {
    Set<DisplayKey> keys = index.get(scope);
    if (keys == null) {
      return;
    }
    keys.remove(key);
    if (keys.isEmpty()) {
      index.remove(scope);
    }
  }

  private static DisplayKey displayKey(String channel, String key, Player viewer, Location location) {
    Objects.requireNonNull(channel);
    Objects.requireNonNull(key);
    Objects.requireNonNull(viewer);
    Objects.requireNonNull(location.getWorld());
    return new DisplayKey(
        channel,
        key,
        viewer.getUniqueId(),
        location.getWorld().getUID(),
        location.getBlockX(),
        location.getBlockY(),
        location.getBlockZ()
    );
  }

  private static Transformation blockTransformation() {
    return new Transformation(
        new Vector3f(-0.01F, -0.01F, -0.01F),
        new Quaternionf(),
        new Vector3f(1.02F, 1.02F, 1.02F),
        new Quaternionf()
    );
  }

  private static int clampDuration(int durationTicks) {
    return Math.max(1, Math.min(20 * 60, durationTicks));
  }

  static int remainingExpiryTicks(long expiresAtMillis, long nowMillis) {
    long remainingMillis = expiresAtMillis - nowMillis;
    if (remainingMillis <= 0L) {
      return 0;
    }
    return (int) Math.min(Integer.MAX_VALUE, (long) Math.ceil(remainingMillis / 50D));
  }

  private static GenerationStamp generationLocked(DisplayKey key) {
    return new GenerationStamp(
        globalGeneration,
        CHANNEL_GENERATIONS.getOrDefault(key.channel(), 0L),
        VIEWER_GENERATIONS.getOrDefault(key.viewerId(), 0L),
        VIEWER_CHANNEL_GENERATIONS.getOrDefault(new ViewerChannelKey(key.channel(), key.viewerId()), 0L),
        VIEWER_KEY_GENERATIONS.getOrDefault(new ViewerKey(key.channel(), key.key(), key.viewerId()), 0L)
    );
  }

  private static void finishRequest(RequestTicket ticket) {
    synchronized (LOCK) {
      if (!ticket.finished().compareAndSet(false, true)) {
        return;
      }
      ACTIVE_REQUESTS.remove(ticket);
      PendingRequest pending = PENDING_DISPLAY_REQUESTS.get(ticket.key());
      if (pending != null && pending.ticket() == ticket) {
        PENDING_DISPLAY_REQUESTS.remove(ticket.key());
      }
      if (finishReservationLocked(ticket)) {
        releaseLocked(ticket.viewerId());
      }
      UUID viewerId = ticket.viewerId();
      int remaining = PENDING_REQUESTS.getOrDefault(viewerId, 0) - 1;
      if (remaining > 0) {
        PENDING_REQUESTS.put(viewerId, remaining);
      } else {
        PENDING_REQUESTS.remove(viewerId);
        if (RETIRING_VIEWERS.contains(viewerId)) {
          clearViewerGenerationsLocked(viewerId);
        }
      }
    }
    ticket.completion().complete(null);
  }

  private static void clearViewerGenerationsLocked(UUID viewerId) {
    VIEWER_GENERATIONS.remove(viewerId);
    VIEWER_CHANNEL_GENERATIONS.keySet().removeIf(key -> viewerId.equals(key.viewerId()));
    VIEWER_KEY_GENERATIONS.keySet().removeIf(key -> viewerId.equals(key.viewerId()));
    RETIRING_VIEWERS.remove(viewerId);
  }

  private static boolean isGenerationCurrentLocked(DisplayRequest request) {
    GenerationStamp generation = request.generation();
    return generation.global() == globalGeneration
        && generation.channel() == CHANNEL_GENERATIONS.getOrDefault(request.key().channel(), 0L)
        && generation.viewer() == VIEWER_GENERATIONS.getOrDefault(request.key().viewerId(), 0L)
        && generation.viewerChannel() == VIEWER_CHANNEL_GENERATIONS.getOrDefault(
            new ViewerChannelKey(request.key().channel(), request.key().viewerId()), 0L)
        && generation.viewerKey() == VIEWER_KEY_GENERATIONS.getOrDefault(
            new ViewerKey(request.key().channel(), request.key().key(), request.key().viewerId()), 0L);
  }

  private static void scheduleOrphanPurge() {
    ChunkAddress address = ORPHAN_PURGE_QUEUE.peek();
    if (address == null || !ORPHAN_PURGE_SCHEDULED.compareAndSet(false, true)) {
      return;
    }

    Location anchor = new Location(address.world(), (address.chunkX() << 4) + 8,
        address.world().getMinHeight(), (address.chunkZ() << 4) + 8);
    if (J.runAt(anchor, ViewerDisplayDirector::drainOrphanPurge, 1)) {
      ORPHAN_PURGE_FAILURES.set(0);
      return;
    }

    ORPHAN_PURGE_SCHEDULED.set(false);
    if (ORPHAN_PURGE_FAILURES.incrementAndGet() >= MAX_ORPHAN_PURGE_ATTEMPTS) {
      ORPHAN_PURGE_QUEUE.clear();
      Adapt.error(new IllegalStateException("Failed to schedule cleanup of stale Adapt private displays."));
      return;
    }
    CompletableFuture.delayedExecutor(50L, TimeUnit.MILLISECONDS).execute(() -> {
      if (Adapt.instance.isEnabled()) {
        scheduleOrphanPurge();
      }
    });
  }

  private static void drainOrphanPurge() {
    int dispatched = 0;
    ChunkAddress address;
    while (dispatched < MAX_ORPHAN_CHUNKS_PER_TICK && (address = ORPHAN_PURGE_QUEUE.poll()) != null) {
      ChunkAddress current = address;
      Location anchor = new Location(current.world(), (current.chunkX() << 4) + 8,
          current.world().getMinHeight(), (current.chunkZ() << 4) + 8);
      if (!J.runAt(anchor, () -> purgeOrphansOwned(current))) {
        if (current.attempts() + 1 < MAX_ORPHAN_PURGE_ATTEMPTS) {
          ORPHAN_PURGE_QUEUE.add(current.retry());
        } else {
          IllegalStateException failure = new IllegalStateException(
              "Failed to clean stale Adapt private displays in chunk "
                  + current.world().getName() + "[" + current.chunkX() + "," + current.chunkZ() + "]");
          Adapt.error(failure);
        }
      }
      dispatched++;
    }

    ORPHAN_PURGE_SCHEDULED.set(false);
    scheduleOrphanPurge();
  }

  private static void purgeOrphansOwned(ChunkAddress address) {
    World world = address.world();
    if (!world.isChunkLoaded(address.chunkX(), address.chunkZ())) {
      return;
    }
    Chunk chunk = world.getChunkAt(address.chunkX(), address.chunkZ());
    for (Entity entity : chunk.getEntities()) {
      if (entity instanceof BlockDisplay display
          && display.getScoreboardTags().contains(DISPLAY_TAG)
          && !isLeased(display)) {
        removeDisplayOwned(display);
      }
    }
  }

  static boolean isLeased(BlockDisplay display) {
    synchronized (LOCK) {
      return LEASED_DISPLAY_IDS.contains(display.getUniqueId());
    }
  }

  private record DisplayKey(String channel, String key, UUID viewerId, UUID worldId,
                            int anchorX, int anchorY, int anchorZ) {
  }

  private record DisplayRequest(DisplayKey key, Player viewer, Location location, BlockData blockData,
                                Color glowColor, Transformation transformation, int durationTicks,
                                GenerationStamp generation, RequestTicket ticket) {
  }

  private record DisplayRequestSpec(DisplayKey key, Player viewer, Location location, BlockData blockData,
                                    Color glowColor, Transformation transformation, int durationTicks) {
  }

  private record GenerationStamp(long global, long channel, long viewer, long viewerChannel, long viewerKey) {
  }

  private record ViewerChannelKey(String channel, UUID viewerId) {
  }

  private record ViewerKey(String channel, String key, UUID viewerId) {
  }

  private record RequestTicket(DisplayKey key, Location anchor, AtomicBoolean reservationHeld, AtomicBoolean started,
                               AtomicBoolean finished, CompletableFuture<Void> completion) {
    private RequestTicket(DisplayKey key, Location anchor) {
      this(key, anchor, new AtomicBoolean(), new AtomicBoolean(), new AtomicBoolean(), new CompletableFuture<>());
    }

    private UUID viewerId() {
      return key.viewerId();
    }
  }

  private static final class PendingRequest {
    private final RequestTicket ticket;
    private DisplayRequest latest;

    private PendingRequest(RequestTicket ticket, DisplayRequest latest) {
      this.ticket = ticket;
      this.latest = latest;
    }

    private RequestTicket ticket() {
      return ticket;
    }

    private DisplayRequest latest() {
      return latest;
    }

    private void replace(DisplayRequest request) {
      latest = request;
    }
  }

  private record CleanupSnapshot(List<DisplayLease> leases, List<RequestTicket> requests) {
  }

  private record RegionKey(UUID worldId, int chunkX, int chunkZ) {
  }

  private record DisplayRemovalBatch(Location anchor, List<DisplayLease> leases) {
  }

  private static final class CleanupFailures {
    private final AtomicInteger count = new AtomicInteger();
    private final AtomicReference<Throwable> first = new AtomicReference<>();

    private void record(Throwable failure) {
      count.incrementAndGet();
      first.compareAndSet(null, failure);
    }

    private boolean isEmpty() {
      return count.get() == 0;
    }

    private void report() {
      int total = count.get();
      if (total == 0) {
        return;
      }
      IllegalStateException failure = new IllegalStateException(
          "Adapt private display cleanup failed for " + total + " regions or displays.", first.get());
      Adapt.warn(failure.getMessage());
      Adapt.error(failure);
    }
  }

  private record ChunkAddress(World world, int chunkX, int chunkZ, int attempts) {
    private ChunkAddress retry() {
      return new ChunkAddress(world, chunkX, chunkZ, attempts + 1);
    }
  }

  private static final class DisplayLease {
    private final BlockDisplay display;
    private final Location anchor;
    private final AtomicBoolean validationPending;
    private long generation;
    private long expiresAt;
    private int durationTicks;

    private DisplayLease(BlockDisplay display, Location anchor, int durationTicks) {
      this.display = display;
      this.anchor = anchor;
      this.validationPending = new AtomicBoolean();
      renew(durationTicks);
    }

    private BlockDisplay display() {
      return display;
    }

    private Location anchor() {
      return anchor;
    }

    private AtomicBoolean validationPending() {
      return validationPending;
    }

    private long generation() {
      return generation;
    }

    private long expiresAt() {
      return expiresAt;
    }

    private int durationTicks() {
      return durationTicks;
    }

    private void renew(int durationTicks) {
      this.durationTicks = durationTicks;
      this.expiresAt = durationTicks <= 0
          ? Long.MAX_VALUE
          : System.currentTimeMillis() + (durationTicks * 50L);
      generation++;
    }
  }
}
