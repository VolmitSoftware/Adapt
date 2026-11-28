package art.arcane.adapt.content.skill;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Assumptions;
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
  private static final Path CORE_ADAPTATION_ROOT =
      PROJECT_ROOT.resolve("../content/src/main/java/art/arcane/adapt/content/adaptation").normalize();
  private static final Path CORE_SKILL_ROOT =
      PROJECT_ROOT.resolve("../content/src/main/java/art/arcane/adapt/content/skill").normalize();
  private static final Path DOCS_ROOT = PROJECT_ROOT.resolve("../../docs/adapt").normalize();
  private static final Pattern ADAPTATION_CLASS = Pattern.compile(
      "(?m)^public\\s+(?:final\\s+)?class\\s+(\\w+)\\s+extends\\s+(?:SimpleAdaptation|LegacyAdaptation)<([\\w.]+)>"
  );
  private static final Pattern CONTENT_ID =
      Pattern.compile("\\bpublic\\s+static\\s+final\\s+Id\\s+ID\\s*=\\s*Id\\.adapt\\(\"([^\"]+)\"\\);");
  private static final Pattern ADAPTATION_HEADING = Pattern.compile(
      "(?m)^###\\s+.+?\\s+\\(`([^`]+)`\\)\\s*$"
  );
  private static final Pattern CONFIG_FIELD = Pattern.compile(
      "@(?:art\\.arcane\\.adapt\\.api\\.config\\.)?ConfigDoc(?:\\([^\\r\\n]*\\))?\\R"
          + "\\s*(?:(?:private|protected|public)\\s+)?(?:static\\s+)?(?:final\\s+)?"
          + "[\\w.<>?,\\[\\] ]+\\s+(\\w+)\\s*="
  );
  private static final List<String> SKILL_BASE_FIELDS = List.of("enabled", "skillColor");
  private static final Pattern BASE_INTERVAL_DEFAULT = Pattern.compile("(?m)^\\s*setInterval\\s*=");
  private static final Pattern TABLE_KEY = Pattern.compile("(?m)^\\| `([^`]+)` \\|");
  private static final Pattern SKILL_CONFIG_FILE = Pattern.compile(
      "plugins/Adapt/skills/([a-z0-9-]+)\\.toml"
  );

  private static void assumeDocs() {
    Assumptions.assumeTrue(Files.isDirectory(DOCS_ROOT), "docs repo is not checked out beside Adapt");
  }

  @Test
  void everyConcreteAdaptationIdAppearsExactlyOnceInSkillDocs() throws IOException {
    assumeDocs();
    Map<Id, AdaptationSource> sourceCatalog = adaptationCatalog();
    Map<Id, Integer> documentedIds = new LinkedHashMap<>();
    for (Path doc : skillDocs()) {
      Matcher headings = ADAPTATION_HEADING.matcher(Files.readString(doc));
      while (headings.find()) {
        documentedIds.merge(documentedAdaptation(headings.group(1)), 1, Integer::sum);
      }
    }

    assertThat(sourceCatalog).hasSize(333);
    assertThat(documentedIds.keySet()).containsExactlyInAnyOrderElementsOf(sourceCatalog.keySet());
    assertThat(documentedIds.values()).allMatch(count -> count == 1);
  }

  @Test
  void everySkillAndAdaptationConfigFieldAppearsInItsOwnTable() throws IOException {
    assumeDocs();
    List<Path> docs = skillDocs();
    Map<Id, SkillSource> skillSourceCatalog = skillCatalog();
    int documentedSkillFields = 0;
    int documentedAdaptationFields = 0;

    for (Path doc : docs) {
      String markdown = Files.readString(doc);
      documentedSkillFields += assertSkillConfigTable(doc, markdown, skillSourceCatalog);
    }

    Map<Id, AdaptationSource> adaptationSourceCatalog = adaptationCatalog();
    Map<Id, String> sections = adaptationSections(docs);
    for (Map.Entry<Id, AdaptationSource> entry : adaptationSourceCatalog.entrySet()) {
      Set<String> sourceFields = adaptationConfigFields(entry.getValue());
      Set<String> documentedFields = configTableKeys(sections.get(entry.getKey()));
      assertThat(documentedFields)
          .as("adaptation config table for %s in %s", entry.getKey(), entry.getValue().path())
          .containsExactlyInAnyOrderElementsOf(sourceFields);
      documentedAdaptationFields += documentedFields.size();
    }

    assertThat(documentedSkillFields).isEqualTo(462);
    assertThat(documentedAdaptationFields).isEqualTo(2390);
  }

  private static int assertSkillConfigTable(
      Path doc,
      String markdown,
      Map<Id, SkillSource> sourceCatalog
  ) {
    Matcher configFileMatcher = SKILL_CONFIG_FILE.matcher(markdown);
    assertThat(configFileMatcher.find()).as("skill config file in %s", doc).isTrue();
    Id skillId = Id.adapt(configFileMatcher.group(1));
    SkillSource skill = sourceCatalog.get(skillId);
    assertThat(skill).as("skill source for %s in %s", skillId, doc).isNotNull();
    Set<String> sourceFields = configFields(skill.source());
    sourceFields.addAll(SKILL_BASE_FIELDS);
    if (BASE_INTERVAL_DEFAULT.matcher(skill.source()).find()) {
      sourceFields.add("setInterval");
    }
    Set<String> documentedFields = configTableKeys(headingSection(markdown, "### Skill configuration defaults"));
    assertThat(documentedFields)
        .as("skill config table for %s", skill.path())
        .containsExactlyInAnyOrderElementsOf(sourceFields);
    return documentedFields.size();
  }

  private static Map<Id, SkillSource> skillCatalog() throws IOException {
    LinkedHashMap<Id, SkillSource> catalog = new LinkedHashMap<>();
    try (Stream<Path> files = Files.list(CORE_SKILL_ROOT)) {
      for (Path file : files
          .filter(path -> path.getFileName().toString().matches("Skill\\w+\\.java"))
          .sorted()
          .toList()) {
        String source = Files.readString(file);
        Matcher idMatcher = CONTENT_ID.matcher(source);
        assertThat(idMatcher.find()).as("skill id in %s", file).isTrue();
        Id id = Id.adapt(idMatcher.group(1));
        SkillSource previous = catalog.put(id, new SkillSource(file, source));
        assertThat(previous).as("duplicate skill id %s", id).isNull();
      }
    }
    assertThat(catalog).hasSize(24);
    return catalog;
  }

  private static Map<Id, AdaptationSource> adaptationCatalog() throws IOException {
    LinkedHashMap<Id, AdaptationSource> catalog = new LinkedHashMap<>();
    try (Stream<Path> files = Files.walk(CORE_ADAPTATION_ROOT)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
        String source = Files.readString(file);
        Matcher classMatcher = ADAPTATION_CLASS.matcher(source);
        if (!classMatcher.find()) {
          continue;
        }
        Matcher idMatcher = CONTENT_ID.matcher(source);
        assertThat(idMatcher.find()).as("adaptation id in %s", file).isTrue();
        Id id = Id.adapt(idMatcher.group(1));
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
    if (BASE_INTERVAL_DEFAULT.matcher(adaptation.source()).find()) {
      fields.add("setInterval");
    }
    if (!fields.isEmpty() || adaptation.configType().equals("Config") || adaptation.configType().endsWith(".Config")) {
      return fields;
    }
    String fileName = adaptation.configType().substring(adaptation.configType().lastIndexOf('.') + 1) + ".java";
    try (Stream<Path> files = Files.walk(CORE_ADAPTATION_ROOT)) {
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

  private static Map<Id, String> adaptationSections(List<Path> docs) throws IOException {
    LinkedHashMap<Id, String> sections = new LinkedHashMap<>();
    for (Path doc : docs) {
      String markdown = Files.readString(doc);
      Matcher headings = ADAPTATION_HEADING.matcher(markdown);
      while (headings.find()) {
        Id id = documentedAdaptation(headings.group(1));
        int end = nextHeading(markdown, headings.end());
        String section = markdown.substring(headings.start(), end);
        String previous = sections.put(id, section);
        assertThat(previous).as("duplicate documented adaptation id %s", id).isNull();
      }
    }
    assertThat(sections).hasSize(333);
    return sections;
  }

  /**
   * The docs name built-in adaptations by their flat name, such as {@code herbalism-green-thumb}; that is the adaptation
   * {@code adapt:herbalism/green_thumb}.
   */
  private static Id documentedAdaptation(String flat) {
    int split = flat.indexOf('-');
    return Id.adapt(flat.substring(0, split) + "/" + flat.substring(split + 1).replace('-', '_'));
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
          .filter(path -> path.getFileName().toString().matches("(?:1[1-9]|2[0-9]|3[0-4])-skill-.+\\.md"))
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .toList();
      assertThat(docs).hasSize(24);
      return docs;
    }
  }

  private record AdaptationSource(Path path, String source, String configType) {
  }

  private record SkillSource(Path path, String source) {
  }

}
