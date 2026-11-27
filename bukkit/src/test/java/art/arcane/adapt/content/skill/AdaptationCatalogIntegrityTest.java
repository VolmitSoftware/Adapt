package art.arcane.adapt.content.skill;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptationCatalogIntegrityTest {
  private static final List<Path> ADAPTATION_ROOTS = List.of(
      Path.of("src/main/java/art/arcane/adapt/content/adaptation"),
      Path.of("../content/src/main/java/art/arcane/adapt/content/adaptation"));
  private static final List<Path> SKILL_ROOTS = List.of(
      Path.of("src/main/java/art/arcane/adapt/content/skill"),
      Path.of("../content/src/main/java/art/arcane/adapt/content/skill"));
  private static final Pattern ADAPTATION_CLASS = Pattern.compile(
      "(?m)^public\\s+(?:final\\s+)?class\\s+(\\w+)\\s+extends\\s+(?:SimpleAdaptation|LegacyAdaptation)<"
  );
  private static final Pattern ADAPTATION_ID =
      Pattern.compile("\\bpublic\\s+static\\s+final\\s+Id\\s+ID\\s*=\\s*Id\\.adapt\\(\"([^\"]+)\"\\);");
  private static final Pattern SUPER_ID = Pattern.compile("\\bsuper\\(ID\\)");
  private static final Pattern CONSTRUCTION = Pattern.compile("\\bnew\\s+(\\w+)\\s*\\(|\\b(\\w+)::new\\b");
  private static final Pattern REGISTRATION = Pattern.compile(
      "\\bregisterAdaptation\\s*\\(\\s*(\\w+)\\.ID\\s*,\\s*(?:(\\w+)::new|\\(\\)\\s*->\\s*(?:\\w+\\s*=\\s*)?new\\s+(\\w+)\\s*\\()");

  @Test
  void everyConcreteAdaptationHasExactlyOneSkillConstructionPath() throws IOException {
    Catalog catalog = adaptationCatalog();
    Map<String, Path> adaptations = catalog.adaptations();
    String skillSource = combinedJavaSource(SKILL_ROOTS);
    Map<String, Integer> constructions = constructionCounts(skillSource);
    Set<String> registered = registeredClasses(skillSource);

    assertThat(adaptations).hasSize(333);
    assertThat(catalog.ids()).hasSize(adaptations.size());
    for (Map.Entry<String, Path> entry : adaptations.entrySet()) {
      assertThat(constructions.getOrDefault(entry.getKey(), 0))
          .as("%s from %s", entry.getKey(), entry.getValue())
          .isEqualTo(1);
      assertThat(registered)
          .as("skill registration for %s", entry.getKey())
          .contains(entry.getKey());
    }
  }

  private static Map<String, Integer> constructionCounts(String source) {
    Map<String, Integer> counts = new HashMap<>();
    Matcher matcher = CONSTRUCTION.matcher(source);
    while (matcher.find()) {
      String type = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
      counts.merge(type, 1, Integer::sum);
    }
    return counts;
  }

  private static Set<String> registeredClasses(String source) {
    Set<String> registered = new HashSet<>();
    Matcher matcher = REGISTRATION.matcher(source);
    while (matcher.find()) {
      String built = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
      if (matcher.group(1).equals(built)) {
        registered.add(built);
      }
    }
    return registered;
  }

  private static Catalog adaptationCatalog() throws IOException {
    Map<String, Path> adaptations = new TreeMap<>();
    Map<String, String> ids = new TreeMap<>();
    for (Path file : javaFiles(ADAPTATION_ROOTS)) {
      String source = Files.readString(file);
      Matcher classMatcher = ADAPTATION_CLASS.matcher(source);
      if (!classMatcher.find()) {
        continue;
      }
      String className = classMatcher.group(1);
      Path previous = adaptations.put(className, file);
      assertThat(previous).as("duplicate adaptation class %s", className).isNull();

      Matcher idMatcher = ADAPTATION_ID.matcher(source);
      assertThat(idMatcher.find()).as("missing adaptation id in %s", file).isTrue();
      assertThat(SUPER_ID.matcher(source).find()).as("constructor passes ID in %s", file).isTrue();
      String adaptationId = "adapt:" + idMatcher.group(1);
      String previousClass = ids.put(adaptationId, className);
      assertThat(previousClass)
          .as("duplicate adaptation id %s in %s and %s", adaptationId, previousClass, className)
          .isNull();
    }
    return new Catalog(adaptations, ids);
  }

  private static String combinedJavaSource(List<Path> roots) throws IOException {
    StringBuilder source = new StringBuilder();
    for (Path file : javaFiles(roots)) {
      source.append(Files.readString(file)).append('\n');
    }
    return source.toString();
  }

  private static List<Path> javaFiles(List<Path> roots) throws IOException {
    List<Path> javaFiles = new ArrayList<>();
    for (Path root : roots) {
      if (!Files.isDirectory(root)) {
        continue;
      }
      try (Stream<Path> files = Files.walk(root)) {
        files.filter(path -> path.toString().endsWith(".java")).sorted().forEach(javaFiles::add);
      }
    }
    return javaFiles;
  }

  private record Catalog(Map<String, Path> adaptations, Map<String, String> ids) {
  }
}
