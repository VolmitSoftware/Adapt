package art.arcane.adapt.content.adaptation.herbalism.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismField;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismHarvest;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismLuck;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismLuckyDrops;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismReplant;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismScarecrows;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockBurnEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Harvest &amp; Replant, Compost Cascade and Herbalist's Luck fused: right-clicking a mature crop with a hoe sweeps the
 * whole field around it, harvesting and replanting every mature crop. Crops that grew on their own pay skill XP and
 * may drop a lucky extra; crops grown with bone meal or by Adapt pay neither. Sneak-clicking a composter runs Compost
 * Cascade, and the Scarecrow tool tends a square of the field while its owner is near.
 */
public class HarvestMoon extends SkillUpgrade<HarvestMoon.Config> {
  public static final Id ID = Id.adapt("herbalism/upgrade/harvest_moon");

  private static final Rgb HARVEST = new Rgb(230, 190, 70);
  private final HerbalismHarvest harvest = new HerbalismHarvest(this, HerbalismReplant.MATERIALS,
      (BlockRef crop) -> crop != null && !Mutations.farmedHarvest(crop), (PlayerRef p, boolean replanted) -> {
        if (replanted) {
          addStat(p, "harvest.planted", 1);
        }
        addStat(p, "harvest.blocks", 1);
        addStat(p, "herbalism.harvest-moon.crops-harvested", 1);
      });
  private final HerbalismLuckyDrops luck = new HerbalismLuckyDrops(this, (PlayerRef p) -> {
    addStat(p, "herbalism.harvest-moon.bonus-yields", 1);
  });

  private final Scarecrow scarecrow;
  private final HerbalismScarecrows scarecrows;

