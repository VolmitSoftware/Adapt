package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.Map;
import java.util.UUID;

public class KineticsMeteorCadence extends SimpleAdaptation<KineticsMeteorCadence.Config> {
  private static final String SLOT_GRAVITY = "dive-gravity";
  private static final String SLOT_DRAG = "dive-drag";
  private static final int REFRESH_TICKS = 8;

  private final Map<UUID, Boolean> diveMarked = playerState();

  public KineticsMeteorCadence() {
    super("kinetics-meteor-cadence");
    registerConfiguration(Config.class);
    setIcon(Material.ANVIL);
    setInterval(9999);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getGravityBoost(level), 0), 1);
    statLore(v, Form.pc(getDragCut(level), 0), 2);
  }

  @EventHandler(ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    if (!e.isSneaking()) {
      removeDiveModifiers(p);
      return;
    }

    refreshDive(p, verticalDelta(p));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    Location to = e.getTo();
    if (to == null) {
      return;
    }

    if (p.isOnGround()) {
      diveMarked.remove(p.getUniqueId());
      return;
    }

    refreshDive(p, to.getY() - e.getFrom().getY());
  }

  static boolean isDiving(boolean onGround, double deltaY, boolean sneaking, boolean holdingMace) {
    return !onGround && deltaY < 0D && sneaking && holdingMace;
  }

  private void refreshDive(Player p, double deltaY) {
    if (!isDiving(p.isOnGround(), deltaY, p.isSneaking(), isMace(p.getInventory().getItemInMainHand()))) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (Attributes.GRAVITY != null) {
      attributes.applyTimed(p, getName(), SLOT_GRAVITY, Attributes.GRAVITY, getGravityBoost(level), AttributeModifier.Operation.MULTIPLY_SCALAR_1, REFRESH_TICKS);
    }
    if (Attributes.AIR_DRAG_MODIFIER != null) {
      attributes.applyTimed(p, getName(), SLOT_DRAG, Attributes.AIR_DRAG_MODIFIER, -getDragCut(level), AttributeModifier.Operation.MULTIPLY_SCALAR_1, REFRESH_TICKS);
    }

    if (diveMarked.putIfAbsent(p.getUniqueId(), Boolean.TRUE) == null) {
      addStat(p, "kinetics.meteor.dives", 1);
    }
  }

  private void removeDiveModifiers(Player p) {
    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (Attributes.GRAVITY != null) {
      attributes.remove(p, getName(), SLOT_GRAVITY, Attributes.GRAVITY);
    }
    if (Attributes.AIR_DRAG_MODIFIER != null) {
      attributes.remove(p, getName(), SLOT_DRAG, Attributes.AIR_DRAG_MODIFIER);
    }
  }

  private double verticalDelta(Player p) {
    return p.getVelocity().getY();
  }

  private double getGravityBoost(int level) {
    return getConfig().gravityBoostBase + (getLevelPercent(level) * getConfig().gravityBoostFactor);
  }

  private double getDragCut(int level) {
    return getConfig().dragCutBase + (getLevelPercent(level) * getConfig().dragCutFactor);
  }

  @ConfigDescription("Sneak while falling with a mace to dive into your smash faster.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base gravity multiplier bonus while diving at level 1.", impact = "Higher values make dives accelerate downward faster.")
    double gravityBoostBase = 0.3;
    @ConfigDoc(value = "Additional gravity multiplier bonus granted at max level.", impact = "Higher values make max-level dives accelerate downward faster.")
    double gravityBoostFactor = 0.6;
    @ConfigDoc(value = "Base air drag reduction while diving at level 1.", impact = "Higher values let dives keep more speed through the air.")
    double dragCutBase = 0.2;
    @ConfigDoc(value = "Additional air drag reduction granted at max level.", impact = "Higher values let max-level dives keep more speed through the air.")
    double dragCutFactor = 0.4;

    public Config() {
      baseCost = 4;
      costFactor = 0.45;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
