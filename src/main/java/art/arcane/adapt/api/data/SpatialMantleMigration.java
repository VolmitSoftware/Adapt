package art.arcane.adapt.api.data;

import art.arcane.adapt.Adapt;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.matter.Matter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * Rewrites mantle regions saved by Adapt releases that stored sections with the Spatial matter codec.
 * Runs once per world folder; the marker file records that every region there is in the VolmLib format.
 */
final class SpatialMantleMigration {
  static final String MARKER = ".matter-format";
  static final String BACKUP_FOLDER = "spatial-backup";
  static final String DAMAGED_FOLDER = "spatial-damaged";
  private static final String MARKER_CONTENT = "volmlib-matter 1\n";
  private static final Pattern REGION_FILE = Pattern.compile("pv?\\.-?\\d+\\.ttp\\.lz4b");

  private SpatialMantleMigration() {
  }

  static void migrate(File folder,
                      int worldHeight,
                      MantleDataAdapter<Matter> current,
                      MantleDataAdapter<Matter> spatial) {
    File marker = new File(folder, MARKER);
    if (marker.exists()) {
      return;
    }
    File[] regions = folder.listFiles(file -> file.isFile() && REGION_FILE.matcher(file.getName()).matches());
    if (regions == null) {
      return;
    }
    Arrays.sort(regions, Comparator.comparing(File::getName));
    int migrated = 0;
    int damaged = 0;
    try {
      for (File region : regions) {
        byte[] original = Files.readAllBytes(region.toPath());
        if (read(original, worldHeight, current) != null) {
          continue;
        }
        TectonicPlate<Matter> plate = read(original, worldHeight, spatial);
        if (plate == null) {
          setAside(folder, region);
          damaged++;
          continue;
        }
        rewrite(folder, region, original, plate, worldHeight, current);
        migrated++;
      }
      Files.writeString(marker.toPath(), MARKER_CONTENT);
    } catch (IOException | RuntimeException failure) {
      Adapt.error("Could not finish migrating Spatial mantle data in " + folder + "; it will be retried on the next start.", failure);
      return;
    }
    if (migrated > 0) {
      Adapt.info("Migrated " + migrated + " Spatial mantle region(s) in " + folder + " to the VolmLib format; originals are in "
          + new File(folder, BACKUP_FOLDER));
    }
    if (damaged > 0) {
      Adapt.warn("Moved " + damaged + " unreadable mantle region(s) in " + folder + " to " + new File(folder, DAMAGED_FOLDER));
    }
  }

  private static void rewrite(File folder,
                              File region,
                              byte[] original,
                              TectonicPlate<Matter> plate,
                              int worldHeight,
                              MantleDataAdapter<Matter> current) throws IOException {
    Path backup = new File(folder, BACKUP_FOLDER).toPath().resolve(region.getName());
    Files.createDirectories(backup.getParent());
    if (!Files.exists(backup)) {
      Files.write(backup, original);
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(original.length);
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      plate.write(out);
    }
    byte[] rewritten = bytes.toByteArray();
    if (read(rewritten, worldHeight, current) == null) {
      throw new IOException("Migrated region " + region.getName() + " does not read back in the VolmLib format");
    }
    Path staged = new File(folder, region.getName() + ".migrating").toPath();
    Files.write(staged, rewritten);
    Files.move(staged, region.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  private static void setAside(File folder, File region) throws IOException {
    Path target = new File(folder, DAMAGED_FOLDER).toPath().resolve(region.getName());
    Files.createDirectories(target.getParent());
    if (Files.exists(target)) {
      target = target.resolveSibling(region.getName() + "." + System.currentTimeMillis());
    }
    Files.move(region.toPath(), target);
  }

  private static TectonicPlate<Matter> read(byte[] bytes, int worldHeight, MantleDataAdapter<Matter> adapter) {
    if (bytes.length == 0) {
      return null;
    }
    try (CountingDataInputStream in = CountingDataInputStream.wrap(new ByteArrayInputStream(bytes))) {
      TectonicPlate<Matter> plate = TectonicPlate.read(worldHeight, in, true, adapter, MantleHooks.NONE);
      return in.count() == bytes.length ? plate : null;
    } catch (IOException | RuntimeException unreadable) {
      return null;
    }
  }
}
