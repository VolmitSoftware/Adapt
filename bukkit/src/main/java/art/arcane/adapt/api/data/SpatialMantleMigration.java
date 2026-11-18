package art.arcane.adapt.api.data;

import art.arcane.adapt.Adapt;
import art.arcane.volmlib.util.data.Varint;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.matter.Matter;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Rewrites mantle regions saved by Adapt releases that stored sections with the Spatial matter codec.
 * The marker file records that every region in a world folder was read in full once. A marked folder is
 * only probed for Spatial regions that turned up later, such as one restored from the backup folder.
 */
final class SpatialMantleMigration {
  static final String MARKER = ".matter-format";
  static final String BACKUP_FOLDER = "spatial-backup";
  static final String DAMAGED_FOLDER = "spatial-damaged";
  private static final String MARKER_CONTENT = "volmlib-matter 1\n";
  private static final String STAGED_SUFFIX = ".migrating";
  private static final Pattern REGION_FILE = Pattern.compile("pv?\\.-?\\d+\\.ttp\\.lz4b");
  private static final long PROGRESS_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5);
  private static final int CHUNKS_PER_REGION = 1024;
  private static final int SECTION_SIZE_BYTES = 12;

  private SpatialMantleMigration() {
  }

  // One lock for every world: a world opened from two threads must not convert the same files twice.
  static synchronized void migrate(String world,
                                   File folder,
                                   int worldHeight,
                                   MantleDataAdapter<Matter> current,
                                   MantleDataAdapter<Matter> spatial) {
    File marker = new File(folder, MARKER);
    boolean marked = marker.exists();
    File[] regions = folder.listFiles(file -> file.isFile() && REGION_FILE.matcher(file.getName()).matches());
    if (regions == null) {
      return;
    }
    List<File> pending = new ArrayList<>();
    for (File region : regions) {
      if (!marked || isSpatial(region.toPath())) {
        pending.add(region);
      }
    }
    if (marked && pending.isEmpty()) {
      return;
    }
    pending.sort(Comparator.comparing(File::getName));
    File backups = new File(folder, BACKUP_FOLDER);
    File setAside = new File(folder, DAMAGED_FOLDER);
    int total = pending.size();
    int checked = 0;
    int migrated = 0;
    int damaged = 0;
    if (total > 0) {
      Adapt.info(marked
          ? "Converting " + total + " Spatial mantle region file(s) found in world " + world + " after its migration."
          : "Checking " + total + " mantle region file(s) of world " + world + " for the Spatial format. This runs once.");
    }
    long lastProgress = System.nanoTime();
    try {
      for (File region : pending) {
        byte[] original = Files.readAllBytes(region.toPath());
        Exception notCurrent = unreadable(original, worldHeight, current);
        if (notCurrent != null) {
          TectonicPlate<Matter> plate = null;
          Exception notSpatial = null;
          try {
            plate = read(original, worldHeight, spatial);
          } catch (IOException | RuntimeException failure) {
            notSpatial = failure;
          }
          if (plate == null) {
            Path target = setAside(setAside, region);
            Adapt.warn("Set aside mantle region " + region.getName() + " of world " + world + " as " + target
                + ". It reads neither as VolmLib (" + describe(notCurrent) + ") nor as Spatial (" + describe(notSpatial) + ").");
            damaged++;
          } else {
            rewrite(backups, region, original, plate, worldHeight, current);
            migrated++;
          }
        }
        checked++;
        long now = System.nanoTime();
        if (now - lastProgress >= PROGRESS_INTERVAL_NANOS && checked < total) {
          lastProgress = now;
          Adapt.info("Mantle migration of world " + world + ": " + checked + " of " + total + " region file(s) checked, "
              + migrated + " converted.");
        }
      }
      if (!marked) {
        Files.writeString(marker.toPath(), MARKER_CONTENT);
      }
    } catch (IOException | RuntimeException failure) {
      Adapt.error("Could not finish migrating Spatial mantle data of world " + world + " in " + folder + " after " + checked
          + " of " + total + " region file(s). The rest is retried on the next start.", failure);
      return;
    }
    if (total > 0) {
      Adapt.info("Mantle data of world " + world + " is in the VolmLib format: " + total + " region file(s) checked, "
          + migrated + " converted" + (migrated > 0 ? " with the originals kept in " + backups : "") + ".");
    }
    if (damaged > 0) {
      Adapt.warn("Moved " + damaged + " unreadable mantle region(s) of world " + world + " to " + setAside
          + ". Their blocks start without mantle data.");
    }
  }

  /**
   * Reads up to the first stored slice of a region and reports whether it is named the way Spatial named slices.
   * A region is written in one format throughout, so the first slice decides and the rest of the file is never read.
   */
  static boolean isSpatial(Path region) {
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(region), 4096))) {
      in.readInt();
      in.readInt();
      int version = Varint.readUnsignedVarInt(in);
      for (int chunk = 0; chunk < CHUNKS_PER_REGION; chunk++) {
        if (in.readInt() <= 0) {
          continue;
        }
        in.readByte();
        in.readByte();
        int sections = in.readUnsignedByte();
        int flags = Varint.readUnsignedVarInt(in);
        in.skipNBytes(version >= 1 ? (flags + 7) / 8 : flags);
        for (int section = 0; section < sections; section++) {
          int size = in.readInt();
          if (size <= 0) {
            continue;
          }
          in.skipNBytes(SECTION_SIZE_BYTES);
          int slices = in.readUnsignedByte();
          int author = in.readUnsignedShort();
          in.skipNBytes(author + Long.BYTES + Short.BYTES);
          if (slices == 0) {
            in.skipNBytes(size - SECTION_SIZE_BYTES - 1 - Short.BYTES - author - Long.BYTES - Short.BYTES);
            continue;
          }
          byte[] name = new byte[in.readUnsignedShort()];
          if (name.length > AdaptSlices.LONGEST_SPATIAL_NAME) {
            return false;
          }
          in.readFully(name);
          return AdaptSlices.isSpatialName(new String(name, StandardCharsets.UTF_8));
        }
      }
      return false;
    } catch (IOException | RuntimeException unreadable) {
      // Not recognisably Spatial. The mantle reports a damaged current-format region when it loads it.
      return false;
    }
  }

  private static void rewrite(File backups,
                              File region,
                              byte[] original,
                              TectonicPlate<Matter> plate,
                              int worldHeight,
                              MantleDataAdapter<Matter> current) throws IOException {
    Path backup = backups.toPath().resolve(region.getName());
    Files.createDirectories(backup.getParent());
    // The region is still unconverted here, so a backup of another length is a leftover of an interrupted run.
    if (!Files.exists(backup) || Files.size(backup) != original.length) {
      replace(backup, original);
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(original.length);
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      plate.write(out);
    }
    byte[] rewritten = bytes.toByteArray();
    Exception unreadable = unreadable(rewritten, worldHeight, current);
    if (unreadable != null) {
      throw new IOException("Migrated region " + region.getName() + " does not read back in the VolmLib format", unreadable);
    }
    replace(region.toPath(), rewritten);
  }

  private static void replace(Path target, byte[] bytes) throws IOException {
    Path staged = target.resolveSibling(target.getFileName() + STAGED_SUFFIX);
    try (FileChannel channel = FileChannel.open(staged,
        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
      channel.force(true);
    }
    try {
      Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException unsupported) {
      Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static Path setAside(File folder, File region) throws IOException {
    Path target = folder.toPath().resolve(region.getName());
    Files.createDirectories(target.getParent());
    if (Files.exists(target)) {
      target = target.resolveSibling(region.getName() + "." + System.currentTimeMillis());
    }
    return Files.move(region.toPath(), target);
  }

  private static Exception unreadable(byte[] bytes, int worldHeight, MantleDataAdapter<Matter> adapter) {
    try {
      read(bytes, worldHeight, adapter);
      return null;
    } catch (IOException | RuntimeException failure) {
      return failure;
    }
  }

  private static TectonicPlate<Matter> read(byte[] bytes, int worldHeight, MantleDataAdapter<Matter> adapter) throws IOException {
    if (bytes.length == 0) {
      throw new IOException("The file is empty");
    }
    try (CountingDataInputStream in = CountingDataInputStream.wrap(new ByteArrayInputStream(bytes))) {
      TectonicPlate<Matter> plate = TectonicPlate.read(worldHeight, in, true, adapter, MantleHooks.NONE);
      if (in.count() != bytes.length) {
        throw new IOException((bytes.length - in.count()) + " byte(s) follow the last chunk");
      }
      return plate;
    }
  }

  private static String describe(Throwable failure) {
    String text = failure.getClass().getSimpleName() + ": " + failure.getMessage();
    Throwable cause = failure.getCause();
    return cause == null ? text : text + ", caused by " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
  }
}
