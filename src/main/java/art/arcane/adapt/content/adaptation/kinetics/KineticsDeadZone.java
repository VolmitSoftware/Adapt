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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class KineticsDeadZone extends SimpleAdaptation<KineticsDeadZone.Config> {
  private static final double SHOVE_DAMPING = 0.2D;
  private static final double SHOVE_LIFT = 0.25D;
  private static final Color ZONE = Color.fromRGB(0xC9D9E8);
  private final Map<UUID, Long> riposteUntil = playerState();
  private final Cooldowns shoveCooldown = cooldowns();

  public KineticsDeadZone() {
    super("kinetics-dead-zone");
    registerConfiguration(Config.class);
    setIcon(Material.ARMOR_STAND);
    setInterval(9999);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getDeadZoneRange(level), 1), 1);
    statLore(v, Form.pc(getRiposteBonus(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    handleShove(e);
    handleRiposte(e);
  }

  static boolean inDeadZone(double distance, double range) {
    if (!Double.isFinite(distance) || !Double.isFinite(range)) {
      return false;
    }

    return distance >= 0D && range > 0D && distance <= range;
  }

  static boolean riposteActive(long nowMs, long deadlineMs) {
    return deadlineMs > 0L && nowMs <= deadlineMs;
  }

  static Vector shoveVector(Vector defenderPosition, Vector attackerPosition, double force) {
    Vector away = attackerPosition.clone().subtract(defenderPosition).setY(0);
    if (away.lengthSquared() < 1.0E-6D) {
      away = new Vector(0, 0, 1);
    } else {
      away.normalize();
    }

    Vector shove = away.multiply(Math.max(0D, force));
    shove.setY(SHOVE_LIFT);
    return shove;
  }

  private void handleShove(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player defender)) {
      return;
    }

    if (!(e.getDamager() instanceof LivingEntity attacker) || attacker.getUniqueId().equals(defender.getUniqueId())) {
      return;
    }

    int level = getActiveLevel(defender);
    if (level <= 0 || !isSpear(defender.getInventory().getItemInMainHand())) {
      return;
    }

    double distance = defender.getLocation().distance(attacker.getLocation());
    if (!inDeadZone(distance, getDeadZoneRange(level))) {
      return;
    }

    if (!shoveCooldown.isReady(defender.getUniqueId(), getConfig().cooldownMs)) {
      return;
    }

    boolean allowed = attacker instanceof Player
        ? canPVP(defender, attacker.getLocation())
        : canPVE(defender, attacker.getLocation());
    if (!allowed) {
      return;
    }

    shoveCooldown.mark(defender.getUniqueId());
    Vector shove = shoveVector(defender.getLocation().toVector(), attacker.getLocation().toVector(), getShoveForce(level));
    J.runEntity(attacker, () -> {
      if (!isRuntimeRegistered() || !attacker.isValid()) {
        return;
      }

      attacker.setVelocity(attacker.getVelocity().multiply(SHOVE_DAMPING).add(shove));
    });
    riposteUntil.put(defender.getUniqueId(), System.currentTimeMillis() + (getConfig().riposteWindowTicks * 50L));
    fx(defender.getLocation().add(0, 1, 0), FxPriority.COMBAT)
        .particle(Particle.SWEEP_ATTACK, 1, 0, 0, 0, 0, 0)
        .dustBurst(ZONE, 5, 0.4D, 1.0F)
        .chord(Sound.ITEM_SHIELD_BLOCK, 0.4F, 1.5F, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5F, 0.8F);
  }

  private void handleRiposte(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player attacker)) {
      return;
    }

    Long deadline = riposteUntil.get(attacker.getUniqueId());
    if (deadline == null || !riposteActive(System.currentTimeMillis(), deadline)) {
      return;
    }

    Adaptation.MeleeContext combat = resolveMeleeContext(e, this::isSpear);
    if (combat == null) {
      return;
    }

    riposteUntil.remove(combat.attacker().getUniqueId());
    e.setDamage(e.getDamage() * (1D + getRiposteBonus(combat.level())));
    fx(combat.target().getLocation().add(0, 1, 0), FxPriority.COMBAT)
        .particle(Particle.CRIT, 8, 0, 0, 0, 0.25D, 0.05D)
        .dustBurst(ZONE, 3, 0.3D, 1.0F)
        .sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6F, 1.3F);
  }

  private double getDeadZoneRange(int level) {
    return getConfig().deadZoneRangeBase + (getLevelPercent(level) * getConfig().deadZoneRangeFactor);
  }

  private double getShoveForce(int level) {
    return getConfig().shoveForceBase + (getLevelPercent(level) * getConfig().shoveForceFactor);
  }

  private double getRiposteBonus(int level) {
    return getConfig().riposteBonusBase + (getLevelPercent(level) * getConfig().riposteBonusFactor);
  }

  @ConfigDescription("Enemies that crowd inside your spear's dead zone get shoved out, arming a riposte.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base radius of the dead zone around you while holding a spear.", impact = "Higher values shove attackers from farther away.")
    double deadZoneRangeBase = 2.0;
    @ConfigDoc(value = "Additional dead zone radius granted at max level.", impact = "Higher values widen the dead zone as the adaptation levels.")
    double deadZoneRangeFactor = 1.0;
    @ConfigDoc(value = "Base force of the shove applied to crowding attackers.", impact = "Higher values throw attackers farther out of the dead zone.")
    double shoveForceBase = 0.5;
    @ConfigDoc(value = "Additional shove force granted at max level.", impact = "Higher values make leveled shoves much stronger.")
    double shoveForceFactor = 0.6;
    @ConfigDoc(value = "How long the riposte window stays armed after a shove, in ticks.", impact = "Higher values give more time to land the boosted counterattack.")
    int riposteWindowTicks = 30;
    @ConfigDoc(value = "Base damage bonus applied to a riposte strike.", impact = "Higher values make counterattacks hit harder at minimum level.")
    double riposteBonusBase = 0.2;
    @ConfigDoc(value = "Additional riposte damage bonus granted at max level.", impact = "Higher values scale counterattack damage with levels.")
    double riposteBonusFactor = 0.4;
    @ConfigDoc(value = "Minimum milliseconds between dead zone shoves.", impact = "Higher values space out shoves; lower values trigger them more often.")
    long cooldownMs = 3000;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 3;
      initialCost = 4;
    }
  }
}
