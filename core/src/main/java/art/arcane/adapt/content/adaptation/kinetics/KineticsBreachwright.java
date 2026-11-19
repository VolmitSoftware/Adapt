package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityAttemptSmashAttackEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

public class KineticsBreachwright extends SimpleAdaptation<KineticsBreachwright.Config> {
  private static final String SLOT_SHRED = "shred";
  private static final String SLOT_SHRED_TOUGHNESS = "shred-toughness";

  private final Cooldowns targetCooldowns = cooldowns();

  public KineticsBreachwright() {
    super("kinetics-breachwright");
    needs(PlatformNeed.item("mace"));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_scrap"));
    setInterval(9999);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getArmorShred(level), 1), 1);
    statLore(v, Form.f(getToughnessShred(level), 1), 2);
    statLore(v, Form.duration(getShredTicks(level) * 50D, 1), 3);
  }

  @Override
  public void onTick() {
    targetCooldowns.clearExpired(getConfig().targetCooldownMs);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityAttemptSmashAttackEvent e) {
    if (!resultAllowsShred(e.originalResult(), e.result())) {
      return;
    }

    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    LivingRef target = e.target();
    if (target == null || !target.isValid() || target.isDead()) {
      return;
    }

    boolean allowed = target instanceof PlayerRef ? canPVP(p, target.location()) : canPVE(p, target.location());
    if (!allowed) {
      return;
    }

    if (!targetCooldowns.isReady(target.uuid(), getConfig().targetCooldownMs)) {
      return;
    }

    targetCooldowns.mark(target.uuid());
    double armorShred = getArmorShred(level);
    double toughnessShred = getToughnessShred(level);
    int shredTicks = getShredTicks(level);
    Tasks.runEntity(target, () -> {
      if (!isRuntimeRegistered() || !target.isValid() || target.isDead()) {
        return;
      }

      AdaptAttributeService attributes = AdaptAttributeService.get();
      attributes.applyTimed(target, getName(), SLOT_SHRED, Attributes.ARMOR, -armorShred, AttributeOperation.ADD_VALUE, shredTicks);
      attributes.applyTimed(target, getName(), SLOT_SHRED_TOUGHNESS, Attributes.ARMOR_TOUGHNESS, -toughnessShred, AttributeOperation.ADD_VALUE, shredTicks);
    });
    addStat(p, "kinetics.smash.shreds", 1);
  }

  static boolean resultAllowsShred(boolean originalResult, UseResult result) {
    return result == UseResult.ALLOW || (result == UseResult.DEFAULT && originalResult);
  }

  static int shredDurationTicks(double base, double factor, double levelPercent) {
    double ticks = base + (levelPercent * factor);
    if (Double.isNaN(ticks)) {
      return 1;
    }
    return Math.max(1, (int) Math.round(ticks));
  }

  private double getArmorShred(int level) {
    return getConfig().armorShredBase + (getLevelPercent(level) * getConfig().armorShredFactor);
  }

  private double getToughnessShred(int level) {
    return getConfig().toughnessShredBase + (getLevelPercent(level) * getConfig().toughnessShredFactor);
  }

  private int getShredTicks(int level) {
    return shredDurationTicks(getConfig().shredTicksBase, getConfig().shredTicksFactor, getLevelPercent(level));
  }

  @ConfigDescription("Your mace smashes shred the target's armor for a short time.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base armor points removed from smashed targets at level 1.", impact = "Higher values make smashed targets take noticeably more damage.")
    double armorShredBase = 2;
    @ConfigDoc(value = "Additional armor points removed at max level.", impact = "Higher values make max-level smashes strip more armor.")
    double armorShredFactor = 4;
    @ConfigDoc(value = "Base armor toughness removed from smashed targets at level 1.", impact = "Higher values weaken heavily armored targets more.")
    double toughnessShredBase = 1;
    @ConfigDoc(value = "Additional armor toughness removed at max level.", impact = "Higher values weaken heavily armored targets more as levels increase.")
    double toughnessShredFactor = 3;
    @ConfigDoc(value = "Base shred duration in ticks at level 1.", impact = "Higher values keep targets shredded longer.")
    double shredTicksBase = 80;
    @ConfigDoc(value = "Additional shred duration in ticks granted at max level.", impact = "Higher values keep targets shredded longer as levels increase.")
    double shredTicksFactor = 60;
    @ConfigDoc(value = "Per-target cooldown in milliseconds between shreds.", impact = "Higher values prevent re-shredding the same target as often.")
    long targetCooldownMs = 3000;

    public Config() {
      baseCost = 4;
      costFactor = 0.45;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
