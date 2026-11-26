package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.KineticsMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class KineticsMeteorCadence extends SimpleAdaptation<KineticsMeteorCadence.Config> {
  public static final PlayerPreference<Control> CONTROL = new PlayerPreference<>(Control.class,
      new PlayerPreference.Definition<>("control", KineticsMessages.KINETICSMETEORCADENCE_PREFERENCE_CONTROL, Control.SNEAK, List.of(
          new PlayerPreference.Choice<>(Control.SNEAK, KineticsMessages.KINETICSMETEORCADENCE_PREFERENCE_CONTROL_SNEAK, Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Control.AUTOMATIC, KineticsMessages.KINETICSMETEORCADENCE_PREFERENCE_CONTROL_AUTOMATIC, Id.minecraft("mace"), 1))));

  private final KineticsDive dive = new KineticsDive(this, playerState(), playerState());

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
    dive.end(player.getPlayer());
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
      dive.removeModifiers(p);
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
      dive.end(p);
      return;
    }

    refreshDive(p, to.y() - e.from().y());
  }

  public static boolean isDiving(boolean onGround, double deltaY, boolean sneaking, boolean holdingMace) {
    return !onGround && deltaY < 0D && sneaking && holdingMace;
  }

  public static double scaledDownwardAcceleration(double base, double factor, double levelPercent) {
    double acceleration = base + (factor * levelPercent);
    if (!Double.isFinite(acceleration)) {
      return 0D;
    }
    return Math.min(2D, Math.max(0D, acceleration));
  }

  private void refreshDive(PlayerRef p, double deltaY) {
    if (!isDiving(p.isOnGround(), deltaY, (p.isSneaking() || preference(p, CONTROL) == Control.AUTOMATIC), isMace(p.inventory().mainHand()))) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    KineticsDive.Pull pull = new KineticsDive.Pull(getGravityBoost(level), getDragCut(level),
        getDownwardAcceleration(level), getTerminalFallSpeed());
    if (dive.refresh(p, pull)) {
      addStat(p, "kinetics.meteor.dives", 1);
    }
  }

  private double verticalDelta(PlayerRef p) {
    return p.velocity().y();
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
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }

  public enum Control { SNEAK, AUTOMATIC }
}
