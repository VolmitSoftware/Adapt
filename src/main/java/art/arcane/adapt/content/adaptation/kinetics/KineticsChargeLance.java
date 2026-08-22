package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

public class KineticsChargeLance extends SimpleAdaptation<KineticsChargeLance.Config> {
  private final Cooldowns cooldowns = cooldowns();

  public KineticsChargeLance() {
    super("kinetics-charge-lance");
    registerConfiguration(Config.class);
    setIcon(Material.IRON_SPEAR);
    setInterval(9999);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getSpeedDamageFactor(level), 2), 1);
    statLore(v, Form.pc(getBonusCap(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.MeleeContext combat = resolveMeleeContext(e, this::isSpear);
    if (combat == null) {
      return;
    }

    Player p = combat.attacker();
    if (p.isInsideVehicle()) {
      return;
    }

    if (!cooldowns.isReady(p.getUniqueId(), getConfig().cooldownMs)) {
      return;
    }

    Vector velocity = p.getVelocity();
    double speed = horizontalSpeed(velocity.getX(), velocity.getZ());
    double bonus = chargeBonus(speed, getConfig().minSpeed, getSpeedDamageFactor(combat.level()), getBonusCap(combat.level()));
    if (bonus <= 0D) {
      return;
    }

    cooldowns.mark(p.getUniqueId());
    e.setDamage(e.getDamage() * (1D + bonus));
    addStat(p, "kinetics.lance.charges", 1);
  }

  private double getSpeedDamageFactor(int level) {
    return getConfig().speedDamageFactorBase + (getLevelPercent(level) * getConfig().speedDamageFactorFactor);
  }

  private double getBonusCap(int level) {
    return getConfig().bonusCapBase + (getLevelPercent(level) * getConfig().bonusCapFactor);
  }

  static double horizontalSpeed(double velocityX, double velocityZ) {
    return Math.sqrt((velocityX * velocityX) + (velocityZ * velocityZ));
  }

  static double chargeBonus(double horizontalSpeed, double minSpeed, double factor, double cap) {
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
      baseCost = 4;
      costFactor = 0.45;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
