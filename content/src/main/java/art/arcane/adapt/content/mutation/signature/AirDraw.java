package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.agility.upgraded.Skyrunner;
import art.arcane.adapt.content.adaptation.ranged.RangedProjectiles;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.item.Potions;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ranged and Agility, either leading, replacing Convergence: drawing a bow in the air slows your fall, up to a budget
 * per airtime, an arrow loosed in the air from at least half a draw flies as if fully drawn, and the first arrow hit
 * you land while airborne refills Skyrunner's mid-air charges once per airtime.
 */
public final class AirDraw extends Signature<AirDraw.Config> {
  private static final Id BOW = Id.minecraft("bow");
  private static final Id SLOW_FALLING = Id.minecraft("slow_falling");
  private static final int HOLD_TICKS = 4;
  private final Map<UUID, PlayerRef> drawing = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> spent = playerState();
  private final Map<UUID, ProjectileRef> shots = playerState();
  private final Map<UUID, Boolean> refreshed = playerState();

  public AirDraw(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("phantom_membrane"));
    setInterval(50);
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Format.duration(budgetTicks(level) * 50D, 1), 1);
  }

  @Override
  public long cooldownMillis(int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Override
  public boolean hasTickDemand() {
    return !drawing.isEmpty();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    PlayerRef player = e.player();
    ItemRef item = e.item();
    if (e.action().isRightClick() && item != null && BOW.equals(item.type()) && getActiveLevel(player) > 0) {
      drawing.put(player.uuid(), player);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityShootBowEvent e) {
    if (e.isCancelled() || !(e.shooter() instanceof PlayerRef player) || !BOW.equals(e.bow().type())
        || !(e.projectile() instanceof ProjectileRef arrow) || !RangedProjectiles.isArrowLike(arrow)) {
      return;
    }
    drawing.remove(player.uuid());
    if (e.force() < getConfig().minimumDraw || player.isOnGround() || !airborne(player) || getActiveLevel(player) <= 0) {
      return;
    }
    shots.put(player.uuid(), arrow);
    fire(player, null, player.location(), false);
    shots.remove(player.uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (e.isCancelled() || !(e.damager() instanceof ProjectileRef arrow) || !RangedProjectiles.isArrowLike(arrow)
        || !(arrow.shooter() instanceof PlayerRef shooter) || !(e.entity() instanceof LivingRef victim)
        || victim.uuid().equals(shooter.uuid()) || refreshed.containsKey(shooter.uuid())
        || getActiveLevel(shooter) <= 0) {
      return;
    }
    AdaptApi.scheduler().runEntity(shooter, () -> refresh(shooter));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    if ((!spent.isEmpty() || !refreshed.isEmpty()) && e.player().isOnGround()) {
      spent.remove(e.player().uuid());
      refreshed.remove(e.player().uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    drawing.remove(e.player().uuid());
  }

  @Override
  public void onTick() {
    for (PlayerRef player : drawing.values()) {
      AdaptApi.scheduler().runEntity(player, () -> hover(player));
    }
  }

  private void hover(PlayerRef player) {
    UUID id = player.uuid();
    ItemRef using = player.activeItem();
    int level = getActiveLevel(player);
    if (level <= 0 || !player.isOnline() || using == null || !BOW.equals(using.type())) {
      drawing.remove(id);
      return;
    }
    if (player.isOnGround()) {
      spent.remove(id);
      return;
    }
    int used = spent.getOrDefault(id, 0);
    if (!airborne(player) || used >= budgetTicks(level)) {
      return;
    }
    spent.put(id, used + 1);
    if (player.effect(SLOW_FALLING) == null || player.effect(SLOW_FALLING).durationTicks() <= HOLD_TICKS) {
      player.addEffect(EffectInstance.of(SLOW_FALLING, HOLD_TICKS, 0).withAmbient(true).withParticles(false).withIcon(false));
      Potions.recordApplied(player, SLOW_FALLING);
    }
  }

  @Override
  public Outcome run(BlockUse use) {
    ProjectileRef arrow = shots.get(use.player().uuid());
    if (arrow == null) {
      return Outcome.MISSED;
    }
    Vec3 velocity = arrow.velocity();
    double speed = velocity.length();
    if (speed > 1.0E-3 && speed < getConfig().fullDrawSpeed) {
      arrow.setVelocity(velocity.multiply(getConfig().fullDrawSpeed / speed));
    }
    arrow.setCritical(true);
    fx(use.player().location(), FxPriority.GAMEPLAY).burst(Particles.CLOUD, 6, 0.4D);
    return Outcome.INSTANT;
  }

  /** Refills Skyrunner's charges for a shooter still in the air, once per airtime. */
  private void refresh(PlayerRef shooter) {
    if (shooter.isOnGround() || !airborne(shooter) || refreshed.containsKey(shooter.uuid())) {
      return;
    }
    Skyrunner skyrunner = PairParts.owned(Id.adapt("agility/upgrade/skyrunner"), Skyrunner.class);
    if (skyrunner != null && skyrunner.refreshCharge(shooter)) {
      refreshed.put(shooter.uuid(), true);
      fx(shooter.location(), FxPriority.GAMEPLAY).burst(Particles.CLOUD, 4, 0.3D);
    }
  }

  private static boolean airborne(PlayerRef player) {
    GameMode mode = player.gameMode();
    return (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && !player.isFlying() && !player.isGliding()
        && !player.isSwimming() && !player.isInWater() && player.vehicle() == null;
  }

  int budgetTicks(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().slowFallTicksAtFirstLevel,
        getConfig().slowFallTicksAtTopLevel));
  }

  @ConfigDescription("Drawing a bow in the air slows your fall, half-drawn arrows loosed in the air fly fully drawn, and an air hit refills Skyrunner's charges.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Ticks of slowed fall per airtime at level 1.", impact = "Higher values let you hang longer while drawing.")
    double slowFallTicksAtFirstLevel = 40;
    @ConfigDoc(value = "Ticks of slowed fall per airtime at the top level.", impact = "Higher values let you hang longer while drawing.")
    double slowFallTicksAtTopLevel = 80;
    @ConfigDoc(value = "Speed of a fully drawn arrow, in blocks per tick.", impact = "Higher values make air shots fly faster and hit harder.")
    double fullDrawSpeed = 3.0;
    @ConfigDoc(value = "Least draw an air shot needs to fly fully drawn, from 0 to 1. 0.5 is half a draw.", impact = "Higher values ask for longer draws before air shots get the boost.")
    double minimumDraw = 0.5;
    @ConfigDoc(value = "Cooldown after an air shot, in milliseconds.", impact = "Higher values space air shots further apart.")
    long cooldownMillis = 0;
  }
}
