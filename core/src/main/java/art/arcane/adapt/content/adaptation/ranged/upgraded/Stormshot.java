package art.arcane.adapt.content.adaptation.ranged.upgraded;

import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.content.adaptation.ranged.ProjectileMemo;
import art.arcane.adapt.content.adaptation.ranged.RangedHeartseeker;
import art.arcane.adapt.content.adaptation.ranged.RangedPreferences;
import art.arcane.adapt.content.adaptation.ranged.RangedProjectiles;
import art.arcane.adapt.content.adaptation.ranged.RangedShots;
import art.arcane.adapt.platform.entity.ArrowPickup;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Force Shot, Heavy Draw and Arrow Piercing fused: arrows leave faster, pierce more targets and hit harder, without the
 * speed Heavy Draw gives up. Its Quiver takes back a share of the arrows that hit.
 */
public class Stormshot extends SkillUpgrade<Stormshot.Config> {
  private static final ProjectileMemo<Integer> STORM_LEVEL = ProjectileMemo.create();
  private static final ProjectileMemo<Integer> STORM_HITS = ProjectileMemo.create();
  private static final ProjectileMemo<Quiver.Kind> RETURNS = ProjectileMemo.create();
  private static final Id INFINITY = Id.minecraft("infinity");
  private final Map<UUID, Double> returnCredit = playerState();
  private final Quiver quiver;

