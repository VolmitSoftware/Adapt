package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.skill.kinetics.KineticsMotion;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;

public class KineticsSoftCatch extends SimpleAdaptation<KineticsSoftCatch.Config> {
  private final Map<UUID, Boolean> airborne = playerState();
  private final Map<UUID, Long> lastBounceAtMs = playerState();

  public KineticsSoftCatch() {
    super("kinetics-soft-catch");
    registerConfiguration(Config.class);
    setIcon(Material.WHITE_WOOL);
    setInterval(9999);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getReduction(level), 0), 1);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    boolean onGround = p.isOnGround();
    Boolean wasAirborne = airborne.put(p.getUniqueId(), !onGround);
    if (!isLanding(onGround, wasAirborne != null && wasAirborne)) {
      return;
    }

    if (KineticsMotion.isBouncySurface(landingSurface(p))) {
      lastBounceAtMs.put(p.getUniqueId(), M.ms());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) {
      return;
    }

    if (e.getDamage() <= 0D) {
      return;
    }

    boolean softLanding = KineticsMotion.isSoftLanding(landingSurface(p));
    boolean graced = withinGrace(M.ms(), lastBounceAtMs.getOrDefault(p.getUniqueId(), Long.MIN_VALUE / 2L), getConfig().postBounceGraceTicks);
    if (!softLanding && !graced) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      double before = e.getDamage();
      double after = reducedDamage(before, getReduction(level));
      double prevented = before - after;
      if (prevented <= 0D) {
        return;
      }

      e.setDamage(after);
      fx(p.getLocation(), FxPriority.TRANSITION)
          .particle(Particle.CLOUD, 6, 0, 0.1D, 0, 0.3D, 0.02D)
          .sound(Sound.BLOCK_WOOL_FALL, 0.6F, 1.1F);
      double reward = rewardXp(prevented, getConfig().xpPerDamagePrevented, getConfig().xpPerEventCap);
      if (reward > 0D) {
        xp(p, p.getLocation(), reward, "kinetics:soft-catch");
      }
    });
  }

  static boolean isLanding(boolean onGround, boolean wasAirborne) {
    return onGround && wasAirborne;
  }

  static boolean withinGrace(long nowMs, long lastBounceAtMs, int graceTicks) {
    return graceTicks > 0 && nowMs - lastBounceAtMs <= graceTicks * 50L;
  }

  static double reduction(double base, double factor, double levelPercent) {
    double value = base + (levelPercent * factor);
    if (!Double.isFinite(value)) {
      return 0D;
    }
    return Math.min(1D, Math.max(0D, value));
  }

  static double reducedDamage(double damage, double reduction) {
    if (!Double.isFinite(damage) || damage <= 0D) {
      return 0D;
    }

    double clamped = Double.isFinite(reduction) ? Math.min(1D, Math.max(0D, reduction)) : 0D;
    return Math.max(0D, damage * (1D - clamped));
  }

  static double rewardXp(double preventedDamage, double xpPerDamage, double cap) {
    if (!Double.isFinite(preventedDamage) || !Double.isFinite(xpPerDamage) || !Double.isFinite(cap)
        || preventedDamage <= 0D || xpPerDamage <= 0D || cap <= 0D) {
      return 0D;
    }
    double reward = preventedDamage * xpPerDamage;
    return Double.isFinite(reward) && reward > 0D ? Math.min(cap, reward) : 0D;
  }

  static Material landingSurface(Material feet, Material below) {
    if (KineticsMotion.isSoftLanding(feet)) {
      return feet;
    }
    return below;
  }

  private Material landingSurface(Player p) {
    Block feet = p.getLocation().getBlock();
    Block below = feet.getRelative(0, -1, 0);
    return landingSurface(feet.getType(), below.getType());
  }

  private double getReduction(int level) {
    return reduction(getConfig().reductionBase, getConfig().reductionFactor, getLevelPercent(level));
  }

  @ConfigDescription("Soft and springy blocks break your fall, and a fresh bounce grants a grace window.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base fraction of fall damage removed on a soft landing before level scaling.", impact = "Higher values absorb more fall damage at every level.")
    double reductionBase = 0.35;
    @ConfigDoc(value = "Additional fall damage reduction granted at max level.", impact = "Higher values widen the absorption gain from leveling.")
    double reductionFactor = 0.45;
    @ConfigDoc(value = "Duration in ticks of the grace window after bouncing off a bouncy block.", impact = "Higher values keep fall protection active longer after a bounce.")
    int postBounceGraceTicks = 30;
    @ConfigDoc(value = "Experience granted per point of fall damage prevented.", impact = "Higher values reward soft landings with more skill xp.")
    double xpPerDamagePrevented = 1.5;
    @ConfigDoc(value = "Maximum experience granted by one softened fall.", impact = "Higher values allow exceptionally large falls to award more xp at once.")
    double xpPerEventCap = 50;

    public Config() {
      baseCost = 4;
      costFactor = 0.45;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
