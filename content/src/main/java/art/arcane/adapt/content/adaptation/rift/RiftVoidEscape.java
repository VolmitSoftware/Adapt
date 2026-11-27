package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.content.DeathSaveSlot;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Void Skin's escape: lethal damage is cancelled and the player is teleported to a nearby safe spot, or the world
 * spawn, for one plain ender pearl that is held in reserve until the teleport lands and refunded when it fails.
 */
public final class RiftVoidEscape {
  public static final DeathSaveSlot DEATH_SAVE = DeathSaveSlot.of(Id.adapt("rift_void_skin"));
  private static final double HARD_MAX_SEARCH_RADIUS = 16D;
  private static final int SAFE_SAMPLES = 32;

  private final SimpleAdaptation<?> owner;
  private final Consumer<PlayerRef> onEscape;
  private final Map<UUID, VoidEscape> pendingEscapes = new ConcurrentHashMap<>();
  private final DataKey<String> pearlReservationKey = DataKey.adapt("rift_void_skin_reserved_pearl", DataCodecs.STRING);
  private final AtomicBoolean acceptingEscapes = new AtomicBoolean(true);

  /**
   * One escape attempt. {@code reserve} pearls are never spent; the search keeps between the two radii; resistance
   * is applied on arrival.
   */
  public record Plan(int reserve, boolean spawnFallback, double searchRadius, double minRadius,
                     long cooldownMillis, int resistanceTicks, int resistanceAmplifier) {
  }

  /** {@code onEscape} runs on the player's thread once an escape lands and its pearl is paid. */
  public RiftVoidEscape(SimpleAdaptation<?> owner, Consumer<PlayerRef> onEscape) {
    this.owner = owner;
    this.onEscape = onEscape;
  }

  /** Whether the player is mid-escape, so lethal hits must not land. */
  public boolean isEscaping(PlayerRef player) {
    return pendingEscapes.containsKey(player.uuid());
  }

  public boolean isAccepting() {
    return acceptingEscapes.get();
  }

  public void activate() {
    acceptingEscapes.set(true);
    for (PlayerRef player : AdaptApi.platform().host().onlinePlayers()) {
      AdaptApi.scheduler().runEntity(player, () -> restoreStrandedReservation(player));
    }
  }

  public void deactivate() {
    acceptingEscapes.set(false);
    List<VoidEscape> operations = new ArrayList<>(pendingEscapes.values());
    pendingEscapes.clear();
    for (VoidEscape operation : operations) {
      PlayerRef player = AdaptApi.platform().host().player(operation.playerId());
      if (player != null) {
        AdaptApi.scheduler().runEntity(player, () -> refundReservation(
            player,
            operation.reservation(),
            AbilityRefundReason.ADAPTATION_DISABLED
        ));
      }
    }
  }

  /** Tries to escape a lethal hit; the hit is cancelled once the escape is reserved. */
  public void attempt(PlayerRef p, EntityDamageEvent e, Plan plan) {
    UUID id = p.uuid();
    if (pendingEscapes.containsKey(id)) {
      e.setCancelled(true);
      return;
    }
    long cooldownMillis = plan.cooldownMillis();
    long now = System.currentTimeMillis();
    long remainingCooldown = RiftVoidEscape.DEATH_SAVE.remaining(p, now);
    if (remainingCooldown > 0L) {
      AdaptApi.log().verbose("Void Skin skipped " + id + ": "
          + remainingCooldown + "ms cooldown left.");
      return;
    }

    if (!hasPlainPearl(p, plan.reserve())) {
      AdaptApi.log().verbose("Void Skin skipped " + id + ": no plain ender pearl in inventory.");
      return;
    }

    Loc destination = findSafeSpot(p, plan);
    if (destination == null) {
      AdaptApi.log().verbose("Void Skin skipped " + id + ": no safe spot and no usable world spawn.");
      return;
    }

    Loc origin = p.location();
    destination = destination.withRotation(origin.yaw(), origin.pitch());
    PearlReservation reservation = reservePlainPearl(p, plan.reserve());
    if (reservation == null) {
      AdaptApi.log().verbose("Void Skin skipped " + id + ": pearl cost was refused.");
      return;
    }

    if (!RiftVoidEscape.DEATH_SAVE.activate(p, now, cooldownMillis)) {
      refundReservation(p, reservation, AbilityRefundReason.ACTIVATION_FAILED);
      return;
    }
    HudStatus.cooldown(id, owner, now, cooldownMillis);
    VoidEscape operation = new VoidEscape(id, origin, plan, reservation);
    pendingEscapes.put(id, operation);
    // Survive first. The promise is that lethal damage never kills while the
    // pearl is banked, so the kill is cancelled before the teleport gets a
    // chance to fail to even start.
    RiftVoidSkin.cancelLethalDamage(e, true);

    CompletableFuture<Boolean> teleport;
    try {
      RiftTeleports.blinking(p, destination);
      teleport = p.teleport(destination, TeleportCause.PLUGIN);
    } catch (RuntimeException exception) {
      pendingEscapes.remove(id, operation);
      refundReservation(p, reservation, AbilityRefundReason.ACTIVATION_FAILED);
      AdaptApi.log().error("RiftVoidEscape failed", exception);
      AdaptApi.log().error("Void Skin could not start an escape teleport for " + id + ".", null);
      return;
    }

    if (teleport == null) {
      pendingEscapes.remove(id, operation);
      refundReservation(p, reservation, AbilityRefundReason.ACTIVATION_FAILED);
      AdaptApi.log().error("Void Skin got no escape teleport future for " + id + ".", null);
      return;
    }

    teleport.whenComplete((Boolean success, Throwable failure) -> finishEscape(p, operation, success, failure));
  }

