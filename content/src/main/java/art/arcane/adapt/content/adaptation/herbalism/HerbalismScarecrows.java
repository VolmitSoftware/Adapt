package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.BuiltinData;
import art.arcane.adapt.api.world.BuildBatch;
import art.arcane.adapt.api.world.BuildBatches;
import art.arcane.adapt.api.world.BuildResult;
import art.arcane.adapt.content.data.Json;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;

/**
 * Harvest Moon's scarecrows: hay bales a player placed with the Scarecrow tool, kept in
 * {@code data/scarecrows/<uuid>.json}. While the owner is near, each one harvests the crops of its square that ripened
 * on their own, replants them, puts the produce in a chest beside it and keeps mobs off the farmland. It never grows
 * anything and pays no skill XP. Breaking one gives the tool back with its wear; explosions and fire leave it alone.
 */
public final class HerbalismScarecrows {
  public static final Id HAY = Id.minecraft("hay_block");
  private static final Id FARMLAND = Id.minecraft("farmland");
  private static final DataKey<String> OWNER = DataKey.adapt("scarecrow_owner", DataCodecs.STRING);
  private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
  private static final int COLUMNS_PER_PASS = 96;

  private final Adaptation<?> owner;
  private final HerbalismHarvest harvest;
  private final IntFunction<Settings> settings;
  private final IntFunction<ItemRef> tool;
  private final ObjIntConsumer<PlayerRef> onHarvested;
  private final Map<UUID, Posts> posts = new ConcurrentHashMap<>();

  /** What a scarecrow does at an owner level. {@code half} is the distance from the scarecrow to its square's edge. */
  public record Settings(int count, int half, int wearLimit, int harvestsPerPass, int repairPerHay, double range) {
  }

  /**
   * {@code tool} makes a Scarecrow item that has already done the given number of harvests; {@code onHarvested} hears
   * how many crops one pass harvested for the owner.
   */
  public HerbalismScarecrows(Adaptation<?> owner, PlayerPreference<HerbalismPreferences.Materials> materials,
                             IntFunction<Settings> settings, IntFunction<ItemRef> tool,
                             ObjIntConsumer<PlayerRef> onHarvested) {
    this.owner = owner;
    this.harvest = new HerbalismHarvest(owner, materials, (BlockRef crop) -> false, (PlayerRef p, boolean replanted) -> {
    });
    this.settings = settings;
    this.tool = tool;
    this.onHarvested = onHarvested;
  }

  /** The owner's scarecrows, as stored. */
  public List<Post> of(UUID player) {
    return posts(player).copy();
  }

  /** Places a scarecrow worn by {@code harvests} at {@code at}, paid with the held tool {@code unit}. */
  public void place(PlayerRef p, int level, BlockRef at, ItemRef unit, int harvests) {
    Settings rules = settings.apply(level);
    Posts mine = posts(p.uuid());
    mine.prune();
    if (mine.size() >= rules.count()) {
      HudStatus.show(p, TextFormat.RED + count(mine.size(), rules.count()));
      return;
    }
    if (!at.isAir() && !at.isReplaceable()) {
      return;
    }
    BuildBatch.Builder batch = BuildBatch.of(owner, p).limit(1).pace(1).onDone((BuildResult result) -> {
      if (result.placed() != 1 || !at.type().equals(HAY)) {
        return;
      }
      at.data().set(OWNER, p.uuid().toString());
      mine.add(new Post(at.world().uid(), at.pos().x(), at.pos().y(), at.pos().z(), harvests));
      HudStatus.show(p, TextFormat.GREEN + count(mine.size(), rules.count()));
      Fx.now(owner, at.center(), FxPriority.TRANSITION)
          .particle(Particles.COMPOSTER, 10, 0, 0.6D, 0, 0.4D, 0.02D)
          .sound(Sounds.BLOCK_GRASS_PLACE, 0.8F, 0.8F);
    });
    batch.place(at, AdaptApi.platform().host().items().blockState(HAY), unit);
    BuildBatches.start(batch.build());
  }

