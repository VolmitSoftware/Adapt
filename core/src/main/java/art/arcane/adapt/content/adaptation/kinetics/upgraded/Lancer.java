package art.arcane.adapt.content.adaptation.kinetics.upgraded;

import art.arcane.adapt.api.adaptation.AdaptationOwnerPulse;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.sound.LingeringSound;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.support.PlatformSupport;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.kinetics.KineticsChargeLance;
import art.arcane.adapt.content.adaptation.kinetics.KineticsImpalePin;
import art.arcane.adapt.content.adaptation.kinetics.KineticsPin;
import art.arcane.adapt.content.adaptation.kinetics.KineticsReach;
import art.arcane.adapt.content.adaptation.kinetics.KineticsStride;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

/**
 * Charge Lance, Phalanx Reach and Impale Pin fused: a held spear reaches farther, every spear hit gains damage from
 * your running speed, and it pins the target with slowness. Where spears do not exist the trident is the lance.
 */
public class Lancer extends SkillUpgrade<Lancer.Config> {
  private final KineticsReach reach = new KineticsReach(this);
  private final KineticsStride stride = new KineticsStride(playerState());
  private final KineticsPin pin = new KineticsPin(this);
  private final Cooldowns pinCooldowns = cooldowns();
  private final AdaptationOwnerPulse.Registration ownerMaintenance;
  private Boolean spears;

  private static final List<PlatformNeed> SPEARS = List.of(PlatformNeed.item("iron_spear"));

  public Lancer() {
    super("upgraded-kinetics-lancer");
    registerConfiguration(Config.class);
    setIcon(PlatformSupport.itemOr(Id.minecraft("iron_spear"), Id.minecraft("trident")));
    setInterval(2000);
    ownerMaintenance = AdaptationOwnerPulse.register(this, this::getInterval,
        (PlayerRef p) -> reconcile(p, p.inventory().mainHand()));
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    PlayerRef p = player.getPlayer();
    reconcile(p, p.inventory().mainHand());
  }

  @Override
  public void unregister() {
    ownerMaintenance.unregister();
    stride.clear();
    super.unregister();
  }

  @Override
  public void onTick() {
    pinCooldowns.clearExpired((long) getConfig().pinCooldownMillis);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getReach(level), 2), 1);
    statLore(v, Form.pc(getBonusCap(level), 0), 2);
    statLore(v, Form.duration(getPinTicks(level) * 50D, 1), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerItemHeldEvent e) {
    PlayerRef p = e.player();
    reconcile(p, p.inventory().item(e.newSlot()));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    stride.track(e, p.vehicle() == null && hasActiveAdaptation(p) && isLance(p.inventory().mainHand()));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    stride.forget(e.player().uuid());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e, this::isLance);
    if (combat == null) {
      return;
    }

    PlayerRef p = combat.attacker();
    int level = combat.level();
    double bonus = KineticsChargeLance.chargeBonus(stride.speed(p), getConfig().minSpeed,
        getSpeedDamageFactor(level), getBonusCap(level));
    if (bonus > 0D) {
      e.setDamage(e.damage() * (1D + bonus));
      addStat(p, "kinetics.lancer.charges", 1);
    }

    LivingRef target = combat.target();
    long cooldownMillis = (long) getConfig().pinCooldownMillis;
    if (pinCooldowns.isReady(target.uuid(), cooldownMillis)) {
      pinCooldowns.mark(target.uuid());
      int ticks = getPinTicks(level);
      pin.pin(target, ticks, getPinTier(level));
      LingeringSound lingering = linger(target);
      Tasks.runEntity(target, lingering::stop, ticks);
    }
    cue(p, MutationCue.FIGHT);
  }

  private void reconcile(PlayerRef p, ItemRef mainHand) {
    reach.reconcile(p, isLance(mainHand), getReach(getActiveLevel(p)));
  }

  /** A spear, or a trident on versions without spears. */
  boolean isLance(ItemRef item) {
    if (spears == null) {
      spears = PlatformSupport.check(SPEARS).usable();
    }
    return spears ? isSpear(item) : isTrident(item);
  }

  double getReach(int level) {
    return getConfig().reachBase + (level * getConfig().reachPerLevel);
  }

  double getSpeedDamageFactor(int level) {
    return getConfig().speedDamageFactorBase + (level * getConfig().speedDamageFactorPerLevel);
  }

  double getBonusCap(int level) {
    return getConfig().bonusCapBase + (level * getConfig().bonusCapPerLevel);
  }

  int getPinTicks(int level) {
    return KineticsImpalePin.durationTicks(getConfig().pinTicksBase, getConfig().pinTicksPerLevel, level);
  }

  int getPinTier(int level) {
    return KineticsImpalePin.slowTier(getConfig().pinTierBase, getConfig().pinTierPerLevel, level);
  }

  @ConfigDescription("A held spear, or trident where spears do not exist, reaches farther; its hits gain damage from your speed and pin the target.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Extra reach in blocks while holding a spear, before level scaling.", impact = "Higher values let spear hits land from farther away.")
    double reachBase = 1.0;
    @ConfigDoc(value = "Extra reach in blocks added per level.", impact = "Higher values reward higher levels more.")
    double reachPerLevel = 0.5;
    @ConfigDoc(value = "Horizontal speed in blocks per tick a hit needs before speed adds damage.", impact = "Lower values let slower hits gain the bonus.")
    double minSpeed = 0.12;
    @ConfigDoc(value = "Bonus damage fraction per block per tick of speed, before level scaling.", impact = "Higher values make fast hits stronger.")
    double speedDamageFactorBase = 1.2;
    @ConfigDoc(value = "Bonus damage fraction per block per tick of speed added per level.", impact = "Higher values reward higher levels more.")
    double speedDamageFactorPerLevel = 0.4;
    @ConfigDoc(value = "Largest speed bonus damage fraction before level scaling.", impact = "Higher values raise the ceiling on fast hits.")
    double bonusCapBase = 0.75;
    @ConfigDoc(value = "Largest speed bonus damage fraction added per level.", impact = "Higher values raise the ceiling at higher levels.")
    double bonusCapPerLevel = 0.25;
    @ConfigDoc(value = "Pin slowness duration before level scaling, in ticks.", impact = "Higher values keep targets pinned longer.")
    double pinTicksBase = 30;
    @ConfigDoc(value = "Pin slowness duration added per level, in ticks.", impact = "Higher values keep targets pinned longer at higher levels.")
    double pinTicksPerLevel = 15;
    @ConfigDoc(value = "Pin slowness amplifier before level scaling.", impact = "Higher values slow pinned targets more.")
    double pinTierBase = 0;
    @ConfigDoc(value = "Pin slowness amplifier added per level, rounded.", impact = "Higher values slow pinned targets more at higher levels.")
    double pinTierPerLevel = 0.7;
    @ConfigDoc(value = "Time before the same target can be pinned again, in milliseconds.", impact = "Higher values stop a target being held in place.")
    double pinCooldownMillis = 2500;
  }
}
