package art.arcane.adapt.engine.asset;

import art.arcane.adapt.api.asset.AssetBundle;
import art.arcane.adapt.api.asset.AssetEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AssetBundle#resources} lists an addon jar from its entry table, so a jar built with directories stripped, like
 * Adapt's own Bukkit distribution, lists the same as one that stores them.
 */
class JarListingTest {
  private static final String OWNER = JarOwner.class.getName().replace('.', '/') + ".class";
  private static final List<String> FILES = List.of("assets/alchemist/textures/gui/skill/alchemy.png",
      "assets/alchemist/themes/alchemy.json");

  @TempDir
  Path folder;

  @Test
  void aJarWithDirectoryEntriesListsItsFiles() throws Exception {
    assertThat(list(jar("with.jar", true))).containsExactly("textures/gui/skill/alchemy.png", "themes/alchemy.json");
  }

  @Test
  void aJarWithoutDirectoryEntriesListsTheSameFiles() throws Exception {
    assertThat(list(jar("without.jar", false))).containsExactly("textures/gui/skill/alchemy.png", "themes/alchemy.json");
  }

  private static List<String> list(Path jar) throws Exception {
    try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
      Class<?> owner = Class.forName(JarOwner.class.getName(), false, loader);
      assertThat(owner.getClassLoader()).isSameAs(loader);
      return AssetBundle.resources(owner, "assets/alchemist").entries().stream().map(AssetEntry::path).toList();
    }
  }

  private Path jar(String name, boolean directories) throws IOException {
    Path jar = folder.resolve(name);
    try (OutputStream file = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(file)) {
      if (directories) {
        for (String directory : List.of("art/", "art/arcane/", "art/arcane/adapt/", "art/arcane/adapt/engine/",
            "art/arcane/adapt/engine/asset/", "assets/", "assets/alchemist/", "assets/alchemist/textures/",
            "assets/alchemist/textures/gui/", "assets/alchemist/textures/gui/skill/", "assets/alchemist/themes/")) {
          out.putNextEntry(new JarEntry(directory));
          out.closeEntry();
        }
      }
      out.putNextEntry(new JarEntry(OWNER));
      try (InputStream owner = JarOwner.class.getClassLoader().getResourceAsStream(OWNER)) {
        owner.transferTo(out);
      }
      out.closeEntry();
      for (String path : FILES) {
        out.putNextEntry(new JarEntry(path));
        out.write(MemoryBundle.png(16, 16, 0xFF000000));
        out.closeEntry();
      }
    }
    return jar;
  }
}