  /**
   * One pass for an online owner: every scarecrow of theirs in range walks up to {@link #COLUMNS_PER_PASS} columns of
   * its square, resuming where the last pass stopped, and harvests up to its per-pass share.
   */
  public void tend(PlayerRef p, int level) {
    if (level <= 0 || !p.isOnline()) {
      return;
    }
    Settings rules = settings.apply(level);
    Posts mine = posts(p.uuid());
    WorldRef world = p.world();
    boolean changed = false;
    for (Post post : mine.copy()) {
      BlockRef block = loadedNear(p, post, rules.range());
      if (block == null) {
        continue;
      }
      if (!isScarecrow(block, p.uuid())) {
        mine.remove(post);
        continue;
      }
      if (post.harvests >= rules.wearLimit()) {
        continue;
      }
      InventoryRef chest = chestBeside(p, block);
      if (chest == null || !hasRoom(chest)) {
        continue;
      }
      int budget = Math.min(rules.harvestsPerPass(), rules.wearLimit() - post.harvests);
      int side = (rules.half() * 2) + 1;
      int columns = side * side;
      int start = Math.floorMod(post.cursor, columns);
      int done = 0;
      int walked = 0;
      while (walked < Math.min(COLUMNS_PER_PASS, columns) && done < budget) {
        int column = (start + walked) % columns;
        int x = post.x + (column / side) - rules.half();
        int z = post.z + (column % side) - rules.half();
        for (int y = -1; y <= 1 && done < budget; y++) {
          BlockRef crop = world.blockAt(x, post.y + y, z);
          if (!HerbalismHarvest.isMature(crop) || Mutations.farmedHarvest(crop)
              || !AdaptApi.scheduler().owns(crop.location()) || !hasRoom(chest)) {
            continue;
          }
          if (harvest.harvest(p, crop, null, (List<ItemRef> produce) -> deposit(chest, block, produce))) {
            done++;
          }
        }
        if (done < budget) {
          walked++;
        }
      }
      post.cursor = (start + walked) % columns;
      if (done > 0) {
        post.harvests += done;
        changed = true;
        onHarvested.accept(p, done);
      }
    }
    if (changed) {
      AdaptApi.scheduler().runAsync(mine::save);
    }
  }

  /** Repairs the scarecrow at {@code block} with one hay bale from the hand; true when the click was a repair. */
  public boolean repair(PlayerRef p, int level, BlockRef block, ItemRef hand) {
    if (block == null || hand == null || hand.isEmpty() || !hand.type().equals(HAY) || !ItemKinds.isItem(hand)
        || !isScarecrow(block, p.uuid())) {
      return false;
    }
    Post post = posts(p.uuid()).at(block);
    if (post == null) {
      return false;
    }
    Settings rules = settings.apply(level);
    if (post.harvests <= 0 || !owner.payItemCost(p, "repair", hand.copyWithAmount(1), 1, () -> {
      ItemRef held = p.inventory().mainHand();
      if (held.isEmpty() || !held.isSimilar(hand)) {
        return false;
      }
      held.setAmount(held.amount() - 1);
      p.inventory().setMainHand(held);
      return true;
    })) {
      return true;
    }
    post.harvests = Math.max(0, post.harvests - rules.repairPerHay());
    posts(p.uuid()).save();
    HudStatus.show(p, TextFormat.GREEN + wear(rules.wearLimit() - post.harvests, rules.wearLimit()));
    Fx.now(owner, block.center(), FxPriority.TRANSITION)
        .particle(Particles.VILLAGER_HAPPY, 6, 0, 0.6D, 0, 0.4D, 0.02D)
        .sound(Sounds.BLOCK_GRASS_PLACE, 0.8F, 1.2F);
    return true;
  }

  /** A broken scarecrow drops its tool, with its wear, instead of the hay bale. */
  public void broken(BlockRef block, Runnable suppressDrops) {
    if (!block.type().equals(HAY)) {
      return;
    }
    UUID ownerId = ownerAt(block);
    if (ownerId == null) {
      return;
    }
    Posts theirs = stored(ownerId);
    Post post = theirs.at(block);
    block.data().remove(OWNER);
    if (post == null) {
      return;
    }
    theirs.remove(post);
    suppressDrops.run();
    block.world().dropItemNaturally(block.center().position(), tool.apply(post.harvests));
  }

  /** Whether {@code block} is a scarecrow; explosions and fire skip those. */
  public boolean guards(BlockRef block) {
    if (!block.type().equals(HAY)) {
      return false;
    }
    UUID ownerId = ownerAt(block);
    return ownerId != null && stored(ownerId).at(block) != null;
  }

