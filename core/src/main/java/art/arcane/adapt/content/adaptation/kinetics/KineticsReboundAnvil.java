package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.adapt.platform.event.entity.EntityAttemptSmashAttackEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.catalog.KineticsMessages;

import java.util.List;

public class KineticsReboundAnvil extends SimpleAdaptation<KineticsReboundAnvil.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> BOUNCE = CommonPreferences.toggle("bounce", KineticsMessages.KINETICSREBOUNDANVIL_PREFERENCE_BOUNCE, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> CUSHION = CommonPreferences.toggle("cushion", KineticsMessages.KINETICSREBOUNDANVIL_PREFERENCE_CUSHION, CommonPreferences.Toggle.ON);

  private static final String SLOT_COIL = "coil";
  private static final String SLOT_CUSHION = "cushion";

  public KineticsReboundAnvil() {
    super("kinetics-rebound-anvil");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("slime_block"));
    setInterval(9999);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BOUNCE, CUSHION);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    AdaptAttributeService.get().removeAll(player.getPlayer(), getName());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.duration(getWindowTicks(level) * 50D, 1), 1);
    statLore(v, Form.pc(getFallRelief(level), 0), 2);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityAttemptSmashAttackEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    if (!smashLands(e.originalResult(), e.result())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    long windowTicks = getWindowTicks(level);
    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (p.hasAttribute(Attributes.BOUNCINESS) && preferenceEnabled(p, BOUNCE)) {
      attributes.applyTimed(p, getName(), SLOT_COIL, Attributes.BOUNCINESS, getBounciness(level), AttributeOperation.ADD_VALUE, windowTicks);
    }
    if (p.hasAttribute(Attributes.FALL_DAMAGE_MULTIPLIER) && preferenceEnabled(p, CUSHION)) {
      attributes.applyTimed(p, getName(), SLOT_CUSHION, Attributes.FALL_DAMAGE_MULTIPLIER, -getFallRelief(level), AttributeOperation.ADD_MULTIPLIED_TOTAL, windowTicks);
    }
    addStat(p, "kinetics.rebound.windows", 1);
  }

  static boolean smashLands(boolean originalResult, UseResult result) {
    return switch (result) {
      case ALLOW -> true;
      case DENY -> false;
      case DEFAULT -> originalResult;
    };
  }

  private double getBounciness(int level) {
    return getConfig().bouncinessBase + (getLevelPercent(level) * getConfig().bouncinessFactor);
  }

  private double getFallRelief(int level) {
    return getConfig().fallReliefBase + (getLevelPercent(level) * getConfig().fallReliefFactor);
  }

  private long getWindowTicks(int level) {
    return windowTicks(getConfig().windowTicksBase, getConfig().windowTicksFactor, getLevelPercent(level));
  }

  static long windowTicks(double base, double factor, double levelPercent) {
    return Math.max(1L, Math.round(base + (levelPercent * factor)));
  }

  @ConfigDescription("Each smash coils your legs: land springy and cushioned, ready for a second meteor.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Bounciness granted at level 0 progression after a smash.", impact = "Higher values spring low-level players higher when landing during the window.")
    double bouncinessBase = 0.5;
    @ConfigDoc(value = "Additional bounciness granted at maximum level progression.", impact = "Higher values spring max-level players higher when landing during the window.")
    double bouncinessFactor = 0.6;
    @ConfigDoc(value = "Fraction of fall damage removed at level 0 progression during the window.", impact = "Higher values cushion low-level landings more during the window.")
    double fallReliefBase = 0.4;
    @ConfigDoc(value = "Additional fraction of fall damage removed at maximum level progression.", impact = "Higher values cushion max-level landings more during the window.")
    double fallReliefFactor = 0.4;
    @ConfigDoc(value = "Duration in ticks of the rebound window at level 0 progression.", impact = "Higher values give low-level players longer to land the springy bounce.")
    double windowTicksBase = 40;
    @ConfigDoc(value = "Additional rebound window duration in ticks at maximum level progression.", impact = "Higher values give max-level players longer to land the springy bounce.")
    double windowTicksFactor = 30;

    public Config() {
      baseCost = 5;
      costFactor = 0.55;
      maxLevel = 3;
      initialCost = 4;
    }
  }
}
