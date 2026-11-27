package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.content.item.ChronoTimeBottle;
import art.arcane.adapt.api.world.BuildBatch;
import art.arcane.adapt.api.world.BuildPayment;
import art.arcane.adapt.api.world.BuildBatches;
import art.arcane.adapt.api.world.BuildResult;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Time Lord's restoration. Blocks that explosions, fire or endermen destroy near a holder are logged for a while; a
 * holder brings the logged blocks around a spot back through build batches, one layer per tick from the bottom up,
 * paid in stored bottle time. Player breaks are never logged. A block whose plain drop spawned in its own cell right
 * after an explosion comes back only while that drop still lies on the ground, and the drop is taken; a block an
 * enderman carried off needs its own item from the ground; burned blocks dropped nothing. A protected block or one
 * whose drop is gone is skipped and the rest continue. Containers, block entities, two-block states, TNT and respawn
 * anchors are never logged.
 */
public final class ChronosRestoration {
  private static final long DROP_WINDOW_MILLIS = 1_000L;
  private static final double GROUND_REACH = 4D;
  private static final Id ENDERMAN = Id.minecraft("enderman");
  private static final Set<String> NEVER = Set.of("tnt", "respawn_anchor", "piston_head", "moving_piston", "fire",
      "soul_fire");

  /** Level and server tuning, read live from the owner's config. */
  public interface Tuning {
    long windowMillis(int level);

    double radius(int level);

    long longestWindowMillis();

    double recordRadius();

    int maxLoggedBlocks();

    double chargeSeconds();

    int blocksPerCharge();
  }

  /** Why a restoration did nothing or left blocks out. */
  public enum Shortfall { NONE, NOTHING, NO_TIME, DROPS_MISSING }

  /** How a restoration ended. */
  public record Outcome(int restored, Shortfall shortfall, BuildResult.Stop stop) {
  }

  private record Key(UUID world, int x, int y, int z) {
  }

  private record Holder(UUID world, double x, double y, double z) {
  }

  /** A destroyed block; {@code drops} holds what it dropped that must be taken back from the ground. */
  private record Entry(Key key, BlockStateRef state, long at, Set<Id> dropTypes, Map<Id, Integer> drops) {
  }

  private final Adaptation<?> owner;
  private final Tuning tuning;
  private final Map<Key, Entry> log = new ConcurrentHashMap<>();
  private final Set<UUID> restoring = ConcurrentHashMap.newKeySet();
  private volatile List<Holder> holders = List.of();

  public ChronosRestoration(Adaptation<?> owner, Tuning tuning) {
    this.owner = owner;
    this.tuning = tuning;
  }

  /** Re-reads where the online holders stand and drops entries older than the longest window. */
  public void tick(long now) {
    List<Holder> found = new ArrayList<>();
    for (PlayerRef player : AdaptApi.platform().host().onlinePlayers()) {
      if (owner.getActiveLevel(player) > 0) {
        Loc at = player.location();
        found.add(new Holder(player.world().uid(), at.x(), at.y(), at.z()));
      }
    }
    holders = List.copyOf(found);
    long oldest = now - tuning.longestWindowMillis();
    log.values().removeIf((Entry entry) -> entry.at() < oldest);
  }

  /** True while the player's restoration still has layers to place. */
  public boolean isRestoring(PlayerRef player) {
    return restoring.contains(player.uuid());
  }

  /** Logs the blocks an explosion is about to destroy; {@code source} is the block that went off, if any. */
  public void exploded(List<BlockRef> blocks, @Nullable BlockRef source, long now) {
    for (BlockRef block : blocks) {
      if ((source == null || !block.pos().equals(source.pos())) && watched(block) && loggable(block)) {
        Set<Id> types = new HashSet<>();
        types.add(block.state().placementItem());
        for (ItemRef drop : block.drops(null, null)) {
          if (drop != null && !drop.isEmpty()) {
            types.add(drop.type());
          }
        }
        record(block, Set.copyOf(types), now);
      }
    }
  }

  /** Logs a block fire is about to destroy; fire drops nothing. */
  public void burned(BlockRef block, long now) {
    if (watched(block) && loggable(block)) {
      record(block, Set.of(), now);
    }
  }

  /** Logs a block an enderman picked up when {@code entity} is one; its own item must be on the ground to restore it. */
  public void changed(EntityRef entity, BlockRef block, BlockStateRef to, long now) {
    if (!ENDERMAN.equals(entity.type()) || !to.isAir() || !watched(block) || !loggable(block)) {
      return;
    }
    Entry entry = record(block, Set.of(), now);
    if (entry != null) {
      entry.drops().put(block.state().placementItem(), 1);
    }
  }

