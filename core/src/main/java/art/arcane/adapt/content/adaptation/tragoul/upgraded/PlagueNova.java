package art.arcane.adapt.content.adaptation.tragoul.upgraded;

import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.content.adaptation.tragoul.TragoulBlood;
import art.arcane.adapt.content.adaptation.tragoul.TragoulLances;
import art.arcane.adapt.content.adaptation.tragoul.TragoulNova;
import art.arcane.adapt.content.adaptation.tragoul.TragoulPlague;
import art.arcane.adapt.content.adaptation.tragoul.TragoulPreferences;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

/**
 * Corpse Explosion, Plague Bearer and Corpse Lances fused: every kill bursts in a blood nova and launches a seeking
 * lance, and a kill that was poisoned or withered spreads its affliction to the mobs around it. A lance's health cost
 * comes out of the blood reservoir first.
 */
public class PlagueNova extends SkillUpgrade<PlagueNova.Config> {
  private final TragoulNova nova = new TragoulNova(this, new TragoulNova.Settings() {
    @Override
    public boolean ignorePassiveMobs() {
      return getConfig().ignorePassiveMobs;
    }

    @Override
    public long chainSuppressionMillis() {
      return getConfig().novaChainSuppressionMillis;
    }

    @Override
    public int maxTargets() {
      return getConfig().novaMaxTargets;
    }

    @Override
    public double radius(int level) {
      return getNovaRadius(level);
    }

    @Override
    public double damage(int level, double victimMaxHealth) {
      return Math.min(getConfig().novaMaxDamage, getConfig().novaBaseDamage + (victimMaxHealth * getVictimHealthFraction(level)));
    }
  }, (PlayerRef player, List<LivingRef> hits) -> {
    addStat(player, "tragoul.plague-nova.mobs-detonated", hits.size());
    xpOnMobs(player, hits, getConfig().xpPerMobHit);
    cue(player, MutationCue.FIGHT);
  });
  private final TragoulPlague plague = new TragoulPlague(this, new TragoulPlague.Settings() {
    @Override
    public boolean ignorePassiveMobs() {
      return getConfig().ignorePassiveMobs;
    }

    @Override
    public int maxSpreadTargets() {
      return getConfig().plagueMaxSpreadTargets;
    }

    @Override
    public int amplifierBonus() {
      return getConfig().plagueAmplifierBonus;
    }

    @Override
    public int maxGenerations() {
      return getConfig().plagueMaxGenerations;
    }

    @Override
    public long afflictionFreshnessMillis() {
      return getConfig().plagueFreshnessMillis;
    }

    @Override
    public double spreadRadius(int level) {
      return getSpreadRadius(level);
    }

    @Override
    public int spreadDurationTicks(int level) {
      return TragoulPlague.durationTicks(getConfig().plagueDurationTicksBase + (level * getConfig().plagueDurationTicksPerLevel));
    }
  }, (PlayerRef player, int spread) -> {
    addStat(player, "tragoul.plague-nova.mobs-infected", spread);
    cue(player, MutationCue.FIGHT);
  });
  private final TragoulLances lances = new TragoulLances(this, new TragoulLances.Settings() {
    @Override
    public boolean ignorePassiveMobs() {
      return getConfig().ignorePassiveMobs;
    }

    @Override
    public int seekerDelay() {
      return getConfig().lanceSeekerDelay;
    }

    @Override
    public double seekerDamageMultiplier() {
      return getConfig().lanceDamageMultiplier;
    }

    @Override
    public double unarmoredDamageMultiplier() {
      return getConfig().lanceUnarmoredDamageMultiplier;
    }

    @Override
    public double selfDamage(int level) {
      return Math.max(0D, getConfig().lanceSelfDamage);
    }
  }, cooldowns(), (PlayerRef player, boolean killed) -> addStat(player, "tragoul.plague-nova.lances-landed", 1),
      this::payLance);

