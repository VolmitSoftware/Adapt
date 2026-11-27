package art.arcane.adapt.content;

import art.arcane.adapt.api.AdaptApi;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Files the built-in content keeps in Adapt's own data folder, where they have always been. */
public final class BuiltinData {
  private BuiltinData() {
  }

  /** A file below Adapt's data folder; its parent folders are created. */
  public static Path file(String first, String... more) {
    Path file = AdaptApi.platform().host().dataDirectory().resolve(Path.of(first, more));
    try {
      Files.createDirectories(file.getParent());
    } catch (IOException error) {
      throw new UncheckedIOException("Cannot create " + file.getParent(), error);
    }
    return file;
  }
}
