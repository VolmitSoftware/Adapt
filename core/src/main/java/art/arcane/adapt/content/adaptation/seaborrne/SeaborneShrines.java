package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every placed Tide Shrine, kept per owner in {@code data/tide-shrines/<uuid>.json} so shrines survive restarts. All
 * files are read on first use, because a shrine can be broken or blown up while its owner is offline.
 */
public final class SeaborneShrines {
  private static final Gson GSON = new Gson();
  private static final String FOLDER = "tide-shrines";

  private final Map<UUID, List<Shrine>> byOwner = new HashMap<>();
  private final Map<Spot, Shrine> bySpot = new HashMap<>();
  private boolean loaded;

  public record Shrine(UUID owner, UUID world, int x, int y, int z) {
    /** Whether {@code location} is in the same world and within {@code radius} blocks of the shrine's centre. */
    public boolean covers(Loc location, double radius) {
      if (location.world() == null || !location.world().uid().equals(world)) {
        return false;
      }
      double dx = location.x() - (x + 0.5D);
      double dy = location.y() - (y + 0.5D);
      double dz = location.z() - (z + 0.5D);
      return (dx * dx) + (dy * dy) + (dz * dz) <= radius * radius;
    }
  }

  public synchronized List<Shrine> of(UUID owner) {
    load();
    return List.copyOf(byOwner.getOrDefault(owner, List.of()));
  }

  public synchronized void add(Shrine shrine) {
    load();
    byOwner.computeIfAbsent(shrine.owner(), (UUID ignored) -> new ArrayList<>()).add(shrine);
    bySpot.put(Spot.of(shrine), shrine);
    save(shrine.owner());
  }

  /** The shrine standing at {@code block}, or null. */
  public synchronized @Nullable Shrine at(BlockRef block) {
    load();
    return bySpot.get(new Spot(block.world().uid(), new BlockPos(block.x(), block.y(), block.z()).asLong()));
  }

  public synchronized void remove(Shrine shrine) {
    load();
    List<Shrine> shrines = byOwner.get(shrine.owner());
    if (shrines != null && shrines.remove(shrine)) {
      bySpot.remove(Spot.of(shrine), shrine);
      save(shrine.owner());
    }
  }

  /** Every owner with at least one shrine. */
  public synchronized List<UUID> owners() {
    load();
    List<UUID> owners = new ArrayList<>();
    for (Map.Entry<UUID, List<Shrine>> entry : byOwner.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        owners.add(entry.getKey());
      }
    }
    return owners;
  }

  private void load() {
    if (loaded) {
      return;
    }
    loaded = true;
    File[] files = folder().listFiles((File dir, String name) -> name.endsWith(".json"));
    if (files == null) {
      return;
    }
    for (File file : files) {
      UUID owner = owner(file.getName());
      if (owner == null) {
        continue;
      }
      try {
        Stored stored = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), Stored.class);
        if (stored == null || stored.shrines == null) {
          continue;
        }
        List<Shrine> shrines = new ArrayList<>();
        for (StoredShrine entry : stored.shrines) {
          if (entry != null && entry.world != null) {
            Shrine shrine = new Shrine(owner, entry.world, entry.x, entry.y, entry.z);
            shrines.add(shrine);
            bySpot.put(Spot.of(shrine), shrine);
          }
        }
        byOwner.put(owner, shrines);
      } catch (IOException | JsonParseException | IllegalArgumentException failure) {
        AdaptLog.warn("Ignoring unreadable tide shrine file " + file + ": " + failure.getMessage());
      }
    }
  }

  private void save(UUID owner) {
    List<Shrine> shrines = byOwner.getOrDefault(owner, List.of());
    Path file = new File(folder(), owner + ".json").toPath();
    try {
      if (shrines.isEmpty()) {
        Files.deleteIfExists(file);
        return;
      }
      Stored stored = new Stored();
      stored.shrines = new ArrayList<>();
      for (Shrine shrine : shrines) {
        StoredShrine entry = new StoredShrine();
        entry.world = shrine.world();
        entry.x = shrine.x();
        entry.y = shrine.y();
        entry.z = shrine.z();
        stored.shrines.add(entry);
      }
      Files.createDirectories(file.getParent());
      Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
      Files.writeString(temporary, GSON.toJson(stored), StandardCharsets.UTF_8);
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException failure) {
      AdaptLog.warn("Could not save tide shrines to " + file + ": " + failure.getMessage());
    }
  }

  private static File folder() {
    return AdaptEnvironment.dataFile("data", FOLDER);
  }

  private static @Nullable UUID owner(String fileName) {
    try {
      return UUID.fromString(fileName.substring(0, fileName.length() - ".json".length()));
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  private record Spot(UUID world, long position) {
    private static Spot of(Shrine shrine) {
      return new Spot(shrine.world(), new BlockPos(shrine.x(), shrine.y(), shrine.z()).asLong());
    }
  }

  private static final class Stored {
    List<StoredShrine> shrines;
  }

  private static final class StoredShrine {
    UUID world;
    int x;
    int y;
    int z;
  }
}
