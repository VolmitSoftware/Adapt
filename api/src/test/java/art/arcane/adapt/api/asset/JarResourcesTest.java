package art.arcane.adapt.api.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class JarResourcesTest {
  private static final String PROBE = JarResourcesProbe.class.getName().replace('.', '/') + ".class";
  private static final Map<String, String> FILES = Map.of(
      "assets/demo/textures/gui/skill/alchemy.png", "png",
      "assets/demo/textures/gui/kit/button.png", "png",
      "assets/demo/themes/alchemy.json", "{}",
      "assets/other/textures/gui/skill/ignored.png", "png",
      "assets/demo_lang/en_US.toml", "x = 1");
  private static final List<String> EXPECTED = List.of(
      "textures/gui/kit/button.png", "textures/gui/skill/alchemy.png", "themes/alchemy.json");

  @TempDir
  Path temp;

  @Test
  void listsAJarWithoutDirectoryEntries() throws Exception {
    Path jar = jar(temp.resolve("flat.jar"), false);

    assertThat(listWith(jar)).isEqualTo(EXPECTED);
  }

  @Test
  void listsAJarWithDirectoryEntries() throws Exception {
    Path jar = jar(temp.resolve("tree.jar"), true);

    assertThat(listWith(jar)).isEqualTo(EXPECTED);
  }

  @Test
  void listsAClassFolderHoldingTheResources() throws Exception {
    Path classes = temp.resolve("classes");
    write(classes, PROBE, probeBytes());
    for (Map.Entry<String, String> file : FILES.entrySet()) {
      write(classes, file.getKey(), file.getValue().getBytes(StandardCharsets.UTF_8));
    }

    assertThat(listWith(classes)).isEqualTo(EXPECTED);
  }

  @Test
  void listsResourcesKeptInASeparateFolderFromTheClasses() throws Exception {
    Path classes = temp.resolve("classes");
    Path resources = temp.resolve("resources");
    write(classes, PROBE, probeBytes());
    for (Map.Entry<String, String> file : FILES.entrySet()) {
      write(resources, file.getKey(), file.getValue().getBytes(StandardCharsets.UTF_8));
    }

    assertThat(listWith(classes, resources)).isEqualTo(EXPECTED);
  }

  @Test
  void aMissingRootListsNothing() throws Exception {
    Path jar = jar(temp.resolve("flat.jar"), false);

    try (URLClassLoader loader = loader(jar)) {
      Class<?> owner = Class.forName(JarResourcesProbe.class.getName(), false, loader);
      assertThat(JarResources.list(owner, "assets/missing")).isEmpty();
    }
  }

  @Test
  void bundlesFromAFlatJarClassifyEveryFile() throws Exception {
    Path jar = jar(temp.resolve("flat.jar"), false);

    try (URLClassLoader loader = loader(jar)) {
      Class<?> owner = Class.forName(JarResourcesProbe.class.getName(), false, loader);
      AssetBundle bundle = AssetBundle.resources(owner, "assets/demo");
      assertThat(bundle.entries()).extracting(AssetEntry::kind)
          .containsExactly(AssetKind.GUI_TEXTURE, AssetKind.SPRITE, AssetKind.THEME_KIT);
      assertThat(new String(bundle.read(bundle.entries().get(2)), StandardCharsets.UTF_8)).isEqualTo("{}");
    }
  }

  private List<String> listWith(Path... roots) throws Exception {
    try (URLClassLoader loader = loader(roots)) {
      Class<?> owner = Class.forName(JarResourcesProbe.class.getName(), false, loader);
      assertThat(owner.getClassLoader()).isSameAs(loader);
      return JarResources.list(owner, "/assets/demo/");
    }
  }

  private static URLClassLoader loader(Path... roots) throws IOException {
    URL[] urls = new URL[roots.length];
    for (int index = 0; index < roots.length; index++) {
      urls[index] = roots[index].toUri().toURL();
    }
    return new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
  }

  private static Path jar(Path jar, boolean directoryEntries) throws IOException {
    Set<String> written = new LinkedHashSet<>();
    try (OutputStream file = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(file)) {
      Map<String, byte[]> entries = new TreeMap<>();
      entries.put(PROBE, probeBytes());
      for (Map.Entry<String, String> resource : FILES.entrySet()) {
        entries.put(resource.getKey(), resource.getValue().getBytes(StandardCharsets.UTF_8));
      }
      for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
        if (directoryEntries) {
          String name = entry.getKey();
          for (int slash = name.indexOf('/'); slash >= 0; slash = name.indexOf('/', slash + 1)) {
            String directory = name.substring(0, slash + 1);
            if (written.add(directory)) {
              out.putNextEntry(new JarEntry(directory));
              out.closeEntry();
            }
          }
        }
        out.putNextEntry(new JarEntry(entry.getKey()));
        out.write(entry.getValue());
        out.closeEntry();
      }
    }
    return jar;
  }

  private static void write(Path root, String name, byte[] bytes) throws IOException {
    Path target = root.resolve(name);
    Files.createDirectories(target.getParent());
    Files.write(target, bytes);
  }

  private static byte[] probeBytes() throws IOException {
    try (InputStream stream = JarResourcesTest.class.getClassLoader().getResourceAsStream(PROBE)) {
      return stream.readAllBytes();
    }
  }
}