  /** Counts a plain item that spawns in a freshly exploded block's cell as that block's drop when the block drops it. */
  public void itemSpawned(ItemEntityRef item, Loc at, long now) {
    Entry entry = log.get(new Key(at.world().uid(), (int) Math.floor(at.x()), (int) Math.floor(at.y()),
        (int) Math.floor(at.z())));
    if (entry == null || now - entry.at() > DROP_WINDOW_MILLIS) {
      return;
    }
    ItemRef stack = item.item();
    if (stack != null && !stack.isEmpty() && entry.dropTypes().contains(stack.type()) && plain(stack)) {
      entry.drops().merge(stack.type(), stack.amount(), Integer::sum);
    }
  }

  /** Restores the logged blocks around {@code center} for {@code player}, layer by layer; {@code done} gets the outcome. */
  public void restore(PlayerRef player, BlockRef center, int level, long now, Consumer<Outcome> done) {
    WorldRef world = center.world();
    double radius = tuning.radius(level);
    long since = now - tuning.windowMillis(level);
    List<Entry> candidates = new ArrayList<>();
    for (Entry entry : log.values()) {
      Key key = entry.key();
      if (!key.world().equals(world.uid()) || entry.at() < since || distanceSquared(key, center) > radius * radius) {
        continue;
      }
      BlockRef block = world.blockAt(key.x(), key.y(), key.z());
      if (block.isAir() || block.isReplaceable()) {
        candidates.add(entry);
      }
    }
    if (candidates.isEmpty()) {
      done.accept(new Outcome(0, Shortfall.NOTHING, BuildResult.Stop.DONE));
      return;
    }

    Map<Id, Integer> ground = groundItems(center, radius);
    boolean missing = false;
    candidates.sort(Comparator.comparingInt((Entry entry) -> entry.key().y())
        .thenComparingDouble((Entry entry) -> distanceSquared(entry.key(), center)));
    List<List<Entry>> layers = new ArrayList<>();
    for (Entry entry : candidates) {
      if (!reserve(ground, entry.drops())) {
        missing = true;
        continue;
      }
      List<Entry> layer = layers.isEmpty() ? null : layers.getLast();
      if (layer == null || layer.getFirst().key().y() != entry.key().y()) {
        layer = new ArrayList<>();
        layers.add(layer);
      }
      layer.add(entry);
    }
    if (layers.isEmpty()) {
      done.accept(new Outcome(0, Shortfall.DROPS_MISSING, BuildResult.Stop.DONE));
      return;
    }

    int blocks = (int) Math.min(Integer.MAX_VALUE,
        (long) Math.floor(storedSeconds(player.inventory()) / tuning.chargeSeconds()) * tuning.blocksPerCharge());
    if (blocks <= 0) {
      done.accept(new Outcome(0, Shortfall.NO_TIME, BuildResult.Stop.NO_ITEMS));
      return;
    }
    if (!restoring.add(player.uuid())) {
      return;
    }
    new Run(player, center, radius, blocks, layers, missing, done).layer(layers.getFirst(), 0);
  }

  /**
   * One restoration: a build batch per layer, each started a tick after the last ends. Pays each block from the bill:
   * its own drops from the ground, and a bottle charge per batch of blocks.
   */
  private final class Run implements BuildPayment {
    private final Map<ItemRef, Entry> byUnit = new IdentityHashMap<>();
    private final Map<ItemRef, List<ItemRef>> takenDrops = new IdentityHashMap<>();
    private final PlayerRef player;
    private final BlockRef center;
    private final double radius;
    private final int blocks;
    private final List<List<Entry>> layers;
    private final Consumer<Outcome> done;
    private boolean missing;
    private boolean noTime;
    private boolean refused;
    private int credit;
    private int restored;

    private Run(PlayerRef player, BlockRef center, double radius, int blocks, List<List<Entry>> layers,
                boolean missing, Consumer<Outcome> done) {
      this.player = player;
      this.center = center;
      this.radius = radius;
      this.blocks = blocks;
      this.layers = layers;
      this.missing = missing;
      this.done = done;
    }

    private void layer(List<Entry> entries, int index) {
      WorldRef world = center.world();
      BuildBatch.Builder builder = BuildBatch.of(owner, player)
          .limit(blocks - restored)
          .pace(entries.size())
          .payFrom(this);
      List<Entry> planned = new ArrayList<>();
      for (Entry entry : entries) {
        Id item = entry.state().placementItem();
        if (item != null) {
          builder.place(world.blockAt(entry.key().x(), entry.key().y(), entry.key().z()), entry.state(),
              AdaptApi.platform().host().items().create(item, 1));
          planned.add(entry);
        }
      }
      builder.onDone((BuildResult result) -> landed(planned, index, result));
      BuildBatch batch = builder.build();
      List<BuildBatch.Step> steps = batch.steps();
      for (int step = 0; step < steps.size(); step++) {
        if (steps.get(step) instanceof BuildBatch.Place place) {
          byUnit.put(place.unit(), planned.get(step));
        }
      }
      if (!BuildBatches.start(batch)) {
        finish(BuildResult.Stop.CANCELLED);
      }
    }

