package art.arcane.adapt.content.adaptation.ranged.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.LingeringSound;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.ranged.ProjectileMemo;
import art.arcane.adapt.content.adaptation.ranged.RangedHeartseeker;
import art.arcane.adapt.content.adaptation.ranged.RangedSnares;
import art.arcane.adapt.content.adaptation.ranged.RangedWebs;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web Snare, Pinning Shot and Floaters fused: a Snare Arrow hit wraps the target in temporary cobwebs, pins it and lifts
 * it, so it hangs in the web off the ground.
 */
public class HuntersWeb extends SkillUpgrade<HuntersWeb.Config> {
  public static final Id ID = Id.adapt("ranged/upgrade/hunters_web");

  private static final ProjectileMemo<Integer> WEB_LEVEL = ProjectileMemo.create();
  private static final Rgb WEB_WHITE = new Rgb(235, 235, 235);
  private final RangedWebs webs = new RangedWebs(this);
  private final Map<UUID, Long> cagedUntil = new ConcurrentHashMap<>();
  private final SnareArrow snareArrow;

  public HuntersWeb() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("cobweb"));
    snareArrow = registerTool(new SnareArrow(this));
  }

  @Override
  protected void onRuntimeActivated() {
    webs.queueLoadedChunkRecovery();
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.duration(getDurationTicks(level) * 50D, 1), 1);
    statLore(v, getConfig().liftAmplifier + 1, 2);
    statLore(v, Format.duration(getConfig().recageMillis, 1), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityShootBowEvent e) {
    int level = snareArrow.spentBy(e);
    if (level > 0 && e.projectile() instanceof ProjectileRef projectile
        && !RangedHeartseeker.isSeekingProjectile(projectile)) {
      WEB_LEVEL.set(projectile, level);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof ProjectileRef projectile)
        || !(projectile.shooter() instanceof PlayerRef player)
        || !(e.entity() instanceof LivingRef target)
        || target.uuid().equals(player.uuid())
        || isProtectedFriendly(null, target)
        || player.uuid().equals(target.tameOwner())) {
      return;
    }

    int level = WEB_LEVEL.getOrDefault(projectile, 0);
    long now = System.currentTimeMillis();
    if (level <= 0 || cagedUntil.getOrDefault(target.uuid(), 0L) > now) {
      return;
    }

    cagedUntil.values().removeIf((Long until) -> until <= now);
    cagedUntil.put(target.uuid(), now + getConfig().recageMillis);
    int durationTicks = getDurationTicks(level);
    cage(player, target, now + (durationTicks * 50L));
    RangedSnares.pin(this, target, getConfig().pinAmplifier, durationTicks);
    RangedSnares.dampen(target, getConfig().horizontalVelocityFactor);
    RangedSnares.levitate(target, durationTicks, getConfig().liftAmplifier);
    fx(target.location().add(0, target.height() * 0.5D, 0), FxPriority.COMBAT)
        .burst(Particles.WHITE_ASH, 12, 0.5D)
        .dustRing(WEB_WHITE, 0.8D, 12, 1.0F);
    LingeringSound lingering = linger(target);
    AdaptApi.scheduler().runEntity(target, lingering::stop, durationTicks);
    AdaptApi.scheduler().runEntity(player, () -> rewardCage(player, target));
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(ChunkEvent e) {
    webs.onChunk(e);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockPistonEvent e) {
    webs.onPiston(e);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockExplodeEvent e) {
    webs.onExplode(e);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(EntityExplodeEvent e) {
    webs.onExplode(e);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockBreakEvent e) {
    webs.onBreak(e);
  }

  @Override
  public void unregister() {
    super.unregister();
    cagedUntil.clear();
    webs.clear();
  }

  private void cage(PlayerRef player, LivingRef target, long expiresAt) {
    Loc feet = target.location();
    for (RangedWebs.WebPlacement placement : RangedWebs.placementCandidates(feet.world(), feet.blockX(),
        feet.blockY() + 1, feet.blockZ())) {
      webs.place(player, placement, expiresAt,
          () -> AdaptApi.scheduler().runEntity(player, () -> addStat(player, "ranged.hunters-web.webs-spun", 1)));
    }
  }

  private void rewardCage(PlayerRef player, LivingRef target) {
    if (!player.isOnline()) {
      return;
    }
    xpOn(player, target, getConfig().xpPerCage);
    addStat(player, "ranged.hunters-web.targets-caged", 1);
    cue(player, MutationCue.FIGHT);
  }

  int getDurationTicks(int level) {
    return Math.max(20, getConfig().durationTicksBase + (level * getConfig().durationTicksPerLevel));
  }

  @ConfigDescription("A Snare Arrow hit cages the target in cobwebs, pins it and holds it off the ground.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Cage duration before level scaling, in ticks.", impact = "Higher values hold targets longer.")
    int durationTicksBase = 30;
    @ConfigDoc(value = "Cage duration added per level, in ticks.", impact = "Higher values reward higher levels more.")
    int durationTicksPerLevel = 20;
    @ConfigDoc(value = "Levitation amplifier that lifts the caged target.", impact = "Higher values lift targets faster.")
    int liftAmplifier = 0;
    @ConfigDoc(value = "Slowness strength of the pin; each step removes 15% movement speed.", impact = "Higher values hold targets more firmly.")
    int pinAmplifier = 4;
    @ConfigDoc(value = "Share of the target's horizontal speed kept when caged.", impact = "Lower values stop targets more sharply.")
    double horizontalVelocityFactor = 0.15;
    @ConfigDoc(value = "Time before the same target can be caged again, in milliseconds.", impact = "Lower values let you cage one target more often.")
    long recageMillis = 4000;
    @ConfigDoc(value = "Skill XP for each caged target.", impact = "Higher values reward caging.")
    double xpPerCage = 8;
  }
}
