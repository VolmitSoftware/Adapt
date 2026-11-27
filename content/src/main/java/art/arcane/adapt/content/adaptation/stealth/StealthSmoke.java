package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Smoke Pellet's cloud: pulses that blind everything inside, hide and conceal the players in it, and make mobs drop
 * their aggression on anyone concealed.
 */
public final class StealthSmoke {
  public static final int PULSE_INTERVAL_TICKS = 10;
  private static final int BLIND_TICKS = PULSE_INTERVAL_TICKS + 30;
  private static final int INVISIBILITY_TICKS = PULSE_INTERVAL_TICKS + 30;
  private static final int MAX_TARGET_HANDOFFS_PER_TICK = 24;
  private static final double AGGRO_CLEAR_RADIUS = 64D;
  private static final Id BLINDNESS = Id.minecraft("blindness");
  private static final Id INVISIBILITY = Id.minecraft("invisibility");
  private static final Id WARDEN = Id.minecraft("warden");

  private final PulseFx pulseFx;
  private final ConcurrentHashMap<UUID, Long> concealmentLeases = new ConcurrentHashMap<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicLong concealmentSequence = new AtomicLong();

  public StealthSmoke(PulseFx pulseFx) {
    this.pulseFx = pulseFx;
  }

  public void release(Loc center, double radius, int pulses) {
    CloudState cloud = new CloudState(center, radius, pulses);
    AdaptApi.scheduler().runAt(center, () -> cloudPulse(cloud));
  }

  public boolean isConcealed(PlayerRef player) {
    return concealmentLeases.containsKey(player.uuid());
  }

  /** Stops mobs from targeting a concealed player and drops their aggression. */
  public void onTarget(EntityTargetEvent e) {
    if (!(e.target() instanceof PlayerRef player) || !isConcealed(player)) {
      return;
    }
    e.setCancelled(true);
    if (e.entity() instanceof MobRef mob) {
      clearAggroOwned(mob, player);
    }
  }

  public void forget(PlayerRef player) {
    concealmentLeases.remove(player.uuid());
  }

  static boolean currentConcealmentLease(Long currentLease, long expectedLease) {
    return currentLease != null && currentLease == expectedLease;
  }

  /** Shows one pulse of a cloud; {@code pulsesLeft} counts down to 1. */
  public interface PulseFx {
    void pulse(Loc center, double radius, int pulsesLeft);
  }

  public void close() {
    closed.set(true);
    concealmentLeases.clear();
  }

  private void cloudPulse(CloudState cloud) {
    Loc center = cloud.center;
    if (closed.get() || cloud.pulsesLeft <= 0) {
      return;
    }

    pulseFx.pulse(center, cloud.radius, cloud.pulsesLeft);

    for (EntityRef nearby : StealthCore.nearbyEntities(center, cloud.radius, entity -> true)) {
      if (!(nearby instanceof LivingRef target)) {
        continue;
      }
      if (target instanceof PlayerRef player) {
        renewConcealment(player);
      }
      enqueueTarget(cloud, target, true);
    }
    double aggroRadius = Math.max(cloud.radius, AGGRO_CLEAR_RADIUS);
    for (EntityRef nearby : StealthCore.nearbyEntities(center, aggroRadius, entity -> entity instanceof MobRef)) {
      if (nearby instanceof MobRef mob) {
        enqueueTarget(cloud, mob, false);
      }
    }
    startTargetDispatch(cloud);

    cloud.pulsesLeft--;
    if (cloud.pulsesLeft > 0) {
      AdaptApi.scheduler().runAt(center, () -> cloudPulse(cloud), PULSE_INTERVAL_TICKS);
    }
  }

  private void enqueueTarget(CloudState cloud, LivingRef target, boolean cloudOccupant) {
    UUID targetId = target.uuid();
    if (!cloud.pendingIds.add(targetId)) {
      return;
    }
    cloud.pending.add(new TargetWork(target, cloudOccupant));
  }

  private void startTargetDispatch(CloudState cloud) {
    if (cloud.dispatching.compareAndSet(false, true)) {
      dispatchTargets(cloud);
    }
  }