  public HarvestMoon() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("golden_hoe"));
    setInterval(2000);
    scarecrow = registerTool(new Scarecrow(this));
    scarecrows = new HerbalismScarecrows(this, HerbalismReplant.MATERIALS, this::scarecrowSettings, scarecrow::worn,
        (PlayerRef p, int harvested) -> {
          addStat(p, "harvest.blocks", harvested);
          addStat(p, "herbalism.harvest-moon.scarecrow-harvests", harvested);
        });
  }

  HerbalismScarecrows scarecrows() {
    return scarecrows;
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    for (PlayerProfile learner : learnedCandidates(System.currentTimeMillis())) {
      PlayerRef p = learner.player();
      if (p != null) {
        withPlayerThread(p, () -> scarecrows.tend(p, getActiveLevel(p)));
      }
    }
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, HerbalismReplant.MATERIALS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getFieldRadius(level), 0), 1);
    statLore(v, Format.f(getBonusChance(level), 0) + "%", 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getConfig().cooldownTicks * 50D, 1), 3);
    statLore(v, getScarecrows(level), 4);
    int side = (getScarecrowHalf(level) * 2) + 1;
    statLore(v, side + "x" + side, 5);
    grantedLore(v, level);
  }

  @Handle(priority = EventPriority.HIGH)
  public void onRepair(PlayerInteractEvent e) {
    if (e.action() != InteractAction.RIGHT_CLICK_BLOCK || e.hand() != Hand.MAIN_HAND) {
      return;
    }
    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    if (level > 0 && scarecrows.repair(p, level, e.clickedBlock(), e.item())) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    scarecrows.broken(e.block(), () -> e.setDropItems(false));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBurnEvent e) {
    if (scarecrows.guards(e.block())) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityExplodeEvent e) {
    e.blocks().removeIf(scarecrows::guards);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockExplodeEvent e) {
    e.blocks().removeIf(scarecrows::guards);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockPistonEvent e) {
    for (BlockRef block : e.blocks()) {
      if (scarecrows.guards(block)) {
        e.setCancelled(true);
        return;
      }
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityChangeBlockEvent e) {
    if (scarecrows.keepsFarmland(e.entity(), e.block())) {
      e.setCancelled(true);
    }
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    scarecrows.forget(e.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    if (e.action() != InteractAction.RIGHT_CLICK_BLOCK || e.hand() != Hand.MAIN_HAND
        || e.useBlock() == UseResult.DENY) {
      return;
    }

    PlayerRef p = e.player();
    BlockRef target = e.clickedBlock();
    int level = getActiveLevel(p);
    if (level <= 0 || target == null || !HerbalismHarvest.isMature(target) || !canBlockBreak(p, target.location())) {
      return;
    }

    HerbalismHarvest.Hoe hoe = HerbalismHarvest.readyHoe(this, p);
    double radius = getFieldRadius(level);
    if (hoe == null
        || (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().ownsArea(target.location(), radius, radius))) {
      return;
    }

    double bonusChance = getBonusChance(level);
    List<SweepHook> hooks = sweepHooks();
    boolean taking = false;
    for (SweepHook hook : hooks) {
      taking |= hook.takesProduce(p);
    }
    boolean targetFarmed = Mutations.farmedHarvest(target);
    List<ItemRef> produce = new ArrayList<>();
    Consumer<List<ItemRef>> sink = taking ? produce::addAll : null;
    List<BlockRef> harvested = new ArrayList<>();
    HerbalismField.scan(target.world(), target.location(), radius, getConfig().fieldBlockBudget, (BlockRef crop) -> {
      boolean farmed = Mutations.farmedHarvest(crop);
      if (HerbalismHarvest.isMature(crop) && harvest.harvest(p, crop, hoe.tool(), sink)) {
        harvested.add(crop);
        if (!farmed) {
          luck.roll(p, crop, bonusChance, HerbalismLuck.LUCK_FOOD);
        }
      }
    });
    if (harvested.isEmpty()) {
      return;
    }
    List<ItemRef> left = produce;
    for (SweepHook hook : hooks) {
      left = hook.swept(p, target, targetFarmed, left);
    }
    harvest.release(p, target, left);

    if (hoe.offHand()) {
      damageOffHand(p, harvested.size());
    } else {
      damageHand(p, harvested.size());
    }
    p.setCooldown(hoe.material(), getConfig().cooldownTicks);
    HudStatus.cooldown(p, this, getConfig().cooldownTicks * 50L);
    xpOnHarvests(p, harvested, getConfig().xpPerCrop);
    cue(p, MutationCue.WORK);
    fx(target.center(), FxPriority.TRANSITION)
        .dustRing(HARVEST, radius, Math.min(40, (int) Math.round(radius * 6)), 1.1F)
        .ring(Particles.COMPOSTER, radius * 0.5D, 16, 0.3D)
        .sound(Sounds.ITEM_SHOVEL_FLATTEN, 0.8F, 0.8F);
  }

  /** The signatures of the pairs built so far that take part in sweeps; few, so a sweep finds them each time. */
  private List<SweepHook> sweepHooks() {
    List<SweepHook> hooks = new ArrayList<>(1);
    for (Signature<?> signature : AdaptApi.content().signatures()) {
      if (signature instanceof SweepHook hook) {
        hooks.add(hook);
      }
    }
    return hooks;
  }

  /** A signature that takes part in its player's Harvest Moon sweeps. */
  public interface SweepHook {
    /** Whether it takes the produce of this player's sweep; asked before every sweep, so it stays cheap. */
    boolean takesProduce(PlayerRef p);

    /**
     * A sweep around {@code target} harvested at least one crop; {@code farmed} when the clicked crop was. {@code produce}
     * holds what the sweep yielded, without the seeds kept for replanting, when a hook takes produce, else nothing.
     * Returns what it leaves, which reaches the player as the sweep's produce would.
     */
    List<ItemRef> swept(PlayerRef p, BlockRef target, boolean farmed, List<ItemRef> produce);
  }

  double getFieldRadius(int level) {
    return getConfig().fieldRadiusBase + (level * getConfig().fieldRadiusPerLevel);
  }

  HerbalismScarecrows.Settings scarecrowSettings(int level) {
    return new HerbalismScarecrows.Settings(getScarecrows(level), getScarecrowHalf(level),
        getConfig().scarecrowWearLimit, getConfig().scarecrowHarvestsPerPass, getConfig().scarecrowRepairPerHay,
        getConfig().scarecrowRange);
  }

  int getScarecrows(int level) {
    return Math.max(1, getConfig().scarecrowsPerLevel * level);
  }

  int getScarecrowHalf(int level) {
    return Math.max(1, getConfig().scarecrowHalfBase + (level * getConfig().scarecrowHalfPerLevel));
  }

  double getBonusChance(int level) {
    return Math.min(100D, getConfig().bonusChanceBase + (level * getConfig().bonusChancePerLevel));
  }

  @ConfigDescription("Right-click a mature crop with a hoe to harvest and replant the whole field, with a chance of extra produce.")
  public static class Config extends SkillUpgrade.ToolConfig {
    @ConfigDoc(value = "Field radius before level scaling, in blocks.", impact = "Higher values sweep larger fields.")
    double fieldRadiusBase = 4;
    @ConfigDoc(value = "Field radius added per level, in blocks.", impact = "Higher values widen the sweep at higher levels.")
    double fieldRadiusPerLevel = 2;
    @ConfigDoc(value = "Most blocks one sweep inspects.", impact = "Lower values reduce work on huge fields.")
    int fieldBlockBudget = 8192;
    @ConfigDoc(value = "Bonus drop chance per crop before level scaling, in percent.", impact = "Higher values make extra produce more common.")
    double bonusChanceBase = 5;
    @ConfigDoc(value = "Bonus drop chance per crop added per level, in percent.", impact = "Higher values reward higher levels more.")
    double bonusChancePerLevel = 5;
    @ConfigDoc(value = "Hoe cooldown after a sweep, in ticks.", impact = "Lower values allow sweeping more often.")
    int cooldownTicks = 60;
    @ConfigDoc(value = "Skill XP for each crop the sweep harvests.", impact = "Higher values reward large fields.")
    double xpPerCrop = 1;
    @ConfigDoc(value = "Scarecrows a player may stand per level.", impact = "Higher values let one player tend more fields.")
    int scarecrowsPerLevel = 1;
    @ConfigDoc(value = "Distance from a scarecrow to the edge of its square before level scaling, in blocks.", impact = "Higher values give every scarecrow a larger square.")
    int scarecrowHalfBase = 2;
    @ConfigDoc(value = "Distance added to a scarecrow's square per level, in blocks.", impact = "Higher values widen the square at higher levels.")
    int scarecrowHalfPerLevel = 2;
    @ConfigDoc(value = "Harvests a scarecrow makes before it wears out.", impact = "Lower values make scarecrows need hay sooner.")
    int scarecrowWearLimit = 2000;
    @ConfigDoc(value = "Harvests one hay bale gives back to a worn scarecrow.", impact = "Higher values make repairs cheaper.")
    int scarecrowRepairPerHay = 250;
    @ConfigDoc(value = "Most crops one scarecrow harvests per pass, every two seconds.", impact = "Higher values clear ripe fields faster.")
    int scarecrowHarvestsPerPass = 8;
    @ConfigDoc(value = "How near the owner must be for a scarecrow to work, in blocks.", impact = "Higher values keep distant fields tended.")
    double scarecrowRange = 64;
  }
}
