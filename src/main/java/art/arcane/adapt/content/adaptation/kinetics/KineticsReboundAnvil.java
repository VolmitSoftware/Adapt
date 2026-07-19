package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class KineticsReboundAnvil extends SimpleAdaptation<KineticsReboundAnvil.Config> {
  private static final String SLOT_COIL = "coil";
  private static final String SLOT_CUSHION = "cushion";

  public KineticsReboundAnvil() {
    super("kinetics-rebound-anvil");
    registerConfiguration(Config.class);
    setIcon(Material.SLIME_BLOCK);
    setInterval(9999);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.duration(getWindowTicks(level) * 50D, 1), 1);
    statLore(v, Form.pc(getFallRelief(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityAttemptSmashAttackEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    if (!smashLands(e.getOriginalResult(), e.getResult())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    long windowTicks = getWindowTicks(level);
    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (Attributes.BOUNCINESS != null) {
      attributes.applyTimed(p, getName(), SLOT_COIL, Attributes.BOUNCINESS, getBounciness(level), AttributeModifier.Operation.ADD_NUMBER, windowTicks);
    }
    if (Attributes.FALL_DAMAGE_MULTIPLIER != null) {
      attributes.applyTimed(p, getName(), SLOT_CUSHION, Attributes.FALL_DAMAGE_MULTIPLIER, -getFallRelief(level), AttributeModifier.Operation.MULTIPLY_SCALAR_1, windowTicks);
    }
    addStat(p, "kinetics.rebound.windows", 1);
  }

  static boolean smashLands(boolean originalResult, Event.Result result) {
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
    return Math.round(getConfig().windowTicksBase + (getLevelPercent(level) * getConfig().windowTicksFactor));
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
