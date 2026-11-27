package art.arcane.adapt.engine.batch;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * One player's undo journal: the blocks of their recent build batches with the exact item each one cost, kept in
 * {@code data/build-journal/<uuid>.json} so it survives restarts. An entry is handed back only through
 * {@link #take}, which removes its batch first, so no batch can be undone twice.
 */
final class BuildJournal {
  private static final Gson GSON = new Gson();
  private static final long MAX_AGE_MILLIS = 86_400_000L;
  private static final List<String> COUNTS = List.of("candles", "pickles", "eggs", "layers", "flower_amount",
      "segment_amount", "charges");

  private final UUID player;
  private final Path file;
  private final List<Batch> batches = new ArrayList<>();

  private BuildJournal(UUID player, Path file) {
    this.player = player;
    this.file = file;
  }

  static BuildJournal load(UUID player) {
    Path file = AdaptEnvironment.dataFile("data", "build-journal", player + ".json").toPath();
    BuildJournal journal = new BuildJournal(player, file);
    if (Files.isRegularFile(file)) {
      try {
        Stored stored = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Stored.class);
        long oldest = System.currentTimeMillis() - MAX_AGE_MILLIS;
        if (stored != null && stored.batches != null) {
          for (Batch batch : stored.batches) {
            if (batch != null && batch.valid() && batch.createdAt >= oldest) {
              journal.batches.add(batch);
            }
          }
        }
      } catch (IOException | JsonParseException failure) {
        AdaptLog.warn("Ignoring unreadable build journal " + file + ": " + failure.getMessage());
      }
    }
    return journal;
  }

  /**
   * The block type with every property that counts items in it (a double slab, candles, pickles, snow layers), so a
   * block that grew more items since it was placed no longer matches.
   */
  static String stateKey(BlockStateRef state) {
    StringBuilder key = new StringBuilder(state.type().toString());
    if ("double".equals(state.property("type"))) {
      key.append("[type=double]");
    }
    for (String count : COUNTS) {
      String value = state.property(count);
      if (value != null) {
        key.append('[').append(count).append('=').append(value).append(']');
      }
    }
    return key.toString();
  }

  /** Keeps a finished batch, then drops the oldest batches of the same owner beyond {@code keep}. */
  synchronized void record(Batch batch, int keep) {
    if (batch.blocks.length == 0) {
      return;
    }
    batches.add(batch);
    int count = 0;
    for (int index = batches.size() - 1; index >= 0; index--) {
      if (batches.get(index).owner.equals(batch.owner) && ++count > Math.max(1, keep)) {
        batches.remove(index);
      }
    }
    save();
  }

  /**
   * Removes and returns the newest batch of one of {@code owners} made within {@code maxAgeMillis}, or null. The
   * journal is saved without it before the caller touches a block.
   */
  synchronized @Nullable Batch take(Collection<String> owners, long maxAgeMillis) {
    long oldest = System.currentTimeMillis() - maxAgeMillis;
    for (int index = batches.size() - 1; index >= 0; index--) {
      Batch batch = batches.get(index);
      if (owners.contains(batch.owner) && batch.createdAt >= oldest) {
        batches.remove(index);
        save();
        return batch;
      }
    }
    return null;
  }

  synchronized int size() {
    return batches.size();
  }

  private void save() {
    try {
      Files.createDirectories(file.getParent());
      if (batches.isEmpty()) {
        Files.deleteIfExists(file);
        return;
      }
      Stored stored = new Stored();
      stored.batches = List.copyOf(batches);
      Path temporary = Files.createTempFile(file.getParent(), player.toString(), ".tmp");
      try {
        Files.writeString(temporary, GSON.toJson(stored), StandardCharsets.UTF_8);
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException failure) {
      AdaptLog.warn("Could not save build journal " + file + ": " + failure.getMessage());
    }
  }

  private static final class Stored {
    private List<Batch> batches;
  }

  /**
   * One batch: the owner adaptation's id, when it ran, its world, the block state keys and paid items it used (paid
   * items in the platform's binary form, Base64), and per block {@code x, y, z, state key index, item index or -1}.
   */
  static final class Batch {
    private static final int FIELDS = 5;
    private String owner;
    private long createdAt;
    private String world;
    private List<String> types;
    private List<String> items;
    private int[] blocks;

    private boolean valid() {
      return owner != null && world != null && types != null && items != null && blocks != null
          && blocks.length % FIELDS == 0;
    }

    String owner() {
      return owner;
    }

    UUID world() {
      return UUID.fromString(world);
    }

    int size() {
      return blocks.length / FIELDS;
    }

    int x(int entry) {
      return blocks[entry * FIELDS];
    }

    int y(int entry) {
      return blocks[entry * FIELDS + 1];
    }

    int z(int entry) {
      return blocks[entry * FIELDS + 2];
    }

    /** The placed block's {@link #stateKey}. */
    String stateKey(int entry) {
      return types.get(blocks[entry * FIELDS + 3]);
    }

    /** The exact item this block cost, or null when the cost was not an item (a cost provider charged something else). */
    @Nullable ItemRef paid(int entry) {
      int index = blocks[entry * FIELDS + 4];
      if (index < 0 || index >= items.size()) {
        return null;
      }
      try {
        return AdaptCore.platform().host().items().deserialize(Base64.getDecoder().decode(items.get(index)));
      } catch (IllegalArgumentException unreadable) {
        return null;
      }
    }
  }

  /** Collects the blocks of a running batch in order. */
  static final class Builder {
    private final String owner;
    private final long createdAt = System.currentTimeMillis();
    private String world;
    private final List<String> types = new ArrayList<>();
    private final List<String> items = new ArrayList<>();
    private final List<Integer> blocks = new ArrayList<>();

    Builder(String owner) {
      this.owner = owner;
    }

    void add(BlockRef block, @Nullable ItemRef paid) {
      if (world == null) {
        world = block.world().uid().toString();
      } else if (!world.equals(block.world().uid().toString())) {
        return;
      }
      blocks.add(block.x());
      blocks.add(block.y());
      blocks.add(block.z());
      blocks.add(index(types, stateKey(block.state())));
      blocks.add(paid == null ? -1 : index(items,
          Base64.getEncoder().encodeToString(AdaptCore.platform().host().items().serialize(paid))));
    }

    boolean isEmpty() {
      return blocks.isEmpty();
    }

    Batch build() {
      Batch batch = new Batch();
      batch.owner = owner;
      batch.createdAt = createdAt;
      batch.world = world == null ? new UUID(0L, 0L).toString() : world;
      batch.types = List.copyOf(types);
      batch.items = List.copyOf(items);
      batch.blocks = new int[blocks.size()];
      for (int index = 0; index < blocks.size(); index++) {
        batch.blocks[index] = blocks.get(index);
      }
      return batch;
    }

    private static int index(List<String> palette, String value) {
      int index = palette.indexOf(value);
      if (index >= 0) {
        return index;
      }
      palette.add(value);
      return palette.size() - 1;
    }
  }
}