    private void landed(List<Entry> planned, int index, BuildResult result) {
      restored += result.placed();
      BuildResult.Stop stop = result.stop();
      if (stop == BuildResult.Stop.DONE) {
        next(index + 1, null);
        return;
      }
      if (stop != BuildResult.Stop.PROTECTED && (stop != BuildResult.Stop.NO_ITEMS || noTime)) {
        finish(stop);
        return;
      }
      refused |= stop == BuildResult.Stop.PROTECTED;
      int skipped = result.placed() + result.skipped() + 1;
      if (skipped >= planned.size()) {
        next(index + 1, null);
      } else {
        next(index, List.copyOf(planned.subList(skipped, planned.size())));
      }
    }

    /** Places {@code rest} of layer {@code index} next tick, or that whole layer when {@code rest} is null. */
    private void next(int index, @Nullable List<Entry> rest) {
      List<Entry> entries = rest != null ? rest : index < layers.size() ? layers.get(index) : null;
      if (entries == null) {
        finish(refused ? BuildResult.Stop.PROTECTED : BuildResult.Stop.DONE);
        return;
      }
      if (restored >= blocks) {
        finish(BuildResult.Stop.LIMIT);
        return;
      }
      if (!AdaptApi.scheduler().runEntity(player, () -> layer(entries, index), 1, () -> finish(BuildResult.Stop.CANCELLED))) {
        finish(BuildResult.Stop.CANCELLED);
      }
    }

    private void finish(BuildResult.Stop stop) {
      if (!restoring.remove(player.uuid())) {
        return;
      }
      Shortfall shortfall = noTime ? Shortfall.NO_TIME : missing ? Shortfall.DROPS_MISSING : Shortfall.NONE;
      done.accept(new Outcome(restored, shortfall, stop));
    }

    @Override
    public @Nullable ItemRef take(ItemRef unit) {
      Entry entry = byUnit.get(unit);
      if (entry == null || log.get(entry.key()) != entry) {
        return null;
      }
      List<ItemRef> drops = takeGround(center, radius, entry.drops());
      if (drops == null) {
        missing = true;
        return null;
      }
      if (credit <= 0) {
        if (!spendCharge(player.inventory(), tuning.chargeSeconds())) {
          dropBack(center, drops);
          noTime = true;
          return null;
        }
        credit += Math.max(1, tuning.blocksPerCharge());
      }
      credit--;
      takenDrops.put(unit, drops);
      log.remove(entry.key(), entry);
      return unit;
    }

    @Override
    public void giveBack(ItemRef taken) {
      credit++;
      Entry entry = byUnit.get(taken);
      if (entry != null) {
        log.putIfAbsent(entry.key(), entry);
      }
      List<ItemRef> drops = takenDrops.remove(taken);
      if (drops != null) {
        dropBack(center, drops);
      }
    }
  }

  private boolean watched(BlockRef block) {
    List<Holder> current = holders;
    if (current.isEmpty() || log.size() >= tuning.maxLoggedBlocks()) {
      return false;
    }
    UUID world = block.world().uid();
    double reach = tuning.recordRadius() * tuning.recordRadius();
    for (Holder holder : current) {
      double dx = holder.x() - block.x();
      double dy = holder.y() - block.y();
      double dz = holder.z() - block.z();
      if (holder.world().equals(world) && (dx * dx) + (dy * dy) + (dz * dz) <= reach) {
        return true;
      }
    }
    return false;
  }

  /** A plain single block with an item: no inventory, no block entity, not half of a door, bed or tall plant. */
  public static boolean loggable(BlockRef block) {
    BlockStateRef state = block.state();
    if (state.isAir() || block.isLiquid() || state.placementItem() == null || NEVER.contains(state.type().path())) {
      return false;
    }
    if (state.property("part") != null) {
      return false;
    }
    String half = state.property("half");
    if ("upper".equals(half) || "lower".equals(half)) {
      return false;
    }
    return block.container() == null && block.blockInventory() == null && block.blockEntityData() == null;
  }

