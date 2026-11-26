package art.arcane.adapt.content.adaptation.seaborrne.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneCharges;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneJet;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneStormStrike;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneSurge;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneTridents;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.PlayerAnimationEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

/**
 * Tidecaller, Hydro Jet, Trident Mastery and Storm Strike fused: a trident swing in water surges and a sneak while
 * swimming jets, both from one charge pool; tridents hit harder and turn back the moment they strike, and the lent
 * Storm Strike calls lightning on its own events. A swing with anything else, or
 * at a block within reach, does not surge.
 */
public class Maelstrom extends SkillUpgrade<Maelstrom.Config> {
  private static final double MINING_REACH = 4.5D;
  private static final DataKey<Integer> LEVEL_KEY = DataKey.adapt("upgraded_seaborne_maelstrom_level", DataCodecs.INT);
  private final SeaborneCharges charges = new SeaborneCharges(this, playerState(), () -> getConfig().chargeRegenMillis);
  private final SeaborneTridents tridents = new SeaborneTridents(this, LEVEL_KEY, new SeaborneTridents.Recall() {
    @Override
    public boolean enabled(PlayerRef thrower) {
      return true;
    }

    @Override
    public double speed(int level) {
      return getRecallSpeed(level);
    }

    @Override
    public int graceTicks(int level) {
      return getConfig().flightTicks;
    }

    @Override
    public void returning(Loc tridentLocation) {
      fx(tridentLocation, FxPriority.TRAIL).particle(Particles.BUBBLE, 3, 0D, 0D, 0D, 0.1D, 0.02D);
    }
  });

