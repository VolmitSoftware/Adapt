package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.event.block.BlockBurnEvent;
import art.arcane.adapt.platform.event.block.BlockCookEvent;
import art.arcane.adapt.platform.event.block.BlockDispenseEvent;
import art.arcane.adapt.platform.event.block.BlockFadeEvent;
import art.arcane.adapt.platform.event.block.BlockFertilizeEvent;
import art.arcane.adapt.platform.event.block.BlockFormEvent;
import art.arcane.adapt.platform.event.block.BlockFromToEvent;
import art.arcane.adapt.platform.event.block.BlockGrowEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.block.BlockReceiveGameEvent;
import art.arcane.adapt.platform.event.block.CrafterCraftEvent;
import art.arcane.adapt.platform.event.block.FurnaceSmeltEvent;
import art.arcane.adapt.platform.event.block.StructureGrowEvent;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.event.player.PlayerHarvestBlockEvent;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.block;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.entity;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.face;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.player;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.stack;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.state;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.vec;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

final class BlockGapTranslations {
  private BlockGapTranslations() {
  }

  static List<Translation<?>> all() {
    return List.of(PISTON_EXTEND, PISTON_RETRACT, CHANGE_BLOCK, FADE, GROW, FORM, BURN, FROM_TO, FERTILIZE, STRUCTURE_GROW,
        RECEIVE_GAME_EVENT, DISPENSE, COOK, CRAFTER_CRAFT, BREW, HARVEST);
  }

  static final Translation<org.bukkit.event.block.BlockPistonExtendEvent> PISTON_EXTEND = BukkitTranslations.simple(
      org.bukkit.event.block.BlockPistonExtendEvent.class, BlockPistonEvent.class,
      (event, poster) -> piston(event, event.getBlocks(), true, poster));

  static final Translation<org.bukkit.event.block.BlockPistonRetractEvent> PISTON_RETRACT = BukkitTranslations.simple(
      org.bukkit.event.block.BlockPistonRetractEvent.class, BlockPistonEvent.class,
      (event, poster) -> piston(event, event.getBlocks(), false, poster));

  private static void piston(org.bukkit.event.block.BlockPistonEvent event, List<Block> moved, boolean extending,
                             Translation.Poster poster) {
    boolean cancelled = event.isCancelled();
    List<BlockRef> blocks = new ArrayList<>(moved.size());
    for (Block block : moved) {
      blocks.add(BukkitRefs.block(block));
    }
    BlockPistonEvent core = new BlockPistonEvent(block(event.getBlock()), face(event.getDirection()), extending, blocks);
    core.setCancelled(cancelled);
    poster.post(core);
    cancel(event, cancelled, core);
  }

