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

public class KineticsQuakeGuard extends SimpleAdaptation<KineticsQuakeGuard.Config> {
  private static final String SLOT_KB = "quake-kb";
  private static final String SLOT_TOUGH = "quake-tough";
  private static final String SLOT_FALL = "quake-fall";

  public KineticsQuakeGuard() {
    super("kinetics-quake-guard");
    registerConfiguration(Config.class);
    setIcon(Material.POLISHED_DEEPSLATE);
    setInterval(9999);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getKbResist(level), 0), 1);
    statLore(v, Form.f(getToughness(level), 1), 2);
    statLore(v, Form.f(getSafeFall(level), 1), 3);
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

    long braceTicks = getBraceTicks(level);
    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (Attributes.KNOCKBACK_RESISTANCE != null) {
      attributes.applyTimed(p, getName(), SLOT_KB, Attributes.KNOCKBACK_RESISTANCE, getKbResist(level), AttributeModifier.Operation.ADD_NUMBER, braceTicks);
    }
    if (Attributes.ARMOR_TOUGHNESS != null) {
      attributes.applyTimed(p, getName(), SLOT_TOUGH, Attributes.ARMOR_TOUGHNESS, getToughness(level), AttributeModifier.Operation.ADD_NUMBER, braceTicks);
    }
    if (Attributes.SAFE_FALL_DISTANCE != null) {
      attributes.applyTimed(p, getName(), SLOT_FALL, Attributes.SAFE_FALL_DISTANCE, getSafeFall(level), AttributeModifier.Operation.ADD_NUMBER, braceTicks);
    }
  }

  static boolean smashLands(boolean originalResult, Event.Result result) {
    return switch (result) {
      case ALLOW -> true;
      case DENY -> false;
      case DEFAULT -> originalResult;
    };
  }

  private double getKbResist(int level) {
    return getConfig().kbResistBase + (getLevelPercent(level) * getConfig().kbResistFactor);
  }

  private double getToughness(int level) {
    return getConfig().toughnessBase + (getLevelPercent(level) * getConfig().toughnessFactor);
  }

  private double getSafeFall(int level) {
    return getConfig().safeFallBase + (getLevelPercent(level) * getConfig().safeFallFactor);
  }

  private long getBraceTicks(int level) {
    return Math.round(getConfig().braceTicksBase + (getLevelPercent(level) * getConfig().braceTicksFactor));
  }

  @ConfigDescription("Landing a smash braces you: knockback resistance, toughness, and safe footing for a moment.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Knockback resistance granted at level 0 progression after landing a smash.", impact = "Higher values make low-level players harder to push around while braced.")
    double kbResistBase = 0.3;
    @ConfigDoc(value = "Additional knockback resistance granted at maximum level progression.", impact = "Higher values make max-level players harder to push around while braced.")
    double kbResistFactor = 0.5;
    @ConfigDoc(value = "Armor toughness granted at level 0 progression after landing a smash.", impact = "Higher values reduce incoming damage more for low-level players while braced.")
    double toughnessBase = 2;
    @ConfigDoc(value = "Additional armor toughness granted at maximum level progression.", impact = "Higher values reduce incoming damage more for max-level players while braced.")
    double toughnessFactor = 4;
    @ConfigDoc(value = "Safe fall distance in blocks granted at level 0 progression after landing a smash.", impact = "Higher values absorb more fall damage for low-level players while braced.")
    double safeFallBase = 2;
    @ConfigDoc(value = "Additional safe fall distance in blocks granted at maximum level progression.", impact = "Higher values absorb more fall damage for max-level players while braced.")
    double safeFallFactor = 4;
    @ConfigDoc(value = "Duration in ticks of the brace at level 0 progression.", impact = "Higher values keep the post-smash protections active longer for low-level players.")
    double braceTicksBase = 40;
    @ConfigDoc(value = "Additional brace duration in ticks at maximum level progression.", impact = "Higher values keep the post-smash protections active longer for max-level players.")
    double braceTicksFactor = 40;

    public Config() {
      baseCost = 4;
      costFactor = 0.45;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
