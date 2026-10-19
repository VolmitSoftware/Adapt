package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.item.GolemItemLooks;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("media-export")
class GolemancyMediaExportTest extends AdaptTestBase {
  private static final String OUTPUT_PROPERTY = "adapt.media.output";
  private static final String DEFAULT_OUTPUT = "build/demo/golemancy-media";

  @Test
  void writesTheRecipeCatalogForTheMediaRenderer() throws IOException {
    Path target = Path.of(System.getProperty(OUTPUT_PROPERTY, DEFAULT_OUTPUT)).resolve("recipes.json");
    Files.createDirectories(target.getParent());
    try (GolemItemLooks looks = GolemItemLooks.open()) {
      Files.writeString(target, GolemancyMediaCatalog.toJson(GolemancyMediaCatalog.build(looks)));
    }

    assertThat(target).isNotEmptyFile();
  }
}
