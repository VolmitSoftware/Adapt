package art.arcane.adapt.content.adaptation.tragoul.upgraded;

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.content.adaptation.tragoul.TragoulBlood;
import art.arcane.adapt.content.adaptation.tragoul.TragoulDrain;
import art.arcane.adapt.content.adaptation.tragoul.TragoulPreferences;
import art.arcane.adapt.content.adaptation.tragoul.TragoulReactiveDamage;
import art.arcane.adapt.content.adaptation.tragoul.TragoulReflection;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSiphon;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Soul Siphon, Will of Pain and Thorns fused: every damage source attributed to you heals you, and whoever strikes you
 * is drained into your health and takes thorns damage back. Siphoned healing past full health, from natural mobs and
 * players only, fills a blood reservoir that other parts of the skill spend before your health; it drains while you are
 * out of combat.
 */
public class CrimsonTide extends SkillUpgrade<CrimsonTide.Config> implements TragoulBlood.Reservoir {
  private final Cooldowns reflectCooldowns = cooldowns();
  private final Map<UUID, Pool> pools = playerState();
  private final TragoulSiphon siphon = new TragoulSiphon(this, playerState(), this::getHealPercent,
      this::getHealCapPerSecond, (PlayerRef player, double healed) -> {
        addStat(player, "tragoul.crimson-tide.health-siphoned", healed);
        cue(player, MutationCue.FIGHT);
        fought(player);
      }, this::fill);
  private final TragoulDrain drain = new TragoulDrain(this, this::getDrainDamage, (PlayerRef player, double healed) -> {
    addStat(player, "tragoul.crimson-tide.health-drained", healed);
    cue(player, MutationCue.FIGHT);
  });
  private final TragoulReflection reflection = new TragoulReflection(this,
      (PlayerRef player, double dealt, boolean killed) -> addStat(player, "tragoul.crimson-tide.damage-reflected", dealt));

