package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.KineticsMessages;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KineticsMeteorCadence extends SimpleAdaptation<KineticsMeteorCadence.Config> {
  public static final PlayerPreference<Control> CONTROL = new PlayerPreference<>(Control.class,
      new PlayerPreference.Definition<>("control", KineticsMessages.KINETICSMETEORCADENCE_PREFERENCE_CONTROL, Control.SNEAK, List.of(
          new PlayerPreference.Choice<>(Control.SNEAK, KineticsMessages.KINETICSMETEORCADENCE_PREFERENCE_CONTROL_SNEAK, Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Control.AUTOMATIC, KineticsMessages.KINETICSMETEORCADENCE_PREFERENCE_CONTROL_AUTOMATIC, Id.minecraft("mace"), 1))));

  private static final String SLOT_GRAVITY = "dive-gravity";
  private static final String SLOT_DRAG = "dive-drag";
  private static final int REFRESH_TICKS = 8;

  private final Map<UUID, Boolean> diveMarked = playerState();
  private final Map<UUID, Integer> acceleratedAtTick = playerState();

  public KineticsMeteorCadence() {
    super("kinetics-meteor-cadence");
    needs(PlatformNeed.item("mace"), PlatformNeed.nativeAttribute(Attributes.GRAVITY));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("anvil"));
    setInterval(9999);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONTROL);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    endDive(player.getPlayer());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getGravityBoost(level), 0), 1);
    statLore(v, Form.pc(getDragCut(level), 0), 2);
    statLore(v, Form.f(getDownwardAcceleration(level), 2), 3);
    statLore(v, Form.f(getTerminalFallSpeed(), 2), 4);
  }

  @Handle
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    if (!e.isSneaking() && preference(p, CONTROL) == Control.SNEAK) {
      removeDiveModifiers(p);
      return;
    }

    refreshDive(p, verticalDelta(p));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    Loc to = e.to();
    if (to == null) {
      return;
    }

    PlayerRef p = e.player();
    if (p.isOnGround()) {
      endDive(p);
      return;
    }

    refreshDive(p, to.y() - e.from().y());
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

  private void refreshDive(PlayerRef p, double deltaY) {
    if (!isDiving(p.isOnGround(), deltaY, (p.isSneaking() || preference(p, CONTROL) == Control.AUTOMATIC), isMace(p.inventory().mainHand()))) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.applyTimed(p, getName(), SLOT_GRAVITY, Attributes.GRAVITY, getGravityBoost(level), AttributeOperation.ADD_MULTIPLIED_TOTAL, REFRESH_TICKS);
    if (attributeExists(Attributes.AIR_DRAG_MODIFIER)) {
      attributes.applyTimed(p, getName(), SLOT_DRAG, Attributes.AIR_DRAG_MODIFIER, -getDragCut(level), AttributeOperation.ADD_MULTIPLIED_TOTAL, REFRESH_TICKS);
    }
    accelerateDive(p, level);

    if (diveMarked.putIfAbsent(p.uuid(), Boolean.TRUE) == null) {
      addStat(p, "kinetics.meteor.dives", 1);
    }
  }

  private void endDive(PlayerRef p) {
    if (diveMarked.isEmpty() && acceleratedAtTick.isEmpty()) {
      return;
    }

    UUID id = p.uuid();
    boolean hadDiveState = diveMarked.remove(id) != null;
    if (acceleratedAtTick.remove(id) != null) {
      hadDiveState = true;
    }
    if (hadDiveState) {
      removeDiveModifiers(p);
    }
  }

  private void removeDiveModifiers(PlayerRef p) {
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.remove(p, getName(), SLOT_GRAVITY, Attributes.GRAVITY);
    if (attributeExists(Attributes.AIR_DRAG_MODIFIER)) {
      attributes.remove(p, getName(), SLOT_DRAG, Attributes.AIR_DRAG_MODIFIER);
    }
  }

  private void accelerateDive(PlayerRef p, int level) {
    UUID id = p.uuid();
    int currentTick = p.ticksLived();
    Integer previousTick = acceleratedAtTick.put(id, currentTick);
    if (!shouldAccelerateAtTick(previousTick, currentTick)) {
      return;
    }

    Vec3 velocity = p.velocity();
    double acceleratedY = acceleratedDownwardVelocity(velocity.y(), getDownwardAcceleration(level), getTerminalFallSpeed());
    if (acceleratedY < velocity.y()) {
      p.setVelocity(new Vec3(velocity.x(), acceleratedY, velocity.z()));
    }
  }

  private double verticalDelta(PlayerRef p) {
    return p.velocity().y();
  }

  private static boolean attributeExists(Id attribute) {
    return AdaptCore.platform().host().registries().exists(RegistryKind.ATTRIBUTE, attribute);
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

  public enum Control { SNEAK, AUTOMATIC }
}
