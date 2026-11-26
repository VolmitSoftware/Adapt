package art.arcane.adapt.content.adaptation.hunter.upgraded;

import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.content.adaptation.hunter.HunterCaptures;
import art.arcane.adapt.content.adaptation.hunter.HunterPreferences;
import art.arcane.adapt.content.adaptation.hunter.HunterSnares;
import art.arcane.adapt.content.adaptation.hunter.HunterTrophies;
import art.arcane.adapt.content.adaptation.hunter.HunterWounds;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.ItemSpawnEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Blood Trail, Snare Line and Trophy Skinner fused: wounded prey leaves a private trail, placed snares root mobs for
 * longer and catch animals alive to be led home, and precise kills drop larger trophies more often.
 */
public class StalkersTrail extends SkillUpgrade<StalkersTrail.Config> {
  private final HunterWounds wounds = new HunterWounds(this,
      (PlayerRef hunter) -> addStat(hunter, "hunter.stalkers-trail.trails-followed", 1));
  private final HunterCaptures captures = new HunterCaptures(this);
  private final HunterSnares snares = new HunterSnares(this, (PlayerRef owner, LivingRef snared) -> {
    addStat(owner, "hunter.stalkers-trail.mobs-snared", 1);
    xpOn(owner, snared, getConfig().xpPerSnare);
  }, new HunterSnares.Capture(captures, this::getCaptureHoldTicks, () -> getConfig().maxCaptivesPerPlayer));

