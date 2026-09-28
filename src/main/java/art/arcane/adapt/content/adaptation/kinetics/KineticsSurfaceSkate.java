package art.arcane.adapt.content.adaptation.kinetics;

import java.util.List;
import art.arcane.adapt.localization.catalog.KineticsMessages;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationOwnerPulse;
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
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

public class KineticsSurfaceSkate extends SimpleAdaptation<KineticsSurfaceSkate.Config> {
  public static final PlayerPreference<Control> CONTROL = new PlayerPreference<>(Control.class,
      new PlayerPreference.Definition<>("control", KineticsMessages.KINETICSSURFACESKATE_PREFERENCE_CONTROL, Control.SPRINT, List.of(
          new PlayerPreference.Choice<>(Control.SPRINT, KineticsMessages.KINETICSSURFACESKATE_PREFERENCE_CONTROL_SPRINT, Material.SUGAR, 1),
          new PlayerPreference.Choice<>(Control.ARMED, KineticsMessages.KINETICSSURFACESKATE_PREFERENCE_CONTROL_ARMED, Material.ICE, 1))));

  private static final double MIN_HORIZONTAL_SPEED_SQUARED = 1.0E-6D;
  private static final double HORIZONTAL_AIR_DRAG = 0.91D;
  private static final long RECONCILE_INTERVAL_MS = 1000L;
  private static final String SLOT_SLIDE = "slide";

  private final AdaptationOwnerPulse.Registration ownerMaintenance;

