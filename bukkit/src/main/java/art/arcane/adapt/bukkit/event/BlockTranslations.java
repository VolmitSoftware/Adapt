package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.data.PdcDataHolder;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.TileState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.block;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.hand;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.item;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.player;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.state;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

final class BlockTranslations {
  private BlockTranslations() {
  }

  static List<Translation<?>> all() {
    return List.of(BREAK, DAMAGE, DROP_ITEM, PLACE, BLOCK_EXPLODE, ENTITY_EXPLODE);
  }

  static final Translation<org.bukkit.event.block.BlockBreakEvent> BREAK =
      new Translation<>(org.bukkit.event.block.BlockBreakEvent.class, List.of(BlockBreakEvent.class)) {
        @Override
        public void bridge(org.bukkit.event.block.BlockBreakEvent event, Poster poster) {
          boolean cancelled = event.isCancelled();
          boolean dropItems = event.isDropItems();
          int experience = event.getExpToDrop();
          BlockBreakEvent core = new BlockBreakEvent(player(event.getPlayer()), block(event.getBlock()), dropItems, experience);
          core.setCancelled(cancelled);
          poster.post(core);
          cancel(event, cancelled, core);
          if (core.dropItems() != dropItems) {
            event.setDropItems(core.dropItems());
          }
          if (core.experience() != experience) {
            event.setExpToDrop(core.experience());
          }
        }
      };

  static final Translation<org.bukkit.event.block.BlockDamageEvent> DAMAGE =
      new Translation<>(org.bukkit.event.block.BlockDamageEvent.class, List.of(BlockDamageEvent.class)) {
        @Override
        public void bridge(org.bukkit.event.block.BlockDamageEvent event, Poster poster) {
          boolean cancelled = event.isCancelled();
          boolean instaBreak = event.getInstaBreak();
          BlockDamageEvent core = new BlockDamageEvent(player(event.getPlayer()), block(event.getBlock()),
              item(event.getItemInHand()), instaBreak);
          core.setCancelled(cancelled);
          poster.post(core);
          cancel(event, cancelled, core);
          if (core.instaBreak() != instaBreak) {
            event.setInstaBreak(core.instaBreak());
          }
        }
      };

  static final Translation<org.bukkit.event.block.BlockDropItemEvent> DROP_ITEM = BukkitTranslations.simple(
      org.bukkit.event.block.BlockDropItemEvent.class, BlockDropItemEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        List<Item> items = event.getItems();
        List<ItemEntityRef> refs = new ArrayList<>(items.size());
        for (Item item : items) {
          refs.add(BukkitRefs.itemEntity(item));
        }
        BlockDropItemEvent core = new BlockDropItemEvent(player(event.getPlayer()), block(event.getBlock()),
            state(event.getBlockState()), refs, spawnerType(event.getBlockState()),
            blockEntityData(event.getBlockState()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        if (!core.items().equals(refs)) {
          Set<Entity> kept = Collections.newSetFromMap(new IdentityHashMap<>());
          for (ItemEntityRef ref : core.items()) {
            kept.add(BukkitRefs.entity(ref));
          }
          items.removeIf(item -> !kept.contains(item));
          for (Entity entity : kept) {
            if (entity instanceof Item item && !items.contains(item)) {
              items.add(item);
            }
          }
        }
      });

  private static DataHolder blockEntityData(BlockState state) {
    return state instanceof TileState tile ? PdcDataHolder.live(tile.getPersistentDataContainer()) : null;
  }

  private static Id spawnerType(BlockState state) {
    if (!(state instanceof CreatureSpawner spawner)) {
      return null;
    }
    EntityType type = spawner.getSpawnedType();
    return type == null ? null : BukkitRefs.id(type);
  }

  static final Translation<org.bukkit.event.block.BlockPlaceEvent> PLACE = BukkitTranslations.simple(
      org.bukkit.event.block.BlockPlaceEvent.class, BlockPlaceEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        boolean build = event.canBuild();
        BlockPlaceEvent core = new BlockPlaceEvent(player(event.getPlayer()), block(event.getBlockPlaced()),
            state(event.getBlockReplacedState()), block(event.getBlockAgainst()), item(event.getItemInHand()),
            hand(event.getHand()));
        core.setCancelled(cancelled);
        core.setBuild(build);
        poster.post(core);
        cancel(event, cancelled, core);
        if (core.canBuild() != build) {
          event.setBuild(core.canBuild());
        }
      });

  static final Translation<org.bukkit.event.block.BlockExplodeEvent> BLOCK_EXPLODE = BukkitTranslations.simple(
      org.bukkit.event.block.BlockExplodeEvent.class, BlockExplodeEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        float yield = event.getYield();
        List<BlockRef> blocks = blocks(event.blockList());
        BlockExplodeEvent core = new BlockExplodeEvent(block(event.getBlock()), state(event.getExplodedBlockState()), blocks, yield);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        if (core.yield() != yield) {
          event.setYield(core.yield());
        }
        writeBlocks(event.blockList(), blocks, core.blocks());
      });

  static final Translation<org.bukkit.event.entity.EntityExplodeEvent> ENTITY_EXPLODE = BukkitTranslations.simple(
      org.bukkit.event.entity.EntityExplodeEvent.class, EntityExplodeEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        float yield = event.getYield();
        List<BlockRef> blocks = blocks(event.blockList());
        EntityExplodeEvent core = new EntityExplodeEvent(BukkitEventMaps.entity(event.getEntity()),
            BukkitRefs.loc(event.getLocation()), blocks, yield);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        if (core.yield() != yield) {
          event.setYield(core.yield());
        }
        writeBlocks(event.blockList(), blocks, core.blocks());
      });

  private static List<BlockRef> blocks(List<Block> blocks) {
    List<BlockRef> refs = new ArrayList<>(blocks.size());
    for (Block block : blocks) {
      refs.add(BukkitRefs.block(block));
    }
    return refs;
  }

  private static void writeBlocks(List<Block> target, List<BlockRef> before, List<BlockRef> after) {
    if (after.equals(before)) {
      return;
    }
    Set<Block> kept = new HashSet<>();
    List<Block> ordered = new ArrayList<>(after.size());
    for (BlockRef ref : after) {
      Block block = BukkitRefs.block(ref);
      if (kept.add(block)) {
        ordered.add(block);
      }
    }
    target.removeIf(block -> !kept.contains(block));
    for (Block block : ordered) {
      if (!target.contains(block)) {
        target.add(block);
      }
    }
  }
}
