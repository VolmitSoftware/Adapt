package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Teleports the owner's nearest eligible pet to a safe spot beside them and puts the tool on cooldown. */
public final class TameRecall {
  private static final int HARD_MAX_CANDIDATES_PER_ACTIVATION = 32;
  private static final int HARD_MAX_AFFECTED_PER_ACTIVATION = 1;

  private final Adaptation<?> owner;
  private final Map<UUID, Long> pendingRecalls;
  private final Consumer<PlayerRef> onRecall;

  /** {@code onRecall} runs on the owner's thread after every completed recall. */
  public TameRecall(Adaptation<?> owner, Map<UUID, Long> pendingRecalls, Consumer<PlayerRef> onRecall) {
    this.owner = owner;
    this.pendingRecalls = pendingRecalls;
    this.onRecall = onRecall;
  }

  public record Tuning(Id tool, double radius, double minDistanceSquared, int cooldownTicks, int hungerCost,
                       int candidateLimit, int affectedLimit) {
  }

  /** Starts a recall for a player at {@code level}; returns false when nothing started and the click should pass. */
  public boolean recall(PlayerRef p, int level, Tuning tuning) {
    int hungerCost = Math.max(0, tuning.hungerCost());
    if (!affords(owner, p, hungerCost)) {
      return false;
    }

    if (tuning.affectedLimit() <= 0) {
      return false;
    }

    double radius = tuning.radius();
    Loc origin = p.location();
    List<LivingRef> candidates = collectCandidates(origin, radius, tuning.candidateLimit());
    if (candidates.isEmpty()) {
      return false;
    }

    UUID playerId = p.uuid();
    long token = System.nanoTime();
    if (pendingRecalls.putIfAbsent(playerId, token) != null) {
      return false;
    }

    RecallScan scan = new RecallScan(p, origin, radius * radius, tuning, hungerCost, token, candidates.size());
    for (LivingRef tameable : candidates) {
      if (!AdaptApi.scheduler().runEntity(tameable, () -> inspectCandidateOwned(scan, tameable))) {
        scan.complete();
      }
    }
    AdaptApi.scheduler().runEntity(p, () -> clearPending(scan), 20);
    return true;
  }

  /** Whether paying {@code hungerCost} leaves the player the hunger reserve they chose for {@code adaptation}. */
  public static boolean affords(Adaptation<?> adaptation, PlayerRef p, int hungerCost) {
    return adaptation.preference(p, TamingPreferences.HUNGER).permits(p.foodLevel(), hungerCost);
  }

  /** A main-hand right click that the server has not denied. */
  public static boolean isRecallGesture(PlayerInteractEvent e) {
    if (e.hand() != Hand.MAIN_HAND) {
      return false;
    }

    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_AIR && action != InteractAction.RIGHT_CLICK_BLOCK) {
      return false;
    }