  public CrimsonTide() {
    super("upgraded-tragoul-crimson-tide");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("redstone_block"));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, TragoulPreferences.PASSIVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getHealPercent(level), 0), 1);
    statLore(v, Form.f(getDrainDamage(level), 1), 2);
    statLore(v, Form.f(getReflectDamage(level), 1), 3);
    statLore(v, Form.f(getReservoirHealth(level) / 2D, 0), 4);
  }

  @Override
  public double draw(PlayerRef player, double amount) {
    Pool pool = pools.get(player.uuid());
    if (pool == null || !(amount > 0D)) {
      return 0D;
    }
    synchronized (pool) {
      settle(pool, System.currentTimeMillis());
      double taken = Math.min(pool.blood, amount);
      pool.blood -= taken;
      return taken;
    }
  }

  @Override
  public double blood(PlayerRef player) {
    Pool pool = pools.get(player.uuid());
    if (pool == null) {
      return 0D;
    }
    synchronized (pool) {
      settle(pool, System.currentTimeMillis());
      return pool.blood;
    }
  }

  @Override
  public double fill(PlayerRef player, double amount) {
    int level = getActiveLevel(player);
    double capacity = getReservoirHealth(level);
    if (level <= 0 || capacity <= 0D || !(amount > 0D)) {
      return 0D;
    }
    long now = System.currentTimeMillis();
    Pool pool = pools.computeIfAbsent(player.uuid(), (UUID ignored) -> new Pool());
    synchronized (pool) {
      settle(pool, now);
      pool.lastCombat = now;
      double before = pool.blood;
      pool.blood = Math.min(capacity, pool.blood + amount);
      double stored = pool.blood - before;
      if (stored > 0D) {
        addStat(player, "tragoul.crimson-tide.blood-stored", stored);
      }
      return stored;
    }
  }

  private void fought(PlayerRef player) {
    Pool pool = pools.get(player.uuid());
    if (pool == null) {
      return;
    }
    long now = System.currentTimeMillis();
    synchronized (pool) {
      settle(pool, now);
      pool.lastCombat = now;
    }
  }

  private void settle(Pool pool, long now) {
    long interval = Math.max(1L, getConfig().bloodDrainIntervalMillis);
    long from = Math.max(pool.lastCombat + Math.max(0L, getConfig().combatWindowMillis), pool.drainedTo);
    if (now <= from) {
      return;
    }
    long steps = (now - from) / interval;
    if (steps > 0L) {
      pool.blood = Math.max(0D, pool.blood - (steps * Math.max(0D, getConfig().bloodDrainPerInterval)));
      pool.drainedTo = from + (steps * interval);
    }
  }
  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageEvent e) {
    siphon.capture(e);
    drain.capture(e);
  }

  @Handle
  public void on(EntityDamageByEntityEvent e) {
    if (TragoulReactiveDamage.isActive() || !(e.entity() instanceof PlayerRef p)) {
      return;
    }
    fought(p);
    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      long cooldownMillis = (long) getConfig().reflectCooldownMillis;
      LivingRef attacker = TragoulReflection.attacker(e.damager());
      if (level <= 0 || attacker == null || !reflectCooldowns.isReady(p.uuid(), cooldownMillis)) {
        return;
      }
      reflectCooldowns.mark(p.uuid(), cooldownMillis);
      reflection.reflect(p, attacker, getReflectDamage(level),
          getConfig().ignorePassiveMobs || preferenceEnabled(p, TragoulPreferences.PASSIVE));
    });
  }

  @Override
  public void unregister() {
    siphon.stop();
    drain.stop();
    pools.clear();
    super.unregister();
  }

  double getHealPercent(int level) {
    return Math.max(0D, getConfig().healPercentBase + (level * getConfig().healPercentPerLevel));
  }

  double getHealCapPerSecond(int level) {
    return Math.max(0.5D, getConfig().healCapPerSecondBase + (level * getConfig().healCapPerSecondPerLevel));
  }

  double getDrainDamage(int level) {
    return Math.max(0D, getConfig().drainDamageBase + (level * getConfig().drainDamagePerLevel));
  }

  double getReflectDamage(int level) {
    return Math.max(0D, getConfig().reflectDamageBase + (level * getConfig().reflectDamagePerLevel));
  }

  double getReservoirHealth(int level) {
    return level <= 0 ? 0D : Math.max(0D, getConfig().reservoirHeartsBase + (level * getConfig().reservoirHeartsPerLevel)) * 2D;
  }

  private static final class Pool {
    private double blood;
    private long lastCombat;
    private long drainedTo;
  }

  @ConfigDescription("Damage you deal heals you, and attackers are drained into your health and take thorns damage.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Fraction of attributed damage returned as healing before level scaling.", impact = "Higher values heal more from every hit you deal.")
    double healPercentBase = 0.15;
    @ConfigDoc(value = "Healing fraction added per level.", impact = "Higher values reward higher levels more.")
    double healPercentPerLevel = 0.10;
    @ConfigDoc(value = "Most health siphoned per second before level scaling.", impact = "Lower values limit healing from rapid or multi-target damage.")
    double healCapPerSecondBase = 3.0;
    @ConfigDoc(value = "Siphon cap per second added per level.", impact = "Higher values let higher levels sustain more healing.")
    double healCapPerSecondPerLevel = 2.0;
    @ConfigDoc(value = "Life drained from each attacker before level scaling.", impact = "Higher values punish and heal more on every hit you take.")
    double drainDamageBase = 0.5;
    @ConfigDoc(value = "Drained life added per level.", impact = "Higher values reward higher levels more.")
    double drainDamagePerLevel = 0.5;
    @ConfigDoc(value = "Thorns damage reflected before level scaling.", impact = "Higher values hurt attackers more.")
    double reflectDamageBase = 1.5;
    @ConfigDoc(value = "Thorns damage added per level.", impact = "Higher values reward higher levels more.")
    double reflectDamagePerLevel = 1.5;
    @ConfigDoc(value = "Time between two thorns reflections, in milliseconds.", impact = "Lower values reflect more often against fast attackers.")
    double reflectCooldownMillis = 1000;
    @ConfigDoc(value = "Exclude passive and neutral mobs from reflected damage.", impact = "When enabled, neutral species are never hurt by the thorns.")
    boolean ignorePassiveMobs = false;
    @ConfigDoc(value = "Blood reservoir size in hearts before level scaling.", impact = "Higher values store more overflow healing.")
    double reservoirHeartsBase = 1;
    @ConfigDoc(value = "Blood reservoir hearts added per level.", impact = "Higher values reward higher levels more.")
    double reservoirHeartsPerLevel = 3;
    @ConfigDoc(value = "Time after your last hit dealt or taken before blood starts to drain, in milliseconds.", impact = "Higher values keep blood longer between fights.")
    long combatWindowMillis = 10000;
    @ConfigDoc(value = "Time between two drains of blood out of combat, in milliseconds.", impact = "Lower values drain the reservoir faster.")
    long bloodDrainIntervalMillis = 10000;
    @ConfigDoc(value = "Blood lost at each drain out of combat, in health points; 2 is one heart.", impact = "Higher values empty the reservoir faster.")
    double bloodDrainPerInterval = 2;
  }
}
