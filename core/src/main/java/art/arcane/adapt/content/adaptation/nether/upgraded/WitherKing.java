package art.arcane.adapt.content.adaptation.nether.upgraded;

import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.content.adaptation.nether.NetherSkeletonHarvest;
import art.arcane.adapt.content.adaptation.nether.NetherSkullToss;
import art.arcane.adapt.content.adaptation.nether.NetherSkullYeet;
import art.arcane.adapt.content.adaptation.nether.NetherWitherHarvest;
import art.arcane.adapt.content.adaptation.nether.NetherWitherResist;
import art.arcane.adapt.content.adaptation.nether.NetherWitherWard;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Skull Toss, Wither Resistance and Wither Harvest fused: wither skeleton skulls are thrown as wither skulls, wither
 * damage is shrugged off with or without netherite, and wither skeletons drop more bones, coal and skulls.
 */
public class WitherKing extends SkillUpgrade<WitherKing.Config> {
  private final NetherSkullToss toss = new NetherSkullToss(this);
  private final NetherWitherWard ward = new NetherWitherWard(this::fx, cooldowns());

  public WitherKing() {
    super("upgraded-nether-wither-king");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("wither_skeleton_skull"));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, NetherSkullYeet.SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.duration(getSkullCooldownMillis(level), 0), 1);
    statLore(v, Form.pc(getShrugChance(level, 0) / 100D, 0), 2);
    statLore(v, Form.pc(getSkullChance(level), 0), 3);
  }

  @ReceiveCancelledEvents
  @Handle(ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    PlayerRef p = e.player();
    if (preferenceEnabled(p, NetherSkullYeet.SNEAK) && !p.isSneaking()) {
      return;
    }
    withAdaptedPlayer(p, () -> {
      if (toss.toss(e, p, getSkullCooldownMillis(getLevel(p)), getConfig().xpPerSkull)) {
        cue(p, MutationCue.FIGHT);
      }
    });
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(EntityDamageByEntityEvent e) {
    if (NetherSkullToss.spares(this, e, getConfig().ignorePassiveMobs)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityRemoveEvent e) {
    NetherSkullToss.forget(e);
  }

  @Handle(ignoreCancelled = false)
  public void on(EntityDamageEvent e) {
    if (e.cause() != DamageCause.WITHER || !(e.entity() instanceof PlayerRef p)) {
      return;
    }

    withActiveLevel(p, e, (int level) -> {
      double chance = getShrugChance(level, NetherWitherWard.netheritePieces(p));
      if (NetherWitherResist.winsChanceRoll(chance, ThreadLocalRandom.current().nextDouble(100D))) {
        e.setCancelled(true);
        if (ward.feedback(p, chance >= 100D)) {
          cue(p, MutationCue.BOON);
        }
      }
    });
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDeathEvent e) {
    PlayerRef p = NetherSkeletonHarvest.harvester(e);
    if (p == null) {
      return;
    }

    withActiveLevel(p, (int level) -> {
      NetherSkeletonHarvest.harvest(this, e, getBonusBones(level), getBonusCoal(level), getSkullChance(level));
      xpOn(p, e.entity(), getConfig().xpPerHarvest);
      cue(p, MutationCue.WORK);
    });
  }

  long getSkullCooldownMillis(int level) {
    return NetherSkullYeet.cooldownSeconds(getConfig().skullCooldownSecondsBase, getConfig().skullCooldownSecondsPerLevel, level)
        * 1000L;
  }

  /** Percent chance to shrug off one wither tick while wearing {@code netheritePieces} pieces of netherite. */
  double getShrugChance(int level, int netheritePieces) {
    return Math.min(100D, getConfig().shrugChanceBase + (level * getConfig().shrugChancePerLevel)
        + (netheritePieces * getConfig().shrugChancePerNetheritePiece));
  }

  int getBonusBones(int level) {
    return NetherWitherHarvest.bonusAmount(getLevelPercent(level), getConfig().bonusBonesBase, getConfig().bonusBonesFactor);
  }

  int getBonusCoal(int level) {
    return NetherWitherHarvest.bonusAmount(getLevelPercent(level), getConfig().bonusCoalBase, getConfig().bonusCoalFactor);
  }

  double getSkullChance(int level) {
    return NetherWitherHarvest.skullChance(getLevelPercent(level), getConfig().skullChanceBase, getConfig().skullChanceFactor,
        getConfig().maxSkullChance);
  }

  @ConfigDescription("Throw wither skulls, shrug off wither and harvest more from wither skeletons.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Skull throw cooldown before level scaling, in seconds.", impact = "Higher values make skull throws rarer.")
    int skullCooldownSecondsBase = 12;
    @ConfigDoc(value = "Skull throw cooldown removed per level, in seconds.", impact = "Higher values let higher levels throw more often; never below 1 second.")
    int skullCooldownSecondsPerLevel = 3;
    @ConfigDoc(value = "Skill XP for each skull thrown.", impact = "Higher values reward throwing skulls.")
    double xpPerSkull = 60;
    @ConfigDoc(value = "Exclude passive and neutral mobs from skull blasts.", impact = "When enabled, thrown skulls spare passive mobs.")
    boolean ignorePassiveMobs = false;
    @ConfigDoc(value = "Percent chance to shrug off a wither tick before level scaling.", impact = "Higher values make wither harmless more often.")
    double shrugChanceBase = 40;
    @ConfigDoc(value = "Percent chance to shrug off wither added per level.", impact = "Higher values reward higher levels more.")
    double shrugChancePerLevel = 20;
    @ConfigDoc(value = "Percent chance to shrug off wither added per netherite armor piece worn.", impact = "Higher values reward wearing netherite.")
    double shrugChancePerNetheritePiece = 5;
    @ConfigDoc(value = "Bonus bones from a wither skeleton before level scaling.", impact = "Higher values drop more bones.")
    double bonusBonesBase = 2;
    @ConfigDoc(value = "Bonus bones added across the levels.", impact = "Higher values reward higher levels more.")
    double bonusBonesFactor = 3;
    @ConfigDoc(value = "Bonus coal from a wither skeleton before level scaling.", impact = "Higher values drop more coal.")
    double bonusCoalBase = 1;
    @ConfigDoc(value = "Bonus coal added across the levels.", impact = "Higher values reward higher levels more.")
    double bonusCoalFactor = 3;
    @ConfigDoc(value = "Wither skeleton skull drop chance before level scaling.", impact = "Higher values drop skulls more often.")
    double skullChanceBase = 0.08;
    @ConfigDoc(value = "Skull drop chance added across the levels.", impact = "Higher values reward higher levels more.")
    double skullChanceFactor = 0.17;
    @ConfigDoc(value = "Highest skull drop chance at any level.", impact = "Caps the skull drop chance.")
    double maxSkullChance = 0.25;
    @ConfigDoc(value = "Skill XP for each wither skeleton harvested.", impact = "Higher values reward hunting wither skeletons.")
    double xpPerHarvest = 15;
  }
}
