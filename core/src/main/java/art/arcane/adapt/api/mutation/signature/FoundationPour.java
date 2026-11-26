package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.batch.BuildBatch;
import art.arcane.adapt.api.batch.BuildBatches;
import art.arcane.adapt.api.batch.BuildResult;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.MutationTools;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.content.adaptation.architect.ArchitectBuilds;
import art.arcane.adapt.content.adaptation.architect.ArchitectChalkFill;
import art.arcane.adapt.content.adaptation.architect.ArchitectChalkLine;
import art.arcane.adapt.content.adaptation.architect.ArchitectSupplyLine;
import art.arcane.adapt.content.adaptation.architect.ArchitectSupplyPayment;
import art.arcane.adapt.content.adaptation.excavation.ExcavationBlocks;
import art.arcane.adapt.content.item.ChalkWandItem.Point;
import art.arcane.adapt.content.item.ChalkWandItem.WandData;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/**
 * Excavation and Architect, either leading, replacing Convergence: left-click with Grading Stakes while a chalk guide
 * is in your off hand or hotbar and a block in your off hand, and the guide's line or circle becomes a foundation. Soft
 * ground along it is dug with the shovel from your hotbar, down to the level's depth from the guide's height, and every
 * cell is filled with the off-hand block, paid like a chalk fill.
 */
public final class FoundationPour extends Signature<FoundationPour.Config> {
  private static final String STAKES = "tool-excavation-grading-stakes";
  private static final int HOTBAR = 9;

  public FoundationPour(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("cobblestone"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, depth(level), 1);
    statLore(v, blocks(level), 2);
  }

  @Override
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Handle(priority = EventPriority.LOW, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    if (e.protectionProbe() || e.hand() != Hand.MAIN_HAND
        || (e.action() != InteractAction.LEFT_CLICK_AIR && e.action() != InteractAction.LEFT_CLICK_BLOCK)) {
      return;
    }
    PlayerRef player = e.player();
    MutationTool<?> stakes = STAKES.equals(MutationTool.idOf(player.inventory().mainHand())) ? MutationTools.find(STAKES) : null;
    if (stakes == null || stakes.level(player) <= 0 || ArchitectBuilds.placedState(player.inventory().offHand()) == null
        || ArchitectChalkLine.plannedWand(player) == null) {
      return;
    }
    if (fire(player, null, player.location(), false)) {
      e.setCancelled(true);
    }
  }

  @Override
  protected Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    WandData wand = ArchitectChalkLine.plannedWand(player);
    ArchitectChalkLine chalk = ArchitectBuilds.find(ArchitectChalkLine.ID, ArchitectChalkLine.class);
    ItemRef off = player.inventory().offHand();
    BlockStateRef state = ArchitectBuilds.placedState(off);
    if (wand == null || chalk == null || state == null || chalk.guide(player, wand).isEmpty()) {
      AdaptHud.actionBar(player, C.GRAY + AdaptLanguage.text(ArchitectMessages.MASTER_BUILDER_NOTHING));
      return Outcome.MISSED;
    }
    if (BuildBatches.get().isRunning(player)) {
      AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(ArchitectMessages.MASTER_BUILDER_BUSY));
      return Outcome.MISSED;
    }
    PlayerInventoryRef inventory = player.inventory();
    int held = inventory.heldSlot();
    int shovel = shovelSlot(inventory);
    ItemRef unit = off.copyWithAmount(1);
    int depth = depth(use.level());
    int durability = Math.max(0, getConfig().durabilityPerBlock);
    WorldRef world = player.world();
    BuildBatch.Builder batch = BuildBatch.of(this, player)
        .pace(Math.max(1, getConfig().blocksPerTick))
        .payFrom(new ArchitectSupplyPayment(player, ArchitectBuilds.find(ArchitectSupplyLine.ID, ArchitectSupplyLine.class)))
        .onDone((BuildResult result) -> {
          if (shovel >= 0 && inventory.heldSlot() == shovel && shovel != held) {
            inventory.setHeldSlot(held);
          }
          AdaptHud.actionBar(player, ArchitectBuilds.report(result));
        });
    int cells = 0;
    int most = blocks(use.level());
    for (Point point : ArchitectChalkFill.path(wand.tool(), wand.plan(), chalk.arcRadiusLimit())) {
      for (int down = 0; down < depth && cells < most; down++) {
        int y = point.y() - down;
        if (y < world.minHeight() || y >= world.maxHeight() || !world.isChunkLoaded(point.x() >> 4, point.z() >> 4)) {
          continue;
        }
        BlockRef cell = world.blockAt(point.x(), y, point.z());
        if (!Tasks.isOwnedByCurrentRegion(cell.location())) {
          continue;
        }
        if (shovel >= 0 && ExcavationBlocks.isShovelable(cell.type()) && !MutationPayouts.farmed(cell)
            && canBlockBreak(player, cell.location())) {
          batch.breakWith(cell, durability, this::isShovel);
          batch.place(cell, state, unit);
          cells++;
        } else if (cell.isAir() || cell.isReplaceable()) {
          batch.place(cell, state, unit);
          cells++;
        }
      }
    }
    if (cells == 0) {
      AdaptHud.actionBar(player, C.GRAY + AdaptLanguage.text(ArchitectMessages.MASTER_BUILDER_NOTHING));
      return Outcome.MISSED;
    }
    if (shovel >= 0 && shovel != held) {
      inventory.setHeldSlot(shovel);
    }
    if (!BuildBatches.get().start(batch.build())) {
      inventory.setHeldSlot(held);
      return Outcome.MISSED;
    }
    if (shovel < 0) {
      AdaptHud.actionBar(player, C.YELLOW + AdaptLanguage.text(ExcavationMessages.GRADING_STAKES_NO_SHOVEL));
    }
    return Outcome.INSTANT;
  }

  private int shovelSlot(PlayerInventoryRef inventory) {
    for (int slot = 0; slot < HOTBAR; slot++) {
      if (isShovel(inventory.item(slot))) {
        return slot;
      }
    }
    return -1;
  }

  int depth(int level) {
    return Math.max(1, level);
  }

  int blocks(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().blocksAtFirstLevel,
        getConfig().blocksAtTopLevel));
  }

  @ConfigDescription("Grading Stakes dig a chalk guide's line or circle and pour it full of your off-hand block as a foundation.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Most blocks one pour lays at level 1; the depth is the level.", impact = "Higher values pour longer guides at once.")
    int blocksAtFirstLevel = 64;
    @ConfigDoc(value = "Most blocks one pour lays at the top level.", impact = "Higher values pour longer guides at once.")
    int blocksAtTopLevel = 320;
    @ConfigDoc(value = "Extra shovel durability lost per block dug for a pour.", impact = "Higher values wear the shovel out faster.")
    int durabilityPerBlock = 1;
    @ConfigDoc(value = "Blocks dug or poured per tick, before the server-wide cap.", impact = "Higher values finish sooner but cost more per tick.")
    int blocksPerTick = 8;
    @ConfigDoc(value = "Cooldown after a pour starts, in milliseconds.", impact = "Higher values space out pours.")
    long cooldownMillis = 0;
  }
}
