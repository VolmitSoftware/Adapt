package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.support.PlatformNeed;
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

public class KineticsQuakeGuard extends SimpleAdaptation<KineticsQuakeGuard.Config> {
  private static final String SLOT_KB = "quake-kb";
  private static final String SLOT_TOUGH = "quake-tough";
  private static final String SLOT_FALL = "quake-fall";

  public KineticsQuakeGuard() {
    super("kinetics-quake-guard");
    needs(PlatformNeed.item("mace"));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("polished_deepslate"));
    setInterval(9999);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    AdaptAttributeService.get().removeAll(player.getPlayer(), getName());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getKbResist(level), 0), 1);
    statLore(v, Form.f(getToughness(level), 1), 2);
    statLore(v, Form.f(getSafeFall(level), 1), 3);
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

    long braceTicks = getBraceTicks(level);
    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (p.hasAttribute(Attributes.KNOCKBACK_RESISTANCE)) {
      attributes.applyTimed(p, getName(), SLOT_KB, Attributes.KNOCKBACK_RESISTANCE, getKbResist(level), AttributeOperation.ADD_VALUE, braceTicks);
    }
    if (p.hasAttribute(Attributes.ARMOR_TOUGHNESS)) {
      attributes.applyTimed(p, getName(), SLOT_TOUGH, Attributes.ARMOR_TOUGHNESS, getToughness(level), AttributeOperation.ADD_VALUE, braceTicks);
    }
    if (p.hasAttribute(Attributes.SAFE_FALL_DISTANCE)) {
      attributes.applyTimed(p, getName(), SLOT_FALL, Attributes.SAFE_FALL_DISTANCE, getSafeFall(level), AttributeOperation.ADD_VALUE, braceTicks);
    }
  }

  static boolean smashLands(boolean originalResult, UseResult result) {
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
    return braceTicks(getConfig().braceTicksBase, getConfig().braceTicksFactor, getLevelPercent(level));
  }

  static long braceTicks(double base, double factor, double levelPercent) {
    return Math.max(1L, Math.round(base + (levelPercent * factor)));
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
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 1;
    }
  }
}
