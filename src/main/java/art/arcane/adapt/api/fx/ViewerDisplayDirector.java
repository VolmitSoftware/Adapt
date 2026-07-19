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
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ViewerDisplayDirector {
  private static final int MAX_ACTIVE_DISPLAYS = 4_096;
  private static final int MAX_DISPLAYS_PER_VIEWER = 128;
  private static final Object LOCK = new Object();
  private static final Map<DisplayKey, DisplayLease> LEASES = new HashMap<>();
  private static final Map<UUID, Integer> VIEWER_COUNTS = new HashMap<>();
  private static final Map<String, Long> CHANNEL_GENERATIONS = new HashMap<>();
  private static int pendingReservations;
  private static long globalGeneration;

  private ViewerDisplayDirector() {
  }

  public static boolean showBlock(String channel, String key, Player viewer, Location location,
                                  BlockData blockData, Color glowColor, int durationTicks) {
    GenerationStamp generation = currentGeneration(channel);
    DisplayRequest request = new DisplayRequest(
        displayKey(channel, key, viewer, location),
        viewer,
        location.clone(),
        blockData.clone(),
        glowColor,
        blockTransformation(),
        clampDuration(durationTicks),
        generation
    );
    return J.runAt(request.location(), () -> spawnOrRenewOwned(request));
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

    double safeThickness = Math.max(0.015D, Math.min(0.5D, thickness));
    GenerationStamp generation = currentGeneration(channel);
    DisplayRequest request = new DisplayRequest(
        displayKey(channel, key, viewer, start),
        viewer,
        start.clone(),
        blockData.clone(),
        glowColor,
        lineTransformation(delta, safeThickness),
        clampDuration(durationTicks),
        generation
    );
    return J.runAt(request.location(), () -> spawnOrRenewOwned(request));
  }

  public static void clearChannel(String channel) {
    if (channel == null) {
      return;
    }
    synchronized (LOCK) {
      CHANNEL_GENERATIONS.merge(channel, 1L, Long::sum);
    }
    clearMatching(key -> channel.equals(key.channel()));
  }

  public static void clearViewer(String channel, UUID viewerId) {
    if (channel == null || viewerId == null) {
      return;
    }
    clearMatching(key -> channel.equals(key.channel()) && viewerId.equals(key.viewerId()));
  }

  public static void clearViewer(UUID viewerId) {
    if (viewerId == null) {
      return;
    }
    clearMatching(key -> viewerId.equals(key.viewerId()));
  }

  public static void clearAll() {
    synchronized (LOCK) {
      globalGeneration++;
    }
    clearMatching(key -> true);
  }

  static boolean isRenderableLine(Vector delta) {
    return delta != null
        && Double.isFinite(delta.getX())
        && Double.isFinite(delta.getY())
        && Double.isFinite(delta.getZ())
        && delta.lengthSquared() > 1.0E-8D;
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
    long generation = lease.generation();
    int delayTicks = lease.durationTicks();
    if (!J.runEntity(lease.display(), () -> expireOwned(key, lease, generation), delayTicks)) {
      remove(key, lease);
    }
  }

  private static void expireOwned(DisplayKey key, DisplayLease lease, long generation) {
    if (!isCurrent(key, lease)) {
      removeDisplayOwned(lease.display());
      return;
    }
    if (lease.generation() != generation || System.currentTimeMillis() < lease.expiresAt()) {
      return;
    }
    remove(key, lease);
  }

  private static void clearMatching(java.util.function.Predicate<DisplayKey> predicate) {
    List<Map.Entry<DisplayKey, DisplayLease>> removed = new ArrayList<>();
    synchronized (LOCK) {
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

  private static GenerationStamp currentGeneration(String channel) {
    synchronized (LOCK) {
      return new GenerationStamp(globalGeneration, CHANNEL_GENERATIONS.getOrDefault(channel, 0L));
    }
  }

  private static boolean isGenerationCurrentLocked(DisplayRequest request) {
    GenerationStamp generation = request.generation();
    return generation.global() == globalGeneration
        && generation.channel() == CHANNEL_GENERATIONS.getOrDefault(request.key().channel(), 0L);
  }

  private record DisplayKey(String channel, String key, UUID viewerId, UUID worldId,
                            int anchorX, int anchorY, int anchorZ) {
  }

  private record DisplayRequest(DisplayKey key, Player viewer, Location location, BlockData blockData,
                                Color glowColor, Transformation transformation, int durationTicks,
                                GenerationStamp generation) {
  }

  private record GenerationStamp(long global, long channel) {
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
      this.expiresAt = System.currentTimeMillis() + (durationTicks * 50L);
      generation++;
    }
  }
}
