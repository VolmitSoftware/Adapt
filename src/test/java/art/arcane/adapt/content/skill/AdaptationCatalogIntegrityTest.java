package art.arcane.adapt.content.skill;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptationCatalogIntegrityTest {
  private static final Path ADAPTATION_ROOT =
      Path.of("src/main/java/art/arcane/adapt/content/adaptation");
  private static final Path SKILL_ROOT =
      Path.of("src/main/java/art/arcane/adapt/content/skill");
  private static final Pattern ADAPTATION_CLASS = Pattern.compile(
      "(?m)^public\\s+(?:final\\s+)?class\\s+(\\w+)\\s+extends\\s+SimpleAdaptation<"
  );
  private static final Pattern ADAPTATION_ID = Pattern.compile("\\bsuper\\(\"([^\"]+)\"\\)");

  @Test
  void everyConcreteAdaptationHasExactlyOneSkillConstructionPath() throws IOException {
    Catalog catalog = adaptationCatalog();
    Map<String, Path> adaptations = catalog.adaptations();
    String skillSource = combinedJavaSource(SKILL_ROOT);

    assertThat(adaptations).hasSize(312);
    assertThat(catalog.ids()).hasSize(adaptations.size());
    for (Map.Entry<String, Path> entry : adaptations.entrySet()) {
      Pattern construction = Pattern.compile("\\bnew\\s+" + Pattern.quote(entry.getKey()) + "\\s*\\(");
      Matcher matcher = construction.matcher(skillSource);
      int constructions = 0;
      while (matcher.find()) {
        constructions++;
      }
      assertThat(constructions)
          .as("%s from %s", entry.getKey(), entry.getValue())
          .isEqualTo(1);
      assertThat(hasRegistration(skillSource, entry.getKey()))
          .as("skill registration for %s", entry.getKey())
          .isTrue();
    }
  }

  private static boolean hasRegistration(String source, String className) {
    String quotedClass = Pattern.quote(className);
    Pattern direct = Pattern.compile(
        "\\bregisterAdaptation\\s*\\(\\s*new\\s+" + quotedClass + "\\s*\\("
    );
    if (direct.matcher(source).find()) {
      return true;
    }

    Pattern assignment = Pattern.compile(
        "\\b" + quotedClass + "\\s+(\\w+)\\s*=\\s*new\\s+" + quotedClass + "\\s*\\("
    );
    Matcher assignments = assignment.matcher(source);
    while (assignments.find()) {
      Pattern indirect = Pattern.compile(
          "\\bregisterAdaptation\\s*\\(\\s*" + Pattern.quote(assignments.group(1)) + "\\s*\\)"
      );
      if (indirect.matcher(source).find()) {
        return true;
      }
    }
    return false;
  }

  private static Catalog adaptationCatalog() throws IOException {
    Map<String, Path> adaptations = new TreeMap<>();
    Map<String, String> ids = new TreeMap<>();
    try (Stream<Path> files = Files.walk(ADAPTATION_ROOT)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
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
        String adaptationId = idMatcher.group(1);
        String previousClass = ids.put(adaptationId, className);
        assertThat(previousClass)
            .as("duplicate adaptation id %s in %s and %s", adaptationId, previousClass, className)
            .isNull();
      }
    }
    return new Catalog(adaptations, ids);
  }

  private static String combinedJavaSource(Path root) throws IOException {
    StringBuilder source = new StringBuilder();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        source.append(Files.readString(file)).append('\n');
      }
    }
    return source.toString();
  }

  private record Catalog(Map<String, Path> adaptations, Map<String, String> ids) {
  }
}