  /** Whether a mob turning {@code farmland} to dirt stands in the square of a working scarecrow whose owner is near. */
  public boolean keepsFarmland(EntityRef mob, BlockRef farmland) {
    if (mob instanceof PlayerRef || !farmland.type().equals(FARMLAND)) {
      return false;
    }
    for (Map.Entry<UUID, Posts> entry : posts.entrySet()) {
      PlayerRef p = AdaptApi.platform().host().player(entry.getKey());
      int level = p == null ? 0 : owner.getActiveLevel(p);
      if (level <= 0) {
        continue;
      }
      Settings rules = settings.apply(level);
      for (Post post : entry.getValue().copy()) {
        if (post.harvests < rules.wearLimit() && covers(post, farmland, rules.half())
            && loadedNear(p, post, rules.range()) != null) {
          return true;
        }
      }
    }
    return false;
  }

  /** Writes and drops the owner's scarecrows from memory when they leave. */
  public void forget(UUID player) {
    Posts gone = posts.remove(player);
    if (gone != null) {
      gone.save();
    }
  }

  private static boolean covers(Post post, BlockRef block, int half) {
    return post.world.equals(block.world().uid()) && Math.abs(block.pos().x() - post.x) <= half
        && Math.abs(block.pos().z() - post.z) <= half && Math.abs(block.pos().y() - post.y) <= 2;
  }

  private @Nullable BlockRef loadedNear(PlayerRef p, Post post, double range) {
    WorldRef world = p.world();
    if (!world.uid().equals(post.world) || !world.isChunkLoaded(post.x >> 4, post.z >> 4)) {
      return null;
    }
    BlockRef block = world.blockAt(post.x, post.y, post.z);
    if (block.center().distanceSquared(p.location()) > range * range
        || !AdaptApi.scheduler().owns(block.location())) {
      return null;
    }
    return block;
  }

  private @Nullable InventoryRef chestBeside(PlayerRef p, BlockRef scarecrow) {
    for (BlockFace side : SIDES) {
      BlockRef next = scarecrow.relative(side);
      String path = next.type().path();
      if ((path.endsWith("chest") || path.equals("barrel")) && !path.contains("ender")
          && AdaptApi.scheduler().owns(next.location()) && owner.canAccessChest(p, next.location())) {
        InventoryRef inventory = next.blockInventory();
        if (inventory != null) {
          return inventory;
        }
      }
    }
    return null;
  }