  public void quit(PlayerRef player) {
    VoidEscape operation = pendingEscapes.remove(player.uuid());
    if (operation != null) {
      refundReservation(player, operation.reservation(), AbilityRefundReason.PLAYER_LEFT);
    }
  }

  public void restoreStrandedReservation(PlayerRef player) {
    String token = player.data().get(pearlReservationKey);
    if (token == null) {
      return;
    }
    try {
      returnPearl(player);
      clearReservation(player, token);
    } catch (Throwable error) {
      AdaptApi.log().error("Void Skin could not recover a reserved ender pearl for " + player.uuid() + ".", null);
      AdaptApi.log().error("RiftVoidEscape failed", error);
    }
  }

  private void finishEscape(PlayerRef p, VoidEscape operation, Boolean success, Throwable failure) {
    if (failure != null) {
      AdaptApi.log().error("RiftVoidEscape failed", failure);
      AdaptApi.log().error("Void Skin escape teleport failed for " + operation.playerId() + ".", null);
    }

    if (!AdaptApi.scheduler().runEntity(p, () -> {
      if (!pendingEscapes.remove(operation.playerId(), operation)) {
        return;
      }
      if (failure != null || !Boolean.TRUE.equals(success) || !p.isOnline()) {
        refundReservation(p, operation.reservation(), AbilityRefundReason.ACTIVATION_FAILED);
        return;
      }

      if (!settleReservation(p, operation.reservation())) {
        return;
      }
      p.setFallDistance(0f);
      p.addEffect(EffectInstance.of(RiftVoidSkin.RESISTANCE, operation.plan().resistanceTicks(), operation.plan().resistanceAmplifier()).withAmbient(true).withParticles(false).withIcon(false));
      Loc actualDestination = p.location();
      RiftVoidSkin.escapeFx(owner, p, operation.origin(), actualDestination);
      onEscape.accept(p);
    })) {
      if (pendingEscapes.remove(operation.playerId(), operation)
          && operation.reservation().resolved().compareAndSet(false, true)) {
        owner.refundCost(operation.reservation().charge().activationId(), AbilityRefundReason.ACTIVATION_FAILED);
        AdaptApi.log().error("Void Skin stranded an escape for " + operation.playerId()
            + "; the reserved pearl returns on rejoin.", null);
      }
    }
  }

