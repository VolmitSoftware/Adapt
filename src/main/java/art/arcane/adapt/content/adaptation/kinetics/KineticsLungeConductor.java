package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import io.papermc.paper.event.entity.EntityLungeEvent;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.util.Vector;

public class KineticsLungeConductor extends SimpleAdaptation<KineticsLungeConductor.Config> {
  private static final Color CONDUCTOR = Color.fromRGB(0xBFE8FF);
  private final Cooldowns lungeCooldown = cooldowns();

  public KineticsLungeConductor() {
    super("kinetics-lunge-conductor");
    registerConfiguration(Config.class);
    setIcon(Material.FEATHER);
    setInterval(9999);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getPowerBonus(level), 1);
    statLore(v, Form.f(getDashBoost(level), 2), 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityLungeEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!lungeCooldown.isReady(p.getUniqueId(), getConfig().cooldownMs)) {
      return;
    }

    e.setLungePower(boostedPower(e.getLungePower(), getPowerBonus(level)));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void finalizeLunge(EntityLungeEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!lungeCooldown.isReady(p.getUniqueId(), getConfig().cooldownMs)) {
      return;
    }

    lungeCooldown.mark(p.getUniqueId());

    Vector direction = p.getLocation().getDirection();
    double boost = getDashBoost(level);
    J.runEntity(p, () -> {
      if (!isRuntimeRegistered() || !p.isOnline() || !p.isValid()) {
        return;
      }

      p.setVelocity(assistVelocity(p.getVelocity(), direction, boost));
      fx(p.getLocation().add(0, 1, 0), FxPriority.GAMEPLAY)
          .particle(Particle.CLOUD, 4, 0, 0.1D, 0, 0.15D, 0.02D)
          .dustBurst(CONDUCTOR, 4, 0.3D, 1.0F)
          .chord(Sound.ITEM_TRIDENT_RIPTIDE_1, 0.35F, 1.6F, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.4F, 1.3F);
    }, 1);
  }

  static int boostedPower(int current, int bonus) {
    return current + Math.max(0, bonus);
  }

  static Vector assistVelocity(Vector current, Vector direction, double boost) {
    Vector result = current.clone();
    if (direction == null || !Double.isFinite(boost) || boost <= 0D) {
      return result;
    }
    double directionX = Double.isFinite(direction.getX()) ? direction.getX() : 0D;
    double directionZ = Double.isFinite(direction.getZ()) ? direction.getZ() : 0D;
    result.setX(result.getX() + (directionX * boost));
    result.setZ(result.getZ() + (directionZ * boost));
    return result;
  }

  private int getPowerBonus(int level) {
    return (int) Math.round(getConfig().powerBonusBase + (getLevelPercent(level) * getConfig().powerBonusFactor));
  }

  private double getDashBoost(int level) {
    return getConfig().dashBoostBase + (getLevelPercent(level) * getConfig().dashBoostFactor);
  }

  @ConfigDescription("Your spear lunges strike harder and carry you farther.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base lunge power added to every boosted lunge.", impact = "Higher values make even low-level lunges noticeably stronger.")
    double powerBonusBase = 1;
    @ConfigDoc(value = "Additional lunge power granted as the adaptation levels up.", impact = "Higher values widen the lunge-power gain from leveling.")
    double powerBonusFactor = 2;
    @ConfigDoc(value = "Base forward velocity assist applied when a lunge fires.", impact = "Higher values carry the lunge farther forward.")
    double dashBoostBase = 0.2;
    @ConfigDoc(value = "Additional forward velocity assist granted at max level.", impact = "Higher values make leveled lunges travel much farther.")
    double dashBoostFactor = 0.3;
    @ConfigDoc(value = "Minimum milliseconds between boosted lunges.", impact = "Higher values space out boosted lunges; lower values allow faster chaining.")
    long cooldownMs = 2500;

    public Config() {
      baseCost = 4;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 3;
    }
  }
}
