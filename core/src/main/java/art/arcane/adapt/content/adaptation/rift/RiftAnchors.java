package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Every placed Rift Anchor with its owner, kept in {@code data/rift-anchors.json} so the network survives restarts,
 * and the anchors of each owner that someone else broke, kept until the owner is told. A waystone is an anchor without
 * a block: a named spot to travel to, counted with the owner's anchors.
 */
public final class RiftAnchors {
  private static final Gson GSON = new Gson();
  private static final Id LODESTONE = Id.minecraft("lodestone");

  private final Path file;
  private final List<Anchor> anchors = new ArrayList<>();
  private final Map<Spot, Anchor> bySpot = new HashMap<>();
  private final Map<String, Integer> lost = new HashMap<>();

  private RiftAnchors(Path file) {
    this.file = file;
  }

  public static RiftAnchors load() {
    Path file = AdaptEnvironment.dataFile("data", "rift-anchors.json").toPath();
    RiftAnchors store = new RiftAnchors(file);
    if (!Files.isRegularFile(file)) {
      return store;
    }
    try {
      Stored stored = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Stored.class);
      if (stored != null && stored.anchors != null) {
        for (Anchor anchor : stored.anchors) {
          if (anchor == null || !anchor.valid()) {
            continue;
          }
          if (anchor.blockless()) {
            store.anchors.add(anchor);
          } else if (!store.bySpot.containsKey(anchor.spot())) {
            store.anchors.add(anchor);
            store.bySpot.put(anchor.spot(), anchor);
          }
        }
      }
      if (stored != null && stored.lost != null) {
        store.lost.putAll(stored.lost);
      }
    } catch (IOException | JsonParseException failure) {
      AdaptLog.warn("Ignoring unreadable rift anchors " + file + ": " + failure.getMessage());
    }
    return store;
  }

  /** One anchor block, or a waystone. */
  public static final class Anchor {
    private String owner;
    private String world;
    private int x;
    private int y;
    private int z;
    private String waystone;
    private String name;

    private Anchor(UUID owner, BlockRef block) {
      this.owner = owner.toString();
      this.world = block.world().uid().toString();
      this.x = block.x();
      this.y = block.y();
      this.z = block.z();
    }

    private Anchor(UUID owner, UUID world, BlockPos at, String waystone, String name) {
      this.owner = owner.toString();
      this.world = world.toString();
      this.x = at.x();
      this.y = at.y();
      this.z = at.z();
      this.waystone = waystone;
      this.name = name;
    }

    /** A waystone: no block, just a spot to travel to. */
    public boolean blockless() {
      return waystone != null;
    }

    /** What the waystone marks, such as an Atlas entry; null for an anchor block. */
    public @Nullable String waystone() {
      return waystone;
    }

    /** The waystone's name; null for an anchor block. */
    public @Nullable String name() {
      return name;
    }

    public UUID owner() {
      return UUID.fromString(owner);
    }

    public UUID worldId() {
      return UUID.fromString(world);
    }

    /** The world it stands in, or null when that world is not loaded. */
    public @Nullable WorldRef world() {
      return AdaptCore.platform().host().world(worldId());
    }

    /** The anchor block's corner, or null when its world is not loaded. */
    public @Nullable Loc location() {
      WorldRef loaded = world();
      return loaded == null ? null : new Loc(loaded, x, y, z);
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public int z() {
      return z;
    }

    private Spot spot() {
      return new Spot(worldId(), new BlockPos(x, y, z).asLong());
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Anchor anchor && anchor.x == x && anchor.y == y && anchor.z == z
          && anchor.world.equals(world) && anchor.owner.equals(owner) && Objects.equals(anchor.waystone, waystone);
    }

    @Override
    public int hashCode() {
      return Objects.hash(owner, world, x, y, z, waystone);
    }

    private boolean valid() {
      try {
        UUID.fromString(owner);
        UUID.fromString(world);
        return true;
      } catch (IllegalArgumentException | NullPointerException invalid) {
        return false;
      }
    }
  }

  /** The owner's anchors in placement order. */
  public synchronized List<Anchor> of(UUID owner) {
    List<Anchor> owned = new ArrayList<>();
    String id = owner.toString();
    for (Anchor anchor : anchors) {
      if (anchor.owner.equals(id)) {
        owned.add(anchor);
      }
    }
    return owned;
  }

  /** The anchor at {@code block}; a record whose block is no longer a lodestone is forgotten instead. */
  public synchronized @Nullable Anchor at(BlockRef block) {
    Anchor anchor = bySpot.get(Spot.of(block));
    if (anchor == null || LODESTONE.equals(block.type())) {
      return anchor;
    }
    forget(anchor);
    return null;
  }

  /** Forgets the record at {@code block}, whose spot something else now fills. */
  public synchronized void forget(BlockRef block) {
    Anchor anchor = bySpot.get(Spot.of(block));
    if (anchor != null) {
      forget(anchor);
    }
  }

  private void forget(Anchor anchor) {
    anchors.remove(anchor);
    bySpot.remove(anchor.spot(), anchor);
    save();
  }

  /** Records a new anchor of {@code owner} at {@code block}, unless one is there already or the owner has {@code max}. */
  public synchronized @Nullable Anchor add(UUID owner, BlockRef block, int max) {
    if (at(block) != null || of(owner).size() >= max) {
      return null;
    }
    Anchor anchor = new Anchor(owner, block);
    anchors.add(anchor);
    bySpot.put(anchor.spot(), anchor);
    save();
    return anchor;
  }

  /**
   * Records a waystone of {@code owner} standing at {@code feet} for {@code waystone}, unless the owner already has
   * one for it or has {@code max} anchors.
   */
  public synchronized @Nullable Anchor addWaystone(UUID owner, UUID world, BlockPos feet, String waystone, String name,
                                                   int max) {
    List<Anchor> owned = of(owner);
    if (waystone == null || owned.size() >= max || waystoneOf(owner, waystone) != null) {
      return null;
    }
    Anchor anchor = new Anchor(owner, world, feet, waystone, name);
    anchors.add(anchor);
    save();
    return anchor;
  }

  /** The owner's waystone for {@code waystone}, or null. */
  public synchronized @Nullable Anchor waystoneOf(UUID owner, String waystone) {
    String id = owner.toString();
    for (Anchor anchor : anchors) {
      if (anchor.owner.equals(id) && waystone.equals(anchor.waystone)) {
        return anchor;
      }
    }
    return null;
  }

  /** Forgets an anchor; true when it was still recorded. */
  public synchronized boolean remove(Anchor anchor) {
    boolean removed = anchors.remove(anchor);
    if (removed) {
      bySpot.remove(anchor.spot(), anchor);
      save();
    }
    return removed;
  }

  /** Remembers that someone else broke one of {@code owner}'s anchors. */
  public synchronized void markLost(UUID owner) {
    lost.merge(owner.toString(), 1, Integer::sum);
    save();
  }

  /** How many of the owner's anchors were broken since they were last told, clearing the count. */
  public synchronized int takeLost(UUID owner) {
    Integer count = lost.remove(owner.toString());
    if (count == null) {
      return 0;
    }
    save();
    return count;
  }

  private void save() {
    try {
      Files.createDirectories(file.getParent());
      if (anchors.isEmpty() && lost.isEmpty()) {
        Files.deleteIfExists(file);
        return;
      }
      Stored stored = new Stored();
      stored.anchors = List.copyOf(anchors);
      stored.lost = Map.copyOf(lost);
      Path temporary = Files.createTempFile(file.getParent(), "rift-anchors", ".tmp");
      try {
        Files.writeString(temporary, GSON.toJson(stored), StandardCharsets.UTF_8);
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException failure) {
      AdaptLog.warn("Could not save rift anchors " + file + ": " + failure.getMessage());
    }
  }

  private record Spot(UUID world, long position) {
    private static Spot of(BlockRef block) {
      return new Spot(block.world().uid(), new BlockPos(block.x(), block.y(), block.z()).asLong());
    }
  }

  private static final class Stored {
    private List<Anchor> anchors;
    private Map<String, Integer> lost;
  }
}
