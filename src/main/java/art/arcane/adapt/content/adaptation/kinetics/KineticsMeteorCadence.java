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
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class KineticsMeteorCadence extends SimpleAdaptation<KineticsMeteorCadence.Config> {
  private static final String SLOT_GRAVITY = "dive-gravity";
  private static final String SLOT_DRAG = "dive-drag";
  private static final int REFRESH_TICKS = 8;

  private final Map<UUID, Boolean> diveMarked = playerState();
  private final Map<UUID, Integer> acceleratedAtTick = playerState();

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
    statLore(v, Form.f(getDownwardAcceleration(level), 2), 3);
    statLore(v, Form.f(getTerminalFallSpeed(), 2), 4);
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
      acceleratedAtTick.remove(p.getUniqueId());
      removeDiveModifiers(p);
      return;
    }

    refreshDive(p, to.getY() - e.getFrom().getY());
  }

  static boolean isDiving(boolean onGround, double deltaY, boolean sneaking, boolean holdingMace) {
    return !onGround && deltaY < 0D && sneaking && holdingMace;
  }

  static double scaledDownwardAcceleration(double base, double factor, double levelPercent) {
    double acceleration = base + (factor * levelPercent);
    if (!Double.isFinite(acceleration)) {
      return 0D;
    }
    return Math.min(2D, Math.max(0D, acceleration));
  }

  static double acceleratedDownwardVelocity(double currentY, double acceleration, double terminalFallSpeed) {
    if (!Double.isFinite(currentY) || currentY >= 0D) {
      return currentY;
    }

    double safeAcceleration = Double.isFinite(acceleration) ? Math.min(2D, Math.max(0D, acceleration)) : 0D;
    double safeTerminal = Double.isFinite(terminalFallSpeed) ? Math.min(10D, Math.max(0D, terminalFallSpeed)) : 0D;
    if (safeAcceleration <= 0D || safeTerminal <= 0D || currentY <= -safeTerminal) {
      return currentY;
    }
    return Math.max(-safeTerminal, currentY - safeAcceleration);
  }

  static boolean shouldAccelerateAtTick(Integer previousTick, int currentTick) {
    return previousTick == null || previousTick != currentTick;
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
    accelerateDive(p, level);

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

  private void accelerateDive(Player p, int level) {
    UUID id = p.getUniqueId();
    int currentTick = p.getTicksLived();
    Integer previousTick = acceleratedAtTick.put(id, currentTick);
    if (!shouldAccelerateAtTick(previousTick, currentTick)) {
      return;
    }

    Vector velocity = p.getVelocity();
    double acceleratedY = acceleratedDownwardVelocity(velocity.getY(), getDownwardAcceleration(level), getTerminalFallSpeed());
    if (acceleratedY < velocity.getY()) {
      velocity.setY(acceleratedY);
      p.setVelocity(velocity);
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

  private double getDownwardAcceleration(int level) {
    return scaledDownwardAcceleration(getConfig().downwardAccelerationBase,
        getConfig().downwardAccelerationFactor, getLevelPercent(level));
  }

  private double getTerminalFallSpeed() {
    double terminalFallSpeed = getConfig().terminalFallSpeed;
    if (!Double.isFinite(terminalFallSpeed)) {
      return 0D;
    }
    return Math.min(10D, Math.max(0D, terminalFallSpeed));
  }

  @ConfigDescription("Sneak while falling with a mace to accelerate sharply downward into your smash.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base gravity multiplier bonus while diving at level 1.", impact = "Higher values make dives accelerate downward faster.")
    double gravityBoostBase = 0.3;
    @ConfigDoc(value = "Additional gravity multiplier bonus granted at max level.", impact = "Higher values make max-level dives accelerate downward faster.")
    double gravityBoostFactor = 0.6;
    @ConfigDoc(value = "Base air drag reduction while diving at level 1.", impact = "Higher values let dives keep more speed through the air.")
    double dragCutBase = 0.2;
    @ConfigDoc(value = "Additional air drag reduction granted at max level.", impact = "Higher values let max-level dives keep more speed through the air.")
    double dragCutFactor = 0.4;
    @ConfigDoc(value = "Downward velocity added once per game tick while diving at level 1.", impact = "Higher values make every meteor dive gain fall speed more aggressively.")
    double downwardAccelerationBase = 0.2;
    @ConfigDoc(value = "Additional downward acceleration granted at max level.", impact = "Higher values make maximum-level meteor dives reach terminal speed sooner.")
    double downwardAccelerationFactor = 0.3;
    @ConfigDoc(value = "Fastest downward velocity Meteor Cadence itself can produce in blocks per tick.", impact = "Higher values allow the dive acceleration to reach a faster terminal fall speed.")
    double terminalFallSpeed = 3.5;

    public Config() {
      baseCost = 4;
      costFactor = 0.45;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