  private @Nullable Entry record(BlockRef block, Set<Id> dropTypes, long now) {
    if (log.size() >= tuning.maxLoggedBlocks()) {
      return null;
    }
    Key key = new Key(block.world().uid(), block.x(), block.y(), block.z());
    Entry entry = new Entry(key, block.state(), now, dropTypes, new ConcurrentHashMap<>());
    log.put(key, entry);
    return entry;
  }

  private static double distanceSquared(Key key, BlockRef center) {
    double dx = key.x() - center.x();
    double dy = key.y() - center.y();
    double dz = key.z() - center.z();
    return (dx * dx) + (dy * dy) + (dz * dz);
  }

  /** A stack with nothing of its own: no name, lore, enchantments or custom data. */
  static boolean plain(ItemRef stack) {
    return !Mutations.isTool(stack) && stack.data().isEmpty()
        && stack.isSimilar(AdaptApi.platform().host().items().create(stack.type(), Math.max(1, stack.amount())));
  }

  private static boolean reserve(Map<Id, Integer> ground, Map<Id, Integer> needed) {
    for (Map.Entry<Id, Integer> need : needed.entrySet()) {
      if (ground.getOrDefault(need.getKey(), 0) < need.getValue()) {
        return false;
      }
    }
    for (Map.Entry<Id, Integer> need : needed.entrySet()) {
      ground.merge(need.getKey(), -need.getValue(), Integer::sum);
    }
    return true;
  }

  private static List<ItemEntityRef> groundEntities(BlockRef center, double radius) {
    double reach = radius + GROUND_REACH;
    List<ItemEntityRef> found = new ArrayList<>();
    for (EntityRef entity : center.world().nearbyEntities(center.center().position(), reach, reach, reach)) {
      if (entity instanceof ItemEntityRef item && item.isValid()) {
        ItemRef stack = item.item();
        if (stack != null && !stack.isEmpty() && plain(stack)) {
          found.add(item);
        }
      }
    }
    return found;
  }

  private static Map<Id, Integer> groundItems(BlockRef center, double radius) {
    Map<Id, Integer> counts = new HashMap<>();
    for (ItemEntityRef item : groundEntities(center, radius)) {
      ItemRef stack = item.item();
      counts.merge(stack.type(), stack.amount(), Integer::sum);
    }
    return counts;
  }

  /** Takes every needed plain item from the ground around {@code center} and returns the stacks taken, or null when any is short. */
  static @Nullable List<ItemRef> takeGround(BlockRef center, double radius, Map<Id, Integer> needed) {
    List<ItemRef> taken = new ArrayList<>();
    if (needed.isEmpty()) {
      return taken;
    }
    Map<Id, Integer> wanted = new HashMap<>(needed);
    if (!reserve(groundItems(center, radius), new HashMap<>(wanted))) {
      return null;
    }
    for (ItemEntityRef item : groundEntities(center, radius)) {
      ItemRef stack = item.item();
      int want = wanted.getOrDefault(stack.type(), 0);
      if (want <= 0) {
        continue;
      }
      int amount = Math.min(want, stack.amount());
      wanted.put(stack.type(), want - amount);
      taken.add(stack.copyWithAmount(amount));
      if (amount >= stack.amount()) {
        item.remove();
      } else {
        item.setItem(stack.copyWithAmount(stack.amount() - amount));
      }
    }
    return taken;
  }

  private static void dropBack(BlockRef center, List<ItemRef> stacks) {
    for (ItemRef stack : stacks) {
      center.world().dropItemNaturally(center.center().position(), stack);
    }
  }

  /** Seconds stored in all chrono bottles the player carries. */
  public static double storedSeconds(PlayerInventoryRef inventory) {
    double total = 0D;
    for (int slot = 0; slot < inventory.size(); slot++) {
      ItemRef item = inventory.item(slot);
      if (ChronoTimeBottle.isBindableItem(item)) {
        total += ChronoTimeBottle.getStoredSeconds(item);
      }
    }
    return total;
  }

  /** Takes {@code seconds} from the carried bottles, across several when needed, or nothing when they hold less. */
  public static boolean spendCharge(PlayerInventoryRef inventory, double seconds) {
    if (storedSeconds(inventory) < seconds) {
      return false;
    }
    double left = seconds;
    for (int slot = 0; slot < inventory.size() && left > 0D; slot++) {
      ItemRef item = inventory.item(slot);
      if (!ChronoTimeBottle.isBindableItem(item)) {
        continue;
      }
      double stored = ChronoTimeBottle.getStoredSeconds(item);
      double spent = Math.min(stored, left);
      if (spent <= 0D) {
        continue;
      }
      ChronoTimeBottle.setStoredSeconds(item, stored - spent);
      inventory.setItem(slot, item);
      left -= spent;
    }
    return true;
  }
}
