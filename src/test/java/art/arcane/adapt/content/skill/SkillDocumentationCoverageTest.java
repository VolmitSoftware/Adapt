package art.arcane.adapt.content.skill;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
  private static final Pattern SKILL_CLASS = Pattern.compile("(?m)^\\| Class \\| `(Skill\\w+)` \\|$");
  private static final Pattern EVENT_HANDLER = Pattern.compile(
      "@(?:EventHandler|ReflectiveHandler)(?:\\([^)]*\\))?\\s+"
          + "(?:@[\\w.]+(?:\\([^\\r\\n]*\\))?\\s+)*"
          + "(?:public|protected|private)\\s+void\\s+(\\w+)"
          + "\\s*\\(\\s*([\\w.]+)",
      Pattern.DOTALL
  );
  private static final Pattern EVENT_TYPE = Pattern.compile("\\b([A-Z][A-Za-z0-9_]*Event)\\b");

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
    int documentedSkillFields = 0;
    int documentedAdaptationFields = 0;

    for (Path doc : docs) {
      String markdown = Files.readString(doc);
      documentedSkillFields += assertSkillConfigTable(doc, markdown);
    }

    Map<String, AdaptationSource> sourceCatalog = adaptationCatalog();
    Map<String, String> sections = adaptationSections(docs);
    for (Map.Entry<String, AdaptationSource> entry : sourceCatalog.entrySet()) {
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

  @Test
  void everyAdaptationEventHandlerAppearsInItsOwnList() throws IOException {
    List<Path> docs = skillDocs();
    Map<String, String> sections = adaptationSections(docs);

    for (Map.Entry<String, AdaptationSource> entry : adaptationCatalog().entrySet()) {
      Set<String> sourceEventTypes = eventTypes(entry.getValue().source());
      Set<String> documentedEventTypes = documentedEvents(sections.get(entry.getKey()));
      assertThat(documentedEventTypes)
          .as("event list for %s in %s", entry.getKey(), entry.getValue().path())
          .containsAll(sourceEventTypes);
    }
  }

  private static int assertSkillConfigTable(Path doc, String markdown) throws IOException {
    Matcher classMatcher = SKILL_CLASS.matcher(markdown);
    assertThat(classMatcher.find()).as("skill class in %s", doc).isTrue();
    String className = classMatcher.group(1);
    String source = Files.readString(SKILL_ROOT.resolve(className + ".java"));
    Set<String> sourceFields = configFields(source);
    if (Pattern.compile("(?m)^\\s*String\\s+skillColor\\s*=").matcher(source).find()) {
      sourceFields.add("skillColor");
    }
    Set<String> documentedFields = configTableKeys(headingSection(markdown, "### Skill configuration defaults"));
    assertThat(documentedFields)
        .as("skill config table for %s", className)
        .containsExactlyInAnyOrderElementsOf(sourceFields);
    return documentedFields.size();
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

  private static Set<String> eventTypes(String source) {
    LinkedHashSet<String> handlers = new LinkedHashSet<>();
    Matcher matcher = EVENT_HANDLER.matcher(source);
    while (matcher.find()) {
      String eventType = matcher.group(2);
      int separator = eventType.lastIndexOf('.');
      String simpleEventType = separator < 0 ? eventType : eventType.substring(separator + 1);
      handlers.add(simpleEventType);
    }
    return handlers;
  }

  private static Set<String> documentedEvents(String section) {
    assertThat(section).as("adaptation reference section").isNotNull();
    int marker = section.indexOf("Listened events:");
    if (marker < 0) {
      marker = section.indexOf("| Listened events |");
      if (marker < 0) {
        return Set.of();
      }
      int end = section.indexOf('\n', marker);
      return eventTypesIn(section.substring(marker, end < 0 ? section.length() : end));
    }
    int lineEnd = section.indexOf('\n', marker);
    String inline = section.substring(marker + "Listened events:".length(), lineEnd < 0 ? section.length() : lineEnd).trim();
    String eventText = inline.isEmpty() ? bulletList(section, lineEnd + 1) : inline;
    return eventTypesIn(eventText);
  }

  private static Set<String> eventTypesIn(String text) {
    LinkedHashSet<String> handlers = new LinkedHashSet<>();
    Matcher matcher = EVENT_TYPE.matcher(text);
    while (matcher.find()) {
      handlers.add(matcher.group(1));
    }
    return handlers;
  }

  private static Map<String, String> adaptationSections(List<Path> docs) throws IOException {
    LinkedHashMap<String, String> sections = new LinkedHashMap<>();
    for (Path doc : docs) {
      String markdown = Files.readString(doc);
      String sharedEvents = sharedEvents(markdown);
      Matcher configFiles = ADAPTATION_CONFIG_FILE.matcher(markdown);
      while (configFiles.find()) {
        String id = configFiles.group(1);
        int headingMarker = markdown.lastIndexOf("\n### ", configFiles.start());
        assertThat(headingMarker).as("reference heading for %s in %s", id, doc).isGreaterThanOrEqualTo(0);
        int end = nextHeading(markdown, configFiles.end());
        String section = markdown.substring(headingMarker + 1, end);
        if (!sharedEvents.isEmpty() && !section.contains("Listened events:")) {
          section = section + "\nListened events: " + sharedEvents + '\n';
        }
        String previous = sections.put(id, section);
        assertThat(previous).as("duplicate documented adaptation id %s", id).isNull();
      }
    }
    assertThat(sections).hasSize(312);
    return sections;
  }

  private static String bulletList(String text, int start) {
    StringBuilder bullets = new StringBuilder();
    int cursor = start;
    boolean found = false;
    while (cursor >= 0 && cursor < text.length()) {
      int lineEnd = text.indexOf('\n', cursor);
      int end = lineEnd < 0 ? text.length() : lineEnd;
      String line = text.substring(cursor, end).trim();
      if (line.isEmpty()) {
        if (found) {
          break;
        }
      } else if (line.startsWith("- ")) {
        bullets.append(line).append('\n');
        found = true;
      } else {
        break;
      }
      cursor = lineEnd < 0 ? -1 : lineEnd + 1;
    }
    return bullets.toString();
  }

  private static String sharedEvents(String markdown) {
    int marker = markdown.indexOf("They all listen to");
    if (marker < 0) {
      return "";
    }
    int end = markdown.indexOf('\n', marker);
    return markdown.substring(marker, end < 0 ? markdown.length() : end);
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

}