  public Stormshot() {
    super("upgraded-ranged-stormshot");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("spectral_arrow"));
    quiver = registerTool(new Quiver(this));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, RangedPreferences.SHOTS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getSpeedBonus(level), 0), 1);
    statLore(v, Form.pc(getDamageBonus(level), 0), 2);
    statLore(v, getPierce(level), 3);
    statLore(v, Form.pc(getReturnShare(level), 0), 4);
    statLore(v, quiver.capacity(level), 5);
  }

  @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef arrow = e.projectile();
    if (RangedHeartseeker.isSeekingProjectile(arrow) || STORM_LEVEL.has(arrow)
        || !RangedProjectiles.isArrowLike(arrow) || !(arrow.shooter() instanceof PlayerRef player)
        || !preference(player, RangedPreferences.SHOTS).accepts(arrow)) {
      return;
    }

    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    Vec3 velocity = RangedShots.quicken(arrow, getSpeedBonus(level));
    fx(arrow.location(), FxPriority.TRAIL)
        .trail(Particles.ELECTRIC_SPARK, velocity.x(), velocity.y(), velocity.z(), 2.0D, 6);
    RangedShots.pierce(arrow, getPierce(level));
    STORM_LEVEL.set(arrow, level);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof ProjectileRef arrow)
        || !(arrow.shooter() instanceof PlayerRef player)
        || !(e.entity() instanceof LivingRef target)
        || e.damage() <= 0D
        || isProtectedFriendly(player, target)) {
      return;
    }

    int level = STORM_LEVEL.getOrDefault(arrow, 0);
    if (level <= 0) {
      return;
    }

    RangedShots.strikeHeavy(e, 1D + getDamageBonus(level));
    int hits = STORM_HITS.getOrDefault(arrow, 0) + 1;
    STORM_HITS.set(arrow, hits);
    fx(target.location().add(0, target.height() * 0.5D, 0), FxPriority.COMBAT)
        .burst(Particles.ELECTRIC_SPARK, Math.min(16, 6 + (hits * 2)), 0.3D);
    Tasks.runEntity(player, () -> rewardHit(player, target));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityShootBowEvent e) {
    if (!(e.shooter() instanceof PlayerRef player) || !e.consumeItem()
        || !(e.projectile() instanceof ProjectileRef arrow) || !RangedProjectiles.isArrowLike(arrow)
        || arrow.type().equals(RangedProjectiles.TRIDENT) || quiver.level(player) <= 0) {
      return;
    }
    Quiver.Kind kind = Quiver.Kind.of(e.consumable());
    if (kind == null) {
      return;
    }
    if (e.bow().enchantmentLevel(INFINITY) <= 0 && arrow.pickupRule() == ArrowPickup.ALLOWED) {
      RETURNS.set(arrow, kind);
    }
    Tasks.runEntity(player, () -> quiver.restock(player, kind), 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void recover(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof ProjectileRef arrow) || !RETURNS.has(arrow)
        || !(arrow.shooter() instanceof PlayerRef player) || !(e.entity() instanceof LivingRef target)
        || e.finalDamage() <= 0D) {
      return;
    }
    Quiver.Kind kind = RETURNS.get(arrow);
    RETURNS.remove(arrow);
    int level = quiver.level(player);
    if (level <= 0 || MutationPayouts.farmed(player, target)) {
      return;
    }
    double credit = returnCredit.getOrDefault(player.uuid(), 0D) + getReturnShare(level);
    if (credit >= 1D && quiver.store(player, kind, 1)) {
      credit -= 1D;
      arrow.setPickupRule(ArrowPickup.DISALLOWED);
      addStat(player, "ranged.stormshot.arrows-returned", 1);
    }
    returnCredit.put(player.uuid(), Math.min(credit, 1D));
  }

  /** Gives a relaunched copy of a Stormshot arrow the original's level, hit count and pierce. */
  public static void carryOver(ProjectileRef from, ProjectileRef to) {
    Integer level = STORM_LEVEL.get(from);
    if (level == null) {
      return;
    }
    STORM_LEVEL.set(to, level);
    STORM_HITS.set(to, STORM_HITS.getOrDefault(from, 0));
    to.setPierceLevel(from.pierceLevel());
  }

  private void rewardHit(PlayerRef player, LivingRef target) {
    if (!player.isOnline()) {
      return;
    }
    xpOn(player, target, getConfig().xpPerHit);
    addStat(player, "ranged.stormshot.hits", 1);
    cue(player, MutationCue.FIGHT);
  }

  double getSpeedBonus(int level) {
    return getConfig().speedBonusBase + (level * getConfig().speedBonusPerLevel);
  }

  double getDamageBonus(int level) {
    return getConfig().damageBonusBase + (level * getConfig().damageBonusPerLevel);
  }

  double getReturnShare(int level) {
    return Math.max(0D, Math.min(1D, getConfig().returnShareBase + (level * getConfig().returnSharePerLevel)));
  }

  int getQuiverStacks(int level) {
    return Math.max(1, getConfig().quiverStacksBase + level);
  }

  int getPierce(int level) {
    return Math.max(0, getConfig().pierceBase + level);
  }

  @ConfigDescription("Arrows fly faster, pierce more targets and hit harder with no speed penalty.")
  public static class Config extends SkillUpgrade.ToolConfig {
    @ConfigDoc(value = "Extra launch speed before level scaling.", impact = "Higher values make arrows leave the bow faster.")
    double speedBonusBase = 0.4;
    @ConfigDoc(value = "Extra launch speed added per level.", impact = "Higher values reward higher levels more.")
    double speedBonusPerLevel = 0.25;
    @ConfigDoc(value = "Extra arrow damage before level scaling.", impact = "Higher values make every arrow hit harder.")
    double damageBonusBase = 0.3;
    @ConfigDoc(value = "Extra arrow damage added per level.", impact = "Higher values reward higher levels more.")
    double damageBonusPerLevel = 0.3;
    @ConfigDoc(value = "Targets pierced before adding the level.", impact = "Higher values let arrows pass through more targets.")
    int pierceBase = 0;
    @ConfigDoc(value = "Skill XP for each arrow hit.", impact = "Higher values reward accurate shooting.")
    double xpPerHit = 3;
    @ConfigDoc(value = "Share of hitting arrows the Quiver takes back before level scaling.", impact = "Higher values return more arrows.")
    double returnShareBase = 0.15;
    @ConfigDoc(value = "Share of hitting arrows the Quiver takes back, added per level.", impact = "Higher values reward higher levels more.")
    double returnSharePerLevel = 0.15;
    @ConfigDoc(value = "Stacks of 64 the Quiver holds before adding the level.", impact = "Higher values let the Quiver hold more arrows.")
    int quiverStacksBase = 1;
  }
}
