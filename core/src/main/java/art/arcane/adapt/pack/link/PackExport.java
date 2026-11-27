package art.arcane.adapt.pack.link;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Writes the pack where operators merge it into Nexo, ItemsAdder or CraftEngine: {@code <directory>/adapt-pack.zip}
 * and the same files unzipped in {@code <directory>/adapt-pack/}, with the pack's SHA-1 in
 * {@code <directory>/adapt-pack.sha1}. An export of a different pack replaces the previous one; the same pack is not
 * written again while its zip and folder are still there.
 */
public final class PackExport {
  public static final String ZIP = "adapt-pack.zip";
  public static final String FOLDER = "adapt-pack";
  public static final String HASH = "adapt-pack.sha1";

  private PackExport() {
  }

  /** Exports {@code artifact} into {@code directory}; false when the same pack was already there and nothing was written. */
  public static boolean export(PackArtifact artifact, Path directory) throws IOException {
    Path zip = directory.resolve(ZIP);
    Path folder = directory.resolve(FOLDER);
    Path hash = directory.resolve(HASH);
    if (Files.isRegularFile(zip) && Files.isDirectory(folder) && Files.isRegularFile(hash)
        && Files.readString(hash, StandardCharsets.UTF_8).strip().equals(artifact.sha1Hex())) {
      return false;
    }
    Files.createDirectories(directory);
    Files.deleteIfExists(hash);
    Path staged = Files.createTempFile(directory, ".adapt-pack-", ".tmp");
    try {
      artifact.writeTo(staged);
      Files.move(staged, zip, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(staged);
    }
    delete(folder);
    Files.createDirectories(folder);
    Path root = folder.toAbsolutePath().normalize();
    try (ZipInputStream in = new ZipInputStream(artifact.open())) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        Path target = root.resolve(entry.getName()).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
          throw new IOException("Pack entry escapes the export folder: " + entry.getName());
        }
        if (entry.isDirectory()) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
    Files.writeString(hash, artifact.sha1Hex(), StandardCharsets.UTF_8);
    return true;
  }

  private static void delete(Path folder) throws IOException {
    if (!Files.exists(folder)) {
      return;
    }
    List<Path> paths;
    try (Stream<Path> walk = Files.walk(folder)) {
      paths = walk.sorted(Comparator.reverseOrder()).toList();
    }
    for (Path path : paths) {
      Files.delete(path);
    }
  }
}
