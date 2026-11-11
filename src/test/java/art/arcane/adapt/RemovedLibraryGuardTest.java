package art.arcane.adapt;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemovedLibraryGuardTest {
  private static final Path MAIN_ROOT = Path.of("src/main");
  private static final List<String> REMOVED_PACKAGES = List.of(
      "art.arcane.chrono",
      "art.arcane.spatial",
      "art.arcane.curse",
      "art.arcane.amulet",
      "art.arcane.fukkit",
      "Fukkit.extensions",
      "Amulet.extensions"
  );
  private static final String MANIFOLD = "manifold";
  private static final List<String> REMOVED_BUILD_WORDS = List.of(
      "volmitdev",
      "fukkit",
      "amulet",
      MANIFOLD,
      "art.arcane.chrono",
      "art.arcane.spatial",
      "art.arcane.curse",
      "libs.chrono",
      "libs.spatial",
      "libs.curse",
      "concurrentlinkedhashmap",
      "libs.lur"
  );
  private static final List<Path> BUILD_FILES = List.of(
      Path.of("build.gradle"),
      Path.of("settings.gradle"),
      Path.of("gradle.properties"),
      Path.of("gradle/libs.versions.toml"),
      Path.of("packaging")
  );
  private static final List<String> REMOVED_CLASSES = List.of(
      "manifold.rt.api.util.Pair",
      "manifold.ext.rt.api.Extension",
      "art.arcane.chrono.ChronoLatch",
      "art.arcane.chrono.PrecisionStopwatch",
      "art.arcane.chrono.RollingSequence",
      "art.arcane.spatial.matter.SpatialMatter",
      "art.arcane.spatial.mantle.Mantle",
      "art.arcane.curse.Curse",
      "art.arcane.amulet.Amulet",
      "art.arcane.fukkit.Fukkit"
  );

  @Test
  void mainSourcesAndResourcesDoNotMentionRemovedLibraries() throws IOException {
    List<Path> files = regularFiles(MAIN_ROOT);
    List<String> needles = new ArrayList<>(dotAndSlashForms(REMOVED_PACKAGES));
    needles.add(MANIFOLD);

    assertThat(files).hasSizeGreaterThan(1000);
    assertThat(mentions(files, needles, false)).isEmpty();
  }

  @Test
  void buildFilesDoNotMentionRemovedLibraries() throws IOException {
    List<Path> files = new ArrayList<>();
    for (Path buildFile : BUILD_FILES) {
      assertThat(buildFile).exists();
      files.addAll(regularFiles(buildFile));
    }

    assertThat(mentions(files, REMOVED_BUILD_WORDS, true)).isEmpty();
  }

  @Test
  void compiledMainClassesDoNotLinkRemovedLibraries() throws IOException, URISyntaxException {
    Path classes = Path.of(Adapt.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    List<Path> classFiles = regularFiles(classes).stream()
        .filter(path -> path.toString().endsWith(".class"))
        .toList();
    List<String> needles = new ArrayList<>(dotAndSlashForms(REMOVED_PACKAGES));
    needles.add(MANIFOLD + "/");
    needles.add(MANIFOLD + ".");

    assertThat(classFiles).hasSizeGreaterThan(1000);
    assertThat(mentions(classFiles, needles, false)).isEmpty();
  }

  @Test
  void removedLibraryClassesAreNotOnTheTestClasspath() {
    ClassLoader loader = getClass().getClassLoader();
    for (String removed : REMOVED_CLASSES) {
      assertThatThrownBy(() -> Class.forName(removed, false, loader))
          .as(removed)
          .isInstanceOf(ClassNotFoundException.class);
      assertThat(loader.getResource(removed.replace('.', '/') + ".class")).as(removed).isNull();
    }
  }

  @Test
  void removedLibraryJarsAreNotOnTheTestClasspath() {
    List<String> jars = Stream.of(System.getProperty("java.class.path").split(File.pathSeparator))
        .map(entry -> Path.of(entry).getFileName().toString().toLowerCase(Locale.ROOT))
        .filter(name -> name.endsWith(".jar"))
        .toList();

    assertThat(jars).isNotEmpty();
    assertThat(jars).noneMatch(name -> name.startsWith("fukkit-")
        || name.startsWith("amulet-")
        || name.startsWith("chrono-")
        || name.startsWith("spatial-")
        || name.startsWith("curse-")
        || name.startsWith(MANIFOLD));
  }

  private static List<String> dotAndSlashForms(List<String> packages) {
    List<String> forms = new ArrayList<>();
    for (String name : packages) {
      forms.add(name);
      forms.add(name.replace('.', '/'));
    }
    return forms;
  }

  private static List<Path> regularFiles(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter(Files::isRegularFile).sorted().toList();
    }
  }

  private static List<String> mentions(List<Path> files, List<String> needles, boolean ignoreCase) throws IOException {
    List<String> found = new ArrayList<>();
    for (Path file : files) {
      String content = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
      String haystack = ignoreCase ? content.toLowerCase(Locale.ROOT) : content;
      for (String needle : needles) {
        if (haystack.contains(ignoreCase ? needle.toLowerCase(Locale.ROOT) : needle)) {
          found.add(file + " mentions " + needle);
        }
      }
    }
    return found;
  }
}
