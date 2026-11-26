package art.arcane.adapt.content.adaptation.herbalism.upgraded;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismBeeShepherd;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowthPulses;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismSeedSower;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismSowing;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

/**
 * Growth Aura, Bee Shepherd and Seed Sower fused: a wide growth aura, with more growth attempts for every bee nearby,
 * that also plants seeds from your hotbar on the empty farmland around you.
 */
public class Wildgrowth extends SkillUpgrade<Wildgrowth.Config> {
  private static final int HOTBAR_SLOTS = 9;
  private final Cooldowns plantCooldowns = cooldowns();
  private final HerbalismSowing sowing = new HerbalismSowing(this);
  private final HerbalismGrowthPulses pulses = new HerbalismGrowthPulses(this, playerState(),
      new HerbalismGrowthPulses.Planner() {
        @Override
        public HerbalismGrowthPulses.Pulse plan(PlayerRef player, int level) {
          sow(player, level);
          double radius = getRadius(level);
          int samples = HerbalismBeeShepherd.growthAttemptsWithBees(HerbalismGrowthPulses.sampleCountForRadius(radius),
              countBees(player, radius), getConfig().maxBonusBees, getConfig().growthBonusPerBee);
          return new HerbalismGrowthPulses.Pulse(radius, samples, getStrength(level), getConfig().foodCost,
              getConfig().surfaceOnly);
        }

        @Override
        public boolean grows(PlayerRef player, Id block) {
          return !AdaptConfig.get().isGrowthBlacklisted(block);
        }

        @Override
        public int foodFloor(PlayerRef player) {
          return getConfig().foodFloor;
        }
      },
      (PlayerRef player, int grown) -> {
        addStat(player, "herbalism.wildgrowth.blocks-grown", grown);
      });

  public Wildgrowth() {
    super("upgraded-herbalism-wildgrowth");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("flowering_azalea"));
    setInterval(10);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRadius(level), 0), 1);
    statLore(v, getMaxPlanted(level), 2);
    statLore(v, Form.f(getConfig().foodCost, 2), 3);
  }

  @Override
  public boolean hasTickDemand() {
    return super.hasTickDemand() || pulses.hasDemand();
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    pulses.tick();
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    pulses.forget(e.player().uuid());
  }

  private void sow(PlayerRef p, int level) {
    long interval = (long) getConfig().plantIntervalMillis;
    if (!plantCooldowns.isReady(p.uuid(), interval)) {
      return;
    }

    Loc feet = p.location();
    BlockRef origin = feet.world().blockAt(feet.blockX(), (int) Math.floor(feet.y() - 0.2D), feet.blockZ());
    int radius = (int) Math.floor(getRadius(level) * getConfig().plantRadiusShare);
    for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
      ItemRef stack = p.inventory().item(slot);
      Id cropType = isItem(stack) ? HerbalismSeedSower.cropType(stack.type()) : null;
      if (cropType == null) {
        continue;
      }

      int planted = sowing.plant(new HerbalismSowing.Request(p, origin, HerbalismSowing.slot(p, slot), stack.type(),
          cropType, radius, getMaxPlanted(level), 0));
      if (planted > 0) {
        plantCooldowns.mark(p.uuid(), interval);
        addStat(p, "harvest.planted", planted);
        addStat(p, "herbalism.wildgrowth.seeds-planted", planted);
        cue(p, MutationCue.WORK);
        return;
      }
    }
  }

  private int countBees(PlayerRef p, double radius) {
    int bees = 0;
    for (EntityRef entity : p.nearbyEntities(radius, radius, radius)) {
      if (HerbalismBeeShepherd.isBee(entity) && ++bees >= getConfig().maxBonusBees) {
        break;
      }
    }
    return bees;
  }

  public double getRadius(int level) {
    return getConfig().radiusBase + (level * getConfig().radiusPerLevel);
  }

  double getStrength(int level) {
    return getConfig().strengthBase + (level * getConfig().strengthPerLevel);
  }

  int getMaxPlanted(int level) {
    return Math.max(1, getConfig().plantedBase + (level * getConfig().plantedPerLevel));
  }

  @ConfigDescription("A wide growth aura, boosted by nearby bees, that also plants hotbar seeds on empty farmland.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Aura radius before level scaling, in blocks.", impact = "Higher values grow crops farther away.")
    double radiusBase = 14;
    @ConfigDoc(value = "Aura radius added per level, in blocks.", impact = "Higher values widen the aura at higher levels.")
    double radiusPerLevel = 4;
    @ConfigDoc(value = "Growth stages per sampled crop before level scaling.", impact = "Higher values age crops faster.")
    double strengthBase = 0;
    @ConfigDoc(value = "Growth stages per sampled crop added per level.", impact = "Higher values reward higher levels more.")
    double strengthPerLevel = 1;
    @ConfigDoc(value = "Food charged for each growth stage.", impact = "Higher values drain hunger faster.")
    double foodCost = 0.1;
    @ConfigDoc(value = "Food level growth never takes you under.", impact = "Higher values keep more food in reserve.")
    int foodFloor = 10;
    @ConfigDoc(value = "Only grow crops on the highest block of each column.", impact = "When enabled, crops under roofs or in caves are skipped.")
    boolean surfaceOnly = true;
    @ConfigDoc(value = "Extra growth attempts per nearby bee, as a share of the base attempts.", impact = "Higher values make bees matter more.")
    double growthBonusPerBee = 0.15;
    @ConfigDoc(value = "Most nearby bees that count toward the bonus.", impact = "Higher values let larger swarms keep helping.")
    int maxBonusBees = 5;
    @ConfigDoc(value = "Planting radius as a share of the aura radius.", impact = "Higher values sow farther from where you stand.")
    double plantRadiusShare = 0.25;
    @ConfigDoc(value = "Seeds planted per sowing before level scaling.", impact = "Higher values plant more each time.")
    int plantedBase = 4;
    @ConfigDoc(value = "Seeds planted per sowing added per level.", impact = "Higher values reward higher levels more.")
    int plantedPerLevel = 4;
    @ConfigDoc(value = "Time between two sowings, in milliseconds.", impact = "Lower values plant more often.")
    double plantIntervalMillis = 2000;
  }
}