    return e.clickedBlock() != null
        ? e.useBlock() != UseResult.DENY
        : e.useItem() != UseResult.DENY;
  }

  public static int candidateLimit(int configured) {
    return Math.max(1, Math.min(configured, HARD_MAX_CANDIDATES_PER_ACTIVATION));
  }

  public static int affectedLimit(int configured) {
    return Math.max(0, Math.min(configured, HARD_MAX_AFFECTED_PER_ACTIVATION));
  }

  static boolean successfulRecallTeleport(Boolean success, Throwable failure) {
    return failure == null && Boolean.TRUE.equals(success);
  }

  private List<LivingRef> collectCandidates(Loc origin, double radius, int limit) {
    List<LivingRef> candidates = new ArrayList<>(limit);
    if (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().ownsArea(origin, radius, radius)) {
      return candidates;
    }
    for (EntityRef entity : origin.world().entitiesIn(Box.around(origin.position(), radius, radius, radius),
        candidate -> candidate instanceof LivingRef && candidate.has(EntityTrait.TAMEABLE))) {
      candidates.add((LivingRef) entity);
      if (candidates.size() >= limit) {
        break;
      }
    }
    return candidates;
  }

  private void inspectCandidateOwned(RecallScan scan, LivingRef tameable) {
    if (!tameable.isValid() || tameable.isDead() || !AdaptApi.platform().host().animals().isTamed(tameable)
        || !scan.petTypes.accepts(tameable.type())
        || (!scan.includeSitting && AdaptApi.platform().host().animals().isSitting(tameable))) {
      scan.complete();
      return;
    }

    UUID tameOwner = tameable.tameOwner();
    if (tameOwner == null || !scan.playerId.equals(tameOwner)) {
      scan.complete();
      return;
    }

    Loc location = tameable.location();
    if (!location.sameWorld(scan.origin)) {
      scan.complete();
      return;
    }

    double distanceSquared = location.distanceSquared(scan.origin);
    if (distanceSquared > scan.tuning.minDistanceSquared() && distanceSquared <= scan.radiusSquared) {
      scan.consider(tameable, distanceSquared);
    }
    scan.complete();
  }

  private void finishScanOwned(RecallScan scan) {
    if (!isPending(scan)
        || !scan.player.isOnline()
        || owner.getActiveLevel(scan.player) <= 0
        || scan.player.hasCooldown(scan.tuning.tool())) {
      clearPending(scan);
      return;
    }

    RecallCandidate nearest = scan.nearest.get();
    Loc safe = findSafeRecallLocation(scan.player);
    if (nearest == null || safe == null || !owner.canPVE(scan.player, safe)) {
      clearPending(scan);
      return;
    }

    if (!AdaptApi.scheduler().runEntity(nearest.tameable(), () -> recallOwned(scan, nearest.tameable(), safe))) {
      clearPending(scan);
    }
  }

  private void recallOwned(RecallScan scan, LivingRef tameable, Loc safe) {
    if (!isPending(scan) || !tameable.isValid() || tameable.isDead()
        || !AdaptApi.platform().host().animals().isTamed(tameable)) {
      clearPending(scan);
      return;
    }

    UUID tameOwner = tameable.tameOwner();
    if (tameOwner == null || !scan.playerId.equals(tameOwner)) {
      clearPending(scan);
      return;
    }

    Loc from = tameable.location();
    if (from.sameWorld(safe) && from.distanceSquared(safe) <= scan.tuning.minDistanceSquared()) {
      clearPending(scan);
      return;
    }

    beginRecallTeleport(scan, tameable, from, safe);
  }

  private void beginRecallTeleport(RecallScan scan, LivingRef tameable, Loc from, Loc safe) {
    CompletableFuture<Boolean> teleport;
    try {
      teleport = tameable.teleport(safe, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      AdaptApi.log().error("Beast Recall could not start teleport for " + tameable.uuid() + ".", null);
      AdaptApi.log().error("TameRecall failed", error);
      clearPending(scan);
      return;
    }
    if (teleport == null) {
      clearPending(scan);
      return;
    }
    teleport.whenComplete((success, failure) ->
        completeRecallTeleport(scan, tameable, from, safe, success, failure));
  }

  private void completeRecallTeleport(RecallScan scan, LivingRef tameable, Loc from, Loc safe,
                                      Boolean success, Throwable failure) {
    if (failure != null) {
      AdaptApi.log().error("Beast Recall teleport failed for " + tameable.uuid() + ".", null);
      AdaptApi.log().error("TameRecall failed", failure);
    }
    if (!successfulRecallTeleport(success, failure)) {
      clearPending(scan);
      return;
    }
    if (!AdaptApi.scheduler().runEntity(tameable, () -> finishRecallTeleportOwned(scan, tameable, from, safe))) {
      clearPending(scan);
      AdaptApi.log().warn("Beast Recall completed teleport but could not schedule completion for "
          + tameable.uuid() + ".");
    }
  }

  private void finishRecallTeleportOwned(RecallScan scan, LivingRef tameable, Loc from, Loc safe) {
    if (!isPending(scan) || !tameable.isValid() || tameable.isDead()) {
      clearPending(scan);
      return;
    }

    tameable.setFallDistance(0);
    if (!AdaptApi.scheduler().runEntity(scan.player, () -> completeRecallOwned(scan, from, safe))) {
      clearPending(scan);
    }
  }

  private void completeRecallOwned(RecallScan scan, Loc from, Loc safe) {
    if (!isPending(scan)) {
      return;
    }
    clearPending(scan);
    if (!scan.player.isOnline()) {
      return;
    }

    int cooldownTicks = scan.tuning.cooldownTicks();
    scan.player.setCooldown(scan.tuning.tool(), cooldownTicks);
    HudStatus.cooldown(scan.player, owner, cooldownTicks * 50L);
    if (scan.hungerCost > 0) {
      scan.player.setFoodLevel(Math.max(0, scan.player.foodLevel() - scan.hungerCost));
    }

    TamingBeastRecall.recallFx(owner, from, safe);
    onRecall.accept(scan.player);
  }

  private void clearPending(RecallScan scan) {
    pendingRecalls.remove(scan.playerId, scan.token);
  }

  private boolean isPending(RecallScan scan) {
    Long current = pendingRecalls.get(scan.playerId);
    return current != null && current == scan.token;
  }

  private static Loc findSafeRecallLocation(PlayerRef p) {
    Loc base = p.location();
    int[][] offsets = {
        {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
        {2, 0}, {-2, 0}, {0, 2}, {0, -2}
    };

    for (int y = 0; y <= 2; y++) {
      for (int[] offset : offsets) {
        Loc candidate = base.add(offset[0], y, offset[1]);
        if (isSafeSpot(candidate)) {
          return candidate;
        }
      }
    }

    return null;
  }

  private static boolean isSafeSpot(Loc location) {
    WorldRef world = location.world();
    BlockRef feet = world.blockAt(location);
    BlockRef head = world.blockAt(location.add(0, 1, 0));
    BlockRef below = world.blockAt(location.add(0, -1, 0));
    return feet.isPassable() && head.isPassable() && below.isSolid();
  }

  private final class RecallScan {
    private final TamingPreferences.Pets petTypes;
    private final boolean includeSitting;
    private final PlayerRef player;
    private final UUID playerId;
    private final Loc origin;
    private final double radiusSquared;
    private final Tuning tuning;
    private final int hungerCost;
    private final long token;
    private final AtomicInteger remaining;
    private final AtomicReference<RecallCandidate> nearest = new AtomicReference<>();
    private final AtomicBoolean finalized = new AtomicBoolean();

    private RecallScan(PlayerRef player, Loc origin, double radiusSquared, Tuning tuning, int hungerCost, long token,
                       int candidates) {
      this.player = player;
      petTypes = owner.preference(player, TamingPreferences.PETS);
      includeSitting = owner.preferenceEnabled(player, TamingPreferences.SITTING);
      playerId = player.uuid();
      this.origin = origin;
      this.radiusSquared = radiusSquared;
      this.tuning = tuning;
      this.hungerCost = hungerCost;
      this.token = token;
      remaining = new AtomicInteger(candidates);
    }

    private void consider(LivingRef tameable, double distanceSquared) {
      RecallCandidate observed = nearest.get();
      while (observed == null || distanceSquared < observed.distanceSquared()) {
        RecallCandidate replacement = new RecallCandidate(tameable, distanceSquared);
        if (nearest.compareAndSet(observed, replacement)) {
          return;
        }
        observed = nearest.get();
      }
    }

    private void complete() {
      if (remaining.decrementAndGet() != 0 || !finalized.compareAndSet(false, true)) {
        return;
      }
      if (!AdaptApi.scheduler().runEntity(player, () -> finishScanOwned(this))) {
        clearPending(this);
      }
    }
  }

  private record RecallCandidate(LivingRef tameable, double distanceSquared) {
  }
}
