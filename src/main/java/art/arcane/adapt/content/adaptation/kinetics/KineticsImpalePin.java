package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;

public class KineticsImpalePin extends SimpleAdaptation<KineticsImpalePin.Config> {
  private static final Color PIN = Color.fromRGB(0xB7D4E8);
  private final Cooldowns targetCooldowns = cooldowns();

  public KineticsImpalePin() {
    super("kinetics-impale-pin");
    registerConfiguration(Config.class);
    setIcon(Material.COBWEB);
    setInterval(9999);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getSweetMax(level), 1), 1);
    statLore(v, Form.duration(getDurationTicks(level) * 50D, 1), 2);
  }

  @Override
  public void onTick() {
    targetCooldowns.clearExpired(getConfig().targetCooldownMs);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.MeleeContext combat = resolveMeleeContext(e, this::isSpear);
    if (combat == null) {
      return;
    }

    Player p = combat.attacker();
    LivingEntity target = combat.target();
    Location eye = p.getEyeLocation();
    double distance = distanceToBounds(eye.getX(), eye.getY(), eye.getZ(), target.getBoundingBox());
    if (!inSweetRange(distance, getConfig().sweetMin, getSweetMax(combat.level()))) {
      return;
    }

    if (!targetCooldowns.isReady(target.getUniqueId(), getConfig().targetCooldownMs)) {
      return;
    }

    targetCooldowns.mark(target.getUniqueId());
    int duration = getDurationTicks(combat.level());
    int tier = getSlowTier(combat.level());
    J.runEntity(target, () -> {
      if (isRuntimeRegistered() && target.isValid()) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, tier, true, true, true), true);
        fx(target.getLocation().add(0D, target.getHeight() * 0.5D, 0D), FxPriority.COMBAT)
            .ring(Particle.CRIT, 0.45D, 8, 0D)
            .dustBurst(PIN, 4, 0.3D, 1F)
            .sound(Sound.BLOCK_CHAIN_PLACE, 0.45F, 0.65F);
      }
    });
  }

  private double getSweetMax(int level) {
    return getConfig().sweetMaxBase + (getLevelPercent(level) * getConfig().sweetMaxFactor);
  }

  private int getSlowTier(int level) {
    return slowTier(getConfig().slowTierBase, getConfig().slowTierFactor, getLevelPercent(level));
  }

  private int getDurationTicks(int level) {
    return durationTicks(getConfig().durationTicksBase, getConfig().durationTicksFactor, getLevelPercent(level));
  }

  static boolean inSweetRange(double distance, double min, double max) {
    return Double.isFinite(distance) && distance >= min && distance <= max;
  }

  static double distanceToBounds(double x, double y, double z, BoundingBox bounds) {
    if (bounds == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
      return Double.NaN;
    }

    double dx = axisDistance(x, bounds.getMinX(), bounds.getMaxX());
    double dy = axisDistance(y, bounds.getMinY(), bounds.getMaxY());
    double dz = axisDistance(z, bounds.getMinZ(), bounds.getMaxZ());
    return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
  }

  private static double axisDistance(double value, double min, double max) {
    if (value < min) {
      return min - value;
    }
    if (value > max) {
      return value - max;
    }
    return 0D;
  }

  static int slowTier(double base, double factor, double levelPercent) {
    return Math.max(0, (int) Math.round(base + (levelPercent * factor)));
  }

  static int durationTicks(double base, double factor, double levelPercent) {
    return Math.max(10, (int) Math.round(base + (levelPercent * factor)));
  }

  @ConfigDescription("Spear hits at sweet range pin the target with heavy slowness.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Minimum distance in blocks for a hit to count as sweet range.", impact = "Lower values let point-blank hits pin; higher values demand more spacing.")
    double sweetMin = 3.0;
    @ConfigDoc(value = "Base maximum distance in blocks for the sweet-range band.", impact = "Higher values widen the pinning band from level one.")
    double sweetMaxBase = 5.0;
    @ConfigDoc(value = "Additional maximum sweet-range distance gained as levels increase.", impact = "Higher values widen the pinning band further at max level.")
    double sweetMaxFactor = 1.5;
    @ConfigDoc(value = "Base slowness amplifier applied on a sweet-range pin.", impact = "Higher values slow pinned targets more from level one.")
    double slowTierBase = 0;
    @ConfigDoc(value = "Additional slowness amplifier gained as levels increase, rounded to an integer.", impact = "Higher values slow pinned targets more at max level.")
    double slowTierFactor = 2;
    @ConfigDoc(value = "Base slowness duration in ticks applied on a pin.", impact = "Higher values keep targets pinned longer from level one.")
    double durationTicksBase = 40;
    @ConfigDoc(value = "Additional slowness duration in ticks gained as levels increase.", impact = "Higher values keep targets pinned longer at max level.")
    double durationTicksFactor = 50;
    @ConfigDoc(value = "Minimum delay between pins on the same target in milliseconds.", impact = "Higher values prevent chain-pinning one target; lower values allow faster re-pins.")
    long targetCooldownMs = 2500;

    public Config() {
      baseCost = 4;
      costFactor = 0.45;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
