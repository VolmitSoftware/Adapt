package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityLungeEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;

public class KineticsLungeConductor extends SimpleAdaptation<KineticsLungeConductor.Config> {
  public static final Id ID = Id.adapt("kinetics/lunge_conductor");
  public static final PlayerPreference<CommonPreferences.Toggle> TRAVEL = CommonPreferences.toggle("travel", Text.key("adapt.kineticslungeconductor.preferences.preference_travel"), CommonPreferences.Toggle.ON);

  private static final Rgb CONDUCTOR = Rgb.of(0xBFE8FF);
  private final Cooldowns lungeCooldown = cooldowns();

  public KineticsLungeConductor() {
    super(ID);
    needs(PlatformNeed.item("iron_spear"));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("feather"));
    setInterval(9999);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TRAVEL);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getPowerBonus(level), 1);
    statLore(v, Format.f(getDashBoost(level), 2), 2);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityLungeEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!lungeCooldown.isReady(p.uuid(), getConfig().cooldownMs)) {
      return;
    }

    e.setPower(boostedPower(e.power(), getPowerBonus(level)));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void finalizeLunge(EntityLungeEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!lungeCooldown.isReady(p.uuid(), getConfig().cooldownMs)) {
      return;
    }

    lungeCooldown.mark(p.uuid(), getConfig().cooldownMs);

    Vec3 direction = p.location().direction();
    double boost = getDashBoost(level);
    AdaptApi.scheduler().runEntity(p, () -> {
      if (!isRuntimeRegistered() || !p.isOnline() || !p.isValid()) {
        return;
      }

      if (hasActiveAdaptation(p) && preferenceEnabled(p, TRAVEL)) {
        p.setVelocity(assistVelocity(p.velocity(), direction, boost));
      }
      fx(p.location().add(0, 1, 0), FxPriority.GAMEPLAY)
          .particle(Particles.CLOUD, 4, 0, 0.1D, 0, 0.15D, 0.02D)
          .dustBurst(CONDUCTOR, 4, 0.3D, 1.0F)
          .chord(Sounds.ITEM_TRIDENT_RIPTIDE_1, 0.35F, 1.6F, Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 0.4F, 1.3F);
    }, 1);
  }

  static int boostedPower(int current, int bonus) {
    return current + Math.max(0, bonus);
  }

  static Vec3 assistVelocity(Vec3 current, Vec3 direction, double boost) {
    if (direction == null || !Double.isFinite(boost) || boost <= 0D) {
      return current;
    }
    double directionX = Double.isFinite(direction.x()) ? direction.x() : 0D;
    double directionZ = Double.isFinite(direction.z()) ? direction.z() : 0D;
    return new Vec3(current.x() + (directionX * boost), current.y(), current.z() + (directionZ * boost));
  }

  private int getPowerBonus(int level) {
    return (int) Math.round(getConfig().powerBonusBase + (getLevelPercent(level) * getConfig().powerBonusFactor));
  }

  private double getDashBoost(int level) {
    return getConfig().dashBoostBase + (getLevelPercent(level) * getConfig().dashBoostFactor);
  }

  @ConfigDescription("Your spear lunges strike harder and carry you farther.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base lunge power added to every boosted lunge.", impact = "Higher values make even low-level lunges noticeably stronger.")
    double powerBonusBase = 1;
    @ConfigDoc(value = "Additional lunge power granted as the adaptation levels up.", impact = "Higher values widen the lunge-power gain from leveling.")
    double powerBonusFactor = 2;
    @ConfigDoc(value = "Base forward velocity assist applied when a lunge fires.", impact = "Higher values carry the lunge farther forward.")
    double dashBoostBase = 0.2;
    @ConfigDoc(value = "Additional forward velocity assist granted at max level.", impact = "Higher values make leveled lunges travel much farther.")
    double dashBoostFactor = 0.3;
    @ConfigDoc(value = "Minimum milliseconds between boosted lunges.", impact = "Higher values space out boosted lunges; lower values allow faster chaining.")
    long cooldownMs = 2500;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 1;
    }
  }
}
