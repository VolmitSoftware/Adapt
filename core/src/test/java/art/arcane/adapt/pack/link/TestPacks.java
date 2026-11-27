package art.arcane.adapt.pack.link;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Small valid resource packs and a {@link PackSource} that tests can swap. */
final class TestPacks {
  private TestPacks() {
  }

  /** A pack whose only texture-free content is its {@code pack.mcmeta} with {@code description}. */
  static PackArtifact pack(String description) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry("pack.mcmeta"));
      zip.write(("{\"pack\":{\"description\":\"" + description + "\",\"min_format\":84,\"max_format\":97}}")
          .getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("assets/adapt/lang/en_us.json"));
      zip.write("{}".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    return PackArtifact.of(bytes.toByteArray(), 84, 97);
  }

  static final class Source implements PackSource {
    private final List<Runnable> listeners = new ArrayList<>();
    private PackArtifact artifact;

    void set(PackArtifact next) {
      artifact = next;
      for (Runnable listener : List.copyOf(listeners)) {
        listener.run();
      }
    }

    @Override
    public Optional<PackArtifact> current() {
      return Optional.ofNullable(artifact);
    }

    @Override
    public Runnable subscribe(Runnable changed) {
      listeners.add(changed);
      return () -> listeners.remove(changed);
    }
  }
}