  public StalkersTrail() {
    super("upgraded-hunter-stalkers-trail");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("tripwire_hook"));
    setInterval(250);
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, HunterPreferences.TRAIL, HunterPreferences.GLOW, HunterPreferences.SNEAK);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    UUID id = player.getPlayer().uuid();
    wounds.forget(id);
    if (getActiveLevel(player.getPlayer()) <= 0) {
      snares.forget(id);
      captures.forget(id);
      AbilityGlyphs.clear(id, this, HudGlyph.CHARGES);
    }
  }

  @Override
  public void unregister() {
    wounds.clear();
    super.unregister();
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.duration(getTrailDurationTicks(level) * 50L, 1), 1);
    statLore(v, Form.duration(getRootDurationTicks(level) * 50L, 1), 2);
    statLore(v, Form.pc(getTrophyChance(level), 0), 3);
    statLore(v, Form.duration(getCaptureHoldTicks(level) * 50L, 0), 4);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    AttackContext attack = resolveAttackContext(e);
    if (attack == null || attack.target() instanceof PlayerRef || !(attack.target() instanceof LivingRef victim)) {
      return;
    }

    if (!HunterWounds.isWounded(victim.health() - e.finalDamage(), victim.maxHealth(), getConfig().woundHealthFraction)) {
      return;
    }

    PlayerRef p = attack.attacker();
    int level = attack.level();
    if (wounds.mark(p, victim, getTrailDurationTicks(level), getTrackingRange(level), getConfig().maxTrackedWounds)) {
      xpSilentOn(p, victim, getConfig().xpPerWound);
      cue(p, MutationCue.FIGHT);
    }
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerInteractEvent e) {
    if (hasActiveAdaptation(e.player())
        && snares.place(e, snareSettings(), this::getCharges, this::getRootDurationTicks)) {
      cue(e.player(), MutationCue.WORK);
    }
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    snares.forget(e.player().uuid());
    captures.forget(e.player().uuid());
    wounds.forget(e.player().uuid());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(ItemSpawnEvent e) {
    if (captures.slippedLead(e.item().item(), e.location())) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDeathEvent e) {
    PlayerRef killer = e.killer();
    LivingRef victim = e.entity();
    if (killer == null || GolemKillCredit.isGolemKill(victim)
        || !hasActiveAdaptation(killer) || victim instanceof PlayerRef || !canDamageTarget(killer, victim)
        || MutationPayouts.farmed(killer, victim)) {
      return;
    }

    int level = getActiveLevel(killer);
    HunterTrophies.Precision precision = HunterTrophies.precision(e, killer, getMinimumRange(level));
    if (!precision.precise()) {
      return;
    }

    HunterTrophies.DropOutcome outcome = HunterTrophies.decideDrops(ThreadLocalRandom.current().nextDouble(),
        ThreadLocalRandom.current().nextDouble(), getTrophyChance(level), getHeadChance(level));
    boolean dropped = false;
    if (outcome.trophy()) {
      int amount = getTrophyAmount(level) + (precision.projectileKill() ? 1 : 0);
      e.drops().add(HunterTrophies.trophyDrop(victim.type(), amount));
      addStat(killer, "hunter.stalkers-trail.trophies-collected", 1);
      xpOn(killer, victim, getConfig().xpPerTrophy);
      dropped = true;
    }
    if (outcome.head()) {
      ItemRef head = HunterTrophies.headDrop(victim.type());
      if (head != null) {
        e.drops().add(head);
        addStat(killer, "hunter.stalkers-trail.heads-collected", 1);
        dropped = true;
      }
    }
    if (dropped) {
      cue(killer, MutationCue.BOON);
      fx(victim.location().add(0, victim.height() * 0.5D, 0), FxPriority.COMBAT)
          .particle(Particles.VILLAGER_HAPPY, 8, 0, 0.2D, 0, 0.3D, 0);
    }
  }

  /** The nearest prey whose blood trail {@code hunter} can see, or null. */
  public EntityRef trailedPrey(PlayerRef hunter) {
    return hasActiveAdaptation(hunter) ? wounds.nearestPrey(hunter) : null;
  }

  @Override
  public boolean hasTickDemand() {
    return !wounds.isEmpty() || !snares.isEmpty() || !captures.isEmpty();
  }

  @Override
  public void onTick() {
    wounds.tick(getConfig().maxTrackedWounds, new HunterWounds.Style(getConfig().trailThickness, getConfig().displayDurationTicks));
    snares.tick(snareSettings());
    captures.tick(new HunterCaptures.Settings(getConfig().leadRange, getConfig().leadBreakRange,
        getConfig().maxCaptivesPerPlayer));
  }

  private HunterSnares.Settings snareSettings() {
    Config config = getConfig();
    return new HunterSnares.Settings(config.triggerRadius, config.rootAmplifier, config.rearmBufferMillis,
        config.snareLifetimeTicks, config.maxSnaresPerPlayer, config.maxActiveSnares, config.maxTargetsPerScan);
  }

  int getCaptureHoldTicks(int level) {
    return Math.max(1, getConfig().captureHoldTicksBase) << Math.max(0, Math.min(level, 8) - 1);
  }

  long getTrailDurationTicks(int level) {
    return getConfig().trailDurationTicksBase + ((long) level * getConfig().trailDurationTicksPerLevel);
  }

  double getTrackingRange(int level) {
    return getConfig().trackingRangeBase + (level * getConfig().trackingRangePerLevel);
  }

  int getRootDurationTicks(int level) {
    return Math.max(1, getConfig().rootDurationTicksBase + (level * getConfig().rootDurationTicksPerLevel));
  }

  int getCharges(int level) {
    return Math.max(1, getConfig().chargesBase + (level * getConfig().chargesPerLevel));
  }

  double getTrophyChance(int level) {
    return Math.min(1D, getConfig().trophyChanceBase + (level * getConfig().trophyChancePerLevel));
  }

  double getHeadChance(int level) {
    return Math.min(1D, getConfig().headChanceBase + (level * getConfig().headChancePerLevel));
  }

  int getTrophyAmount(int level) {
    return Math.max(1, getConfig().trophyAmountBase + (level * getConfig().trophyAmountPerLevel));
  }

  double getMinimumRange(int level) {
    return Math.max(4D, getConfig().minimumRangeBase - (level * getConfig().minimumRangePerLevel));
  }

  @ConfigDescription("Wounded prey leaves a private trail, snares hold longer and catch animals alive, and precise kills drop more trophies.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Fraction of max health a target must fall to or below to leave a trail.", impact = "Higher values start trails earlier in the fight.")
    double woundHealthFraction = 0.5;
    @ConfigDoc(value = "Blood trail duration before level scaling, in ticks.", impact = "Higher values keep trails longer.")
    long trailDurationTicksBase = 200;
    @ConfigDoc(value = "Blood trail duration added per level, in ticks.", impact = "Higher values reward higher levels more.")
    long trailDurationTicksPerLevel = 100;
    @ConfigDoc(value = "Distance a trail stays visible before level scaling, in blocks.", impact = "Higher values let you track from farther away.")
    double trackingRangeBase = 32;
    @ConfigDoc(value = "Tracking distance added per level, in blocks.", impact = "Higher values reward higher levels more.")
    double trackingRangePerLevel = 8;
    @ConfigDoc(value = "Most trails tracked at once on the server.", impact = "Lower values reduce scheduling and particle work.")
    int maxTrackedWounds = 64;
    @ConfigDoc(value = "Thickness of each trail segment.", impact = "Higher values make the trail easier to follow.")
    double trailThickness = 0.06;
    @ConfigDoc(value = "How long each trail segment stays, in ticks.", impact = "Higher values leave a longer trail behind the prey.")
    int displayDurationTicks = 30;
    @ConfigDoc(value = "Silent skill XP when a new target starts bleeding.", impact = "Higher values reward wounding prey.")
    double xpPerWound = 3;
    @ConfigDoc(value = "Snare root duration before level scaling, in ticks.", impact = "Higher values hold snared mobs longer.")
    int rootDurationTicksBase = 60;
    @ConfigDoc(value = "Snare root duration added per level, in ticks.", impact = "Higher values reward higher levels more.")
    int rootDurationTicksPerLevel = 30;
    @ConfigDoc(value = "Trigger charges per snare before level scaling.", impact = "Higher values let one snare catch more mobs.")
    int chargesBase = 4;
    @ConfigDoc(value = "Trigger charges added per level.", impact = "Higher values reward higher levels more.")
    int chargesPerLevel = 1;
    @ConfigDoc(value = "Snare trigger radius, in blocks.", impact = "Higher values catch mobs from farther away.")
    double triggerRadius = 1.6;
    @ConfigDoc(value = "Slowness-equivalent amplifier of the root; each step removes 15% speed, up to a full stop.", impact = "Higher values hold rooted mobs more firmly.")
    int rootAmplifier = 6;
    @ConfigDoc(value = "Milliseconds a snared mob cannot set off the same snare again after its root ends.", impact = "Higher values spread charges across more mobs.")
    long rearmBufferMillis = 500;
    @ConfigDoc(value = "How long a placed snare lasts, in ticks.", impact = "Higher values keep snares armed longer.")
    long snareLifetimeTicks = 2400;
    @ConfigDoc(value = "Most snares one player can keep placed.", impact = "Lower values reduce tracked snare state.")
    int maxSnaresPerPlayer = 4;
    @ConfigDoc(value = "Most snares placed with this upgrade on the server.", impact = "Lower values reduce scan work.")
    int maxActiveSnares = 64;
    @ConfigDoc(value = "Most mobs one snare roots per scan.", impact = "Lower values reduce work in crowds.")
    int maxTargetsPerScan = 8;
    @ConfigDoc(value = "Skill XP for each mob snared.", impact = "Higher values reward trapping.")
    double xpPerSnare = 6;
    @ConfigDoc(value = "Trophy chance on a precise kill before level scaling.", impact = "Higher values drop trophies more often.")
    double trophyChanceBase = 0.3;
    @ConfigDoc(value = "Trophy chance added per level.", impact = "Higher values reward higher levels more.")
    double trophyChancePerLevel = 0.1;
    @ConfigDoc(value = "Head chance on a precise kill before level scaling.", impact = "Higher values drop heads more often.")
    double headChanceBase = 0.04;
    @ConfigDoc(value = "Head chance added per level.", impact = "Higher values reward higher levels more.")
    double headChancePerLevel = 0.03;
    @ConfigDoc(value = "Trophy items per drop before level scaling.", impact = "Higher values drop larger trophies.")
    int trophyAmountBase = 2;
    @ConfigDoc(value = "Trophy items added per level.", impact = "Higher values reward higher levels more.")
    int trophyAmountPerLevel = 1;
    @ConfigDoc(value = "Shortest bow-kill distance that counts as precise before level scaling, in blocks.", impact = "Lower values make ranged kills count sooner.")
    double minimumRangeBase = 14;
    @ConfigDoc(value = "Precise-kill distance removed per level, in blocks.", impact = "Higher values make ranged precision easier at higher levels.")
    double minimumRangePerLevel = 2;
    @ConfigDoc(value = "Skill XP for each trophy drop.", impact = "Higher values reward precise kills.")
    double xpPerTrophy = 16;
    @ConfigDoc(value = "How long a snare holds a caught animal at level 1, in ticks; it doubles each level.", impact = "Higher values give you longer to reach your catch.")
    int captureHoldTicksBase = 600;
    @ConfigDoc(value = "Distance at which a caught animal is tied to you with a lead, in blocks.", impact = "Higher values let you take a catch from farther away.")
    double leadRange = 6;
    @ConfigDoc(value = "Distance at which a led animal slips its lead, in blocks.", impact = "Higher values let you lead faster or around more obstacles.")
    double leadBreakRange = 12;
    @ConfigDoc(value = "Most caught animals one player can hold or lead at once.", impact = "Lower values limit herding.")
    int maxCaptivesPerPlayer = 4;
  }
}
