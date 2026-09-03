package art.arcane.adapt.content.skill;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SkillDocumentationCoverageTest {
  private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));
  private static final Path ADAPTATION_ROOT =
      PROJECT_ROOT.resolve("src/main/java/art/arcane/adapt/content/adaptation");
  private static final Path SKILL_ROOT =
      PROJECT_ROOT.resolve("src/main/java/art/arcane/adapt/content/skill");
  private static final Path DOCS_ROOT = PROJECT_ROOT.resolve("../docs/adapt").normalize();
  private static final Pattern ADAPTATION_CLASS = Pattern.compile(
      "(?m)^public\\s+(?:final\\s+)?class\\s+(\\w+)\\s+extends\\s+SimpleAdaptation<([\\w.]+)>"
  );
  private static final Pattern ADAPTATION_ID = Pattern.compile("\\bsuper\\(\"([^\"]+)\"\\)");
  private static final Pattern ADAPTATION_HEADING = Pattern.compile(
      "(?m)^###\\s+.+?\\s+\\(`([^`]+)`\\)\\s*$"
  );
  private static final Pattern ADAPTATION_CONFIG_FILE = Pattern.compile(
      "plugins/Adapt/adaptations/([a-z0-9-]+)\\.toml"
  );
  private static final Pattern CONFIG_FIELD = Pattern.compile(
      "@(?:art\\.arcane\\.adapt\\.util\\.config\\.)?ConfigDoc(?:\\([^\\r\\n]*\\))?\\R"
          + "\\s*(?:(?:private|protected|public)\\s+)?(?:static\\s+)?(?:final\\s+)?"
          + "[\\w.<>?,\\[\\] ]+\\s+(\\w+)\\s*="
  );
  private static final Pattern TABLE_KEY = Pattern.compile("(?m)^\\| `([^`]+)` \\|");
  private static final Pattern SKILL_ID = Pattern.compile("\\bsuper\\(\"([^\"]+)\"\\s*,");
  private static final Pattern SKILL_CONFIG_FILE = Pattern.compile(
      "plugins/Adapt/skills/([a-z0-9-]+)\\.toml"
  );

  @Test
  void everyConcreteAdaptationIdAppearsExactlyOnceInSkillDocs() throws IOException {
    Map<String, AdaptationSource> sourceCatalog = adaptationCatalog();
    Map<String, Integer> documentedIds = new LinkedHashMap<>();
    for (Path doc : skillDocs()) {
      Matcher headings = ADAPTATION_HEADING.matcher(Files.readString(doc));
      while (headings.find()) {
        documentedIds.merge(headings.group(1), 1, Integer::sum);
      }
    }

    assertThat(sourceCatalog).hasSize(312);
    assertThat(documentedIds.keySet()).containsExactlyInAnyOrderElementsOf(sourceCatalog.keySet());
    assertThat(documentedIds.values()).allMatch(count -> count == 1);
  }

  @Test
  void everySkillAndAdaptationConfigFieldAppearsInItsOwnTable() throws IOException {
    List<Path> docs = skillDocs();
    Map<String, SkillSource> skillSourceCatalog = skillCatalog();
    int documentedSkillFields = 0;
    int documentedAdaptationFields = 0;

    for (Path doc : docs) {
      String markdown = Files.readString(doc);
      documentedSkillFields += assertSkillConfigTable(doc, markdown, skillSourceCatalog);
    }

    Map<String, AdaptationSource> adaptationSourceCatalog = adaptationCatalog();
    Map<String, String> sections = adaptationSections(docs);
    for (Map.Entry<String, AdaptationSource> entry : adaptationSourceCatalog.entrySet()) {
      Set<String> sourceFields = adaptationConfigFields(entry.getValue());
      Set<String> documentedFields = configTableKeys(sections.get(entry.getKey()));
      assertThat(documentedFields)
          .as("adaptation config table for %s in %s", entry.getKey(), entry.getValue().path())
          .containsExactlyInAnyOrderElementsOf(sourceFields);
      documentedAdaptationFields += documentedFields.size();
    }

    assertThat(documentedSkillFields).isEqualTo(319);
    assertThat(documentedAdaptationFields).isEqualTo(2099);
  }

  private static int assertSkillConfigTable(
      Path doc,
      String markdown,
      Map<String, SkillSource> sourceCatalog
  ) {
    Matcher configFileMatcher = SKILL_CONFIG_FILE.matcher(markdown);
    assertThat(configFileMatcher.find()).as("skill config file in %s", doc).isTrue();
    String skillId = configFileMatcher.group(1);
    SkillSource skill = sourceCatalog.get(skillId);
    assertThat(skill).as("skill source for %s in %s", skillId, doc).isNotNull();
    Set<String> sourceFields = configFields(skill.source());
    if (Pattern.compile("(?m)^\\s*String\\s+skillColor\\s*=").matcher(skill.source()).find()) {
      sourceFields.add("skillColor");
    }
    Set<String> documentedFields = configTableKeys(headingSection(markdown, "### Skill configuration defaults"));
    assertThat(documentedFields)
        .as("skill config table for %s", skill.path())
        .containsExactlyInAnyOrderElementsOf(sourceFields);
    return documentedFields.size();
  }

  private static Map<String, SkillSource> skillCatalog() throws IOException {
    LinkedHashMap<String, SkillSource> catalog = new LinkedHashMap<>();
    try (Stream<Path> files = Files.list(SKILL_ROOT)) {
      for (Path file : files
          .filter(path -> path.getFileName().toString().matches("Skill\\w+\\.java"))
          .sorted()
          .toList()) {
        String source = Files.readString(file);
        Matcher idMatcher = SKILL_ID.matcher(source);
        assertThat(idMatcher.find()).as("skill id in %s", file).isTrue();
        String id = idMatcher.group(1);
        SkillSource previous = catalog.put(id, new SkillSource(file, source));
        assertThat(previous).as("duplicate skill id %s", id).isNull();
      }
    }
    assertThat(catalog).hasSize(23);
    return catalog;
  }

  private static Map<String, AdaptationSource> adaptationCatalog() throws IOException {
    LinkedHashMap<String, AdaptationSource> catalog = new LinkedHashMap<>();
    try (Stream<Path> files = Files.walk(ADAPTATION_ROOT)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
        String source = Files.readString(file);
        Matcher classMatcher = ADAPTATION_CLASS.matcher(source);
        if (!classMatcher.find()) {
          continue;
        }
        Matcher idMatcher = ADAPTATION_ID.matcher(source);
        assertThat(idMatcher.find()).as("adaptation id in %s", file).isTrue();
        String id = idMatcher.group(1);
        AdaptationSource previous = catalog.put(
            id,
            new AdaptationSource(file, source, classMatcher.group(2))
        );
        assertThat(previous).as("duplicate adaptation id %s", id).isNull();
      }
    }
    return catalog;
  }

  private static Set<String> adaptationConfigFields(AdaptationSource adaptation) throws IOException {
    Set<String> fields = configFields(adaptation.source());
    if (!fields.isEmpty() || adaptation.configType().equals("Config") || adaptation.configType().endsWith(".Config")) {
      return fields;
    }
    String fileName = adaptation.configType().substring(adaptation.configType().lastIndexOf('.') + 1) + ".java";
    try (Stream<Path> files = Files.walk(ADAPTATION_ROOT)) {
      Path configFile = files
          .filter(path -> path.getFileName().toString().equals(fileName))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("Missing adaptation config source " + fileName));
      return configFields(Files.readString(configFile));
    }
  }

  private static Set<String> configFields(String source) {
    LinkedHashSet<String> fields = new LinkedHashSet<>();
    Matcher matcher = CONFIG_FIELD.matcher(source);
    while (matcher.find()) {
      fields.add(matcher.group(1));
    }
    return fields;
  }

  private static Map<String, String> adaptationSections(List<Path> docs) throws IOException {
    LinkedHashMap<String, String> sections = new LinkedHashMap<>();
    for (Path doc : docs) {
      String markdown = Files.readString(doc);
      Matcher configFiles = ADAPTATION_CONFIG_FILE.matcher(markdown);
      while (configFiles.find()) {
        String id = configFiles.group(1);
        int headingMarker = markdown.lastIndexOf("\n### ", configFiles.start());
        assertThat(headingMarker).as("reference heading for %s in %s", id, doc).isGreaterThanOrEqualTo(0);
        int end = nextHeading(markdown, configFiles.end());
        String section = markdown.substring(headingMarker + 1, end);
        String previous = sections.put(id, section);
        assertThat(previous).as("duplicate documented adaptation id %s", id).isNull();
      }
    }
    assertThat(sections).hasSize(312);
    return sections;
  }

  private static String headingSection(String markdown, String heading) {
    int start = markdown.indexOf(heading);
    assertThat(start).as("documentation heading %s", heading).isGreaterThanOrEqualTo(0);
    return markdown.substring(start, nextHeading(markdown, start + heading.length()));
  }

  private static int nextHeading(String markdown, int start) {
    int nextLevelThree = markdown.indexOf("\n### ", start);
    int nextLevelTwo = markdown.indexOf("\n## ", start);
    if (nextLevelThree < 0) {
      return nextLevelTwo < 0 ? markdown.length() : nextLevelTwo;
    }
    if (nextLevelTwo < 0) {
      return nextLevelThree;
    }
    return Math.min(nextLevelThree, nextLevelTwo);
  }

  private static Set<String> configTableKeys(String text) {
    assertThat(text).as("documentation config section").isNotNull();
    int start = text.indexOf("| Key |");
    if (start < 0) {
      return Set.of();
    }
    int end = text.indexOf("\n\n", start);
    String table = end < 0 ? text.substring(start) : text.substring(start, end);
    LinkedHashSet<String> keys = new LinkedHashSet<>();
    Matcher matcher = TABLE_KEY.matcher(table);
    while (matcher.find()) {
      keys.add(matcher.group(1));
    }
    return keys;
  }

  private static List<Path> skillDocs() throws IOException {
    try (Stream<Path> files = Files.list(DOCS_ROOT)) {
      List<Path> docs = files
          .filter(path -> path.getFileName().toString().matches("(?:1[1-9]|2[0-9]|3[0-3])-skill-.+\\.md"))
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .toList();
      assertThat(docs).hasSize(23);
      return docs;
    }
  }

  private record AdaptationSource(Path path, String source, String configType) {
  }

  private record SkillSource(Path path, String source) {
  }

}
