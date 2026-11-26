package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.KineticsMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityAttemptSmashAttackEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.UUID;

public class KineticsWindburst extends SimpleAdaptation<KineticsWindburst.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> HOSTILES = CommonPreferences.toggle("hostiles", KineticsMessages.KINETICSWINDBURST_PREFERENCE_HOSTILES, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", KineticsMessages.KINETICSWINDBURST_PREFERENCE_SNEAK, CommonPreferences.Toggle.OFF);

  private static final String SLOT_BRACE = "brace";
  private static final int BRACE_TICKS = 20;

  private final Cooldowns cooldowns = cooldowns();
  private final KineticsShockwave shockwave = new KineticsShockwave(this);

  public KineticsWindburst() {
    super("kinetics-windburst");
    needs(PlatformNeed.item("mace"));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("wind_charge"));
    setInterval(9999);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    AdaptAttributeService.get().removeAll(player.getPlayer(), getName());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, HOSTILES, SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRadius(level), 1), 1);
    statLore(v, Form.f(getForce(level), 2), 2);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityAttemptSmashAttackEvent e) {
    if (!smashLands(e.originalResult(), e.result())) {
      return;
    }

    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || (preferenceEnabled(p, SNEAK) && !p.isSneaking())) {
      return;
    }

    if (!burstReady(p.fallDistance(), getMinFallDistance(level))) {
      return;
    }

    UUID id = p.uuid();
    if (!cooldowns.isReady(id, getConfig().cooldownMs)) {
      return;
    }

    cooldowns.mark(id, getConfig().cooldownMs);
    double radius = getRadius(level);
    double force = getForce(level);
    Loc origin = p.location();
    shockwave.release(new KineticsShockwave.Wave(p, origin, radius, force, 0D, preferenceEnabled(p, HOSTILES),
        (LivingRef target) -> { }));

    if (p.hasAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)) {
      AdaptAttributeService.get().applyTimed(p, getName(), SLOT_BRACE, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0D, AttributeOperation.ADD_VALUE, BRACE_TICKS);
    }

    xp(p, origin, getConfig().xpPerBurst, "kinetics:windburst");
    addStat(p, "kinetics.windburst.bursts", 1);
  }

  public static boolean burstReady(double fallDistance, double minFallDistance) {
    return fallDistance >= minFallDistance;
  }

  public static boolean smashLands(boolean originalResult, UseResult result) {
    return result == UseResult.ALLOW || (result == UseResult.DEFAULT && originalResult);
  }

  public static double minFallDistance(double base, double factor, double levelPercent) {
    double distance = base + (levelPercent * factor);
    if (Double.isNaN(distance)) {
      return 0D;
    }
    return Math.max(0D, distance);
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private double getForce(int level) {
    return getConfig().forceBase + (getLevelPercent(level) * getConfig().forceFactor);
  }

  private double getMinFallDistance(int level) {
    return minFallDistance(getConfig().minFallDistanceBase, getConfig().minFallDistanceFactor, getLevelPercent(level));
  }

  public static void impactFx(Adaptation<?> owner, Loc at) {
    Fx.now(owner, at, FxPriority.COMBAT)
        .particle(Particles.CLOUD, 3, 0, 0, 0, 0.15D, 0.03D);
  }

  static void burstFx(Adaptation<?> owner, Loc origin, double radius) {
    Fx.now(owner, origin, FxPriority.COMBAT)
        .particle(Particles.GUST, 1, 0, 0, 0, 0.1D, 0.02D)
        .chord(Sounds.ENTITY_WIND_CHARGE_WIND_BURST, 0.9F, 0.8F, Sounds.ENTITY_GENERIC_EXPLODE, 0.4F, 0.6F);
    int ringPoints = Math.min(24, 10 + (int) (radius * 2));
    FxTimeline.at(owner, origin.add(0, 0.4D, 0))
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(Math.min(48.0D, (radius * 2.0D) + 8.0D))
        .frame((fx, tick, progress) -> fx.ring(Particles.CLOUD, 0.4D + (radius * progress), ringPoints, 0.05D))
        .start();
  }

  @ConfigDescription("Heavy smashes erupt in a radial shockwave that hurls nearby enemies away.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base shockwave radius in blocks at level 1.", impact = "Higher values let the burst reach more distant enemies.")
    double radiusBase = 2.5;
    @ConfigDoc(value = "Additional shockwave radius granted at max level.", impact = "Higher values extend the burst reach as levels increase.")
    double radiusFactor = 2.5;
    @ConfigDoc(value = "Base knockback force at level 1.", impact = "Higher values hurl enemies further from the smash point.")
    double forceBase = 0.6;
    @ConfigDoc(value = "Additional knockback force granted at max level.", impact = "Higher values hurl enemies further as levels increase.")
    double forceFactor = 0.8;
    @ConfigDoc(value = "Base fall distance in blocks required to trigger a burst at level 1.", impact = "Higher values demand higher drops before smashes burst.")
    double minFallDistanceBase = 3;
    @ConfigDoc(value = "Fall distance requirement change applied at max level.", impact = "Negative values make bursts easier to trigger as levels increase.")
    double minFallDistanceFactor = -1;
    @ConfigDoc(value = "Cooldown in milliseconds between bursts.", impact = "Higher values make bursts available less often.")
    long cooldownMs = 4000;
    @ConfigDoc(value = "XP granted per triggered burst.", impact = "Higher values speed up kinetics skill progression from bursts.")
    double xpPerBurst = 8;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
