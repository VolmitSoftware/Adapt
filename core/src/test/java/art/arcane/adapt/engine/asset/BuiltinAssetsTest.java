package art.arcane.adapt.engine.asset;

import art.arcane.adapt.api.asset.AssetBundle;
import art.arcane.adapt.api.asset.AssetEntry;
import art.arcane.adapt.api.asset.AssetKind;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinAssetsTest {
  @TempDir
  Path root;

  @Test
  void theIndexListsTheBundleAndEachFileReads() throws IOException {
    byte[] icon = MemoryBundle.png(16, 16, 0xFF123456);
    Path sprite = root.resolve("assets/adapt/textures/gui/skill/agility.png");
    Files.createDirectories(sprite.getParent());
    Files.write(sprite, icon);
    Files.writeString(root.resolve(BuiltinAssets.INDEX), "textures/gui/skill/agility.png\n\n");

    try (URLClassLoader loader = new URLClassLoader(new URL[]{root.toUri().toURL()}, null)) {
      AssetBundle bundle = BuiltinAssets.bundle(loader);
      assertThat(bundle.namespace()).isEqualTo("adapt");
      assertThat(bundle.entries()).containsExactly(
          new AssetEntry(AssetKind.SPRITE, Id.adapt("gui/skill/agility"), "textures/gui/skill/agility.png"));
      assertThat(bundle.read(bundle.entries().get(0))).isEqualTo(icon);
    }
  }

  @Test
  void aLoaderWithoutTheIndexGivesAnEmptyBundle() throws IOException {
    try (URLClassLoader loader = new URLClassLoader(new URL[]{root.toUri().toURL()}, null)) {
      assertThat(BuiltinAssets.bundle(loader).entries()).isEmpty();
    }
  }
}
