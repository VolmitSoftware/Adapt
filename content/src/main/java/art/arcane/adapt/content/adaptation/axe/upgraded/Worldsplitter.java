package art.arcane.adapt.content.adaptation.axe.upgraded;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.axe.AxePreferences;
import art.arcane.adapt.content.adaptation.axe.AxeSmash;
import art.arcane.adapt.content.adaptation.axe.AxeThrow;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;

/**
 * Axe Ground Smash and Throwing Axe fused: a thrown axe smashes the ground where it lands, damaging and throwing back
 * everything around the impact, then flies back to the thrower.
 */
public class Worldsplitter extends SkillUpgrade<Worldsplitter.Config> {
  public static final Id ID = Id.adapt("axes/upgrade/worldsplitter");

  private final AxeSmash smasher = new AxeSmash(this,
      (PlayerRef player, List<LivingRef> hits) -> {
        addStat(player, "axes.worldsplitter.mobs-hit", hits.size());
        xpOnMobs(player, hits, getConfig().xpPerSmashTarget);
      });
  private final AxeThrow throwing = new AxeThrow(this, cooldowns(), new AxeThrow.Rules() {
    @Override
    public AxeThrow.Toss toss(int level, Id axe) {
      return new AxeThrow.Toss(getCooldownMillis(level), AxeThrow.meleeBaseDamage(axe) * getDamageMultiplier(level),
          getConfig().throwSpeed, true, getConfig().durabilityCost, getConfig().maxFlightTicks);
    }

    @Override
    public boolean ignorePassiveMobs() {
      return getConfig().ignorePassiveMobs;
    }

    @Override
    public void thrown(PlayerRef player) {
      addStat(player, "axes.worldsplitter.thrown", 1);
    }

    @Override
    public void hit(PlayerRef owner, LivingRef target) {
      addStat(owner, "axes.worldsplitter.hits", 1);
      xpOn(owner, target, getConfig().xpPerHit);
    }

    @Override
    public void landed(PlayerRef owner, Loc impact) {
      smash(owner, impact);
    }

    @Override
    public void launchFx(Loc spawn, Vec3 direction, ProjectileRef axe, int flightTicks) {
      timeline(axe)
          .duration(flightTicks)
          .priority(FxPriority.TRAIL)
          .cullRadius(40)
          .frame((f, tick, progress) -> f.particle(Particles.CRIT, 2, 0D, 0D, 0D, 0.08D, 0.0D))
          .start();
    }

    @Override
    public void breakFx(Loc impact) {
      fx(impact, FxPriority.TRANSITION).burst(Particles.SMOKE, 6, 0.25D);
    }

    @Override
    public void hitFx(LivingRef target) {
      fx(target.location().add(0D, target.height() * 0.6D, 0D), FxPriority.COMBAT).burst(Particles.CRIT, 8, 0.35D);
    }

    @Override
    public void returnFx(PlayerRef owner) {
      fx(owner.eyeLocation(), FxPriority.TRANSITION).burst(Particles.ENCHANT, 8, 0.3D);
    }

    @Override
    public AxeThrow.ReturnSweep returnSweep(PlayerRef owner) {
      int level = getActiveLevel(owner);
      return level <= 0 ? null : new AxeThrow.ReturnSweep(getReturnTargets(level), getConfig().returnDamageRatio,
          getConfig().returnPathWidth, getConfig().returnDurabilityPerHit);
    }

    @Override
    public void returned(PlayerRef owner, List<LivingRef> hits) {
      addStat(owner, "axes.worldsplitter.return-hits", hits.size());
      for (Adaptation<?> upgrade : getSkill().getUpgrades()) {
        if (upgrade instanceof SunderingCleave cleave) {
          for (LivingRef target : hits) {
            cleave.shred(owner, target);
          }
        }
      }
      cue(owner, MutationCue.FIGHT);
    }
  });