  private static boolean hasRoom(InventoryRef chest) {
    for (int slot = 0; slot < chest.size(); slot++) {
      ItemRef item = chest.item(slot);
      if (item == null || item.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private static void deposit(InventoryRef chest, BlockRef scarecrow, List<ItemRef> produce) {
    for (ItemRef item : produce) {
      for (ItemRef overflow : chest.addItem(item)) {
        scarecrow.world().dropItemNaturally(scarecrow.center().position(), overflow);
      }
    }
  }

  private static boolean isScarecrow(BlockRef block, UUID player) {
    return block.type().equals(HAY) && player.equals(ownerOf(block));
  }

  /**
   * The owner of the scarecrow at {@code block}: from its block data, or, when the server already dropped that data,
   * from the scarecrows of the players loaded now.
   */
  private @Nullable UUID ownerAt(BlockRef block) {
    UUID marked = ownerOf(block);
    if (marked != null) {
      return marked;
    }
    for (Map.Entry<UUID, Posts> entry : posts.entrySet()) {
      if (entry.getValue().at(block) != null) {
        return entry.getKey();
      }
    }
    return null;
  }

  private static @Nullable UUID ownerOf(BlockRef block) {
    String raw = block.hasData() ? block.data().get(OWNER) : null;
    if (raw == null) {
      return null;
    }
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException unreadable) {
      return null;
    }
  }

  private String count(int placed, int max) {
    return Text.key("adapt.tool.herbalism.scarecrow.placed").resolve() + " " + placed + "/" + max;
  }

  private String wear(int left, int max) {
    return Text.key("adapt.tool.herbalism.scarecrow.harvests").resolve() + " " + left + "/" + max;
  }

  private Posts posts(UUID player) {
    return posts.computeIfAbsent(player, Posts::load);
  }

  /** The player's scarecrows, read from disk without keeping them in memory when the player is not loaded. */
  private Posts stored(UUID player) {
    Posts loaded = posts.get(player);
    return loaded != null ? loaded : Posts.load(player);
  }

  /** One placed scarecrow and the harvests it has done. */
  public static final class Post {
    private final UUID world;
    private final int x;
    private final int y;
    private final int z;
    private int harvests;
    private int cursor;

    Post(UUID world, int x, int y, int z, int harvests) {
      this.world = world;
      this.x = x;
      this.y = y;
      this.z = z;
      this.harvests = Math.max(0, harvests);
    }

    public int harvests() {
      return harvests;
    }

    boolean at(BlockRef block) {
      return world.equals(block.world().uid()) && x == block.pos().x() && y == block.pos().y() && z == block.pos().z();
    }
  }

  private static final class Posts {
    private final Path file;
    private final List<Post> list = new ArrayList<>();
    private final Object writeLock = new Object();

    private Posts(Path file) {
      this.file = file;
    }

    static Posts load(UUID player) {
      Posts posts = new Posts(BuiltinData.file("data", "scarecrows", player + ".json"));
      if (Files.isRegularFile(posts.file)) {
        try {
          Stored stored = Json.read(Files.readString(posts.file, StandardCharsets.UTF_8), Stored.class);
          if (stored != null && stored.posts != null) {
            for (StoredPost post : stored.posts) {
              if (post != null && post.world != null) {
                posts.list.add(new Post(UUID.fromString(post.world), post.x, post.y, post.z, post.harvests));
              }
            }
          }
        } catch (IOException | IllegalArgumentException failure) {
          AdaptApi.log().warn("Ignoring unreadable scarecrows " + posts.file + ": " + failure.getMessage());
        }
      }
      return posts;
    }

    synchronized List<Post> copy() {
      return List.copyOf(list);
    }

    synchronized int size() {
      return list.size();
    }

    synchronized @Nullable Post at(BlockRef block) {
      for (Post post : list) {
        if (post.at(block)) {
          return post;
        }
      }
      return null;
    }

    void add(Post post) {
      synchronized (this) {
        list.add(post);
      }
      save();
    }

    void remove(Post post) {
      boolean removed;
      synchronized (this) {
        removed = list.remove(post);
      }
      if (removed) {
        save();
      }
    }

    /** Drops scarecrows whose loaded block is no longer theirs. */
    void prune() {
      boolean changed;
      synchronized (this) {
        changed = list.removeIf((Post post) -> {
          WorldRef world = AdaptApi.platform().host().world(post.world);
          if (world == null || !world.isLoaded() || !world.isChunkLoaded(post.x >> 4, post.z >> 4)) {
            return false;
          }
          BlockRef block = world.blockAt(post.x, post.y, post.z);
          return !block.type().equals(HAY) || ownerOf(block) == null;
        });
      }
      if (changed) {
        save();
      }
    }

    private synchronized @Nullable Stored snapshot() {
      if (list.isEmpty()) {
        return null;
      }
      Stored stored = new Stored();
      stored.posts = new ArrayList<>(list.size());
      for (Post post : list) {
        StoredPost row = new StoredPost();
        row.world = post.world.toString();
        row.x = post.x;
        row.y = post.y;
        row.z = post.z;
        row.harvests = post.harvests;
        stored.posts.add(row);
      }
      return stored;
    }

    /** Writes the newest snapshot; writes are serialized so an older snapshot never lands after a newer one. */
    void save() {
      synchronized (writeLock) {
        try {
          Files.createDirectories(file.getParent());
          Stored stored = snapshot();
          if (stored == null) {
            Files.deleteIfExists(file);
            return;
          }
          Path temporary = Files.createTempFile(file.getParent(), "scarecrows", ".tmp");
          try {
            Files.writeString(temporary, Json.write(stored), StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
          } finally {
            Files.deleteIfExists(temporary);
          }
        } catch (IOException failure) {
          AdaptApi.log().warn("Could not save scarecrows " + file + ": " + failure.getMessage());
        }
      }
    }
  }

  private static final class Stored {
    private List<StoredPost> posts;
  }

  private static final class StoredPost {
    private String world;
    private int x;
    private int y;
    private int z;
    private int harvests;
  }
}