  public KineticsSurfaceSkate() {
    super("kinetics-surface-skate");
    registerConfiguration(Config.class);
    setIcon(Material.PACKED_ICE);
    setInterval(RECONCILE_INTERVAL_MS);
    ownerMaintenance = AdaptationOwnerPulse.register(
        this,
        this::getInterval,
        this::reconcile
    );
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONTROL);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    AdaptAttributeService.get().removeAll(player.getPlayer(), getName());
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getSlidePercent(level), 0), 1);
    statLore(v, Form.pc(getSneakBrakePercent(), 0), 2);
  }

  @Override
  public void unregister() {
    ownerMaintenance.unregister();
    super.unregister();
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
      if (!shouldSlide(e.isSprinting(), p.isSneaking(), level)) {
        return;
      }

      attributes.apply(p, getName(), SLOT_SLIDE, Attributes.FRICTION_MODIFIER, nativeSlideModifier(getSlidePercent(level)), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    });
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    if (!e.isSneaking()) {
      return;
    }

    AdaptAttributeService.get().removeAll(p, getName());
    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0 || !p.isOnGround()) {
        return;
      }

      Vector velocity = p.getVelocity();
      if (applyHorizontalBrake(velocity, getSneakBrakePercent())) {
        p.setVelocity(velocity);
      }
    });
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    if (Attributes.FRICTION_MODIFIER != null || e.getTo() == null || e instanceof PlayerTeleportEvent) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isOnGround()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!shouldSlide(p.isSprinting() || preference(p, CONTROL) == Control.ARMED, p.isSneaking(), level)) {
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
        getSlidePercent(level))) {
      return;
    }

    p.setVelocity(velocity);
  }

  static double slidePercent(double base, double factor, double levelPercent) {
    double normalizedLevel = finitePercent(levelPercent);
    return finitePercent(base + (normalizedLevel * factor));
  }

  static double nativeSlideModifier(double slidePercent) {
    return -finitePercent(slidePercent);
  }

  static boolean shouldSlide(boolean sprinting, boolean sneaking, int level) {
    return sprinting && !sneaking && level > 0;
  }

  static double modifiedSurfaceFriction(double blockSlipperiness, double slidePercent) {
    if (!Double.isFinite(blockSlipperiness)) {
      return 0D;
    }
    double surfaceFriction = finitePercent(blockSlipperiness);
    double cancelledFriction = finitePercent(slidePercent);
    return surfaceFriction + ((1D - surfaceFriction) * cancelledFriction);
  }

  static boolean applyFallbackHorizontalVelocity(Vector velocity, double movementX, double movementZ,
      double blockSlipperiness, double slidePercent) {
    if (velocity == null
        || !Double.isFinite(velocity.getX()) || !Double.isFinite(velocity.getY()) || !Double.isFinite(velocity.getZ())
        || !Double.isFinite(movementX) || !Double.isFinite(movementZ)
        || !Double.isFinite(blockSlipperiness) || !Double.isFinite(slidePercent)
        || slidePercent <= 0D) {
      return false;
    }

    double movementSquared = (movementX * movementX) + (movementZ * movementZ);
    if (movementSquared <= MIN_HORIZONTAL_SPEED_SQUARED) {
      return false;
    }
    double retention = HORIZONTAL_AIR_DRAG * modifiedSurfaceFriction(blockSlipperiness, slidePercent);
    double adjustedX = movementX * retention;
    double adjustedZ = movementZ * retention;
    double velocitySquared = (velocity.getX() * velocity.getX()) + (velocity.getZ() * velocity.getZ());
    double adjustedSquared = (adjustedX * adjustedX) + (adjustedZ * adjustedZ);
    if (!Double.isFinite(adjustedSquared) || adjustedSquared <= velocitySquared) {
      return false;
    }

    velocity.setX(adjustedX);
    velocity.setZ(adjustedZ);
    return true;
  }

  static boolean applyHorizontalBrake(Vector velocity, double brakePercent) {
    if (velocity == null
        || !Double.isFinite(velocity.getX()) || !Double.isFinite(velocity.getY()) || !Double.isFinite(velocity.getZ())) {
      return false;
    }

    double horizontalSpeedSquared = (velocity.getX() * velocity.getX()) + (velocity.getZ() * velocity.getZ());
    double normalizedBrake = finitePercent(brakePercent);
    if (horizontalSpeedSquared <= MIN_HORIZONTAL_SPEED_SQUARED || normalizedBrake <= 0D) {
      return false;
    }

    double retained = 1D - normalizedBrake;
    velocity.setX(velocity.getX() * retained);
    velocity.setZ(velocity.getZ() * retained);
    return true;
  }

  static double finitePercent(double value) {
    return Double.isFinite(value) ? Math.min(1D, Math.max(0D, value)) : 0D;
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

    if (shouldSlide(p.isSprinting() || preference(p, CONTROL) == Control.ARMED, p.isSneaking(), level)) {
      attributes.apply(p, getName(), SLOT_SLIDE, Attributes.FRICTION_MODIFIER, nativeSlideModifier(getSlidePercent(level)), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    } else {
      attributes.remove(p, getName(), SLOT_SLIDE, Attributes.FRICTION_MODIFIER);
    }
  }

  private double getSlidePercent(int level) {
    return slidePercent(getConfig().slidePercentBase, getConfig().slidePercentFactor, getLevelPercent(level));
  }

  private double getSneakBrakePercent() {
    return finitePercent(getConfig().sneakBrakePercent);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.slidePercentBase = finitePercent(loadedConfig.slidePercentBase);
    loadedConfig.slidePercentFactor = Math.min(
        finitePercent(loadedConfig.slidePercentFactor),
        1D - loadedConfig.slidePercentBase
    );
    loadedConfig.sneakBrakePercent = finitePercent(loadedConfig.sneakBrakePercent);
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @ConfigDescription("Sprint to cancel a level-scaled percentage of friction on every ground surface; press sneak to brake horizontally.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base percentage of each ground surface's friction loss cancelled while sprinting before level scaling, from 0 to 1.", impact = "Higher values preserve more horizontal momentum on every ground material at every level.")
    double slidePercentBase = 0.15;
    @ConfigDoc(value = "Additional ground-friction percentage cancelled at max level, from 0 to 1 minus slidePercentBase.", impact = "Higher values make leveling add a stronger slide without exceeding complete friction cancellation.")
    double slidePercentFactor = 0.35;
    @ConfigDoc(value = "Percentage of horizontal velocity removed immediately when sneak is pressed while grounded, from 0 to 1.", impact = "The default 1 stops horizontal movement completely; lower values leave some momentum after braking.")
    double sneakBrakePercent = 1D;

    public Config() {
      baseCost = 4;
      costFactor = 0.45;
      maxLevel = 5;
      initialCost = 2;
    }
  }

  public enum Control { SPRINT, ARMED }
}
