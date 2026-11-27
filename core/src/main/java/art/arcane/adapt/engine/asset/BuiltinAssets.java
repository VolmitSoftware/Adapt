package art.arcane.adapt.engine.asset;

import art.arcane.adapt.api.asset.AssetBundle;
import art.arcane.adapt.api.asset.AssetEntry;
import art.arcane.adapt.platform.value.Id;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Adapt's own art as the {@code adapt} bundle. The build writes {@value #INDEX} next to the files, one path below
 * {@code assets/adapt/} per line, because a packed or directory-stripped jar cannot be listed.
 */
public final class BuiltinAssets {
  public static final String INDEX = "adapt-asset-index.txt";
  private static final String ROOT = "assets/" + Id.ADAPT + "/";

  private BuiltinAssets() {
  }

  /** The bundle {@code loader} holds; empty when the index is missing, as in a build without the art. */
  public static AssetBundle bundle(ClassLoader loader) {
    Objects.requireNonNull(loader, "loader");
    List<AssetEntry> found = new ArrayList<>();
    try (InputStream index = loader.getResourceAsStream(INDEX)) {
      if (index != null) {
        BufferedReader lines = new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8));
        for (String line = lines.readLine(); line != null; line = lines.readLine()) {
          String path = line.strip();
          if (!path.isEmpty()) {
            found.add(AssetBundle.classify(Id.ADAPT, path));
          }
        }
      }
    } catch (IOException failure) {
      throw new UncheckedIOException("Cannot read " + INDEX, failure);
    }
    List<AssetEntry> entries = List.copyOf(found);
    return new AssetBundle() {
      @Override
      public String namespace() {
        return Id.ADAPT;
      }

      @Override
      public List<AssetEntry> entries() {
        return entries;
      }

      @Override
      public byte[] read(AssetEntry entry) {
        try (InputStream stream = loader.getResourceAsStream(ROOT + entry.path())) {
          if (stream == null) {
            throw new IllegalArgumentException("Listed but missing: " + ROOT + entry.path());
          }
          return stream.readAllBytes();
        } catch (IOException failure) {
          throw new UncheckedIOException("Cannot read " + ROOT + entry.path(), failure);
        }
      }
    };
  }
}