  public Worldsplitter() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("golden_axe"));
  }

  @Override
  protected void onRuntimeActivated() {
    throwing.activate();
  }

  @Override
  public void unregister() {
    throwing.close();
    super.unregister();
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, AxePreferences.SNEAK, AxePreferences.IGNORE_PASSIVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getDamageMultiplier(level), 2), 1);
    statLore(v, Format.f(getSmashDamage(level), 1), 2);
    statLore(v, Format.f(getSmashRadius(level), 1), 3);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getCooldownMillis(level), 1), 4);
    statLore(v, getReturnTargets(level), 5);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    throwing.interact(e);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    if (ItemKinds.isAxe(p.inventory().mainHand()) && getActiveLevel(p) > 0) {
      throwing.blockBroken(p);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileHitEvent e) {
    throwing.projectileHit(e);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityRemoveEvent e) {
    throwing.entityRemoved(e);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDamageByEntityEvent e) {
    throwing.entityDamaged(e);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDeathEvent e) {
    throwing.entityDied(e);
  }

  @Handle(ignoreCancelled = false)
  public void on(PlayerJoinEvent e) {
    throwing.joined(e.player());
  }

  @Handle(ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    throwing.quit(e.player());
  }

  private void smash(PlayerRef owner, Loc impact) {
    int level = getActiveLevel(owner);
    if (level <= 0) {
      return;
    }

    double damage = getSmashDamage(level);
    double radius = getSmashRadius(level);
    fx(impact, FxPriority.GAMEPLAY)
        .ring(Particles.POOF, radius, (int) Math.min(32D, Math.max(10D, radius * 4D)), 0.2D)
        .burst(Particles.EXPLOSION, 1, 0D);
    smasher.smash(owner, impact, radius, damage, damage * getConfig().smashEdgeDamageRatio,
        getConfig().smashForce, getConfig().ignorePassiveMobs || preferenceEnabled(owner, AxePreferences.IGNORE_PASSIVE));
    cue(owner, MutationCue.FIGHT);
  }

  double getDamageMultiplier(int level) {
    return getConfig().damageMultiplierBase + (level * getConfig().damageMultiplierPerLevel);
  }

  double getSmashDamage(int level) {
    return getConfig().smashDamageBase + (level * getConfig().smashDamagePerLevel);
  }

  double getSmashRadius(int level) {
    return getConfig().smashRadiusBase + (level * getConfig().smashRadiusPerLevel);
  }

  int getReturnTargets(int level) {
    return switch (Math.max(1, Math.min(3, level))) {
      case 1 -> getConfig().returnTargetsLevel1;
      case 2 -> getConfig().returnTargetsLevel2;
      default -> getConfig().returnTargetsLevel3;
    };
  }

  long getCooldownMillis(int level) {
    return (long) Math.max(getConfig().minimumCooldownMillis,
        getConfig().cooldownMillisBase - (level * getConfig().cooldownMillisPerLevel));
  }

  @ConfigDescription("A thrown axe smashes the ground where it lands, then flies back to you through everything in its way.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Exclude passive and neutral mobs from the smash and from bounced axes.", impact = "When enabled, protected mobs are skipped. Direct throws and player targeting are unchanged.")
    boolean ignorePassiveMobs = false;
    @ConfigDoc(value = "Fraction of the axe's melee damage dealt on a throw hit, before level scaling.", impact = "Higher values make thrown axes hit harder.")
    double damageMultiplierBase = 0.9;
    @ConfigDoc(value = "Melee-damage fraction added per level.", impact = "Higher values reward higher levels more.")
    double damageMultiplierPerLevel = 0.2;
    @ConfigDoc(value = "Flight velocity of a thrown axe in blocks per tick.", impact = "Higher values make throws travel faster and flatter.")
    double throwSpeed = 2.0;
    @ConfigDoc(value = "Smash damage at the impact point before level scaling.", impact = "Higher values make the smash hit harder.")
    double smashDamageBase = 4;
    @ConfigDoc(value = "Smash damage added per level.", impact = "Higher values reward higher levels more.")
    double smashDamagePerLevel = 2;
    @ConfigDoc(value = "Fraction of the smash damage dealt at the edge of the radius.", impact = "Higher values flatten the falloff.")
    double smashEdgeDamageRatio = 0.4;
    @ConfigDoc(value = "Smash radius in blocks before level scaling.", impact = "Higher values reach more targets.")
    double smashRadiusBase = 3;
    @ConfigDoc(value = "Smash radius added per level, in blocks.", impact = "Higher values widen the smash at higher levels.")
    double smashRadiusPerLevel = 1;
    @ConfigDoc(value = "Knockback at the impact point.", impact = "Higher values throw targets farther.")
    double smashForce = 1.0;
    @ConfigDoc(value = "Throw cooldown before level scaling, in milliseconds.", impact = "Higher values make throws rarer.")
    double cooldownMillisBase = 2500;
    @ConfigDoc(value = "Cooldown removed per level, in milliseconds.", impact = "Higher values let higher levels throw more often.")
    double cooldownMillisPerLevel = 500;
    @ConfigDoc(value = "Shortest cooldown at any level, in milliseconds.", impact = "Lower values allow more frequent throws.")
    double minimumCooldownMillis = 1000;
    @ConfigDoc(value = "Durability spent from the axe on each throw.", impact = "Higher values wear thrown axes out faster.")
    int durabilityCost = 3;
    @ConfigDoc(value = "Ticks a thrown axe stays airborne before it returns on its own.", impact = "Higher values let throws travel farther.")
    int maxFlightTicks = 80;
    @ConfigDoc(value = "Skill XP for each thrown-axe hit on an entity.", impact = "Higher values reward accurate throws.")
    double xpPerHit = 6;
    @ConfigDoc(value = "Skill XP for each target the smash hits.", impact = "Higher values reward smashing groups.")
    double xpPerSmashTarget = 4;
    @ConfigDoc(value = "Most creatures the returning axe strikes at level 1.", impact = "Higher values cut through larger crowds on the way back.")
    int returnTargetsLevel1 = 3;
    @ConfigDoc(value = "Most creatures the returning axe strikes at level 2.", impact = "Higher values cut through larger crowds on the way back.")
    int returnTargetsLevel2 = 5;
    @ConfigDoc(value = "Most creatures the returning axe strikes at level 3.", impact = "Higher values cut through larger crowds on the way back.")
    int returnTargetsLevel3 = 8;
    @ConfigDoc(value = "Fraction of the throw damage each creature on the return path takes.", impact = "Higher values make the return hit harder.")
    double returnDamageRatio = 0.5;
    @ConfigDoc(value = "How far from the line between the impact and you a creature is struck, in blocks.", impact = "Higher values widen the return path.")
    double returnPathWidth = 1.25;
    @ConfigDoc(value = "Axe durability each creature struck on the return costs.", impact = "Higher values wear the axe faster; the return never breaks it.")
    int returnDurabilityPerHit = 1;
  }
}