  static final Translation<org.bukkit.event.entity.EntityChangeBlockEvent> CHANGE_BLOCK = BukkitTranslations.simple(
      org.bukkit.event.entity.EntityChangeBlockEvent.class, EntityChangeBlockEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        EntityChangeBlockEvent core = new EntityChangeBlockEvent(entity(event.getEntity()), block(event.getBlock()),
            BukkitRefs.blockState(event.getBlockData()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.block.BlockFadeEvent> FADE = BukkitTranslations.simple(
      org.bukkit.event.block.BlockFadeEvent.class, BlockFadeEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        BlockFadeEvent core = new BlockFadeEvent(block(event.getBlock()), state(event.getNewState()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.block.BlockGrowEvent> GROW = BukkitTranslations.simple(
      org.bukkit.event.block.BlockGrowEvent.class, BlockGrowEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        BlockGrowEvent core = new BlockGrowEvent(block(event.getBlock()), state(event.getNewState()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.block.BlockFormEvent> FORM = BukkitTranslations.simple(
      org.bukkit.event.block.BlockFormEvent.class, BlockFormEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        BlockFormEvent core = new BlockFormEvent(block(event.getBlock()), state(event.getNewState()),
            event instanceof EntityBlockFormEvent byEntity ? entity(byEntity.getEntity()) : null);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.block.BlockBurnEvent> BURN = BukkitTranslations.simple(
      org.bukkit.event.block.BlockBurnEvent.class, BlockBurnEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        BlockBurnEvent core = new BlockBurnEvent(block(event.getBlock()), block(event.getIgnitingBlock()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.block.BlockFromToEvent> FROM_TO = BukkitTranslations.simple(
      org.bukkit.event.block.BlockFromToEvent.class, BlockFromToEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        BlockFromToEvent core = new BlockFromToEvent(block(event.getBlock()), block(event.getToBlock()), face(event.getFace()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.block.BlockFertilizeEvent> FERTILIZE = BukkitTranslations.simple(
      org.bukkit.event.block.BlockFertilizeEvent.class, BlockFertilizeEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        BlockFertilizeEvent core = new BlockFertilizeEvent(player(event.getPlayer()), block(event.getBlock()),
            GapEventMaps.changes(event.getBlocks()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.world.StructureGrowEvent> STRUCTURE_GROW = BukkitTranslations.simple(
      org.bukkit.event.world.StructureGrowEvent.class, StructureGrowEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        Location origin = event.getLocation();
        StructureGrowEvent core = new StructureGrowEvent(block(origin.getBlock()), player(event.getPlayer()),
            event.isFromBonemeal(), GapEventMaps.changes(event.getBlocks()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.block.BlockReceiveGameEvent> RECEIVE_GAME_EVENT = BukkitTranslations.simple(
      org.bukkit.event.block.BlockReceiveGameEvent.class, BlockReceiveGameEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        BlockReceiveGameEvent core = new BlockReceiveGameEvent(block(event.getBlock()), GapEventMaps.key(event.getEvent()),
            entity(event.getEntity()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.block.BlockDispenseEvent> DISPENSE = BukkitTranslations.simple(
      org.bukkit.event.block.BlockDispenseEvent.class, BlockDispenseEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        BlockDispenseEvent core = new BlockDispenseEvent(block(event.getBlock()), GapEventMaps.itemOrEmpty(event.getItem()),
            vec(event.getVelocity()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.block.BlockCookEvent> COOK = new Translation<>(
      org.bukkit.event.block.BlockCookEvent.class, List.of(BlockCookEvent.class, FurnaceSmeltEvent.class)) {
    @Override
    public Class<? extends BlockCookEvent> target(org.bukkit.event.block.BlockCookEvent event) {
      return event instanceof org.bukkit.event.inventory.FurnaceSmeltEvent ? FurnaceSmeltEvent.class : BlockCookEvent.class;
    }

    @Override
    public void bridge(org.bukkit.event.block.BlockCookEvent event, Poster poster) {
      boolean cancelled = event.isCancelled();
      ItemRef result = GapEventMaps.itemOrEmpty(event.getResult());
      BlockRef block = block(event.getBlock());
      ItemRef source = GapEventMaps.itemOrEmpty(event.getSource());
      BlockCookEvent core = event instanceof org.bukkit.event.inventory.FurnaceSmeltEvent
          ? new FurnaceSmeltEvent(block, source, result)
          : new BlockCookEvent(block, source, result);
      core.setCancelled(cancelled);
      poster.post(core);
      cancel(event, cancelled, core);
      if (core.result() != result) {
        event.setResult(stack(core.result()));
      }
    }
  };

  static final Translation<org.bukkit.event.block.CrafterCraftEvent> CRAFTER_CRAFT = BukkitTranslations.simple(
      org.bukkit.event.block.CrafterCraftEvent.class, CrafterCraftEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        CrafterCraftEvent core = new CrafterCraftEvent(block(event.getBlock()), BukkitRefs.id(event.getRecipe().getKey()),
            GapEventMaps.itemOrEmpty(event.getResult()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.inventory.BrewEvent> BREW = BukkitTranslations.simple(
      org.bukkit.event.inventory.BrewEvent.class, BrewEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        BrewerInventory contents = event.getContents();
        List<ItemRef> potions = new ArrayList<>(3);
        for (int slot = 0; slot < 3; slot++) {
          potions.add(GapEventMaps.itemOrEmpty(contents.getItem(slot)));
        }
        List<ItemStack> nativeResults = event.getResults();
        List<ItemRef> results = GapEventMaps.items(nativeResults);
        int brewingTime = contents.getHolder() instanceof BrewingStand stand ? stand.getBrewingTime() : 0;
        BrewEvent core = new BrewEvent(block(event.getBlock()), GapEventMaps.itemOrEmpty(contents.getIngredient()), potions,
            results, event.getFuelLevel(), brewingTime);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        List<ItemRef> after = core.results();
        for (int index = 0; index < after.size(); index++) {
          ItemRef ref = after.get(index);
          if (index >= results.size() || ref != results.get(index)) {
            ItemStack replacement = stack(ref);
            if (index < nativeResults.size()) {
              nativeResults.set(index, replacement);
            } else {
              nativeResults.add(replacement);
            }
          }
        }
        while (nativeResults.size() > after.size()) {
          nativeResults.removeLast();
        }
      });

  static final Translation<org.bukkit.event.player.PlayerHarvestBlockEvent> HARVEST = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerHarvestBlockEvent.class, PlayerHarvestBlockEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        Hand hand = BukkitEventMaps.hand(event.getHand());
        PlayerHarvestBlockEvent core = new PlayerHarvestBlockEvent(player(event.getPlayer()), block(event.getHarvestedBlock()),
            hand == null ? Hand.MAIN_HAND : hand, GapEventMaps.items(event.getItemsHarvested()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });
}
