package art.arcane.adapt.content.adaptation.pickaxe.upgraded;

import art.arcane.adapt.api.adaptation.BonusBlockBreaks;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.batch.BuildResult;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.support.PlatformSupport;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeBore;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeDeepCore;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeMiningBoost;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeObsidianRush;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeVein;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PickaxeMessages;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Tunnel Bore, Deep Core and Obsidian Rush fused: mining stone, deepslate or obsidian bores the 3 by 3 face around it,
 * and deepslate and obsidian mine at the speed of stone, where the version has the break speed attribute. Hands out
 * Prospector's Charges, which open a rough sphere of stone around the wall they are pressed into.
 */
public class Earthshaker extends SkillUpgrade<Earthshaker.Config> {
  private static final double STONE_HARDNESS = 1.5D;
  private static final int FACE = 3;

  private final boolean speedSupported =
      PlatformSupport.check(List.of(PlatformNeed.attribute(Attributes.BLOCK_BREAK_SPEED))).usable();
  private final PickaxeMiningBoost boost = new PickaxeMiningBoost(this, "stone-speed", Attributes.BLOCK_BREAK_SPEED,
      playerState());
  private final ProspectorsCharge charge;

  public Earthshaker() {
    super("upgraded-pickaxe-earthshaker");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("crying_obsidian"));
    charge = registerTool(new ProspectorsCharge(this));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, FACE + "x" + FACE, 1);
    statLore(v, Form.pc(getSpeedShare(level), 0), 2);
    statLore(v, getConfig().durabilityPerBonusBlock, 3);
    statLore(v, getChargeRadius(level), 4);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    PlayerRef player = e.player();
    BlockRef origin = e.block();
    if (!bores(player, origin)) {
      return;
    }

    Id originType = origin.type();
    ItemRef tool = player.inventory().mainHand();
    List<BlockRef> targets = PickaxeBore.plane(this, player, origin, FACE, FACE, (Id type) -> boreable(type, tool));
    if (!targets.isEmpty()) {
      Tasks.runEntity(player, () -> bore(player, origin, originType, targets), 1);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockDamageEvent e) {
    PlayerRef player = e.player();
    BlockRef block = e.block();
    Id type = block.type();
    ItemRef tool = player.inventory().mainHand();
    boolean hard = PickaxeDeepCore.DEEPSLATE_BLOCKS.contains(type)
        || (PickaxeObsidianRush.isRushTarget(type) && PickaxeObsidianRush.isRushPickaxe(tool));
    if (!speedSupported || !hard || !isPickaxe(tool) || getConfig().boostDurationTicks <= 0) {
      return;
    }

    BlockActionContext context = resolveInteractContext(player, block.location());
    double bonus = context == null ? 0D : stoneSpeedBonus(block.hardness()) * getSpeedShare(context.level());
    if (bonus > 0D && boost.surge(player, bonus, getConfig().boostDurationTicks)) {
      cue(player, MutationCue.BOON);
      fx(player.eyeLocation(), FxPriority.TRANSITION)
          .particle(Particles.CRIT, 4, 0, 0.3, 0, 0.2, 0.05)
          .particle(Particles.ELECTRIC_SPARK, 3, 0, 0.3, 0, 0.2, 0.03);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(BlockDropItemEvent e) {
    if (!BonusBlockBreaks.isActive(e.player()) || !charge.discards(e.player(), e.block(), e.brokenState().type())) {
      return;
    }
    for (ItemEntityRef item : e.items()) {
      item.remove();
    }
    e.items().clear();
  }

  /** Pays for the natural blocks a charge opened and reports how its batch ended. */
  void chargeDone(PlayerRef player, List<BlockRef> opened, BuildResult result) {
    if (!opened.isEmpty()) {
      xpOnBlocks(player, opened, getConfig().xpPerBlock);
      addStat(player, "pickaxe.earthshaker.blocks-blasted", opened.size());
      cue(player, MutationCue.WORK);
    }
    String message = switch (result.stop()) {
      case TOOL_WORN -> C.RED + AdaptLanguage.text(PickaxeMessages.PROSPECTORS_CHARGE_WORN);
      case PROTECTED, OUT_OF_REACH -> C.RED + AdaptLanguage.text(PickaxeMessages.PROSPECTORS_CHARGE_PROTECTED);
      default -> C.GRAY + AdaptLanguage.text(PickaxeMessages.PROSPECTORS_CHARGE_OPENED,
          trusted("blocks", String.valueOf(opened.size())));
    };
    AdaptHud.actionBar(player, message);
  }

  void fuseFx(BlockRef wall, int fuseTicks) {
    timeline(wall.center())
        .duration(fuseTicks)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          fx.particle(Particles.SMOKE, 2, 0, 0.5D, 0, 0.15D, 0.01D);
          if (tick % 10 == 0) {
            fx.sound(Sounds.BLOCK_STONE_BREAK, 0.4F, (float) (0.8D + progress));
          }
        })
        .start();
  }

  void blastFx(BlockRef center) {
    fx(center.center(), FxPriority.GAMEPLAY)
        .particle(Particles.EXPLOSION, 1, 0, 0, 0, 0, 0)
        .particle(Particles.blockCrack(center.state()), 24, 0, 0, 0, 1.2D, 0.1D)
        .sound(Sounds.ENTITY_GENERIC_EXPLODE, 0.7F, 1.4F);
  }

  /** Whether a charge at {@code level} opens a block of {@code type}, with {@code pickaxe} doing the breaking. */
  boolean blastable(Id type, int level, ItemRef pickaxe) {
    return PickaxeBore.STONE.contains(type)
        || PickaxeDeepCore.DEEPSLATE_BLOCKS.contains(type)
        || PickaxeVein.isOre(type)
        || (level >= getConfig().chargeObsidianLevel && PickaxeObsidianRush.isRushTarget(type)
        && PickaxeObsidianRush.isRushPickaxe(pickaxe));
  }

  private void bore(PlayerRef player, BlockRef origin, Id originType, List<BlockRef> targets) {
    if (!PickaxeBore.ready(this, player, origin, originType)) {
      return;
    }

    ItemRef tool = player.inventory().mainHand();
    fx(origin.center(), FxPriority.GAMEPLAY).ring(Particles.CLOUD, 1.4D, 16, 0.1D);
    List<BlockRef> bored = new ArrayList<>(targets.size());
    int broken = PickaxeBore.breakTargets(this, player, targets, (Id type) -> boreable(type, tool),
        getConfig().durabilityPerBonusBlock, (BlockRef target, int index) -> {
          bored.add(target);
          fx(target.center(), FxPriority.TRAIL).particle(Particles.blockCrack(target.state()), 2, 0, 0, 0, 0.2, 0.0);
        });
    if (broken > 0) {
      xpOnBlocks(player, bored, getConfig().xpPerBlock);
      addStat(player, "pickaxe.earthshaker.blocks-bored", broken);
      cue(player, MutationCue.WORK);
    }
  }

  /** Whether {@code player} breaking {@code origin} sets a bore off: the held pickaxe, the block and the break rules. */
  public boolean bores(PlayerRef player, BlockRef origin) {
    ItemRef tool = player.inventory().mainHand();
    return !BonusBlockBreaks.isActive(player) && isPickaxe(tool) && boreable(origin.type(), tool)
        && resolveBlockBreakContext(player, origin.location()) != null;
  }

  static boolean boreable(Id type, ItemRef tool) {
    return PickaxeBore.STONE.contains(type)
        || PickaxeDeepCore.DEEPSLATE_BLOCKS.contains(type)
        || (PickaxeObsidianRush.isRushTarget(type) && PickaxeObsidianRush.isRushPickaxe(tool));
  }

  static double stoneSpeedBonus(float hardness) {
    return Math.max(0D, (hardness / STONE_HARDNESS) - 1D);
  }

  int getChargeRadius(int level) {
    return Math.max(1, getConfig().chargeRadiusBase + level);
  }

  double getSpeedShare(int level) {
    return Math.min(1D, level * getConfig().speedSharePerLevel);
  }

  @ConfigDescription("Mining stone, deepslate or obsidian bores a 3 by 3 face, the harder blocks mine at stone speed, and Prospector's Charges open stone around them.")
  public static class Config extends SkillUpgrade.ToolConfig {
    @ConfigDoc(value = "Share of the gap to stone speed closed per level on deepslate and obsidian.", impact = "Higher values reach full stone speed at lower levels; capped at the full gap.")
    double speedSharePerLevel = 0.34;
    @ConfigDoc(value = "How long the speed lasts after each swing, in ticks.", impact = "Higher values keep the speed between slow swings.")
    int boostDurationTicks = 60;
    @ConfigDoc(value = "Extra pickaxe durability lost per bored block.", impact = "Higher values wear the pickaxe out faster.")
    int durabilityPerBonusBlock = 1;
    @ConfigDoc(value = "Skill XP for each bored block.", impact = "Higher values reward tunnelling.")
    double xpPerBlock = 0.5;
    @ConfigDoc(value = "Charge radius in blocks before adding the level.", impact = "Higher values open larger spheres at every level.")
    int chargeRadiusBase = 1;
    @ConfigDoc(value = "Ticks between pressing a charge and its blast.", impact = "Higher values give more time to step away.")
    int chargeFuseTicks = 60;
    @ConfigDoc(value = "Blocks a blast breaks per tick.", impact = "Higher values open the sphere faster but cost more block updates per tick.")
    int chargeBlocksPerTick = 12;
    @ConfigDoc(value = "How long ores just outside the sphere glow, in ticks.", impact = "Higher values keep the outlines up longer.")
    int chargeGlowTicks = 200;
    @ConfigDoc(value = "Level from which a charge also opens obsidian, with a diamond or netherite pickaxe.", impact = "Lower values let charges open obsidian sooner.")
    int chargeObsidianLevel = 3;
  }
}