  public PlagueNova() {
    super("upgraded-tragoul-plague-nova");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("poisonous_potato"));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, TragoulPreferences.PASSIVE, TragoulPreferences.TARGETS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getNovaRadius(level), 1), 1);
    statLore(v, Form.f(getSpreadRadius(level), 1), 2);
    statLore(v, Form.pc(getVictimHealthFraction(level), 0), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    plague.mark(e);
  }

  @Handle(priority = EventPriority.LOWEST)
  public void onLance(EntityDeathEvent e) {
    lances.launch(e);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    plague.spread(e);
    PlayerRef killer = e.entity().killer();
    if (killer != null && !GolemKillCredit.isGolemKill(e.entity())) {
      nova.request(killer, e.entity(), false);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    lances.cancel(e.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    lances.cancel(e.player().uuid());
  }

  @Override
  public void unregister() {
    plague.stop();
    lances.stop();
    super.unregister();
  }

  private void payLance(PlayerRef player, double cost) {
    double rest = cost - TragoulBlood.draw(this, player, cost);
    if (rest > 0D) {
      applyPlayerDamage(player, rest);
    }
  }

  double getNovaRadius(int level) {
    return Math.min(TragoulNova.MAX_RADIUS, Math.max(1D, getConfig().novaRadiusBase + (level * getConfig().novaRadiusPerLevel)));
  }

  double getVictimHealthFraction(int level) {
    return Math.max(0D, getConfig().victimHealthFractionBase + (level * getConfig().victimHealthFractionPerLevel));
  }

  double getSpreadRadius(int level) {
    return TragoulPlague.spreadRadius(getConfig().plagueRadiusBase + (level * getConfig().plagueRadiusPerLevel));
  }

  @ConfigDescription("Kills burst in a blood nova and launch a seeking lance; poisoned or withered kills spread their affliction.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Exclude passive and neutral mobs from the nova, the plague and the lances.", impact = "When enabled, neutral species are never hit by any part of the upgrade.")
    boolean ignorePassiveMobs = false;
    @ConfigDoc(value = "Nova radius before level scaling, in blocks.", impact = "Higher values hit mobs farther from the corpse.")
    double novaRadiusBase = 4.0;
    @ConfigDoc(value = "Nova radius added per level, in blocks.", impact = "Higher values widen the nova at higher levels; capped at 16.")
    double novaRadiusPerLevel = 1.5;
    @ConfigDoc(value = "Flat nova damage to every mob hit.", impact = "Higher values make every burst hit harder.")
    double novaBaseDamage = 3.0;
    @ConfigDoc(value = "Share of the corpse's maximum health added to nova damage, before level scaling.", impact = "Higher values make tough kills burst harder.")
    double victimHealthFractionBase = 0.15;
    @ConfigDoc(value = "Corpse health share added per level.", impact = "Higher values reward higher levels more.")
    double victimHealthFractionPerLevel = 0.10;
    @ConfigDoc(value = "Most nova damage dealt to one mob.", impact = "Stops boss kills from one-shotting crowds.")
    double novaMaxDamage = 24.0;
    @ConfigDoc(value = "Most mobs one nova hits; at most 16.", impact = "Lower values cap work and damage in crowds.")
    int novaMaxTargets = 12;
    @ConfigDoc(value = "Time a nova-struck mob cannot burst again, in milliseconds.", impact = "Higher values stop chain reactions sooner.")
    long novaChainSuppressionMillis = 5000;
    @ConfigDoc(value = "Plague spread radius before level scaling, in blocks.", impact = "Higher values infect mobs farther from the corpse.")
    double plagueRadiusBase = 8.0;
    @ConfigDoc(value = "Plague spread radius added per level, in blocks.", impact = "Higher values widen the spread at higher levels; capped at 24.")
    double plagueRadiusPerLevel = 3.0;
    @ConfigDoc(value = "Spread affliction duration before level scaling, in ticks.", impact = "Higher values keep infected mobs afflicted longer.")
    double plagueDurationTicksBase = 100;
    @ConfigDoc(value = "Spread affliction duration added per level, in ticks.", impact = "Higher values reward higher levels more.")
    double plagueDurationTicksPerLevel = 40;
    @ConfigDoc(value = "Most mobs infected by one death; at most 8.", impact = "Lower values cap work per death.")
    int plagueMaxSpreadTargets = 6;
    @ConfigDoc(value = "Potion amplifier levels added when the affliction spreads.", impact = "Higher values make spread poison and wither stronger.")
    int plagueAmplifierBonus = 1;
    @ConfigDoc(value = "Most generations one affliction can jump through; at most 4.", impact = "Caps how far a plague cascades.")
    int plagueMaxGenerations = 3;
    @ConfigDoc(value = "How long a mob stays marked after your hit, in milliseconds.", impact = "Higher values let older poisons still spread on death.")
    long plagueFreshnessMillis = 15000;
    @ConfigDoc(value = "Lance damage as a multiple of the killing blow.", impact = "Higher values make lances hit harder; clamped to 4.")
    double lanceDamageMultiplier = 1.0;
    @ConfigDoc(value = "Lance damage multiplier while you wear no armor.", impact = "Higher values reward fighting unarmored; clamped to 10.")
    double lanceUnarmoredDamageMultiplier = 3.0;
    @ConfigDoc(value = "Delay before a lance lands, in ticks.", impact = "Lower values make lances strike sooner; clamped to 1 to 40.")
    int lanceSeekerDelay = 10;
    @ConfigDoc(value = "Damage you take when a lance lands.", impact = "Higher values make lances cost more health.")
    double lanceSelfDamage = 1.0;
    @ConfigDoc(value = "Skill XP for each mob a nova hits.", impact = "Higher values reward bursting crowds.")
    double xpPerMobHit = 6;
  }
}
