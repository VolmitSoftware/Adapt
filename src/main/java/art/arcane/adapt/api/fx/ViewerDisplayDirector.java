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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public final class ViewerDisplayDirector {
  private static final int MAX_ACTIVE_DISPLAYS = 4_096;
  private static final int MAX_DISPLAYS_PER_VIEWER = 128;
  private static final int MAX_ORPHAN_CHUNKS_PER_TICK = 32;
  private static final int MAX_ORPHAN_PURGE_ATTEMPTS = 3;
  private static final String DISPLAY_TAG = "adapt_viewer_display";
  private static final Object LOCK = new Object();
  private static final Map<DisplayKey, DisplayLease> LEASES = new HashMap<>();
  private static final Map<UUID, Integer> VIEWER_COUNTS = new HashMap<>();
  private static final Map<String, Long> CHANNEL_GENERATIONS = new HashMap<>();
  private static final Map<UUID, Long> VIEWER_GENERATIONS = new HashMap<>();
  private static final Map<ViewerChannelKey, Long> VIEWER_CHANNEL_GENERATIONS = new HashMap<>();
  private static final Map<ViewerKey, Long> VIEWER_KEY_GENERATIONS = new HashMap<>();
  private static final Map<UUID, Integer> PENDING_REQUESTS = new HashMap<>();
  private static final Set<UUID> RETIRING_VIEWERS = new HashSet<>();
  private static final Queue<ChunkAddress> ORPHAN_PURGE_QUEUE = new ConcurrentLinkedQueue<>();
  private static final AtomicBoolean ORPHAN_PURGE_SCHEDULED = new AtomicBoolean();
  private static final AtomicInteger ORPHAN_PURGE_FAILURES = new AtomicInteger();
  private static int pendingReservations;
  private static long globalGeneration;

  private ViewerDisplayDirector() {
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
    ReservedGeneration reserved = reserveGeneration(channel, key, viewer.getUniqueId());
    DisplayRequest request = new DisplayRequest(
        displayKey,
        viewer,
        ownedLocation,
        ownedBlockData,
        glowColor,
        transformation,
        durationTicks,
        reserved.generation(),
        reserved.ticket()
    );
    return scheduleRequest(request);
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
    ReservedGeneration reserved = reserveGeneration(channel, key, viewer.getUniqueId());
    DisplayRequest request = new DisplayRequest(
        displayKey,
        viewer,
        ownedLocation,
        ownedBlockData,
        glowColor,
        transformation,
        clampDuration(durationTicks),
        reserved.generation(),
        reserved.ticket()
    );
    return scheduleRequest(request);
  }

  public static void clearChannel(String channel) {
    if (channel == null) {
      return;
    }
    clearMatching(key -> channel.equals(key.channel()), () -> {
      CHANNEL_GENERATIONS.merge(channel, 1L, Long::sum);
      VIEWER_CHANNEL_GENERATIONS.keySet().removeIf(key -> channel.equals(key.channel()));
      VIEWER_KEY_GENERATIONS.keySet().removeIf(key -> channel.equals(key.channel()));
    });
  }

  public static void clearViewer(String channel, UUID viewerId) {
    if (channel == null || viewerId == null) {
      return;
    }
    clearMatching(key -> channel.equals(key.channel()) && viewerId.equals(key.viewerId()), () -> {
      VIEWER_CHANNEL_GENERATIONS.merge(new ViewerChannelKey(channel, viewerId), 1L, Long::sum);
      VIEWER_KEY_GENERATIONS.keySet().removeIf(
          key -> channel.equals(key.channel()) && viewerId.equals(key.viewerId()));
    });
  }

  public static void clearViewerKey(String channel, String key, UUID viewerId) {
    if (channel == null || key == null || viewerId == null) {
      return;
    }
    clearMatching(displayKey -> channel.equals(displayKey.channel())
        && key.equals(displayKey.key())
        && viewerId.equals(displayKey.viewerId()),
        () -> VIEWER_KEY_GENERATIONS.merge(new ViewerKey(channel, key, viewerId), 1L, Long::sum));
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
    synchronized (LOCK) {
      DisplayLease lease = LEASES.get(displayKey);
      if (lease == null) {
        return false;
      }
      if (lease.display().isValid()) {
        return true;
      }
      removeLeaseLocked(displayKey, lease);
      return false;
    }
  }

  public static void clearViewer(UUID viewerId) {
    if (viewerId == null) {
      return;
    }
    clearMatching(key -> viewerId.equals(key.viewerId()), () -> {
      VIEWER_GENERATIONS.merge(viewerId, 1L, Long::sum);
      VIEWER_CHANNEL_GENERATIONS.keySet().removeIf(key -> viewerId.equals(key.viewerId()));
      VIEWER_KEY_GENERATIONS.keySet().removeIf(key -> viewerId.equals(key.viewerId()));
    });
  }

  public static void retireViewer(UUID viewerId) {
    if (viewerId == null) {
      return;
    }
    clearMatching(key -> viewerId.equals(key.viewerId()), () -> {
      RETIRING_VIEWERS.add(viewerId);
      VIEWER_GENERATIONS.merge(viewerId, 1L, Long::sum);
      VIEWER_CHANNEL_GENERATIONS.keySet().removeIf(key -> viewerId.equals(key.viewerId()));
      VIEWER_KEY_GENERATIONS.keySet().removeIf(key -> viewerId.equals(key.viewerId()));
      if (!PENDING_REQUESTS.containsKey(viewerId)) {
        clearViewerGenerationsLocked(viewerId);
      }
    });
  }

  public static void clearAll() {
    clearMatching(key -> true, () -> {
      globalGeneration++;
      CHANNEL_GENERATIONS.clear();
      VIEWER_GENERATIONS.clear();
      VIEWER_CHANNEL_GENERATIONS.clear();
      VIEWER_KEY_GENERATIONS.clear();
      PENDING_REQUESTS.clear();
      RETIRING_VIEWERS.clear();
    });
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
    if (!request.viewer().isOnline()) {
      return;
    }

    DisplayLease existing;
    synchronized (LOCK) {
      if (!isGenerationCurrentLocked(request)) {
        return;
      }
      existing = LEASES.get(request.key());
      if (existing != null && existing.display().isValid()) {
        existing.renew(request.durationTicks());
      } else {
        if (existing != null) {
          removeLeaseLocked(request.key(), existing);
        }
        if (!reserveLocked(request.key().viewerId())) {
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
      releaseReservation(request.key().viewerId());
      Adapt.warn("Failed to spawn private reveal display for " + request.key().channel() + ": "
          + error.getClass().getSimpleName()
          + (error.getMessage() == null ? "" : " - " + error.getMessage()));
      error.printStackTrace();
      return;
    }

    DisplayLease lease = new DisplayLease(display, request.location().clone(), request.durationTicks());
    boolean accepted;
    synchronized (LOCK) {
      pendingReservations = Math.max(0, pendingReservations - 1);
      accepted = isGenerationCurrentLocked(request);
      DisplayLease replaced = accepted ? LEASES.put(request.key(), lease) : null;
      if (!accepted) {
        releaseLocked(request.key().viewerId());
      } else if (replaced != null) {
        releaseLocked(request.key().viewerId());
        removeDisplay(replaced);
      }
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

  private static boolean scheduleRequest(DisplayRequest request) {
    boolean scheduled;
    try {
      scheduled = J.runAt(request.location(), () -> {
        try {
          spawnOrRenewOwned(request);
        } finally {
          finishRequest(request.ticket());
        }
      });
    } catch (RuntimeException | Error error) {
      finishRequest(request.ticket());
      throw error;
    }
    if (!scheduled) {
      finishRequest(request.ticket());
    }
    return scheduled;
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

  private static void clearMatching(Predicate<DisplayKey> predicate, Runnable invalidation) {
    List<Map.Entry<DisplayKey, DisplayLease>> removed = new ArrayList<>();
    synchronized (LOCK) {
      invalidation.run();
      for (Map.Entry<DisplayKey, DisplayLease> entry : LEASES.entrySet()) {
        if (predicate.test(entry.getKey())) {
          removed.add(Map.entry(entry.getKey(), entry.getValue()));
        }
      }
      for (Map.Entry<DisplayKey, DisplayLease> entry : removed) {
        removeLeaseLocked(entry.getKey(), entry.getValue());
      }
    }
    for (Map.Entry<DisplayKey, DisplayLease> entry : removed) {
      removeDisplay(entry.getValue());
    }
  }

  private static void remove(DisplayKey key, DisplayLease lease) {
    boolean removed;
    synchronized (LOCK) {
      removed = LEASES.remove(key, lease);
      if (removed) {
        releaseLocked(key.viewerId());
      }
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

  private static boolean isCurrent(DisplayKey key, DisplayLease lease) {
    synchronized (LOCK) {
      return LEASES.get(key) == lease;
    }
  }

  private static boolean reserveLocked(UUID viewerId) {
    int viewerCount = VIEWER_COUNTS.getOrDefault(viewerId, 0);
    if (LEASES.size() + pendingReservations >= MAX_ACTIVE_DISPLAYS
        || viewerCount >= MAX_DISPLAYS_PER_VIEWER) {
      return false;
    }
    VIEWER_COUNTS.put(viewerId, viewerCount + 1);
    pendingReservations++;
    return true;
  }

  private static void releaseReservation(UUID viewerId) {
    synchronized (LOCK) {
      pendingReservations = Math.max(0, pendingReservations - 1);
      releaseLocked(viewerId);
    }
  }

  private static void releaseLocked(UUID viewerId) {
    int remaining = VIEWER_COUNTS.getOrDefault(viewerId, 0) - 1;
    if (remaining <= 0) {
      VIEWER_COUNTS.remove(viewerId);
    } else {
      VIEWER_COUNTS.put(viewerId, remaining);
    }
  }

  private static void removeLeaseLocked(DisplayKey key, DisplayLease lease) {
    if (LEASES.remove(key, lease)) {
      releaseLocked(key.viewerId());
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

  private static ReservedGeneration reserveGeneration(String channel, String key, UUID viewerId) {
    synchronized (LOCK) {
      PENDING_REQUESTS.merge(viewerId, 1, Integer::sum);
      GenerationStamp generation = new GenerationStamp(
          globalGeneration,
          CHANNEL_GENERATIONS.getOrDefault(channel, 0L),
          VIEWER_GENERATIONS.getOrDefault(viewerId, 0L),
          VIEWER_CHANNEL_GENERATIONS.getOrDefault(new ViewerChannelKey(channel, viewerId), 0L),
          VIEWER_KEY_GENERATIONS.getOrDefault(new ViewerKey(channel, key, viewerId), 0L)
      );
      return new ReservedGeneration(generation, new RequestTicket(viewerId));
    }
  }

  private static void finishRequest(RequestTicket ticket) {
    if (!ticket.finished().compareAndSet(false, true)) {
      return;
    }
    synchronized (LOCK) {
      UUID viewerId = ticket.viewerId();
      int remaining = PENDING_REQUESTS.getOrDefault(viewerId, 0) - 1;
      if (remaining > 0) {
        PENDING_REQUESTS.put(viewerId, remaining);
        return;
      }
      PENDING_REQUESTS.remove(viewerId);
      if (RETIRING_VIEWERS.contains(viewerId)) {
        clearViewerGenerationsLocked(viewerId);
      }
    }
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
      new IllegalStateException("Failed to schedule cleanup of stale Adapt private displays.").printStackTrace();
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
          failure.printStackTrace();
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

  private static boolean isLeased(BlockDisplay display) {
    synchronized (LOCK) {
      for (DisplayLease lease : LEASES.values()) {
        if (lease.display().getUniqueId().equals(display.getUniqueId())) {
          return true;
        }
      }
      return false;
    }
  }

  private record DisplayKey(String channel, String key, UUID viewerId, UUID worldId,
                            int anchorX, int anchorY, int anchorZ) {
  }

  private record DisplayRequest(DisplayKey key, Player viewer, Location location, BlockData blockData,
                                Color glowColor, Transformation transformation, int durationTicks,
                                GenerationStamp generation, RequestTicket ticket) {
  }

  private record ReservedGeneration(GenerationStamp generation, RequestTicket ticket) {
  }

  private record GenerationStamp(long global, long channel, long viewer, long viewerChannel, long viewerKey) {
  }

  private record ViewerChannelKey(String channel, UUID viewerId) {
  }

  private record ViewerKey(String channel, String key, UUID viewerId) {
  }

  private record RequestTicket(UUID viewerId, AtomicBoolean finished) {
    private RequestTicket(UUID viewerId) {
      this(viewerId, new AtomicBoolean());
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
    private long generation;
    private long expiresAt;
    private int durationTicks;

    private DisplayLease(BlockDisplay display, Location anchor, int durationTicks) {
      this.display = display;
      this.anchor = anchor;
      renew(durationTicks);
    }

    private BlockDisplay display() {
      return display;
    }

    private Location anchor() {
      return anchor;
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
