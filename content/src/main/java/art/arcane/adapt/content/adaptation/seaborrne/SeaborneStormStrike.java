package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.ability.AbilityDamage;
import art.arcane.adapt.api.ability.Minions;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Melee and trident hits call lightning while the attacker is wet: the first hit on each target and every few hits
 * after it. The bolt is drawn with particles and sounds and its damage rides on the hit itself, so it starts no fire,
 * charges no creeper, converts no mob, powers no lightning rod and passes every protection and PvP check the hit
 * already passed. Farmed mobs, minions and the attacker's own pets are never struck.
 */
public class SeaborneStormStrike extends SimpleAdaptation<SeaborneStormStrike.Config> {
  public static final Id ID = Id.adapt("seaborne/storm_strike");
  private static final double BOLT_HEIGHT = 7D;
  private static final int BOLT_SEGMENTS = 4;
  private static final int MAX_REMEMBERED_TARGETS = 32;

  private final Map<UUID, Tally> tallies = playerState();

  public SeaborneStormStrike() {
    super(ID);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.seaborn.storm_strike");
    setIcon(Id.minecraft("lightning_rod"));
  }


  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getDamage(level, false), 1), 1);
    statLore(v, getStrikeEvery(level, false), 2);
    statLore(v, Format.f(getDamage(level, true), 1), 3);
    statLore(v, getStrikeEvery(level, true), 4);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    Strike strike = resolve(e);
    if (strike == null) {
      return;
    }
    PlayerRef attacker = strike.attacker();
    LivingRef target = strike.target();
    if (!SeaborneWet.isWet(attacker) || attacker.uuid().equals(target.tameOwner()) || Minions.isMinion(target)
        || !strikes(target)) {
      return;
    }

    boolean thunder = SeaborneWet.isInThunderstorm(attacker);
    long now = System.currentTimeMillis();
    Tally tally = tallies.computeIfAbsent(attacker.uuid(), (UUID ignored) -> new Tally());
    if (!tally.hit(target.uuid(), now, getStrikeEvery(strike.level(), thunder), getConfig())) {
      return;
    }

    double damage = getDamage(strike.level(), thunder);
    e.setDamage(e.damage() + damage);
    bolt(target, thunder);
    addStat(attacker, "seaborne.storm-strike.strikes", 1);
    if (!(target instanceof PlayerRef) && MobOrigin.paysKillReward(attacker, target)) {
      xp(attacker, target.location(), getConfig().xpPerStrike);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    tallies.remove(e.player().uuid());
  }

  /** A direct melee hit or a thrown trident's hit on a living target the attacker may damage; null otherwise. */
  private Strike resolve(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof LivingRef target) || AbilityDamage.dealing()) {
      return null;
    }
    PlayerRef attacker;
    if (e.cause() == DamageCause.ENTITY_ATTACK && e.damager() instanceof PlayerRef melee) {
      attacker = melee;
    } else if (e.damager() instanceof ProjectileRef thrown && SeaborneTridents.TRIDENT.equals(thrown.type())
        && thrown.shooter() instanceof PlayerRef thrower) {
      attacker = thrower;
    } else {
      return null;
    }
    int level = getActiveLevel(attacker);
    if (level <= 0 || attacker.uuid().equals(target.uuid()) || !canDamageTarget(attacker, target)) {
      return null;
    }
    return new Strike(attacker, target, level);
  }

  private boolean strikes(LivingRef target) {
    if (target instanceof PlayerRef) {
      return getConfig().strikePlayers;
    }
    return target.has(EntityTrait.ENEMY) || getConfig().strikePassiveMobs;
  }

  private void bolt(LivingRef target, boolean thunder) {
    Loc ground = target.location();
    Loc from = ground.add(0D, BOLT_HEIGHT, 0D);
    for (int segment = 1; segment <= BOLT_SEGMENTS; segment++) {
      double drop = BOLT_HEIGHT * segment / BOLT_SEGMENTS;
      double sway = segment == BOLT_SEGMENTS ? 0D : (segment % 2 == 0 ? 0.45D : -0.45D);
      Loc to = ground.add(sway, BOLT_HEIGHT - drop, -sway);
      fx(from, FxPriority.COMBAT).line(Particles.ELECTRIC_SPARK, to.x(), to.y(), to.z(), 6);
      from = to;
    }
    fx(ground.add(0D, 1D, 0D), FxPriority.COMBAT)
        .particle(Particles.FLASH, 1, 0D, 0D, 0D, 0D, 0D)
        .burst(Particles.ELECTRIC_SPARK, thunder ? 16 : 10, 0.4D)
        .chord(Sounds.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7F, 1.4F, Sounds.ITEM_TRIDENT_THUNDER, thunder ? 0.5F : 0.3F, 1.6F);
  }

  double getDamage(int level, boolean thunder) {
    double damage = getConfig().damageAtLevelOne + (progress(level) * (getConfig().damageAtTopLevel - getConfig().damageAtLevelOne));
    return Math.max(0D, damage * (thunder ? Math.max(1D, getConfig().thunderDamageMultiplier) : 1D));
  }

  int getStrikeEvery(int level, boolean thunder) {
    double every = getConfig().strikeEveryAtLevelOne
        + (progress(level) * (getConfig().strikeEveryAtTopLevel - getConfig().strikeEveryAtLevelOne));
    int rounded = (int) Math.round(every) - (thunder ? Math.max(0, getConfig().thunderStrikeEveryReduction) : 0);
    return Math.max(Math.max(2, getConfig().minimumStrikeEvery), rounded);
  }

  private double progress(int level) {
    int max = getMaxLevel();
    return max <= 1 ? 1D : Math.max(0D, Math.min(1D, (level - 1D) / (max - 1D)));
  }

  private record Strike(PlayerRef attacker, LivingRef target, int level) {
  }

  /** One attacker's hit counts per target, forgetting a target after a quiet spell. */
  static final class Tally {
    private final Map<UUID, Count> targets = new LinkedHashMap<>();
    private long lastStrikeAt;

    /** Counts a hit on {@code target} at {@code now}; true when it calls a strike. */
    synchronized boolean hit(UUID target, long now, int every, Config config) {
      forget(now, Math.max(1000L, config.targetMemoryMillis));
      Count count = targets.remove(target);
      if (count == null) {
        count = new Count();
      }
      targets.put(target, count);
      while (targets.size() > MAX_REMEMBERED_TARGETS) {
        Iterator<UUID> oldest = targets.keySet().iterator();
        oldest.next();
        oldest.remove();
      }
      count.lastHitAt = now;
      count.sinceStrike++;
      boolean due = count.struckAt == 0L || count.sinceStrike >= every;
      if (!due || now - count.struckAt < Math.max(0L, config.targetStrikeGapMillis)
          || now - lastStrikeAt < Math.max(0L, config.strikeGapMillis)) {
        return false;
      }
      count.sinceStrike = 0;
      count.struckAt = now;
      lastStrikeAt = now;
      return true;
    }

    private void forget(long now, long memory) {
      targets.values().removeIf((Count count) -> now - count.lastHitAt > memory);
    }
  }

  private static final class Count {
    private int sinceStrike;
    private long lastHitAt;
    private long struckAt;
  }

  @ConfigDescription("While rain falls on you or you are in water, your melee and trident hits call lightning on the first hit against each target and every few hits after it; thunderstorms strike harder and more often.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Lightning damage added to the hit at level 1.", impact = "Higher values make every strike hit harder.")
    double damageAtLevelOne = 3;
    @ConfigDoc(value = "Lightning damage added to the hit at the top level.", impact = "Higher values reward higher levels more.")
    double damageAtTopLevel = 7;
    @ConfigDoc(value = "Damage multiplier while you stand in a thunderstorm's rain.", impact = "Higher values make thunderstorms more dangerous for your targets.")
    double thunderDamageMultiplier = 1.5;
    @ConfigDoc(value = "Hits on one target from one strike to the next at level 1; the first hit always strikes.", impact = "Lower values strike more often.")
    int strikeEveryAtLevelOne = 3;
    @ConfigDoc(value = "Hits on one target from one strike to the next at the top level.", impact = "Lower values strike more often at the top level.")
    int strikeEveryAtTopLevel = 2;
    @ConfigDoc(value = "Hits removed from the count in a thunderstorm.", impact = "Higher values make thunderstorms strike more often.")
    int thunderStrikeEveryReduction = 1;
    @ConfigDoc(value = "Fewest hits from one strike to the next; never below 2.", impact = "Higher values keep strikes from landing on back-to-back hits.")
    int minimumStrikeEvery = 2;
    @ConfigDoc(value = "Shortest time between two strikes on the same target, in milliseconds.", impact = "Higher values limit strikes on one target.")
    long targetStrikeGapMillis = 1500;
    @ConfigDoc(value = "Shortest time between any two of your strikes, in milliseconds.", impact = "Higher values limit strikes against crowds.")
    long strikeGapMillis = 600;
    @ConfigDoc(value = "Time a target is remembered after your last hit on it, in milliseconds; after that the next hit counts as the first again.", impact = "Lower values let returning to a target strike again sooner.")
    long targetMemoryMillis = 20000;
    @ConfigDoc(value = "Seaborne XP for each strike on a natural mob; strikes on players pay none.", impact = "Higher values level Seaborne faster in storms.")
    double xpPerStrike = 4;
    @ConfigDoc(value = "Whether strikes land on mobs that are not hostile, such as animals, villagers and mobs that only fight back.", impact = "True lets lightning hit passive and neutral mobs.")
    boolean strikePassiveMobs = false;
    @ConfigDoc(value = "Whether strikes land on other players where PvP is allowed.", impact = "True lets lightning hit players.")
    boolean strikePlayers = false;

    public Config() {
      baseCost = 5;
      initialCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
    }
  }
}
