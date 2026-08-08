package art.arcane.adapt.api.mutation;

import art.arcane.adapt.util.config.ConfigDoc;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MutationDocumentationCoverageTest {
  private static final Pattern TABLE_KEY = Pattern.compile("(?m)^\\| `([^`]+)` \\|");
  private static final Pattern TYPE_HEADING = Pattern.compile(
      "(?m)^###\\s+.+?\\s+\\(`([^`]+)`\\)\\s*$"
  );

  @Test
  void mutationDocsCoverEveryAnnotatedConfigField() throws IOException {
    Path docs = Path.of(System.getProperty("user.dir"), "docs");
    String overview = Files.readString(docs.resolve("34 - Mutations Overview.md"));
    String catalog = Files.readString(docs.resolve("35 - Mutations Catalog.md"));

    Set<String> coreFields = annotatedFields(MutationConfig.class);
    Set<String> documentedCore = tableKeys(section(
        overview,
        "## Core config defaults (`MutationConfig`)",
        "Hotload watches"
    ));
    assertThat(documentedCore).containsExactlyInAnyOrderElementsOf(coreFields);

    Set<String> profileFields = annotatedFields(MutationConfig.Profile.class);
    Set<String> documentedProfile = tableKeys(section(
        overview,
        "### Per-type profile defaults",
        "### Cooperative consent modes"
    ));
    assertThat(documentedProfile).containsExactlyInAnyOrderElementsOf(profileFields);

    Map<String, String> sections = typeSections(catalog);
    int typeSpecificFields = 0;
    MutationConfig defaults = MutationConfig.defaults();
    for (MutationType type : MutationType.values()) {
      Set<String> sourceFields = annotatedFields(defaults.profile(type).getClass());
      Set<String> documentedFields = tableKeys(sections.get(type.id()));
      assertThat(documentedFields)
          .as("type-specific mutation config for %s", type.id())
          .containsExactlyInAnyOrderElementsOf(sourceFields);
      typeSpecificFields += documentedFields.size();
    }

    assertThat(coreFields).hasSize(19);
    assertThat(profileFields).hasSize(6);
    assertThat(typeSpecificFields).isEqualTo(68);
  }

  private static Set<String> annotatedFields(Class<?> type) {
    LinkedHashSet<String> names = new LinkedHashSet<>();
    for (Field field : type.getDeclaredFields()) {
      if (field.isAnnotationPresent(ConfigDoc.class)) {
        names.add(field.getName());
      }
    }
    return names;
  }

  private static Set<String> tableKeys(String markdown) {
    LinkedHashSet<String> keys = new LinkedHashSet<>();
    Matcher matcher = TABLE_KEY.matcher(markdown);
    while (matcher.find()) {
      keys.add(matcher.group(1));
    }
    return keys;
  }

  private static String section(String markdown, String startMarker, String endMarker) {
    int start = markdown.indexOf(startMarker);
    int end = markdown.indexOf(endMarker, start + startMarker.length());
    assertThat(start).as("section start %s", startMarker).isGreaterThanOrEqualTo(0);
    assertThat(end).as("section end %s", endMarker).isGreaterThan(start);
    return markdown.substring(start, end);
  }

  private static Map<String, String> typeSections(String markdown) {
    LinkedHashMap<String, String> sections = new LinkedHashMap<>();
    Matcher matcher = TYPE_HEADING.matcher(markdown);
    String previousId = null;
    int previousOffset = -1;
    while (matcher.find()) {
      if (previousId != null) {
        sections.put(previousId, markdown.substring(previousOffset, matcher.start()));
      }
      previousId = matcher.group(1);
      previousOffset = matcher.start();
    }
    if (previousId != null) {
      sections.put(previousId, markdown.substring(previousOffset));
    }
    assertThat(sections).hasSize(15);
    return sections;
  }
}
