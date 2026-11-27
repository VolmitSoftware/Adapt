package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.ranged.ProjectileMemo;
import art.arcane.adapt.content.adaptation.ranged.RangedProjectiles;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.entity.ArrowPickup;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chronos with Ranged, replacing Convergence: a fully drawn bow shot fires again a moment later from where it left, on
 * the same path, without an arrow. The replay has no shooter, so it sets off no Ranged effect and pays no XP; it never
 * hurts its archer or anything the archer may not hurt, and cannot be picked up.
 */
public final class ReplayedShot extends Signature<ReplayedShot.Config> {
  private static final Id BOW = Id.minecraft("bow");
  private static final ProjectileMemo<UUID> REPLAYS = ProjectileMemo.create();
  private final Map<UUID, Shot> shots = new ConcurrentHashMap<>();

  public ReplayedShot(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bow"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Format.pc(share(level), 0), 1);
  }

  @Override
  public long cooldownMillis(int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityShootBowEvent e) {
    if (!(e.shooter() instanceof PlayerRef player) || !(e.projectile() instanceof ProjectileRef arrow)
        || !BOW.equals(e.bow().type()) || e.force() < getConfig().fullDrawForce
        || !RangedProjectiles.ARROW.equals(arrow.type()) && !RangedProjectiles.SPECTRAL_ARROW.equals(arrow.type())
        || getActiveLevel(player) <= 0) {
      return;
    }
    shots.put(player.uuid(), new Shot(arrow.type(), arrow.location(), arrow.velocity(), arrow.damage()));
    try {
      fire(player, null, arrow.location(), false);
    } finally {
      shots.remove(player.uuid());
    }
  }

  @Handle(priority = EventPriority.LOW)
  public void on(EntityDamageByEntityEvent e) {
    UUID ownerId = REPLAYS.get(e.damager());
    if (ownerId == null) {
      return;
    }
    PlayerRef owner = AdaptApi.platform().host().player(ownerId);
    EntityRef target = e.entity();
    if (owner == null || !owner.isOnline() || target.uuid().equals(ownerId) || !canDamageTarget(owner, target)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    shots.remove(e.player().uuid());
  }

  @Override
  public Outcome run(BlockUse use) {
    Shot shot = shots.get(use.player().uuid());
    if (shot == null || shot.velocity().lengthSquared() <= 0D) {
      return Outcome.MISSED;
    }
    PlayerRef player = use.player();
    double damage = shot.damage() * share(use.level());
    int delay = Math.max(1, getConfig().delayTicks);
    Loc from = shot.from();
    fx(from, FxPriority.TRAIL).particle(Particles.END_ROD, 6, 0.1D, 0.1D, 0.1D, 0.01D, 0D);
    AdaptApi.scheduler().runAt(from, () -> {
      if (!player.isOnline() || !from.world().isLoaded()) {
        return;
      }
      from.world().spawn(shot.type(), from, (EntityRef spawned) -> {
        if (spawned instanceof ProjectileRef replay) {
          REPLAYS.set(replay, player.uuid());
          replay.setPickupRule(ArrowPickup.DISALLOWED);
          replay.setCritical(false);
          replay.setDamage(damage);
          replay.setVelocity(shot.velocity());
        }
      });
      fx(from, FxPriority.TRAIL).particle(Particles.END_ROD, 10, 0.15D, 0.15D, 0.15D, 0.02D, 0D);
    }, delay);
    return Outcome.INSTANT;
  }

  double share(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().damageShareAtFirstLevel, getConfig().damageShareAtTopLevel);
  }

  private record Shot(Id type, Loc from, Vec3 velocity, double damage) {
  }

  @ConfigDescription("A fully drawn bow shot fires again from where it left, on the same path, without an arrow.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Share of the shot's base damage the replay deals at level 1.", impact = "Higher values make the replay hit harder.")
    double damageShareAtFirstLevel = 0.6;
    @ConfigDoc(value = "Share of the shot's base damage the replay deals at the top level.", impact = "Higher values make the replay hit harder.")
    double damageShareAtTopLevel = 1;
    @ConfigDoc(value = "Ticks between the shot and its replay.", impact = "Higher values replay the shot later.")
    int delayTicks = 30;
    @ConfigDoc(value = "Draw strength a shot needs, from 0 to 1.", impact = "Lower values replay shots that were not fully drawn.")
    float fullDrawForce = 1F;
    @ConfigDoc(value = "Cooldown after a replay, in milliseconds.", impact = "Higher values make replays rarer.")
    long cooldownMillis = 8_000L;
  }
}