  public Maelstrom() {
    super("upgraded-seaborne-maelstrom");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("trident"));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getMaxCharges(level), 1);
    statLore(v, Form.pc(getTridentBonus(level), 0), 2);
    statLore(v, Form.f(getRecallSpeed(level), 1), 3);
    grantedLore(v, level);
  }

  @Override
  protected boolean runsOnGrant(Adaptation<?> original) {
    return original instanceof SeaborneStormStrike;
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef player = e.player();
    if (!e.isSneaking() || !player.isSwimming()) {
      return;
    }
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    if (!SeaborneJet.respectsFoodReserve(player.foodLevel(), player.saturation(), player.exhaustion(), getConfig().hungerCost, 0)
        || !charges.spend(player, getMaxCharges(level))) {
      FxPresets.failFizzle(this, player);
      return;
    }

    Vec3 direction = SeaborneJet.launch(player, getJetForce(level), getConfig().hungerCost);
    fx(player.location().add(0D, 0.5D, 0D), FxPriority.GAMEPLAY)
        .trail(Particles.BUBBLE, -direction.x(), -direction.y(), -direction.z(), 1.6D, 12);
    addStat(player, "seaborne.maelstrom.jets", 1);
    cue(player, MutationCue.BOON);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerAnimationEvent e) {
    PlayerRef player = e.player();
    if (e.animation() != PlayerAnimationEvent.Animation.ARM_SWING
        || !SeaborneTridents.TRIDENT.equals(player.inventory().mainHand().type()) || !SeaborneSurge.inWater(player)
        || player.targetBlock(MINING_REACH) != null) {
      return;
    }
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    Vec3 direction = SeaborneSurge.direction(player, false);
    if (direction.lengthSquared() <= 0.000001 || SeaborneSurge.blockedAhead(player, direction, getConfig().wallCheckDistance)
        || !charges.spend(player, getMaxCharges(level))) {
      FxPresets.failFizzle(this, player);
      return;
    }

    boolean wasSwimming = player.isSwimming();
    player.setVelocity(SeaborneSurge.velocity(player.velocity(), direction, getSurgeStrength(level), getConfig().surgeLift,
        true, getConfig().maxSurgeVelocity));
    if (wasSwimming) {
      SeaborneSurge.keepSwimming(player);
    }
    fx(player.location().add(0D, 1.0D, 0D), FxPriority.GAMEPLAY)
        .trail(Particles.SPLASH, direction.x(), direction.y(), direction.z(), 2.0D, 14);
    addStat(player, "seaborne.maelstrom.surges", 1);
    cue(player, MutationCue.BOON);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    SeaborneTridents.Hit hit = tridents.resolveHit(e);
    if (hit == null) {
      return;
    }

    SeaborneTridents.strike(e, getTridentBonus(hit.level()));
    fx(e.entity().location().add(0D, 1.0D, 0D), FxPriority.COMBAT).particle(Particles.CRIT, 10, 0D, 0D, 0D, 0.4D, 0.1D);
    if (hit.trident() != null) {
      SeaborneTridents.release(hit.trident());
    }
    xpOn(hit.attacker(), e.entity(), getConfig().xpPerTridentHit);
    cue(hit.attacker(), MutationCue.FIGHT);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef trident = e.projectile();
    if (!SeaborneTridents.TRIDENT.equals(trident.type()) || !(trident.shooter() instanceof PlayerRef player)) {
      return;
    }
    int level = getActiveLevel(player);
    if (level > 0) {
      tridents.launch(trident, player, level, Math.max(1, getConfig().recallDelayTicks));
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    charges.forget(e.player().uuid());
  }

  int getMaxCharges(int level) {
    return SeaborneCharges.maxCharges(getConfig().chargesBase, getConfig().chargesFactor, getLevelPercent(level));
  }

  double getJetForce(int level) {
    return getConfig().jetForceBase + (level * getConfig().jetForcePerLevel);
  }

  double getSurgeStrength(int level) {
    return getConfig().surgeStrengthBase + (level * getConfig().surgeStrengthPerLevel);
  }

  double getTridentBonus(int level) {
    return SeaborneTridents.damageBonus(getConfig().tridentBonusBase, getConfig().tridentBonusFactor, getLevelPercent(level));
  }

  double getRecallSpeed(int level) {
    return SeaborneTridents.recallSpeed(getConfig().recallSpeedBase, getConfig().recallSpeedFactor, getLevelPercent(level));
  }

  @ConfigDescription("Surge and jet from one charge pool; tridents hit harder, return the moment they strike, and call storms through the lent Storm Strike.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Charges shared by surge and jet at level 1.", impact = "Higher values allow longer chains of rushes.")
    int chargesBase = 3;
    @ConfigDoc(value = "Charges added between level 1 and the top level.", impact = "Higher values reward higher levels with more rushes.")
    double chargesFactor = 3;
    @ConfigDoc(value = "Time to regain one charge, in milliseconds.", impact = "Lower values refill the pool faster.")
    long chargeRegenMillis = 2000;
    @ConfigDoc(value = "Jet force before level scaling.", impact = "Higher values push each jet farther.")
    double jetForceBase = 1.0;
    @ConfigDoc(value = "Jet force added per level.", impact = "Higher values widen the gap between levels.")
    double jetForcePerLevel = 0.3;
    @ConfigDoc(value = "Hunger exhaustion each jet costs.", impact = "Higher values make jetting more tiring.")
    double hungerCost = 1.5;
    @ConfigDoc(value = "Surge strength before level scaling.", impact = "Higher values make each surge faster.")
    double surgeStrengthBase = 1.1;
    @ConfigDoc(value = "Surge strength added per level.", impact = "Higher values widen the gap between levels.")
    double surgeStrengthPerLevel = 0.3;
    @ConfigDoc(value = "Upward push added to each surge.", impact = "Higher values lift surges toward the surface.")
    double surgeLift = 0.04;
    @ConfigDoc(value = "Fastest speed a surge can leave you with.", impact = "Lower values are gentler on collisions.")
    double maxSurgeVelocity = 2.4;
    @ConfigDoc(value = "Distance ahead checked for walls before a surge, in blocks.", impact = "Higher values refuse surges near walls sooner.")
    double wallCheckDistance = 1.2;
    @ConfigDoc(value = "Trident bonus damage at level 1, as a fraction.", impact = "Higher values raise every trident hit.")
    double tridentBonusBase = 0.3;
    @ConfigDoc(value = "Trident bonus damage added between level 1 and the top level.", impact = "Higher values reward higher levels more.")
    double tridentBonusFactor = 0.4;
    @ConfigDoc(value = "Speed of a returning trident at level 1.", impact = "Higher values bring tridents back faster.")
    double recallSpeedBase = 1.4;
    @ConfigDoc(value = "Return speed added between level 1 and the top level.", impact = "Higher values reward higher levels with faster returns.")
    double recallSpeedFactor = 1.0;
    @ConfigDoc(value = "Ticks a thrown trident flies before turning back when it hits nothing.", impact = "Higher values let missed throws travel farther.")
    int flightTicks = 30;
    @ConfigDoc(value = "Ticks after a throw before the return checks start.", impact = "Higher values delay every return.")
    int recallDelayTicks = 1;
    @ConfigDoc(value = "Skill XP for each trident hit.", impact = "Higher values reward trident fighting.")
    double xpPerTridentHit = 6;
  }
}
