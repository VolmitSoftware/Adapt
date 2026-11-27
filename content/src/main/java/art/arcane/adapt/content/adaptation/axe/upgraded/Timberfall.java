package art.arcane.adapt.content.adaptation.axe.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.integration.Integrations;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.BlockFlag;
import art.arcane.adapt.content.adaptation.axe.AxeDropCatch;
import art.arcane.adapt.content.adaptation.axe.AxeFelling;
import art.arcane.adapt.content.adaptation.axe.AxePreferences;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.List;
import java.util.Set;

import static art.arcane.adapt.api.world.BlockFlag.VEIN_MINED;

/**
 * Axe Chop, Wood-Miner and Leaf-Miner fused: breaking one log fells the whole tree up its trunk and through its
 * canopy, leaves included, and the drops of logs and leaves go straight into the inventory.
 */
public class Timberfall extends SkillUpgrade<Timberfall.Config> {
  public static final Id ID = Id.adapt("axes/upgrade/timberfall");

  private static final int CANOPY_SEARCH = 2;
  private static final BlockFlag FELLING = new BlockFlag();

  public Timberfall() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("oak_sapling"));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, AxePreferences.TRIGGER, AxePreferences.WORK, AxePreferences.ITEMS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getMaxLogs(level), 1);
    statLore(v, getMaxLeaves(level), 2);
    statLore(v, getRadius(level), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (VEIN_MINED.get(e.block()) || Integrations.isFellingBreak(e)) {
      return;
    }

    PlayerRef p = e.player();
    BlockRef origin = e.block();
    if (!ItemKinds.isAxe(p.inventory().mainHand()) || !AxeFelling.isWood(origin.type())
        || !preference(p, AxePreferences.TRIGGER).accepts(p.isSneaking())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    VEIN_MINED.add(origin);
    FELLING.add(origin);
    Id logType = origin.type();
    BlockStateRef logData = origin.state();
    double work = preference(p, AxePreferences.WORK).multiplier();
    int maxLogs = Math.max(1, (int) Math.floor(getMaxLogs(level) * work));
    int radius = getRadius(level);
    BlockRef top = AxeFelling.trunkTop(this, origin, getConfig().trunkHeight);
    Set<BlockRef> logs = AxeFelling.scanWood(this, p, origin, radius, maxLogs);
    if (!top.equals(origin) && logs.size() < maxLogs) {
      logs.addAll(AxeFelling.scanWood(this, p, top, radius, maxLogs - logs.size()));
    }
    logs.remove(origin);

    BlockRef canopy = canopyLeaf(top);
    Set<BlockRef> leaves = canopy == null ? Set.of()
        : AxeFelling.floodFoliage(this, p, canopy, getConfig().leafRadius,
            Math.max(1, (int) Math.floor(getMaxLeaves(level) * work)));
    Id leafType = canopy == null ? null : canopy.type();

    if (!AdaptApi.scheduler().runEntity(p, () -> fell(p, origin, logType, logData, logs, leafType, leaves), 1)) {
      VEIN_MINED.remove(origin);
      FELLING.remove(origin);
    }
  }

  /** Whether {@code origin} is the log a Timberfall swing is felling its tree from, while that break is handled. */
  public static boolean fells(BlockRef origin) {
    return FELLING.get(origin);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockDropItemEvent e) {
    PlayerRef p = e.player();
    if (resolveBlockBreakContext(p, e.block().location(), null, true) == null || !ItemKinds.isAxe(p.inventory().mainHand())) {
      return;
    }

    Id broken = e.brokenState().type();
    if (!ItemKinds.isLog(broken) && !ItemKinds.isLeaves(broken)) {
      return;
    }

    AxeDropCatch.Caught caught = AxeDropCatch.transfer(e, p, preference(p, AxePreferences.ITEMS)::accepts);
    if (caught.count() > 0) {
      addStat(p, "axes.timberfall.items-caught", caught.count());
      fx(e.block().location().add(0.5D, 0.5D, 0.5D), FxPriority.TRAIL)
          .line(Particles.ENCHANT, p.location().x(), p.location().y() + 1.0D, p.location().z(), 6);
    }
  }

  private BlockRef canopyLeaf(BlockRef top) {
    for (int y = 1; y >= -CANOPY_SEARCH; y--) {
      for (int x = -CANOPY_SEARCH; x <= CANOPY_SEARCH; x++) {
        for (int z = -CANOPY_SEARCH; z <= CANOPY_SEARCH; z++) {
          BlockRef candidate = top.relative(x, y, z);
          if (AxeFelling.isFoliage(candidate.type())) {
            return candidate;
          }
        }
      }
    }
    return null;
  }

  private void fell(PlayerRef p, BlockRef origin, Id logType, BlockStateRef logData, Set<BlockRef> logs, Id leafType,
                    Set<BlockRef> leaves) {
    FELLING.remove(origin);
    if (!p.isOnline() || getActiveLevel(p) <= 0 || !ItemKinds.isAxe(p.inventory().mainHand())
        || !p.world().uid().equals(origin.world().uid())
        || (AdaptApi.scheduler().isRegionized()
        && (!AdaptApi.scheduler().owns(p) || !AdaptApi.scheduler().owns(origin.location())))
        || origin.type().equals(logType)) {
      VEIN_MINED.remove(origin);
      return;
    }

    List<BlockRef> felledLogs = AxeFelling.breakAll(this, p, origin, logType, logs);
    int felled = felledLogs.size();
    addStat(p, "axes.timberfall.logs-felled", felled);
    xpOnBlocks(p, felledLogs, getConfig().xpPerLog);
    if (leafType != null) {
      List<BlockRef> cleared = AxeFelling.breakAll(this, p, origin, leafType, leaves);
      xpOnBlocks(p, cleared.subList(1, cleared.size()), getConfig().xpPerLeaf);
    }
    cue(p, MutationCue.WORK);
    fx(origin.location().add(0.5D, 0.5D, 0.5D), FxPriority.TRANSITION)
        .column(Particles.blockCrack(logData), Math.min(24, 4 + felled), 3.0D)
        .sound(Sounds.ENTITY_RAVAGER_STUNNED, 0.3F, 0.6F);
  }

  int getMaxLogs(int level) {
    return getConfig().maxLogsBase + (level * getConfig().maxLogsPerLevel);
  }

  int getMaxLeaves(int level) {
    return getConfig().maxLeavesBase + (level * getConfig().maxLeavesPerLevel);
  }

  int getRadius(int level) {
    return getConfig().radiusBase + level;
  }

  @ConfigDescription("Breaking a log fells the whole tree, leaves included, into your inventory.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Most logs one swing fells, before level scaling.", impact = "Higher values fell larger trees in one swing.")
    int maxLogsBase = 48;
    @ConfigDoc(value = "Logs added to the limit per level.", impact = "Higher values reward higher levels more.")
    int maxLogsPerLevel = 32;
    @ConfigDoc(value = "Most leaves one swing clears, before level scaling.", impact = "Higher values clear wider canopies.")
    int maxLeavesBase = 128;
    @ConfigDoc(value = "Leaves added to the limit per level.", impact = "Higher values reward higher levels more.")
    int maxLeavesPerLevel = 64;
    @ConfigDoc(value = "Log search radius around the broken log and the trunk top, before adding the level.", impact = "Higher values reach farther branches.")
    int radiusBase = 4;
    @ConfigDoc(value = "Tallest trunk followed up from the broken log, in blocks.", impact = "Higher values fell taller trees to the top.")
    int trunkHeight = 32;
    @ConfigDoc(value = "Leaf search radius around the canopy, in blocks.", impact = "Higher values clear wider canopies.")
    int leafRadius = 7;
    @ConfigDoc(value = "Skill XP for each log felled.", impact = "Higher values reward felling.")
    double xpPerLog = 2;
    @ConfigDoc(value = "Skill XP for each leaf cleared.", impact = "Higher values reward clearing canopies.")
    double xpPerLeaf = 0.25;
  }
}