  private void dispatchTargets(CloudState cloud) {
    if (closed.get()) {
      cloud.pending.clear();
      cloud.pendingIds.clear();
      cloud.dispatching.set(false);
      return;
    }

    for (int index = 0; index < MAX_TARGET_HANDOFFS_PER_TICK; index++) {
      TargetWork work = cloud.pending.poll();
      if (work == null) {
        finishTargetDispatch(cloud);
        return;
      }
      LivingRef target = work.target();
      cloud.pendingIds.remove(target.uuid());
      AdaptApi.scheduler().runEntity(target, () -> processTargetOwned(cloud, work));
    }
    if (!cloud.pending.isEmpty()) {
      AdaptApi.scheduler().runAt(cloud.center, () -> dispatchTargets(cloud), 1);
      return;
    }
    finishTargetDispatch(cloud);
  }

  private void finishTargetDispatch(CloudState cloud) {
    cloud.dispatching.set(false);
    if (!cloud.pending.isEmpty() && cloud.dispatching.compareAndSet(false, true)) {
      AdaptApi.scheduler().runAt(cloud.center, () -> dispatchTargets(cloud), 1);
    }
  }

  private void processTargetOwned(CloudState cloud, TargetWork work) {
    LivingRef target = work.target();
    if (closed.get() || !target.isValid() || target.isDead() || !target.world().equals(cloud.center.world())) {
      return;
    }
    if (work.cloudOccupant()) {
      Loc targetLocation = target.location();
      if (targetLocation.distanceSquared(cloud.center) > cloud.radius * cloud.radius) {
        return;
      }
      target.addEffect(EffectInstance.of(BLINDNESS, BLIND_TICKS, 0).withParticles(false).withIcon(false));
      if (target instanceof PlayerRef player) {
        renewConcealment(player);
        applyInvisibilityOwned(player);
      }
    }
    if (target instanceof MobRef mob) {
      LivingRef currentTarget = mob.target();
      if (work.cloudOccupant()) {
        clearAggroOwned(mob, currentTarget);
      } else if (currentTarget instanceof PlayerRef player && isConcealed(player)) {
        clearAggroOwned(mob, player);
      } else if (mob.type().equals(WARDEN)
          && AdaptApi.platform().host().animals().angryAt(mob) instanceof PlayerRef player
          && isConcealed(player)) {
        clearAggroOwned(mob, player);
      }
    }
  }

  private void renewConcealment(PlayerRef player) {
    UUID playerId = player.uuid();
    long lease = concealmentSequence.incrementAndGet();
    concealmentLeases.put(playerId, lease);
    boolean scheduled = AdaptApi.scheduler().runEntity(player, () -> {
      Long currentLease = concealmentLeases.get(playerId);
      if (currentConcealmentLease(currentLease, lease)) {
        concealmentLeases.remove(playerId, currentLease);
      }
    }, INVISIBILITY_TICKS);
    if (!scheduled) {
      concealmentLeases.remove(playerId, lease);
    }
  }

  private void applyInvisibilityOwned(PlayerRef player) {
    EffectInstance current = player.effect(INVISIBILITY);
    if (current != null && current.durationTicks() > INVISIBILITY_TICKS + 5) {
      return;
    }
    player.addEffect(EffectInstance.of(INVISIBILITY, INVISIBILITY_TICKS, 0).withParticles(false).withIcon(false));
  }

  /** Makes the mob drop its target and aggression; a warden also forgets its anger at {@code currentTarget}. */
  public static void clearAggroOwned(MobRef mob, LivingRef currentTarget) {
    mob.setTarget(null);
    AdaptApi.platform().host().animals().setAggressive(mob, false);
    if (mob.type().equals(WARDEN) && currentTarget != null) {
      AdaptApi.platform().host().animals().clearAnger(mob, currentTarget);
    }
  }

  private static final class CloudState {
    private final Loc center;
    private final double radius;
    private final ConcurrentLinkedQueue<TargetWork> pending = new ConcurrentLinkedQueue<>();
    private final Set<UUID> pendingIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean dispatching = new AtomicBoolean();
    private int pulsesLeft;

    private CloudState(Loc center, double radius, int pulsesLeft) {
      this.center = center;
      this.radius = radius;
      this.pulsesLeft = pulsesLeft;
    }
  }

  private record TargetWork(LivingRef target, boolean cloudOccupant) {
  }
}