  private Loc findSafeSpot(PlayerRef p, Plan plan) {
    Loc origin = p.location();
    double maxRadius = Math.max(3D, Math.min(HARD_MAX_SEARCH_RADIUS, plan.searchRadius()));
    double minRadius = Math.min(maxRadius - 1D, Math.max(2D, plan.minRadius()));
    Loc best = null;
    double bestDistance = -1D;
    for (int i = 0; i < SAFE_SAMPLES; i++) {
      double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2D);
      double distance = minRadius + ThreadLocalRandom.current().nextDouble(maxRadius - minRadius);
      double dx = Math.cos(angle) * distance;
      double dz = Math.sin(angle) * distance;
      Loc feet = origin.add(dx, 0, dz);
      if (!AdaptApi.scheduler().owns(feet)) {
        continue;
      }
      Loc resolved = resolveStandable(feet.world().blockAt(feet), 4);
      if (resolved == null) {
        continue;
      }
      double resolvedDistance = resolved.distanceSquared(origin);
      if (resolvedDistance > bestDistance) {
        bestDistance = resolvedDistance;
        best = resolved;
      }
    }
    return best != null ? best : plan.spawnFallback() ? RiftVoidSkin.worldSpawnFallback(p.world()) : null;
  }

  private static Loc resolveStandable(BlockRef base, int vertical) {
    for (int dy = 0; dy <= vertical; dy++) {
      BlockRef up = base.relative(0, dy, 0);
      if (isStandable(up)) {
        return up.location().add(0.5, 0, 0.5);
      }
      BlockRef down = base.relative(0, -dy, 0);
      if (isStandable(down)) {
        return down.location().add(0.5, 0, 0.5);
      }
    }
    return null;
  }

  private static boolean isStandable(BlockRef feet) {
    BlockRef head = feet.relative(BlockFace.UP);
    BlockRef ground = feet.relative(BlockFace.DOWN);
    return feet.isPassable()
        && head.isPassable()
        && ground.isSolid()
        && !RiftVoidSkin.HAZARDS.contains(ground.type())
        && !RiftVoidSkin.HAZARDS.contains(feet.type())
        && !RiftVoidSkin.HAZARDS.contains(head.type());
  }

  private static boolean hasPlainPearl(PlayerRef p, int reserve) {
    return RiftPearls.plainPearls(p) > reserve;
  }

  private PearlReservation reservePlainPearl(PlayerRef p, int reserve) {
    if (!hasPlainPearl(p, reserve)) {
      return null;
    }

    String token = UUID.randomUUID().toString();
    AtomicBoolean defaultConsumed = new AtomicBoolean();
    ItemRef unit = AdaptApi.platform().host().items().create(RiftVoidSkin.ENDER_PEARL, 1);
    AbilityCharge charge = owner.payItemCostDeferred(p, "pearl", unit, 1, () -> {
      if (!RiftPearls.takePlainPearl(p)) {
        return false;
      }
      p.data().set(pearlReservationKey, token);
      defaultConsumed.set(true);
      return true;
    });
    return charge.allowed()
        ? new PearlReservation(charge, token, defaultConsumed.get(), new AtomicBoolean())
        : null;
  }

  private void refundReservation(PlayerRef p, PearlReservation reservation, AbilityRefundReason reason) {
    if (!reservation.resolved().compareAndSet(false, true)) {
      return;
    }
    owner.refundCost(reservation.charge().activationId(), reason);
    if (!reservation.defaultConsumed() || !ownsReservation(p, reservation.token())) {
      return;
    }

    try {
      returnPearl(p);
      clearReservation(p, reservation.token());
    } catch (Throwable error) {
      AdaptApi.log().error("Void Skin could not return a reserved ender pearl to " + p.uuid() + ".", null);
      AdaptApi.log().error("RiftVoidEscape failed", error);
    }
  }

  private boolean settleReservation(PlayerRef player, PearlReservation reservation) {
    if (!reservation.resolved().compareAndSet(false, true)) {
      return false;
    }
    if (reservation.defaultConsumed() && !ownsReservation(player, reservation.token())) {
      owner.refundCost(reservation.charge().activationId(), AbilityRefundReason.ACTIVATION_FAILED);
      return false;
    }
    boolean settled = owner.settleCost(reservation.charge().activationId());
    if (!RiftVoidSkin.acceptSettlement(
        reservation.defaultConsumed(),
        reservation.charge().defaultCostSuppressed(),
        settled
    )) {
      owner.refundCost(reservation.charge().activationId(), AbilityRefundReason.ACTIVATION_FAILED);
      return false;
    }
    if (reservation.defaultConsumed()) {
      clearReservation(player, reservation.token());
    }
    return true;
  }

  private static void returnPearl(PlayerRef player) {
    for (ItemRef leftover : player.inventory().addItem(AdaptApi.platform().host().items().create(RiftVoidSkin.ENDER_PEARL, 1))) {
      player.world().dropItemNaturally(player.location().position(), leftover);
    }
  }

  private boolean ownsReservation(PlayerRef player, String token) {
    return token.equals(player.data().get(pearlReservationKey));
  }

  private void clearReservation(PlayerRef player, String token) {
    if (ownsReservation(player, token)) {
      player.data().remove(pearlReservationKey);
    }
  }

  private record PearlReservation(AbilityCharge charge, String token, boolean defaultConsumed, AtomicBoolean resolved) {
  }

  private record VoidEscape(UUID playerId, Loc origin, Plan plan, PearlReservation reservation) {
  }
}
