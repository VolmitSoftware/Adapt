package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationOwnerPulse;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.KineticsMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class KineticsRubberSoul extends SimpleAdaptation<KineticsRubberSoul.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> BOUNCE = CommonPreferences.toggle("bounce", KineticsMessages.KINETICSRUBBERSOUL_PREFERENCE_BOUNCE, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SPRINGY = CommonPreferences.toggle("springy", KineticsMessages.KINETICSRUBBERSOUL_PREFERENCE_SPRINGY, CommonPreferences.Toggle.ON);

  private final KineticsLandingWatch landings = new KineticsLandingWatch(playerState(), playerState());
  private final KineticsSprings springs = new KineticsSprings(this);
  private final AdaptationOwnerPulse.Registration ownerMaintenance;

  public KineticsRubberSoul() {
    super("kinetics-rubber-soul");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("slime_ball"));
    setInterval(1000);
    ownerMaintenance = AdaptationOwnerPulse.register(
        this,
        this::getInterval,
        this::maintainOwner
    );
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BOUNCE, SPRINGY);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    AdaptAttributeService.get().removeAll(player.getPlayer(), getName());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getBounciness(level), 2), 1);
    statLore(v, Form.f(getSoftBlockBonus(level), 2), 2);
  }

  @Override
  public void unregister() {
    ownerMaintenance.unregister();
    landings.clear();
    super.unregister();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    landings.observe(this, e, this::applySpringload);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    if (!e.isCancelled()) {
      landings.forget(e.player().uuid());
    }
  }

  void applySpringload(PlayerRef p, PlayerMoveEvent e) {
    if (!preferenceEnabled(p, SPRINGY) || !springs.canSpringload(p)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      springs.springload(p, getSoftBlockBonus(level), getConfig().bonusWindowTicks);
    });
  }

  public static boolean isLanding(boolean onGround, boolean wasAirborne) {
    return onGround && wasAirborne;
  }

  public static double bounciness(double base, double factor, double levelPercent) {
    double value = base + (levelPercent * factor);
    if (!Double.isFinite(value)) {
      return 0D;
    }
    return Math.max(0D, value);
  }

  public static double softBlockBonus(double base, double factor, double levelPercent) {
    double value = base + (levelPercent * factor);
    if (!Double.isFinite(value)) {
      return 0D;
    }
    return Math.max(0D, value);
  }

  private void maintainOwner(PlayerRef player) {
    springs.maintainSole(player, hasActiveAdaptation(player) && preferenceEnabled(player, BOUNCE),
        getBounciness(getLevel(player)));
  }

  private double getBounciness(int level) {
    return bounciness(getConfig().bouncinessBase, getConfig().bouncinessFactor, getLevelPercent(level));
  }

  private double getSoftBlockBonus(int level) {
    return softBlockBonus(getConfig().softBlockBonusBase, getConfig().softBlockBonusFactor, getLevelPercent(level));
  }

  static void springloadFx(Adaptation<?> owner, Loc at) {
    Fx.now(owner, at, FxPriority.GAMEPLAY)
        .particle(Particles.ITEM_SLIME, 6, 0, 0.2D, 0, 0.3D, 0.05D)
        .sound(Sounds.BLOCK_SLIME_BLOCK_FALL, 0.5F, 1.4F);
  }

  @ConfigDescription("Passive bounciness preserves landing momentum. Slime, honey, and bed landings grant a short springload bonus.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base passive bounciness bonus applied while the adaptation is active.", impact = "Higher values make every landing springier at every level.")
    double bouncinessBase = 0.15;
    @ConfigDoc(value = "Additional passive bounciness granted at max level.", impact = "Higher values widen the springiness gain from leveling.")
    double bouncinessFactor = 0.35;
    @ConfigDoc(value = "Base extra bounciness granted after landing on a bouncy block before level scaling.", impact = "Higher values make bouncy-block landings launch harder at every level.")
    double softBlockBonusBase = 0.3;
    @ConfigDoc(value = "Additional bouncy-block bounciness granted at max level.", impact = "Higher values widen the bouncy-block launch gain from leveling.")
    double softBlockBonusFactor = 0.5;
    @ConfigDoc(value = "Duration in ticks of the bouncy-block bounciness bonus window.", impact = "Higher values keep the springload bonus active longer after each bouncy landing.")
    long bonusWindowTicks = 40;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 1;
    }
  }
}
