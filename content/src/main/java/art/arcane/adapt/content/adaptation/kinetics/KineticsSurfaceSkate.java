package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KineticsSurfaceSkate extends SimpleAdaptation<KineticsSurfaceSkate.Config> {
  public static final Id ID = Id.adapt("kinetics/surface_skate");
  public static final PlayerPreference<Control> CONTROL = PlayerPreference.of("control", Control.class, Control.SPRINT, List.of(
          new PlayerPreference.Choice<>(Control.SPRINT, Text.key("adapt.kineticssurfaceskate.preferences.preference_control_sprint"), Id.minecraft("sugar"), 1),
          new PlayerPreference.Choice<>(Control.ARMED, Text.key("adapt.kineticssurfaceskate.preferences.preference_control_armed"), Id.minecraft("ice"), 1))).labelled(Text.key("adapt.kineticssurfaceskate.preferences.preference_control"));

  private static final double MIN_HORIZONTAL_SPEED_SQUARED = 1.0E-6D;
  private static final double HORIZONTAL_AIR_DRAG = 0.91D;
  private static final long RECONCILE_INTERVAL_MS = 1000L;
  private static final String SLOT_SLIDE = "slide";
  /** A skater whose step is shorter than this has come to rest and gets ordinary friction back. */
  private static final double REST_SPEED_SQUARED = 9.0E-4D;
  /** How long a skater who is no longer sprinting may send no movement before they count as stopped. */
  private static final long REST_MS = 500L;
  /**
   * A grounded step this much shorter than the one before it means the player let go: held input keeps the step
   * length, airborne drag only shortens it to 0.91, and released ground friction cuts it to about 0.55.
   */
  private static final double RELEASE_RATIO = 0.8D;

  private final Map<UUID, Glide> glides = new ConcurrentHashMap<>();


  public KineticsSurfaceSkate() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("packed_ice"));
    setInterval(RECONCILE_INTERVAL_MS);
    pulseOwners(this::getInterval, this::reconcile);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONTROL);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    AdaptAttributeService.get().removeAll(player.player(), getId().toString());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getSlidePercent(level), 0), 1);
    statLore(v, Format.pc(getSneakBrakePercent(), 0), 2);
  }

  @Override
  public void unregister() {
    glides.clear();
    super.unregister();
  }

  @Handle
  public void on(PlayerToggleSprintEvent e) {
    if (!e.isSprinting()) {
      return;
    }

    PlayerRef p = e.player();
    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (shouldSlide(true, p.isSneaking(), level)) {
        applyFriction(p, level);
      }
    });
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    if (!e.isSneaking()) {
      return;
    }

    AdaptAttributeService.get().removeAll(p, getId().toString());
    Glide glide = glides.remove(p.uuid());
    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0 || !p.isOnGround()) {
        return;
      }

      Vec3 motion = glide == null ? p.velocity() : new Vec3(glide.stepX(), p.velocity().y(), glide.stepZ());
      Vec3 braked = applyHorizontalBrake(motion, getSneakBrakePercent());
      if (braked != null) {
        p.setVelocity(braked);
      }
    });
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    UUID id = p.uuid();
    Glide previous = glides.get(id);
    boolean skating = p.isSprinting() || preference(p, CONTROL) == Control.ARMED;
    if (!skating && previous == null) {
      return;
    }

    int level = getActiveLevel(p);
    if (!shouldSlide(true, p.isSneaking(), level)) {
      stop(p);
      return;
    }

    Loc to = e.to();
    double stepX = to.x() - e.from().x();
    double stepZ = to.z() - e.from().z();
    if (!skating && p.isOnGround() && (stepX * stepX) + (stepZ * stepZ) < REST_SPEED_SQUARED) {
      stop(p);
      return;
    }

    glides.put(id, new Glide(stepX, stepZ, System.currentTimeMillis()));
    if (p.hasAttribute(Attributes.FRICTION_MODIFIER)) {
      return;
    }

    if (previous == null || !p.isOnGround() || !released(previous.stepX(), previous.stepZ(), stepX, stepZ)) {
      return;
    }

    Vec3 velocity = glideVelocity(
        p.velocity(),
        stepX,
        stepZ,
        AdaptApi.platform().host().items().blockFriction(
            to.world().blockAt(to.blockX(), (int) Math.floor(to.y() - 0.500001D), to.blockZ()).type()),
        getSlidePercent(level));
    if (velocity != null) {
      p.setVelocity(velocity);
    }
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(PlayerQuitEvent e) {
    glides.remove(e.player().uuid());
  }

  static double slidePercent(double base, double factor, double levelPercent) {
    double normalizedLevel = finitePercent(levelPercent);
    return finitePercent(base + (normalizedLevel * factor));
  }

  static boolean shouldSlide(boolean sprinting, boolean sneaking, int level) {
    return sprinting && !sneaking && level > 0;
  }

  static double nativeSlideModifier(double slidePercent) {
    return -finitePercent(slidePercent);
  }

  /** Whether the step is short enough against the one before it to mean the player stopped pushing. */
  static boolean released(double previousX, double previousZ, double stepX, double stepZ) {
    double previousSquared = (previousX * previousX) + (previousZ * previousZ);
    double stepSquared = (stepX * stepX) + (stepZ * stepZ);
    return stepSquared > MIN_HORIZONTAL_SPEED_SQUARED && stepSquared < previousSquared * RELEASE_RATIO * RELEASE_RATIO;
  }

  static double modifiedSurfaceFriction(double blockSlipperiness, double slidePercent) {
    if (!Double.isFinite(blockSlipperiness)) {
      return 0D;
    }
    double surfaceFriction = finitePercent(blockSlipperiness);
    return surfaceFriction + ((1D - surfaceFriction) * finitePercent(slidePercent));
  }

  /**
   * The velocity a coasting player keeps from their last step with part of the surface's friction cancelled, or null
   * when there is nothing to keep. It stands in for the friction attribute on versions without one, and is only sent
   * while the player is not pushing, so it never fights their input.
   */
  static Vec3 glideVelocity(Vec3 velocity, double stepX, double stepZ, double blockSlipperiness, double slidePercent) {
    if (velocity == null || !Double.isFinite(velocity.y())
        || !Double.isFinite(stepX) || !Double.isFinite(stepZ)
        || !Double.isFinite(blockSlipperiness) || !Double.isFinite(slidePercent)
        || slidePercent <= 0D
        || (stepX * stepX) + (stepZ * stepZ) <= MIN_HORIZONTAL_SPEED_SQUARED) {
      return null;
    }

    double retention = HORIZONTAL_AIR_DRAG * modifiedSurfaceFriction(blockSlipperiness, slidePercent);
    return new Vec3(stepX * retention, velocity.y(), stepZ * retention);
  }

  static Vec3 applyHorizontalBrake(Vec3 velocity, double brakePercent) {
    if (velocity == null
        || !Double.isFinite(velocity.x()) || !Double.isFinite(velocity.y()) || !Double.isFinite(velocity.z())) {
      return null;
    }

    double horizontalSpeedSquared = (velocity.x() * velocity.x()) + (velocity.z() * velocity.z());
    double normalizedBrake = finitePercent(brakePercent);
    if (horizontalSpeedSquared <= MIN_HORIZONTAL_SPEED_SQUARED || normalizedBrake <= 0D) {
      return null;
    }

    double retained = 1D - normalizedBrake;
    return new Vec3(velocity.x() * retained, velocity.y(), velocity.z() * retained);
  }

  static double finitePercent(double value) {
    return Double.isFinite(value) ? Math.min(1D, Math.max(0D, value)) : 0D;
  }

  private void reconcile(PlayerRef p) {
    if (p == null || !p.isOnline()) {
      return;
    }

    int level = getActiveLevel(p);
    Glide glide = glides.get(p.uuid());
    boolean skating = p.isSprinting() || preference(p, CONTROL) == Control.ARMED;
    boolean coasting = glide != null && System.currentTimeMillis() - glide.at() <= REST_MS;
    if (shouldSlide(skating || coasting, p.isSneaking(), level)) {
      applyFriction(p, level);
    } else {
      stop(p);
    }
  }

  /** Lowers the player's ground friction where the game has the attribute; elsewhere the glide stands in for it. */
  private void applyFriction(PlayerRef p, int level) {
    if (p.hasAttribute(Attributes.FRICTION_MODIFIER)) {
      AdaptAttributeService.get().apply(p, getId().toString(), SLOT_SLIDE, Attributes.FRICTION_MODIFIER,
          nativeSlideModifier(getSlidePercent(level)), AttributeOperation.ADD_MULTIPLIED_TOTAL);
    }
  }

  private void stop(PlayerRef p) {
    glides.remove(p.uuid());
    AdaptAttributeService.get().remove(p, getId().toString(), SLOT_SLIDE, Attributes.FRICTION_MODIFIER);
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


  @ConfigDescription("Sprint to cancel a level-scaled percentage of friction on every ground surface until you stop; press sneak to brake horizontally.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base percentage of each ground surface's friction loss cancelled while sprinting before level scaling, from 0 to 1.", impact = "Higher values preserve more horizontal momentum on every ground material at every level.")
    double slidePercentBase = 0.15;
    @ConfigDoc(value = "Additional ground-friction percentage cancelled at max level, from 0 to 1 minus slidePercentBase.", impact = "Higher values make leveling add a stronger slide without exceeding complete friction cancellation.")
    double slidePercentFactor = 0.8;
    @ConfigDoc(value = "Percentage of horizontal velocity removed immediately when sneak is pressed while grounded, from 0 to 1.", impact = "The default 1 stops horizontal movement completely; lower values leave some momentum after braking.")
    double sneakBrakePercent = 1D;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }

  public enum Control { SPRINT, ARMED }

  /** A skating player's last horizontal step and when it was seen. */
  private record Glide(double stepX, double stepZ, long at) {
  }
}
