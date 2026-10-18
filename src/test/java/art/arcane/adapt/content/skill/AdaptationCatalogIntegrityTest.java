package art.arcane.adapt.content.skill;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
  private static final Pattern CONSTRUCTION = Pattern.compile("\\bnew\\s+(\\w+)\\s*\\(");
  private static final Pattern DIRECT_REGISTRATION =
      Pattern.compile("\\bregisterAdaptation\\s*\\(\\s*new\\s+(\\w+)\\s*\\(");
  private static final Pattern LOCAL_CONSTRUCTION =
      Pattern.compile("\\b([A-Z]\\w*)\\s+(\\w+)\\s*=\\s*new\\s+\\1\\s*\\(");
  private static final Pattern VARIABLE_REGISTRATION =
      Pattern.compile("\\bregisterAdaptation\\s*\\(\\s*(\\w+)\\s*\\)");

  @Test
  void everyConcreteAdaptationHasExactlyOneSkillConstructionPath() throws IOException {
    Catalog catalog = adaptationCatalog();
    Map<String, Path> adaptations = catalog.adaptations();
    String skillSource = combinedJavaSource(SKILL_ROOT);
    Map<String, Integer> constructions = constructionCounts(skillSource);
    Set<String> registered = registeredClasses(skillSource);

    assertThat(adaptations).hasSize(312);
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
      counts.merge(matcher.group(1), 1, Integer::sum);
    }
    return counts;
  }

  private static Set<String> registeredClasses(String source) {
    Set<String> registered = new HashSet<>();
    Matcher direct = DIRECT_REGISTRATION.matcher(source);
    while (direct.find()) {
      registered.add(direct.group(1));
    }

    Set<String> registeredVariables = new HashSet<>();
    Matcher variables = VARIABLE_REGISTRATION.matcher(source);
    while (variables.find()) {
      registeredVariables.add(variables.group(1));
    }

    Matcher locals = LOCAL_CONSTRUCTION.matcher(source);
    while (locals.find()) {
      if (registeredVariables.contains(locals.group(2))) {
        registered.add(locals.group(1));
      }
    }
    return registered;
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
