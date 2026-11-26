package art.arcane.adapt.content.adaptation.hunter.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.content.adaptation.hunter.HunterBigGameHunter;
import art.arcane.adapt.content.adaptation.hunter.HunterRamp;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Predator Focus and Big Game Hunter fused: repeated hits on one target ramp your damage, large and boss mobs ramp
 * twice as fast and may give one more roll of their own drops, and the Tracker's Compass leads you to your prey.
 */
public class ApexPredator extends SkillUpgrade<ApexPredator.Config> {
  private static final Rgb APEX_RED = new Rgb(190, 40, 40);
  private static final DataKey<Byte> ROLLED_KEY = DataKey.adapt("hunter-quarry-rolled", DataCodecs.BYTE);
  private final HunterRamp ramps = new HunterRamp(playerState());
  private final Map<UUID, Set<UUID>> quarries = playerState();
  private final TrackersCompass compass;

  public ApexPredator() {
    super("upgraded-hunter-apex-predator");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("crossbow"));
    setInterval(500);
    compass = registerTool(new TrackersCompass(this));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getPerStackBonus(level), 0), 1);
    statLore(v, getRampCap(level), 2);
    statLore(v, Math.max(1, getConfig().bigGameStacksPerHit), 3);
    statLore(v, Form.pc(getExtraRollChance(level), 0), 4);
    statLore(v, (int) getCompassRange(level), 5);
    statLore(v, getKindsRemembered(level), 6);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    AttackContext attack = resolveAttackContext(e);
    if (attack == null || !HunterRamp.ramps(e)) {
      return;
    }

    PlayerRef p = attack.attacker();
    EntityRef target = attack.target();
    int level = attack.level();
    if (level >= getConfig().quarryLevel && !(target instanceof PlayerRef) && TrackersCompass.special(target)) {
      markQuarry(p, target);
    }
    int cap = getRampCap(level);
    long now = M.ms();
    int stacks = ramps.hit(p.uuid(), target.uuid(), cap, stacksPerHit(target), getConfig().decayMillis, now);
    AbilityGlyphs.duration(p.uuid(), this, now, getConfig().decayMillis, stacks, cap);

    double multiplier = HunterRamp.damageMultiplier(stacks, getPerStackBonus(level));
    if (multiplier <= 1.0D) {
      return;
    }

    e.setDamage(e.damage() * multiplier);
    addStat(p, "hunter.apex-predator.ramped-hits", 1);
    xpSilentOn(p, target, getConfig().xpPerRampedHit);
    cue(p, MutationCue.FIGHT);
    if (stacks >= cap) {
      fx(target.location().add(0, target.height() * 0.5D, 0), FxPriority.COMBAT)
          .dustBurst(APEX_RED, 10, 0.35D, 1.3F)
          .particle(Particles.CRIT_MAGIC, 6, 0, 0.25D, 0, 0.3D, 0.0D);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDeathEvent e) {
    PlayerRef killer = e.killer();
    LivingRef victim = e.entity();
    if (killer == null || victim instanceof PlayerRef || GolemKillCredit.isGolemKill(victim)
        || !hasActiveAdaptation(killer) || !canDamageTarget(killer, victim) || MutationPayouts.farmed(killer, victim)) {
      return;
    }

    compass.recordKill(killer, victim.type());
    if (!HunterBigGameHunter.isLargeQuarry(victim) || victim.data().has(ROLLED_KEY)) {
      return;
    }
    victim.data().set(ROLLED_KEY, (byte) 1);
    if (ThreadLocalRandom.current().nextDouble() >= getExtraRollChance(getActiveLevel(killer))) {
      return;
    }
    List<ItemRef> roll = HunterBigGameHunter.extraRoll(victim, killer);
    if (roll.isEmpty()) {
      return;
    }
    for (ItemRef drop : roll) {
      if (drop != null && !drop.isEmpty()) {
        e.drops().add(drop.copy());
      }
    }
    addStat(killer, "hunter.apex-predator.extra-rolls", 1);
    cue(killer, MutationCue.BOON);
    fx(victim.location().add(0, victim.height() * 0.5D, 0), FxPriority.COMBAT)
        .dustBurst(APEX_RED, 14, 0.6D, 1.4F)
        .particle(Particles.GLOW, 6, 0, 0.5D, 0, 0.4D, 0);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    quarries.remove(e.player().uuid());
  }

  @Override
  public void onTick() {
    for (AdaptPlayer learner : getServer().getLearnedAdaptPlayerSnapshot(getName())) {
      PlayerRef player = learner.getPlayer();
      if (player == null || compass.level(player) <= 0 || !compass.held(player)) {
        continue;
      }
      Tasks.runEntity(player, () -> {
        int level = compass.level(player);
        if (level > 0) {
          compass.track(player, level, quarries(player));
        }
      });
    }
  }

  private Set<UUID> quarries(PlayerRef player) {
    Set<UUID> marked = quarries.get(player.uuid());
    if (marked == null) {
      return Set.of();
    }
    synchronized (marked) {
      return Set.copyOf(marked);
    }
  }

  /** The nearest prey whose blood trail the hunter can see through Stalker's Trail, or null. */
  EntityRef trailedPrey(PlayerRef hunter) {
    Skill<?> skill = getSkill();
    if (skill == null) {
      return null;
    }
    for (Adaptation<?> adaptation : Skill.ownedAdaptations(skill)) {
      if (adaptation instanceof StalkersTrail trail) {
        return trail.trailedPrey(hunter);
      }
    }
    return null;
  }

  void compassSet(PlayerRef player) {
    addStat(player, "hunter.apex-predator.kinds-tracked", 1);
    cue(player, MutationCue.WORK);
  }

  private void markQuarry(PlayerRef player, EntityRef target) {
    Set<UUID> marked = quarries.computeIfAbsent(player.uuid(), (UUID ignored) -> new LinkedHashSet<>());
    synchronized (marked) {
      marked.remove(target.uuid());
      marked.add(target.uuid());
      while (marked.size() > Math.max(1, getConfig().maxQuarries)) {
        marked.remove(marked.iterator().next());
      }
    }
  }

  /** Counts a pet's bite on {@code target} into its owner's ramp; the stacks it now holds, 0 without this upgrade. */
  public int petHit(PlayerRef owner, EntityRef target) {
    int level = getActiveLevel(owner);
    if (level <= 0) {
      return 0;
    }
    return ramps.hit(owner.uuid(), target.uuid(), getRampCap(level), stacksPerHit(target), getConfig().decayMillis, M.ms());
  }

  int stacksPerHit(EntityRef target) {
    return HunterBigGameHunter.isLargeQuarry(target) ? Math.max(1, getConfig().bigGameStacksPerHit) : 1;
  }

  double getPerStackBonus(int level) {
    return getConfig().perStackBonusBase + (level * getConfig().perStackBonusPerLevel);
  }

  int getRampCap(int level) {
    return Math.max(1, getConfig().rampCapBase + (level * getConfig().rampCapPerLevel));
  }

  double getExtraRollChance(int level) {
    return Math.min(1D, Math.max(0D, getConfig().extraRollChanceBase + (level * getConfig().extraRollChancePerLevel)));
  }

  double getCompassRange(int level) {
    return Math.max(0D, getConfig().compassRangePerLevel * level);
  }

  int getKindsRemembered(int level) {
    int[] kinds = getConfig().kindsRememberedByLevel;
    if (kinds == null || kinds.length == 0) {
      return 1;
    }
    return Math.max(1, kinds[Math.max(0, Math.min(kinds.length, level) - 1)]);
  }

  @ConfigDescription("Repeated hits on one target ramp your damage, big game rolls its drops again, and a compass tracks your prey.")
  public static class Config extends SkillUpgrade.ToolConfig {
    @ConfigDoc(value = "Bonus damage per ramp stack before level scaling.", impact = "Higher values make every stack hit harder.")
    double perStackBonusBase = 0.06;
    @ConfigDoc(value = "Bonus damage per ramp stack added per level.", impact = "Higher values reward higher levels more.")
    double perStackBonusPerLevel = 0.02;
    @ConfigDoc(value = "Ramp stack cap before level scaling.", impact = "Higher values let the ramp climb further.")
    int rampCapBase = 4;
    @ConfigDoc(value = "Ramp stack cap added per level.", impact = "Higher values let higher levels climb further.")
    int rampCapPerLevel = 2;
    @ConfigDoc(value = "Stacks gained per hit on large and boss mobs.", impact = "Higher values ramp faster against big game.")
    int bigGameStacksPerHit = 2;
    @ConfigDoc(value = "Milliseconds without a hit before the ramp resets.", impact = "Higher values forgive slower attacks.")
    long decayMillis = 3500;
    @ConfigDoc(value = "Silent skill XP for each ramped hit.", impact = "Higher values reward sustained focus.")
    double xpPerRampedHit = 2;
    @ConfigDoc(value = "Chance of one more roll of a large or boss mob's own drops before level scaling.", impact = "Higher values give big game extra loot more often.")
    double extraRollChanceBase = 0.1;
    @ConfigDoc(value = "Extra roll chance added per level.", impact = "Higher values reward higher levels more.")
    double extraRollChancePerLevel = 0.2;
    @ConfigDoc(value = "Tracker's Compass range per level, in blocks.", impact = "Higher values find prey farther away in loaded chunks.")
    double compassRangePerLevel = 64;
    @ConfigDoc(value = "Kinds one Tracker's Compass remembers, from level 1 upward.", impact = "Higher values let you switch between more kinds.")
    int[] kindsRememberedByLevel = {1, 3, 6};
    @ConfigDoc(value = "Level from which the compass tracks bosses and named mobs you have hit.", impact = "Lower values unlock quarry tracking sooner.")
    int quarryLevel = 3;
    @ConfigDoc(value = "Bosses and named mobs remembered per player for the compass.", impact = "Higher values keep more quarries tracked.")
    int maxQuarries = 4;
    @ConfigDoc(value = "Killed kinds a compass offers in its menu.", impact = "Higher values keep a longer list.")
    int maxKilledKinds = 18;
    @ConfigDoc(value = "Hunger points paid each time the compass is set to a kind.", impact = "Higher values make setting a kind cost more food.")
    int setHungerCost = 1;
  }
}
