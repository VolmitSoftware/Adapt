package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

public class KineticsSurfaceSkate extends SimpleAdaptation<KineticsSurfaceSkate.Config> {
  private static final double MIN_HORIZONTAL_SPEED_SQUARED = 1.0E-6D;
  private static final long RECONCILE_INTERVAL_MS = 1000L;
  private static final String SLOT_SLIDE = "slide";
  private static final String SLOT_GRIP = "grip";

  public KineticsSurfaceSkate() {
    super("kinetics-surface-skate");
    registerConfiguration(Config.class);
    setIcon(Material.PACKED_ICE);
    setInterval(RECONCILE_INTERVAL_MS);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getSlideFriction(level), 0), 1);
    statLore(v, Form.pc(getGripFriction(level), 0), 2);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    for (AdaptPlayer adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      Player player = adaptPlayer.getPlayer();
      withPlayerThread(player, () -> reconcile(player));
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void on(PlayerToggleSprintEvent e) {
    Player p = e.getPlayer();
    if (Attributes.FRICTION_MODIFIER == null) {
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (!e.isSprinting()) {
      attributes.remove(p, getName(), SLOT_SLIDE, Attributes.FRICTION_MODIFIER);
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      attributes.apply(p, getName(), SLOT_SLIDE, Attributes.FRICTION_MODIFIER, -getSlideFriction(level), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    });
  }

  @EventHandler(ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    if (Attributes.FRICTION_MODIFIER == null) {
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (!e.isSneaking()) {
      attributes.remove(p, getName(), SLOT_GRIP, Attributes.FRICTION_MODIFIER);
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      attributes.apply(p, getName(), SLOT_GRIP, Attributes.FRICTION_MODIFIER, getGripFriction(level), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    if (Attributes.FRICTION_MODIFIER != null || e.getTo() == null || e instanceof PlayerTeleportEvent) {
      return;
    }

    Player p = e.getPlayer();
    if (!((Entity) p).isOnGround()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    double frictionDelta = frictionDelta(
        p.isSprinting(), p.isSneaking(), getSlideFriction(level), getGripFriction(level));
    if (frictionDelta == 0D) {
      return;
    }

    Location to = e.getTo();
    Vector velocity = p.getVelocity();
    if (!applyFallbackHorizontalVelocity(
        velocity,
        to.getX() - e.getFrom().getX(),
        to.getZ() - e.getFrom().getZ(),
        to.getWorld().getBlockAt(to.getBlockX(), (int) Math.floor(to.getY() - 0.500001D), to.getBlockZ())
            .getType().getSlipperiness(),
        frictionDelta)) {
      return;
    }

    p.setVelocity(velocity);
  }

  static double slideFriction(double base, double factor, double levelPercent) {
    double value = base + (levelPercent * factor);
    if (Double.isNaN(value)) {
      return 0D;
    }
    return Math.max(0D, value);
  }

  static double gripFriction(double base, double factor, double levelPercent) {
    double value = base + (levelPercent * factor);
    if (Double.isNaN(value)) {
      return 0D;
    }
    return Math.max(0D, value);
  }

  static boolean shouldSlide(boolean sprinting, int level) {
    return sprinting && level > 0;
  }

  static boolean shouldGrip(boolean sneaking, int level) {
    return sneaking && level > 0;
  }

  static double frictionDelta(boolean sprinting, boolean sneaking, double slideReduction, double gripIncrease) {
    double delta = 0D;
    if (sprinting && Double.isFinite(slideReduction)) {
      delta -= Math.max(0D, slideReduction);
    }
    if (sneaking && Double.isFinite(gripIncrease)) {
      delta += Math.max(0D, gripIncrease);
    }
    return delta;
  }

  static double fallbackVelocityScale(double velocityX, double velocityZ, double movementX, double movementZ,
      double blockSlipperiness, double frictionDelta) {
    if (!Double.isFinite(velocityX) || !Double.isFinite(velocityZ)
        || !Double.isFinite(movementX) || !Double.isFinite(movementZ)
        || !Double.isFinite(blockSlipperiness) || !Double.isFinite(frictionDelta)) {
      return 1D;
    }

    double velocitySquared = (velocityX * velocityX) + (velocityZ * velocityZ);
    if (velocitySquared <= MIN_HORIZONTAL_SPEED_SQUARED) {
      return 1D;
    }

    double baseFriction = Math.min(1D, Math.max(1.0E-6D, blockSlipperiness));
    double modifier = Math.max(0D, 1D + frictionDelta);
    double modifiedFriction = Math.min(1D, Math.max(0D, 1D - ((1D - baseFriction) * modifier)));
    double scale = modifiedFriction / baseFriction;
    if (scale <= 1D) {
      return Math.max(0D, scale);
    }

    double velocity = Math.sqrt(velocitySquared);
    double movement = Math.sqrt((movementX * movementX) + (movementZ * movementZ));
    double boundedVelocity = Math.min(velocity * scale, Math.max(velocity, movement));
    return boundedVelocity / velocity;
  }

  static boolean applyFallbackHorizontalVelocity(Vector velocity, double movementX, double movementZ,
      double blockSlipperiness, double frictionDelta) {
    if (velocity == null
        || !Double.isFinite(velocity.getX()) || !Double.isFinite(velocity.getY()) || !Double.isFinite(velocity.getZ())
        || !Double.isFinite(movementX) || !Double.isFinite(movementZ)) {
      return false;
    }

    double velocitySquared = (velocity.getX() * velocity.getX()) + (velocity.getZ() * velocity.getZ());
    double movementSquared = (movementX * movementX) + (movementZ * movementZ);
    if (velocitySquared <= MIN_HORIZONTAL_SPEED_SQUARED && movementSquared <= MIN_HORIZONTAL_SPEED_SQUARED) {
      return false;
    }

    double baseX = velocitySquared <= MIN_HORIZONTAL_SPEED_SQUARED ? movementX : velocity.getX();
    double baseZ = velocitySquared <= MIN_HORIZONTAL_SPEED_SQUARED ? movementZ : velocity.getZ();
    double scale = fallbackVelocityScale(
        baseX, baseZ, movementX, movementZ, blockSlipperiness, frictionDelta);
    double adjustedX = baseX * scale;
    double adjustedZ = baseZ * scale;
    if (!Double.isFinite(adjustedX) || !Double.isFinite(adjustedZ)
        || (Math.abs(adjustedX - velocity.getX()) <= 1.0E-9D
        && Math.abs(adjustedZ - velocity.getZ()) <= 1.0E-9D)) {
      return false;
    }

    velocity.setX(adjustedX);
    velocity.setZ(adjustedZ);
    return true;
  }

  private void reconcile(Player p) {
    if (p == null || !p.isOnline()) {
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    int level = getActiveLevel(p);
    if (Attributes.FRICTION_MODIFIER == null || level <= 0) {
      attributes.removeAll(p, getName());
      return;
    }

    if (shouldSlide(p.isSprinting(), level)) {
      attributes.apply(p, getName(), SLOT_SLIDE, Attributes.FRICTION_MODIFIER, -getSlideFriction(level), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    } else {
      attributes.remove(p, getName(), SLOT_SLIDE, Attributes.FRICTION_MODIFIER);
    }

    if (shouldGrip(p.isSneaking(), level)) {
      attributes.apply(p, getName(), SLOT_GRIP, Attributes.FRICTION_MODIFIER, getGripFriction(level), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    } else {
      attributes.remove(p, getName(), SLOT_GRIP, Attributes.FRICTION_MODIFIER);
    }
  }

  private double getSlideFriction(int level) {
    return slideFriction(getConfig().slideFrictionBase, getConfig().slideFrictionFactor, getLevelPercent(level));
  }

  private double getGripFriction(int level) {
    return gripFriction(getConfig().gripFrictionBase, getConfig().gripFrictionFactor, getLevelPercent(level));
  }

  @ConfigDescription("Sprint to slide across the ground with lowered friction; sneak to grip hard.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base fraction of ground friction removed while sprinting before level scaling.", impact = "Higher values make sprint-sliding slicker at every level.")
    double slideFrictionBase = 0.15;
    @ConfigDoc(value = "Additional friction reduction granted at max level while sprinting.", impact = "Higher values widen the slickness gain from leveling.")
    double slideFrictionFactor = 0.35;
    @ConfigDoc(value = "Base fraction of extra ground friction applied while sneaking before level scaling.", impact = "Higher values make sneak-gripping stickier at every level.")
    double gripFrictionBase = 0.2;
    @ConfigDoc(value = "Additional friction increase granted at max level while sneaking.", impact = "Higher values widen the grip gain from leveling.")
    double gripFrictionFactor = 0.4;

    public Config() {
      baseCost = 4;
      costFactor = 0.45;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
