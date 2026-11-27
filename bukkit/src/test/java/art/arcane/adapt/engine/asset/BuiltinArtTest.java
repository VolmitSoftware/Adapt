package art.arcane.adapt.engine.asset;

import art.arcane.adapt.api.asset.AssetBundle;
import art.arcane.adapt.api.asset.AssetEntry;
import art.arcane.adapt.api.asset.AssetKind;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The jar's built-in art, read the way the server reads it: the generated index lists every file under
 * {@code textures/gui} and {@code themes}, and every file passes the catalog's checks.
 */
class BuiltinArtTest {
  private static final Path ART = Path.of("../adapters/client-common/src/main/resources/assets/adapt");

  @Test
  void theIndexListsEveryArtFileAndNothingIsRejected() throws IOException {
    AssetBundle bundle = BuiltinAssets.bundle(BuiltinArtTest.class.getClassLoader());
    assertThat(bundle.entries()).extracting(AssetEntry::path).containsExactlyInAnyOrderElementsOf(files());

    List<String> errors = new ArrayList<>();
    AdaptAssetCatalog catalog = new AdaptAssetCatalog(new SkillLooks() {
      @Override
      public List<Id> ids() {
        return List.of();
      }

      @Override
      public Optional<SkillLook> look(Id skill) {
        return Optional.empty();
      }
    }, errors::add);
    InstalledBundle installed = catalog.install(bundle, Id.ADAPT);

    assertThat(errors).isEmpty();
    assertThat(installed.entries()).hasSameSizeAs(bundle.entries());
    for (String kit : List.of("tooltip", "tile", "token_learned")) {
      Id sprite = Id.adapt("gui/kit/" + kit);
      assertThat(catalog.sprite(sprite)).as(sprite.toString()).hasValueSatisfying(
          (byte[] png) -> assertThat(PngImages.size(png)).isNotNull());
      assertThat(installed.entries()).contains(new AssetEntry(AssetKind.GUI_TEXTURE, sprite, "textures/gui/kit/" + kit + ".png"));
    }
    assertThat(catalog.themes()).hasSize(24).allMatch((Id skill) -> !catalog.theme(skill).orElseThrow().generated());
  }

  private static List<String> files() throws IOException {
    List<String> files = new ArrayList<>();
    for (String folder : List.of("textures/gui", "themes")) {
      try (Stream<Path> walk = Files.walk(ART.resolve(folder))) {
        walk.filter(Files::isRegularFile).forEach((Path file) -> files.add(ART.relativize(file).toString().replace('\\', '/')));
      }
    }
    return files;
  }
}
