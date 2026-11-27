package art.arcane.adapt.content.adaptation.pickaxe.upgraded;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeHardening;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxePact;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeRepair;
import art.arcane.adapt.api.world.BonusBreaks;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.concurrent.ThreadLocalRandom;

import static art.arcane.adapt.api.world.BlockFlag.VEIN_MINED;

/**
 * Unbreakable Pact, Repair Rhythm and Stone Skin fused: the pickaxe stops at its last durability point, mined blocks
 * repair it, and mining builds resistance. Only the swung block counts: placed blocks and the extra blocks a vein or a
 * bore breaks neither repair the pickaxe nor build resistance.
 */
public class EternalPick extends SkillUpgrade<EternalPick.Config> {
  public static final Id ID = Id.adapt("pickaxe/upgrade/eternal_pick");

  private final PickaxeRepair repair = new PickaxeRepair(playerState(),
      (PlayerRef p, int restored) -> addStat(p, "pickaxe.eternal-pick.durability-restored", restored));
  private final PickaxeHardening hardening = new PickaxeHardening(this, playerState());

  public EternalPick() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_pickaxe"));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getRepairChance(level), 0), 1);
    statLore(v, getConfig().restorePerRepair, 2);
    statLore(v, getTierCap(level), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef player = e.player();
    ItemRef hand = player.inventory().mainHand();
    BlockRef block = e.block();
    if (!ItemKinds.isPickaxe(hand) || !block.isPreferredTool(hand) || BonusBreaks.isActive(player)
        || VEIN_MINED.get(block) || Mutations.farmed(block)) {
      return;
    }

    BlockActionContext context = resolveBlockBreakContext(player, block.location());
    if (context == null) {
      return;
    }

    int level = context.level();
    if (hand.damage() > 0 && ThreadLocalRandom.current().nextDouble() < getRepairChance(level)) {
      repair.queue(player, hand, Math.max(1, getConfig().restorePerRepair));
    }

    PickaxeHardening.Step step = hardening.harden(player, getTierCap(level), getConfig().blocksPerStack,
        getConfig().stackDurationMs, getConfig().effectDurationTicks);
    if (step.reachedCap()) {
      cue(player, MutationCue.BOON);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerItemDamageEvent e) {
    repair.onItemDamage(e);
    PlayerRef player = e.player();
    if (e.isCancelled() || !ItemKinds.isPickaxe(e.item()) || getActiveLevel(player) <= 0) {
      return;
    }

    if (PickaxePact.spare(e)) {
      addStat(player, "pickaxe.eternal-pick.saves", 1);
      cue(player, MutationCue.WORK);
    }
  }

  double getRepairChance(int level) {
    return Math.min(1D, getConfig().repairChanceBase + (level * getConfig().repairChancePerLevel));
  }

  int getTierCap(int level) {
    return Math.max(1, Math.min(level, getConfig().maxAmplifier + 1));
  }

  @ConfigDescription("The pickaxe never breaks, mining repairs it, and mining builds resistance.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Chance per mined block to repair the pickaxe, before level scaling.", impact = "Higher values repair more often.")
    double repairChanceBase = 0.25;
    @ConfigDoc(value = "Repair chance added per level.", impact = "Higher values reward higher levels more.")
    double repairChancePerLevel = 0.15;
    @ConfigDoc(value = "Durability restored by each repair.", impact = "Higher values restore more per repair.")
    int restorePerRepair = 2;
    @ConfigDoc(value = "Blocks mined for each resistance tier.", impact = "Higher values make tiers slower to build.")
    int blocksPerStack = 4;
    @ConfigDoc(value = "How long built stacks last without mining, in milliseconds.", impact = "Higher values keep stacks between breaks.")
    long stackDurationMs = 6000;
    @ConfigDoc(value = "Duration of the resistance after each block, in ticks.", impact = "Higher values keep resistance longer after mining stops.")
    int effectDurationTicks = 80;
    @ConfigDoc(value = "Highest resistance amplifier the stacks reach.", impact = "Higher values allow stronger resistance at the top level.")
    int maxAmplifier = 2;
  }
}
