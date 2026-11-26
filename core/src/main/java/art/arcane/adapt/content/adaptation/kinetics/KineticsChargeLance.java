package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;


public class KineticsChargeLance extends SimpleAdaptation<KineticsChargeLance.Config> {
  private final Cooldowns cooldowns = cooldowns();
  private final KineticsStride stride = new KineticsStride(playerState());

  public KineticsChargeLance() {
    super("kinetics-charge-lance");
    needs(PlatformNeed.item("iron_spear"));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_spear"));
    setInterval(9999);
  }

  @Override
  public void unregister() {
    stride.clear();
    super.unregister();
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getSpeedDamageFactor(level), 2), 1);
    statLore(v, Form.pc(getBonusCap(level), 0), 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent event) {
    PlayerRef player = event.player();
    stride.track(event, player.vehicle() == null && hasActiveAdaptation(player)
        && isSpear(player.inventory().mainHand()));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent event) {
    stride.forget(event.player().uuid());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e, this::isSpear);
    if (combat == null) {
      return;
    }

    PlayerRef p = combat.attacker();
    if (p.vehicle() != null) {
      return;
    }

    if (!cooldowns.isReady(p.uuid(), getConfig().cooldownMs)) {
      return;
    }

    double bonus = chargeBonus(stride.speed(p), getConfig().minSpeed, getSpeedDamageFactor(combat.level()), getBonusCap(combat.level()));
    if (bonus <= 0D) {
      return;
    }

    cooldowns.mark(p.uuid(), getConfig().cooldownMs);
    e.setDamage(e.damage() * (1D + bonus));
    addStat(p, "kinetics.lance.charges", 1);
  }

  private double getSpeedDamageFactor(int level) {
    return getConfig().speedDamageFactorBase + (getLevelPercent(level) * getConfig().speedDamageFactorFactor);
  }

  private double getBonusCap(int level) {
    return getConfig().bonusCapBase + (getLevelPercent(level) * getConfig().bonusCapFactor);
  }

  public static double horizontalSpeed(double velocityX, double velocityZ) {
    return Math.sqrt((velocityX * velocityX) + (velocityZ * velocityZ));
  }

  public static double chargeBonus(double horizontalSpeed, double minSpeed, double factor, double cap) {
    if (!Double.isFinite(horizontalSpeed) || horizontalSpeed < minSpeed) {
      return 0D;
    }

    return Math.max(0D, Math.min(cap, horizontalSpeed * factor));
  }

  @ConfigDescription("Spear hits scale with your speed. Hit them at a run.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base multiplier converting horizontal speed into bonus damage fraction.", impact = "Higher values reward fast charges with more bonus damage from level one.")
    double speedDamageFactorBase = 0.8;
    @ConfigDoc(value = "Additional speed-to-damage multiplier gained as levels increase.", impact = "Higher values make max-level charges hit harder at speed.")
    double speedDamageFactorFactor = 1.2;
    @ConfigDoc(value = "Minimum horizontal speed required for a charge bonus to apply.", impact = "Lower values trigger the bonus while barely moving; higher values require a real sprint.")
    double minSpeed = 0.18;
    @ConfigDoc(value = "Base cap on the bonus damage fraction per hit.", impact = "Higher values allow larger charge bonuses from level one.")
    double bonusCapBase = 0.5;
    @ConfigDoc(value = "Additional bonus-damage cap gained as levels increase.", impact = "Higher values raise the max-level charge damage ceiling.")
    double bonusCapFactor = 0.75;
    @ConfigDoc(value = "Minimum delay between charge bonuses in milliseconds.", impact = "Higher values space out charge hits; lower values allow faster repeats.")
    long cooldownMs = 1500;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
