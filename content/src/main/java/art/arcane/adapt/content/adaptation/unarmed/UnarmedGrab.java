package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;

/**
 * The sneak-punch grab and hurl shared by Grapple and Seismic Throw. A sneaking bare-hand hit grabs the target; the
 * next hit, or letting go of sneak, hurls it where the player looks.
 */
public final class UnarmedGrab {
  private final Adaptation<?> owner;
  private final Map<UUID, Grab> grabs;
  private final Map<UUID, Long> pendingHurls;
  private final Cooldowns cooldown;
  private final LongSupplier grabTimeoutMillis;
  private final IntFunction<Throw> throwAtLevel;
  private final Hurled onHurled;

  public UnarmedGrab(Adaptation<?> owner, Map<UUID, Grab> grabs, Map<UUID, Long> pendingHurls, Cooldowns cooldown,
                     LongSupplier grabTimeoutMillis, IntFunction<Throw> throwAtLevel, Hurled onHurled) {
    this.owner = owner;
    this.grabs = grabs;
    this.pendingHurls = pendingHurls;
    this.cooldown = cooldown;
    this.grabTimeoutMillis = grabTimeoutMillis;
    this.throwAtLevel = throwAtLevel;
    this.onHurled = onHurled;
  }

  /** How hard and how far one level throws, and what a throw costs. */
  public record Throw(double force, double upwardBoost, double maxRange, long cooldownMillis, double exhaustion) {
  }

  @FunctionalInterface
  public interface Hurled {
    /** Runs on the player's thread after the target was thrown. */
    void hurled(PlayerRef player, LivingRef target);
  }

  /** A bare-hand hit: hurls a held target, or grabs {@code target} when the player is sneaking. */
  public void strike(PlayerRef p, EntityRef target, int level) {
    long now = System.currentTimeMillis();
    Grab state = grabs.remove(p.uuid());
    if (state != null) {
      HudStatus.clear(p.uuid(), owner, HudGlyph.DURATION);
    }
    if (state != null && now - state.grabbedAtMillis <= grabTimeoutMillis.getAsLong()) {
      hurl(p, state.target, level);
      return;
    }

    if (!p.isSneaking() || !cooldown.isReady(p.uuid(), throwAtLevel.apply(level).cooldownMillis())) {
      return;
    }

    if (!(target instanceof LivingRef victim)) {
      return;
    }

    if (victim.has(EntityTrait.BOSS) || !owner.preference(p, UnarmedPreferences.TARGETS).accepts(victim)) {
      return;
    }

    grabs.put(p.uuid(), new Grab(victim, now));
    HudStatus.duration(p.uuid(), owner, now, grabTimeoutMillis.getAsLong());
    UnarmedGrapple.grabFx(owner, p, victim);
  }

  /** The player let go of sneak: hurls a held target unless the player releases on the next hit only. */
  public void unsneak(PlayerRef p) {
    if (owner.preference(p, UnarmedPreferences.RELEASE) == UnarmedPreferences.Release.NEXT_HIT) {
      return;
    }

    Grab state = grabs.remove(p.uuid());
    if (state == null) {
      return;
    }
    HudStatus.clear(p.uuid(), owner, HudGlyph.DURATION);

    long now = System.currentTimeMillis();
    if (now - state.grabbedAtMillis > grabTimeoutMillis.getAsLong()) {
      return;
    }

    int level = owner.getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    hurl(p, state.target, level);
  }

  public void forget(PlayerRef p) {
    if (grabs.remove(p.uuid()) != null) {
      HudStatus.clear(p.uuid(), owner, HudGlyph.DURATION);
    }
  }

  private void hurl(PlayerRef p, LivingRef target, int level) {
    UUID playerId = p.uuid();
    long now = System.currentTimeMillis();
    if (pendingHurls.getOrDefault(playerId, 0L) > now) {
      return;
    }
    pendingHurls.put(playerId, now + 1000L);

    Throw tuning = throwAtLevel.apply(level);
    Loc playerLocation = p.location();
    Vec3 velocity = playerLocation.direction().normalize().multiply(tuning.force()).withY(tuning.upwardBoost());
    HurlRequest request = new HurlRequest(p, target, playerLocation, velocity, tuning.maxRange() * tuning.maxRange(), playerId);
    boolean scheduled = AdaptApi.scheduler().runEntity(target, () -> prepareHurlTargetOwned(request));
    if (!scheduled) {
      pendingHurls.remove(playerId);
    }
  }

  private void prepareHurlTargetOwned(HurlRequest request) {
    if (!isHurlTargetValid(request)) {
      pendingHurls.remove(request.playerId());
      return;
    }

    if (!(request.target() instanceof PlayerRef)) {
      applyHurlTargetOwned(request);
      return;
    }

    Loc targetLocation = request.target().location();
    if (!AdaptApi.scheduler().runEntity(request.player(), () -> authorizePlayerHurlOwnerOwned(request, targetLocation))) {
      pendingHurls.remove(request.playerId());
    }
  }

  private void authorizePlayerHurlOwnerOwned(HurlRequest request, Loc targetLocation) {
    if (!request.player().isOnline() || !owner.canPVP(request.player(), targetLocation)) {
      pendingHurls.remove(request.playerId());
      return;
    }

    if (!AdaptApi.scheduler().runEntity(request.target(), () -> applyHurlTargetOwned(request))) {
      pendingHurls.remove(request.playerId());
    }
  }

  private void applyHurlTargetOwned(HurlRequest request) {
    if (!isHurlTargetValid(request)) {
      pendingHurls.remove(request.playerId());
      return;
    }

    request.target().setVelocity(request.velocity());
    UnarmedGrapple.hurlFx(owner, request.target(), request.velocity());
    if (!AdaptApi.scheduler().runEntity(request.player(), () -> completeHurl(request.player(), request.target()))) {
      pendingHurls.remove(request.playerId());
    }
  }

  private boolean isHurlTargetValid(HurlRequest request) {
    if (!request.target().isValid() || request.target().isDead()) {
      return false;
    }
    Loc targetLocation = request.target().location();
    return targetLocation.sameWorld(request.playerLocation())
        && targetLocation.distanceSquared(request.playerLocation()) <= request.maxRangeSquared();
  }

  private void completeHurl(PlayerRef player, LivingRef target) {
    pendingHurls.remove(player.uuid());
    if (!player.isOnline()) {
      return;
    }

    Throw tuning = throwAtLevel.apply(owner.getActiveLevel(player));
    cooldown.mark(player.uuid(), tuning.cooldownMillis());
    player.setExhaustion(player.exhaustion() + (float) tuning.exhaustion());
    UnarmedGrapple.throwFx(owner, player);
    onHurled.hurled(player, target);
  }

  public static final class Grab {
    private final LivingRef target;
    private final long grabbedAtMillis;

    private Grab(LivingRef target, long grabbedAtMillis) {
      this.target = target;
      this.grabbedAtMillis = grabbedAtMillis;
    }
  }

  private record HurlRequest(PlayerRef player, LivingRef target, Loc playerLocation, Vec3 velocity,
                             double maxRangeSquared, UUID playerId) {
  }
}
