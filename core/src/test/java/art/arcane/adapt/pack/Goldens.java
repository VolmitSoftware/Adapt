package art.arcane.adapt.pack;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Text goldens under {@code art/arcane/adapt/pack/golden}; set ADAPT_UPDATE_PACK_GOLDENS=1 to rewrite them. */
public final class Goldens {
  private static final Path SOURCE = Path.of("src/test/resources/art/arcane/adapt/pack/golden");

  private Goldens() {
  }

  public static void check(String name, String actual) {
    if ("1".equals(System.getenv("ADAPT_UPDATE_PACK_GOLDENS"))) {
      try {
        Files.createDirectories(SOURCE);
        Files.writeString(SOURCE.resolve(name + ".txt"), actual, StandardCharsets.UTF_8);
      } catch (IOException failure) {
        throw new UncheckedIOException(failure);
      }
      return;
    }
    try (InputStream raw = Goldens.class.getResourceAsStream("golden/" + name + ".txt")) {
      assertThat(raw).as("golden " + name).isNotNull();
      assertThat(actual).isEqualTo(new String(raw.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }
}
