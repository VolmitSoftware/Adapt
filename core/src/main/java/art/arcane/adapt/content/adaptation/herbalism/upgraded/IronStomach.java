package art.arcane.adapt.content.adaptation.herbalism.upgraded;

import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismHungerShield;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismHungryHippo;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

/**
 * Hungry Shield and Herbalist's Hippo fused: every damage type is paid from hunger first, and each meal adds its own
 * food value again once it has been eaten.
 */
public class IronStomach extends SkillUpgrade<IronStomach.Config> {
  private final HerbalismHungerShield shield = new HerbalismHungerShield(this, cooldowns(), cooldowns(), cooldowns(),
      (PlayerRef p, double absorbed) -> {
        addStat(p, "herbalism.iron-stomach.damage-absorbed", (int) Math.ceil(absorbed));
        cue(p, MutationCue.BOON);
      });

  public IronStomach() {
    super("upgraded-herbalism-iron-stomach");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("golden_carrot"));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getEffectiveness(level), 0), 1);
    statLore(v, Form.pc(getConfig().extraMealShare, 0), 2);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level > 0) {
        shield.absorb(p, e, getEffectiveness(level), false, getConfig().dotChargeIntervalMs);
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerItemConsumeEvent e) {
    PlayerRef p = e.player();
    Id food = e.item().type();
    if (e.isCancelled() || !HerbalismHungryHippo.isFood(food) || !hasActiveAdaptation(p)) {
      return;
    }

    int before = p.foodLevel();
    Tasks.runEntity(p, () -> secondHelping(p, food, before), 1);
  }

  private void secondHelping(PlayerRef p, Id food, int before) {
    int level = getActiveLevel(p);
    int extra = (int) Math.round((p.foodLevel() - before) * getConfig().extraMealShare);
    if (level <= 0 || extra <= 0) {
      return;
    }

    HerbalismHungryHippo.feed(p, extra);
    addStat(p, "herbalism.iron-stomach.bonus-food", extra);
    cue(p, MutationCue.BOON);
    HerbalismHungryHippo.mealFx(this, p, food, level);
  }

  double getEffectiveness(int level) {
    return Math.min(getConfig().maxEffectiveness, getConfig().effectivenessBase + (level * getConfig().effectivenessPerLevel));
  }

  @ConfigDescription("Hunger takes 50 to 80% of every damage type before your health, and food fills twice as much.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Share of each hit paid from hunger before level scaling.", impact = "Higher values move more damage onto your food bar.")
    double effectivenessBase = 0.35;
    @ConfigDoc(value = "Share of each hit paid from hunger added per level.", impact = "Higher values reward higher levels more.")
    double effectivenessPerLevel = 0.15;
    @ConfigDoc(value = "Highest share of a hit paid from hunger at any level.", impact = "Caps the absorption.")
    double maxEffectiveness = 0.9;
    @ConfigDoc(value = "Milliseconds between hunger charges for damage over time such as fire, poison and drowning.", impact = "Higher values let more ticks through but drain food slower.")
    long dotChargeIntervalMs = 1000;
    @ConfigDoc(value = "Food a meal adds again, as a share of what it gave.", impact = "1 doubles every meal; higher values fill even more.")
    double extraMealShare = 1.0;
  }
}
